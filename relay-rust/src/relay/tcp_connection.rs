/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use log::*;
use mio::net::TcpStream;
use mio::{Event, PollOpt, Ready, Token};
use rand::random;
use std::cell::RefCell;
use std::cmp;
use std::io;
use std::num::Wrapping;
use std::net::{SocketAddrV4};
use std::rc::{Rc, Weak};

use byteorder::{WriteBytesExt};

use super::proxy_config::ProxyConfig;
use super::proxy_config::get_proxy_for_addr;
use super::socks5_protocol::{Socks5State, Authentication};
use super::socks5_protocol;
use super::socks5_protocol::MAX_ADDR_LEN;

use super::binary;
use super::client::{Client, ClientChannel};
use super::connection::{Connection, ConnectionId};
use super::ipv4_header::Ipv4Header;
use super::ipv4_packet::{Ipv4Packet, MAX_PACKET_LENGTH};
use super::packet_source::PacketSource;
use super::packetizer::Packetizer;
use super::selector::Selector;
use super::stream_buffer::StreamBuffer;
use super::tcp_header::{self, TcpHeader, TcpHeaderMut};
use super::transport_header::{TransportHeader, TransportHeaderMut};

const TAG: &str = "TcpConnection";

// same value as GnirehtetService.MTU in the client
const MTU: u16 = 0x4000;
// 20 bytes for IP headers, 20 bytes for TCP headers
const MAX_PAYLOAD_LENGTH: u16 = MTU - 20 - 20 as u16;

pub struct TcpConnection {
    self_weak: Weak<RefCell<TcpConnection>>,
    id: ConnectionId,
    client: Weak<RefCell<Client>>,
    stream: TcpStream,
    interests: Ready,
    token: Token,
    client_to_network: StreamBuffer,
    network_to_client: Packetizer,
    packet_for_client_length: Option<u16>,
    closed: bool,
    tcb: Tcb,
    socks5_state : Socks5State,    
}

// Transport Control Block
struct Tcb {
    state: TcpState,
    syn_sequence_number: u32,
    sequence_number: Wrapping<u32>,
    acknowledgement_number: Wrapping<u32>,
    their_acknowledgement_number: u32,
    fin_sequence_number: Option<u32>,
    fin_received: bool,
    client_window: u16,
}

// See RFC793: <https://tools.ietf.org/html/rfc793#page-23>
#[derive(Debug, PartialEq, Eq)]
enum TcpState {
    Init,
    SynSent,
    SynReceived,
    Established,
    CloseWait,
    LastAck,
    Closing,
    FinWait1,
    FinWait2,
}

impl TcpState {
    fn is_connected(&self) -> bool {
        self != &TcpState::Init && self != &TcpState::SynSent && self != &TcpState::SynReceived
    }

    fn is_closed(&self) -> bool {
        self == &TcpState::FinWait1
            || self == &TcpState::FinWait2
            || self == &TcpState::Closing
            || self == &TcpState::LastAck
    }
}

impl Tcb {
    fn new() -> Self {
        Self {
            state: TcpState::Init,
            syn_sequence_number: 0,
            sequence_number: Wrapping(0),
            acknowledgement_number: Wrapping(0),
            their_acknowledgement_number: 0,
            fin_sequence_number: None,
            fin_received: false,
            client_window: 0,
        }
    }

    fn remaining_client_window(&self) -> u16 {
        let wrapped_remaining = Wrapping(self.their_acknowledgement_number)
            + Wrapping(u32::from(self.client_window))
            - self.sequence_number;
        let remaining = wrapped_remaining.0;
        if remaining <= u32::from(self.client_window) {
            remaining as u16
        } else {
            0
        }
    }

    fn numbers(&self) -> String {
        format!(
            "(seq={}, ack={})",
            self.sequence_number, self.acknowledgement_number
        )
    }
}

impl TcpConnection {
    #[allow(clippy::needless_pass_by_value)] // semantically, headers are consumed
    pub fn create(
        selector: &mut Selector,
        id: ConnectionId,
        client: Weak<RefCell<Client>>,
        ipv4_header: Ipv4Header,
        transport_header: TransportHeader,
    ) -> io::Result<Rc<RefCell<Self>>> {
        cx_info!(target: TAG, id, "Open");

        // determine if we should use proxy for destination ip
        let stream : TcpStream;
        let proxy_init_state : Socks5State;    
        match get_proxy_for_addr(id.rewritten_destination().into()) {
            None => {
                stream = Self::create_stream(&id)?;
                proxy_init_state = Socks5State::NoProxy;
            }
            Some(cnf) => {
                stream = Self::create_proxy_stream(&cnf)?;
                proxy_init_state = Socks5State::Socks5HostNotConnected;
            },
        }

        let tcp_header = Self::tcp_header_of_transport(transport_header);

        // shrink the TCP options to pass a minimal refrence header to the packetizer
        let mut shrinked_tcp_header_raw = [0u8; 20];
        shrinked_tcp_header_raw.copy_from_slice(&tcp_header.raw()[..20]);
        let mut shrinked_tcp_header_data = tcp_header.data().clone();
        {
            let mut shrinked_tcp_header =
                shrinked_tcp_header_data.bind_mut(&mut shrinked_tcp_header_raw);
            shrinked_tcp_header.shrink_options();
            assert_eq!(20, shrinked_tcp_header.header_length());
        }

        let shrinked_transport_header = shrinked_tcp_header_data
            .bind(&shrinked_tcp_header_raw)
            .into();

        let packetizer = Packetizer::new(&ipv4_header, &shrinked_transport_header);

        // interests will be set on the first packet received
        // set the initial value now so that they won't need to be updated
        let interests = Ready::writable();
        let rc = Rc::new(RefCell::new(Self {
            self_weak: Weak::new(),
            id,
            client,
            stream,
            interests,
            token: Token(0), // default value, will be set afterwards
            client_to_network: StreamBuffer::new(4 * MAX_PACKET_LENGTH),
            network_to_client: packetizer,
            packet_for_client_length: None,
            closed: false,
            tcb: Tcb::new(),
            socks5_state : proxy_init_state,
        }));

        {
            let mut self_ref = rc.borrow_mut();

            // keep a shared reference to this
            self_ref.self_weak = Rc::downgrade(&rc);

            let rc2 = rc.clone();
            // must annotate selector type: https://stackoverflow.com/a/44004103/1987178
            let handler =
                move |selector: &mut Selector, event| rc2.borrow_mut().on_ready(selector, event);
            let token =
                selector.register(&self_ref.stream, handler, interests, PollOpt::level())?;
            self_ref.token = token;
        }
        Ok(rc)
    }

    fn create_stream(id: &ConnectionId) -> io::Result<TcpStream> {
        TcpStream::connect(&id.rewritten_destination().into())
    }
    
    fn create_proxy_stream(proxy_config: &ProxyConfig ) -> io::Result<TcpStream> {
        TcpStream::connect(&proxy_config.proxy_addr.into())
    }
    
    fn remove_from_router(&self) {
        // route is embedded in router which is embedded in client: the client necessarily exists
        let client_rc = self.client.upgrade().expect("Expected client not found");
        let mut client = client_rc.borrow_mut();
        client.router().remove(self);
    }

    fn on_ready(&mut self, selector: &mut Selector, event: Event) {
        #[allow(clippy::match_wild_err_arm)]
        match self.process(selector, event) {
            Ok(_) => (),
            Err(ref err) if err.kind() == io::ErrorKind::WouldBlock => {
                cx_debug!(target: TAG, self.id, "Spurious event, ignoring")
            }
            Err(_) => panic!("Unexpected unhandled error"),
        }
    }

    fn socks5_update_interests(&mut self, selector: &mut Selector, new_interests: Ready) {
        assert!(!self.closed);                    

        cx_debug!(target: TAG, self.id, "socks5_update_interests: {:?}", new_interests);
        if self.interests != new_interests {
            // interests must be changed
            self.interests = new_interests;
            selector
                .reregister(&self.stream, self.token, new_interests, PollOpt::level())
                .expect("Cannot register on poll");
        }
    }
        
    
    fn handle_socks5_state(&mut self, selector: &mut Selector, ready: Ready) -> io::Result<()> {                
        let proxy_config: ProxyConfig;

        match get_proxy_for_addr(self.id.rewritten_destination().into()) {
            None => panic!("don't set proxy parameters"),
            Some(c) => proxy_config = c,
        };

        match self.socks5_state {
            // Gnirehtet socks5 client -> socks5 server, request to authenticate
            Socks5State::Socks5HostNotConnected => {
                let auth : Authentication = match proxy_config.username.len() {
                    0 => Authentication::None,
                    _ => Authentication::Password { username: &* (proxy_config.username), password: &* proxy_config.password },
                };

                let packet_len = if auth.is_no_auth() { 3 } else { 4 };
                let packet = [
                    socks5_protocol::consts::SOCKS5_VERSION, // protocol version
                    if auth.is_no_auth() { 1 } else { 2 }, // method count
                    0, // no auth (always offered)
                    auth.id(), // method
                ];

                self.client_to_network.read_from(&packet[..packet_len]);
                match self.client_to_network.write_to(&mut self.stream) {
                //match self.stream.write_all(packet[..packet_len]) {
                    Ok(w) => {
                        cx_debug!(target: TAG, self.id, "Write to socks5 for auth request {}, packet payload length: {}", auth.id(), w);
                        self.socks5_update_interests(selector, Ready::readable()); // change interest to write after read
                        self.socks5_state = Socks5State::Socks5AuthSend;
                    }
                    Err(err) => {
                        if err.kind() == io::ErrorKind::WouldBlock {
                            // rethrow
                            return Err(err);
                        }
                        cx_error!(target: TAG, self.id, "Cannot write socks5 for auth request: [{:?}] {}", err.kind(), err );
                        // error or hup
                        self.close(selector);
                    }
                }
            },
            // socks5 server <- Gnirehtet socks5 client, no auth or username/password authenticate
            Socks5State::Socks5AuthSend => {
                if ready.is_readable() {
                    match socks5_protocol::socks5_read_auth_method_response(&mut self.stream) {
                        Ok(selected_method) =>{
                            cx_debug!(target: TAG, self.id, "SOCKS5 LOG auth method = {}", selected_method);

                            self.socks5_update_interests(selector, Ready::writable()); // change interest to write after read

                            match selected_method {
                                0 => {
                                    // if no auth need, goto cmd connect
                                    self.socks5_state = Socks5State::Socks5AuthDone;
                                },
                                2 => {
                                    self.socks5_state = Socks5State::Socks5AuthUsernamePasswordSend;
                                }
                                _ => cx_error!(target: TAG, self.id, "SOCKS5 ERR unsupported auth method {}", selected_method)
                            }
                        }
                        Err(err) => {
                            if err.kind() == io::ErrorKind::WouldBlock {
                                // rethrow
                                return Err(err);
                            }
                            cx_error!(target: TAG, self.id, "SOCKS5 ERR Cannot read socks5 for auth response: [{:?}] {}", err.kind(), err);
                            self.send_empty_packet_to_client(selector, tcp_header::FLAG_RST);
                            self.close(selector);
                        }
                    }
                }
            },
            // Gnirehtet socks5 client -> socks5 server, username/password authenticate
            Socks5State::Socks5AuthUsernamePasswordSend => {
                if ready.is_writable() {
                    let mut packet = [0; MAX_ADDR_LEN];
                    let username = proxy_config.username.as_bytes();
                    let password = proxy_config.password.as_bytes();

                    packet[0] = 1; // protocol version

                    packet[1] = 0; // ulen
                    let mut u: &mut [u8] = &mut packet[2..];
                    let mut ulen: usize = 0;
                    for byte in username { // copy_from_slice
                        let _ = u.write_u8(*byte);
                        ulen += 1;
                    }
                    packet[1] = ulen as u8; // ulen

                    packet[2+ulen] = 0; // plen
                    let mut p: &mut [u8] = &mut packet[2+ulen+1..];
                    let mut plen: usize = 0;
                    for byte in password { // copy_from_slice
                        let _ = p.write_u8(*byte);
                        plen += 1;
                    }
                    packet[2+ulen] = plen as u8; // plen

                    self.client_to_network.read_from(&packet[..3+ulen+plen]);
                    match self.client_to_network.write_to(&mut self.stream) {
                        //match self.stream.write_all(packet[..packet_len]) {
                        Ok(w) => {
                            cx_debug!(target: TAG, self.id, "packets {:X?}", &packet[..3+ulen+plen]);
                            cx_debug!(target: TAG, self.id, "Write to socks5 for username/password authenticate, packet payload length: {}", w);
                            self.socks5_update_interests(selector, Ready::readable()); // change interest to write after read
                            self.socks5_state = Socks5State::Socks5AuthUsernamePasswordDone;
                        }
                        Err(err) => {
                            if err.kind() == io::ErrorKind::WouldBlock {
                                // rethrow
                                return Err(err);
                            }
                            cx_error!(target: TAG, self.id, "Cannot write socks5 for username/password authenticate: [{:?}] {}", err.kind(), err );
                            // error or hup
                            self.close(selector);
                        }
                    }
                }
            }
            // socks5 server <- Gnirehtet socks5 client, check authenticate result, expect 5 1
            Socks5State::Socks5AuthUsernamePasswordDone => {
                if ready.is_readable() {
                    match socks5_protocol::socks5_read_username_password_auth_response(&mut self.stream) {
                        Ok(authenticate_status) =>{
                            cx_debug!(target: TAG, self.id, "SOCKS5 LOG authenticate status = {}", authenticate_status);

                            self.socks5_update_interests(selector, Ready::writable()); // change interest to write after read

                            match authenticate_status {
                                0 => {
                                    // if no auth need, goto cmd connect
                                    self.socks5_update_interests(selector, Ready::writable()); // change interest to write after read
                                    self.socks5_state = Socks5State::Socks5AuthDone;
                                },
                                _ => {
                                    cx_error!(target: TAG, self.id, "SOCKS5 ERR authenticate failed {}", authenticate_status);
                                    self.send_empty_packet_to_client(selector, tcp_header::FLAG_RST);
                                    self.close(selector);
                                }
                            }
                        }
                        Err(err) => {
                            if err.kind() == io::ErrorKind::WouldBlock {
                                // rethrow
                                return Err(err);
                            }
                            cx_error!(target: TAG, self.id, "SOCKS5 ERR Cannot read socks5 for auth response: [{:?}] {}", err.kind(), err);
                            self.send_empty_packet_to_client(selector, tcp_header::FLAG_RST);
                            self.close(selector);
                        }
                    }
                }
            }
            // Gnirehtet socks5 client -> socks5 server: cmd connect
            Socks5State::Socks5AuthDone => {
                if ready.is_writable() {

                    let mut packet = [0; MAX_ADDR_LEN + 3];
                    packet[0] = socks5_protocol::consts::SOCKS5_VERSION; // protocol version
                    packet[1] = socks5_protocol::consts::SOCKS5_CMD_TCP_CONNECT; // command
                    packet[2] = 0; // reserved
                    packet[3] = 1; // ATYP address type of IP V4
                
                    let target_addr: SocketAddrV4 = self.id.rewritten_destination().into();
                    let to_addr : [u8; 4] = target_addr.ip().octets();
                    packet[4] = to_addr[0];
                    packet[5] = to_addr[1];
                    packet[6] = to_addr[2];
                    packet[7] = to_addr[3];

                    let to_port : [u8; 2] = target_addr.port().to_be_bytes();
                    packet[8] = to_port[0];
                    packet[9] = to_port[1];

                    self.client_to_network.read_from(&packet[..10]);

                    match self.client_to_network.write_to(&mut self.stream) {
                        Ok(w) => {
                            cx_debug!(target: TAG, self.id, "Write to socks5 for cmd connect, packet payload length:{}", w);
                            self.socks5_update_interests(selector, Ready::readable()); // change interest to read after write
                            self.socks5_state = Socks5State::TargetAddrSend;
                        }
                        Err(err) => {
                            if err.kind() == io::ErrorKind::WouldBlock {
                                // rethrow
                                return Err(err);
                            }
                            cx_error!(target: TAG, self.id, "Cannot write socks5 for cmd connect: [{:?}] {}", err.kind(), err);
                            // error or hup
                            self.close(selector);
                        }
                    }
                }
            },
            // check cmd connect result, if ok, all done.
            Socks5State::TargetAddrSend => {
                if ready.is_readable() {
                    match socks5_protocol::socks5_read_response(&mut self.stream) {
                        Ok(addr) => {
                            // if no auth need, goto cmd connect
                            cx_error!(target: TAG, self.id, "Read socks5 for cmd connect, OK, response: {}",  addr);

                            self.socks5_update_interests(selector, Ready::writable()); // change interest to write after read
                            self.socks5_state = Socks5State::RemoteConnected;
                        }
                        Err(err) => {
                            if err.kind() == io::ErrorKind::WouldBlock {
                                // rethrow
                                return Err(err);
                            }
                            cx_error!(target: TAG, self.id, "Cannot read socks5 for auth response: [{:?}] {}", err.kind(), err);
                            self.send_empty_packet_to_client(selector, tcp_header::FLAG_RST);
                            self.close(selector);
                        }
                    }
                }
            },
            _ => {
                cx_debug!(target: TAG, self.id, "unknown socks5_state");
            }
        }

        Ok(())
    }

    // return Err(err) with err.kind() == io::ErrorKind::WouldBlock on spurious event
    fn process(&mut self, selector: &mut Selector, event: Event) -> io::Result<()> {
        if !self.closed {
            let ready = event.readiness();
            if ready.is_readable() || ready.is_writable() {
                // if use proxy and proxy not ready to use, prepare socks5 connection first.
                if self.socks5_state != Socks5State::NoProxy && self.socks5_state != Socks5State::RemoteConnected {
                    // should connect proxy first
                    let _ = self.handle_socks5_state(selector, ready);

                    return Ok(())
                }

                if ready.is_writable() {
                    if self.tcb.state == TcpState::SynSent {
                        // writable is first triggered when the stream is connected
                        self.process_connect(selector);
                    } else {
                        self.process_send(selector)?;
                    }
                }

                if !self.closed && ready.is_readable() {
                    match self.process_receive(selector) {
                        Ok(_) => (),
                        Err(err) => {
                            if err.kind() == io::ErrorKind::WouldBlock && ready.is_writable() {
                                cx_debug!(target: TAG, self.id, "already write, update interests here");
                                self.update_interests(selector);
                            }
                            return Err(err);
                        }
                    }
                }

                if !self.closed {
                    self.update_interests(selector);
                }
            } else {
                cx_debug!(target: TAG, self.id, "received ready = {:?}", ready);
                // error or hup
                self.close(selector);
            }
            if self.closed {
                // on_ready is not called from the router, so the connection must remove itself
                self.remove_from_router();
            }
        }
        Ok(())
    }

    // return Err(err) with err.kind() == io::ErrorKind::WouldBlock on spurious event
    fn process_send(&mut self, selector: &mut Selector) -> io::Result<()> {
        match self.client_to_network.write_to(&mut self.stream) {
            Ok(w) => {
                if w != 0 {
                    self.tcb.acknowledgement_number += Wrapping(w as u32);

                    if self.tcb.fin_received && self.client_to_network.is_empty() {
                        let client_rc = self.client.upgrade().expect("Expected client not found");
                        let mut client = client_rc.borrow_mut();
                        cx_debug!(
                            target: TAG,
                            self.id,
                            "No more pending data, process the pending FIN"
                        );
                        self.do_handle_fin(selector, &mut client.channel());
                    } else {
                        cx_debug!(
                            target: TAG,
                            self.id,
                            "Sending ACK {} to client",
                            self.tcb.numbers()
                        );
                        self.send_empty_packet_to_client(selector, tcp_header::FLAG_ACK);
                    }
                } else {
                    cx_debug!(target: TAG, self.id, "State = {:?}, process_send, close selector", self.tcb.state);
                    self.close(selector);
                }
            }
            Err(err) => {
                if err.kind() == io::ErrorKind::WouldBlock {
                    // rethrow
                    return Err(err);
                }
                cx_error!(
                    target: TAG,
                    self.id,
                    "Cannot write: [{:?}] {}",
                    err.kind(),
                    err
                );
                self.send_empty_packet_to_client(selector, tcp_header::FLAG_RST);
                self.close(selector);
            }
        }
        Ok(())
    }

    // return Err(err) with err.kind() == io::ErrorKind::WouldBlock on spurious event
    fn process_receive(&mut self, selector: &mut Selector) -> io::Result<()> {
        assert!(
            self.packet_for_client_length.is_none(),
            "A pending packet was not sent"
        );
        let remaining_client_window = self.tcb.remaining_client_window();
        assert!(
            remaining_client_window > 0,
            "process_received() must not be called when window == 0"
        );
        let max_payload_length =
            Some(cmp::min(remaining_client_window, MAX_PAYLOAD_LENGTH) as usize);
        Self::update_headers(
            &mut self.network_to_client,
            &self.tcb,
            tcp_header::FLAG_ACK | tcp_header::FLAG_PSH,
        );
        match self
            .network_to_client
            .packetize_read(&mut self.stream, max_payload_length)
        {
            Ok(Some(ipv4_packet)) => {
                match Self::send_to_client(&self.client, selector, &ipv4_packet) {
                    Ok(_) => {
                        let len = ipv4_packet.payload().unwrap().len();
                        cx_debug!(
                            target: TAG,
                            self.id,
                            "Packet ({} bytes) sent to client {}",
                            len,
                            self.tcb.numbers()
                        );
                        self.tcb.sequence_number += Wrapping(len as u32);
                    }
                    Err(_) => {
                        // ask to the client to pull when its buffer is not full
                        let client_rc = self.client.upgrade().expect("Expected client not found");
                        let mut client = client_rc.borrow_mut();
                        let self_rc = self.self_weak.upgrade().unwrap();
                        client.register_pending_packet_source(self_rc);
                        self.packet_for_client_length = Some(ipv4_packet.length());
                    }
                };
            }
            Ok(None) => {
                self.eof(selector);
            }
            Err(err) => {
                if err.kind() == io::ErrorKind::WouldBlock {
                    // rethrow
                    return Err(err);
                }
                cx_error!(
                    target: TAG,
                    self.id,
                    "Cannot read: [{:?}] {}",
                    err.kind(),
                    err
                );
                self.send_empty_packet_to_client(selector, tcp_header::FLAG_RST);
                self.close(selector);
            }
        }
        Ok(())
    }

    fn process_connect(&mut self, selector: &mut Selector) {
        assert_eq!(self.tcb.state, TcpState::SynSent);
        self.tcb.state = TcpState::SynReceived;
        cx_debug!(target: TAG, self.id, "State = {:?}", self.tcb.state);
        self.send_empty_packet_to_client(selector, tcp_header::FLAG_SYN | tcp_header::FLAG_ACK);
        self.tcb.sequence_number += Wrapping(1); // SYN counts for 1 byte
    }

    fn send_to_client(
        client: &Weak<RefCell<Client>>,
        selector: &mut Selector,
        ipv4_packet: &Ipv4Packet,
    ) -> io::Result<()> {
        let client_rc = client.upgrade().expect("Expected client not found");
        let mut client = client_rc.borrow_mut();
        client.send_to_client(selector, &ipv4_packet)
    }

    /// Borrow self.client and send empty packet to it
    ///
    /// To be used if called by on_ready() (so the client is not borrowed yet).
    fn send_empty_packet_to_client(&mut self, selector: &mut Selector, flags: u16) {
        let client_rc = self.client.upgrade().expect("Expected client not found");
        let mut client = client_rc.borrow_mut();
        self.reply_empty_packet_to_client(selector, &mut client.channel(), flags)
    }

    /// Send empty packet to the client channel (that already borrows the client)
    ///
    /// To be used if called by send_to_network() (called by the client, so it is already
    /// borrowed).
    fn reply_empty_packet_to_client(
        &mut self,
        selector: &mut Selector,
        client_channel: &mut ClientChannel,
        flags: u16,
    ) {
        let ipv4_packet = Self::create_empty_response_packet(
            &self.id,
            &mut self.network_to_client,
            &self.tcb,
            flags,
        );
        if let Err(err) = client_channel.send_to_client(selector, &ipv4_packet) {
            // losing such an empty packet will not break the TCP connection
            cx_warn!(
                target: TAG,
                self.id,
                "Cannot send packet to client: {}",
                err
            );
        }
    }

    fn eof(&mut self, selector: &mut Selector) {
        self.send_empty_packet_to_client(selector, tcp_header::FLAG_FIN | tcp_header::FLAG_ACK);
        self.tcb.fin_sequence_number = Some(self.tcb.sequence_number.0);
        self.tcb.sequence_number += Wrapping(1); // FIN counts for 1 byte
        self.tcb.state = if self.tcb.state == TcpState::CloseWait {
            TcpState::LastAck
        } else {
            TcpState::FinWait1
        };
        cx_debug!(target: TAG, self.id, "State = {:?}", self.tcb.state);
    }

    #[inline]
    fn tcp_header_of_transport(transport_header: TransportHeader) -> TcpHeader {
        if let TransportHeader::Tcp(tcp_header) = transport_header {
            tcp_header
        } else {
            panic!("Not a TCP header");
        }
    }

    #[inline]
    fn tcp_header_of_transport_mut(transport_header: TransportHeaderMut) -> TcpHeaderMut {
        if let TransportHeaderMut::Tcp(tcp_header) = transport_header {
            tcp_header
        } else {
            panic!("Not a TCP header");
        }
    }

    #[inline]
    fn tcp_header_of_packet<'a>(ipv4_packet: &'a Ipv4Packet) -> TcpHeader<'a> {
        if let Some(TransportHeader::Tcp(tcp_header)) = ipv4_packet.transport_header() {
            tcp_header
        } else {
            panic!("Not a TCP packet");
        }
    }

    fn update_headers(packetizer: &mut Packetizer, tcb: &Tcb, flags: u16) {
        let mut tcp_header = Self::tcp_header_of_transport_mut(packetizer.transport_header_mut());
        tcp_header.set_sequence_number(tcb.sequence_number.0);
        tcp_header.set_acknowledgement_number(tcb.acknowledgement_number.0);
        tcp_header.set_flags(flags);
    }

    fn handle_packet(
        &mut self,
        selector: &mut Selector,
        client_channel: &mut ClientChannel,
        ipv4_packet: &Ipv4Packet,
    ) {
        let tcp_header = Self::tcp_header_of_packet(ipv4_packet);
        if self.tcb.state == TcpState::Init {
            self.handle_first_packet(selector, client_channel, ipv4_packet);
            return;
        }

        if tcp_header.is_syn() {
            self.handle_duplicate_syn(selector, client_channel, ipv4_packet);
            return;
        }

        let expected_packet =
            (self.tcb.acknowledgement_number + Wrapping(self.client_to_network.size() as u32)).0;
        if tcp_header.sequence_number() != expected_packet {
            // ignore packet already received or out-of-order, retransmission is already
            // managed by both sides
            cx_warn!(
                target: TAG,
                self.id,
                "Ignoring packet {} (acking {}); expecting {}; flags={}",
                tcp_header.sequence_number(),
                tcp_header.acknowledgement_number(),
                expected_packet,
                tcp_header.flags()
            );
            return;
        }

        self.tcb.client_window = tcp_header.window();
        self.tcb.their_acknowledgement_number = tcp_header.acknowledgement_number();

        cx_debug!(
            target: TAG,
            self.id,
            "Receiving expected packet {} (flags={})",
            tcp_header.sequence_number(),
            tcp_header.flags()
        );

        if tcp_header.is_rst() {
            self.close(selector);
            return;
        }

        if tcp_header.is_ack() {
            cx_debug!(
                target: TAG,
                self.id,
                "Client acked {}",
                tcp_header.acknowledgement_number()
            );

            self.handle_ack(selector, client_channel, ipv4_packet);
        }

        if tcp_header.is_fin() {
            self.handle_fin(selector, client_channel);
        }

        if let Some(fin_sequence_number) = self.tcb.fin_sequence_number {
            if tcp_header.acknowledgement_number() == fin_sequence_number + 1 {
                cx_debug!(target: TAG, self.id, "Received ACK of FIN");
                self.handle_fin_ack(selector);
            }
        }
    }

    fn handle_first_packet(
        &mut self,
        selector: &mut Selector,
        client_channel: &mut ClientChannel,
        ipv4_packet: &Ipv4Packet,
    ) {
        cx_debug!(target: TAG, self.id, "handle_first_packet()");
        let tcp_header = Self::tcp_header_of_packet(ipv4_packet);
        if tcp_header.is_syn() {
            let their_sequence_number = tcp_header.sequence_number();
            self.tcb.acknowledgement_number = Wrapping(their_sequence_number) + Wrapping(1);
            self.tcb.syn_sequence_number = their_sequence_number;

            self.tcb.sequence_number = Wrapping(random::<u32>());
            cx_debug!(
                target: TAG,
                self.id,
                "Initialized seq={}; ack={}",
                self.tcb.sequence_number,
                self.tcb.acknowledgement_number
            );
            self.tcb.client_window = tcp_header.window();
            self.tcb.state = TcpState::SynSent;
            cx_debug!(target: TAG, self.id, "State = {:?}", self.tcb.state);
        } else {
            cx_warn!(
                target: TAG,
                self.id,
                "Unexpected first packet {}; acking {}; flags={}",
                tcp_header.sequence_number(),
                tcp_header.acknowledgement_number(),
                tcp_header.flags()
            );
            // make a RST in the window client
            self.tcb.sequence_number = Wrapping(tcp_header.acknowledgement_number());
            self.reply_empty_packet_to_client(selector, client_channel, tcp_header::FLAG_RST);
            self.close(selector);
        }
    }

    fn handle_duplicate_syn(
        &mut self,
        selector: &mut Selector,
        client_channel: &mut ClientChannel,
        ipv4_packet: &Ipv4Packet,
    ) {
        let tcp_header = Self::tcp_header_of_packet(ipv4_packet);
        let their_sequence_number = tcp_header.sequence_number();
        cx_debug!(target: TAG, self.id, "State = {:?}, handle_duplicate_syn", self.tcb.state);
        if self.tcb.state == TcpState::SynSent {
            // the connection is not established yet, we can accept this packet as if it were the
            // first SYN
            self.tcb.syn_sequence_number = their_sequence_number;
            self.tcb.acknowledgement_number = Wrapping(their_sequence_number) + Wrapping(1);
        } else if their_sequence_number != self.tcb.syn_sequence_number {
            // duplicate SYN with different sequence number
            self.reply_empty_packet_to_client(selector, client_channel, tcp_header::FLAG_RST);
            self.close(selector);
        }
    }

    fn handle_fin(&mut self, selector: &mut Selector, client_channel: &mut ClientChannel) {
        cx_debug!(
            target: TAG,
            self.id,
            "Received a FIN from the client {}",
            self.tcb.numbers()
        );

        self.tcb.fin_received = true;
        if self.client_to_network.is_empty() {
            cx_debug!(
                target: TAG,
                self.id,
                "No pending data, process the FIN immediately"
            );
            self.do_handle_fin(selector, client_channel);
        }
        // otherwise, the FIN will be processed once client_to_network is empty
    }

    fn do_handle_fin(&mut self, selector: &mut Selector, client_channel: &mut ClientChannel) {
        self.tcb.acknowledgement_number += Wrapping(1); // received FIN counts for 1 byte

        if self.tcb.state == TcpState::Established {
            self.reply_empty_packet_to_client(
                selector,
                client_channel,
                tcp_header::FLAG_FIN | tcp_header::FLAG_ACK,
            );
            self.tcb.fin_sequence_number = Some(self.tcb.sequence_number.0);
            self.tcb.sequence_number += Wrapping(1); // FIN counts for 1 byte
                                                     // the connection will be closed by RAII, so switch immediately to LastAck
                                                     // (bypass CloseWait)
            self.tcb.state = TcpState::LastAck;
            cx_debug!(target: TAG, self.id, "State = {:?}", self.tcb.state);
        } else if self.tcb.state == TcpState::FinWait1 {
            self.reply_empty_packet_to_client(selector, client_channel, tcp_header::FLAG_ACK);
            self.tcb.state = TcpState::Closing;
            cx_debug!(target: TAG, self.id, "State = {:?}", self.tcb.state);
        } else if self.tcb.state == TcpState::FinWait2 {
            self.reply_empty_packet_to_client(selector, client_channel, tcp_header::FLAG_ACK);
            self.close(selector);
        } else {
            cx_warn!(
                target: TAG,
                self.id,
                "Received FIN was state was {:?}",
                self.tcb.state
            );
        }
    }

    fn handle_fin_ack(&mut self, selector: &mut Selector) {
        cx_debug!(target: TAG, self.id, "State = {:?}", self.tcb.state);
        if self.tcb.state == TcpState::LastAck || self.tcb.state == TcpState::Closing {
            self.close(selector);
        } else if self.tcb.state == TcpState::FinWait1 {
            self.tcb.state = TcpState::FinWait2;
        } else if self.tcb.state != TcpState::FinWait2 {
            cx_warn!(
                target: TAG,
                self.id,
                "Received FIN ACK while state was {:?}",
                self.tcb.state
            );
        }
    }

    fn handle_ack(
        &mut self,
        _selector: &mut Selector,
        _client_channel: &mut ClientChannel,
        ipv4_packet: &Ipv4Packet,
    ) {
        cx_debug!(target: TAG, self.id, "handle_ack()");
        if self.tcb.state == TcpState::SynReceived {
            self.tcb.state = TcpState::Established;
            cx_debug!(target: TAG, self.id, "State = {:?}", self.tcb.state);
            return;
        }

        if log_enabled!(target: TAG, Level::Trace) {
            cx_trace!(
                target: TAG,
                self.id,
                "{}",
                binary::build_packet_string(ipv4_packet.raw())
            );
        }

        let payload = ipv4_packet.payload().expect("No payload");
        if payload.is_empty() {
            // no data to transmit
            return;
        }

        if self.client_to_network.remaining() < payload.len() {
            cx_warn!(target: TAG, self.id, "Not enough space, dropping packet");
            return;
        }

        self.client_to_network.read_from(payload);
        // data will be ACKed once written to the network socket
    }

    fn create_empty_response_packet<'a>(
        id: &ConnectionId,
        packetizer: &'a mut Packetizer,
        tcb: &Tcb,
        flags: u16,
    ) -> Ipv4Packet<'a> {
        Self::update_headers(packetizer, tcb, flags);
        cx_debug!(
            target: TAG,
            id,
            "Forging empty response (flags={}) {}",
            flags,
            tcb.numbers()
        );
        if (flags & tcp_header::FLAG_ACK) != 0 {
            cx_debug!(target: TAG, id, "Acking {}", tcb.numbers());
        }
        let ipv4_packet = packetizer.packetize_empty_payload();
        if log_enabled!(target: TAG, Level::Trace) {
            cx_trace!(
                target: TAG,
                id,
                "{}",
                binary::build_packet_string(ipv4_packet.raw())
            );
        }
        ipv4_packet
    }

    fn update_interests(&mut self, selector: &mut Selector) {
        assert!(!self.closed);
        let mut ready = Ready::empty();
        if self.tcb.state == TcpState::SynSent {
            // waiting for connectable
            ready = Ready::writable()
        } else {
            if self.may_read() {
                ready |= Ready::readable()
            }
            if self.may_write() {
                ready |= Ready::writable()
            }
        }
        cx_debug!(target: TAG, self.id, "interests: {:?}", ready);
        if self.interests != ready {
            // interests must be changed
            self.interests = ready;
            selector
                .reregister(&self.stream, self.token, ready, PollOpt::level())
                .expect("Cannot register on poll");
        }
    }

    fn may_read(&self) -> bool {
        if !self.tcb.state.is_connected() || self.tcb.state.is_closed() {
            return false;
        }
        if self.packet_for_client_length.is_some() {
            // a packet is already pending
            return false;
        }
        self.tcb.remaining_client_window() > 0
    }

    fn may_write(&self) -> bool {
        !self.client_to_network.is_empty()
    }
}

impl Connection for TcpConnection {
    fn id(&self) -> &ConnectionId {
        &self.id
    }

    fn send_to_network(
        &mut self,
        selector: &mut Selector,
        client_channel: &mut ClientChannel,
        ipv4_packet: &Ipv4Packet,
    ) {
        self.handle_packet(selector, client_channel, ipv4_packet);
        if !self.closed {
            self.update_interests(selector);
        }
    }

    fn close(&mut self, selector: &mut Selector) {
        cx_info!(target: TAG, self.id, "Close");
        self.closed = true;
        if let Err(err) = selector.deregister(&self.stream, self.token) {
            // do not panic, this can happen in mio
            // see <https://github.com/Genymobile/gnirehtet/issues/136>
            cx_warn!(
                target: TAG,
                self.id,
                "Fail to deregister TCP stream: {:?}",
                err
            );
        }
        // socket will be closed by RAII
    }

    fn is_expired(&self) -> bool {
        // no external timeout expiration
        false
    }

    fn is_closed(&self) -> bool {
        self.closed
    }
}

impl PacketSource for TcpConnection {
    fn get(&mut self) -> Option<Ipv4Packet> {
        if let Some(len) = self.packet_for_client_length {
            Some(self.network_to_client.inflate(len))
        } else {
            None
        }
    }

    fn next(&mut self, selector: &mut Selector) {
        let len = self
            .packet_for_client_length
            .expect("next() called on empty packet source");
        cx_debug!(
            target: TAG,
            self.id,
            "Deferred packet ({} bytes) sent to client {}",
            len,
            self.tcb.numbers()
        );
        self.tcb.sequence_number += Wrapping(u32::from(len));
        self.packet_for_client_length = None;
        self.update_interests(selector);
    }
}

/*
impl AsyncRead for mio::net::TcpStream {
    fn poll_read(
        self: Pin<&mut Self>,
        cx: &mut task::Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<Result<(), io::Error>> {
        self.project().stream.poll_read(cx, buf)
    }
}

impl AsyncWrite for mio::net::TcpStream {
    fn poll_write(self: Pin<&mut Self>, cx: &mut task::Context<'_>, buf: &[u8]) -> Poll<Result<usize, io::Error>> {
        self.project().stream.poll_write(cx, buf)
    }

    fn poll_flush(self: Pin<&mut Self>, cx: &mut task::Context<'_>) -> Poll<Result<(), io::Error>> {
        self.project().stream.poll_flush(cx)
    }

    fn poll_shutdown(self: Pin<&mut Self>, cx: &mut task::Context<'_>) -> Poll<Result<(), io::Error>> {
        self.project().stream.poll_shutdown(cx)
    }
}
*/