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

use std::cell::RefCell;
use std::cmp;
use std::io;
use std::num::Wrapping;
use std::rc::{Rc, Weak};
use log::LogLevel;
use mio::{Event, PollOpt, Ready, Token};
use mio::net::TcpStream;
use rand::random;

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

const TAG: &'static str = "TcpConnection";

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
}

// Transport Control Block
struct Tcb {
    state: TcpState,
    syn_sequence_number: u32,
    sequence_number: Wrapping<u32>,
    acknowledgement_number: Wrapping<u32>,
    their_acknowledgement_number: u32,
    client_window: u16,
    remote_closed: bool,
}

#[derive(Debug, PartialEq, Eq)]
enum TcpState {
    Init,
    SynSent,
    SynReceived,
    Established,
    CloseWait,
    LastAck,
}

impl Tcb {
    fn new() -> Self {
        Self {
            state: TcpState::Init,
            syn_sequence_number: 0,
            sequence_number: Wrapping(0),
            acknowledgement_number: Wrapping(0),
            their_acknowledgement_number: 0,
            client_window: 0,
            remote_closed: false,
        }
    }

    fn remaining_client_window(&self) -> u16 {
        let wrapped_remaining = Wrapping(self.their_acknowledgement_number) +
            Wrapping(self.client_window as u32) -
            self.sequence_number;
        let remaining = wrapped_remaining.0;
        if remaining <= self.client_window as u32 {
            remaining as u16
        } else {
            0
        }
    }

    fn numbers(&self) -> String {
        format!(
            "(seq={}, ack={})",
            self.sequence_number,
            self.acknowledgement_number
        )
    }
}

impl TcpConnection {
    pub fn new(
        selector: &mut Selector,
        id: ConnectionId,
        client: Weak<RefCell<Client>>,
        ipv4_header: Ipv4Header,
        transport_header: TransportHeader,
    ) -> io::Result<Rc<RefCell<Self>>> {
        cx_info!(target: TAG, id, "Open");
        let stream = Self::create_stream(&id)?;

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
            id: id,
            client: client,
            stream: stream,
            interests: interests,
            token: Token(0), // default value, will be set afterwards
            client_to_network: StreamBuffer::new(4 * MAX_PACKET_LENGTH),
            network_to_client: packetizer,
            packet_for_client_length: None,
            closed: false,
            tcb: Tcb::new(),
        }));

        {
            let mut self_ref = rc.borrow_mut();

            // keep a shared reference to this
            self_ref.self_weak = Rc::downgrade(&rc);

            let rc2 = rc.clone();
            // must anotate selector type: https://stackoverflow.com/a/44004103/1987178
            let handler =
                move |selector: &mut Selector, event| rc2.borrow_mut().on_ready(selector, event);
            let token = selector.register(
                &self_ref.stream,
                handler,
                interests,
                PollOpt::level(),
            )?;
            self_ref.token = token;
        }
        Ok(rc)
    }

    fn create_stream(id: &ConnectionId) -> io::Result<TcpStream> {
        TcpStream::connect(&id.rewritten_destination().into())
    }

    fn remove_from_router(&self) {
        // route is embedded in router which is embedded in client: the client necessarily exists
        let client_rc = self.client.upgrade().expect("Expected client not found");
        let mut client = client_rc.borrow_mut();
        client.router().remove(self);
    }

    fn on_ready(&mut self, selector: &mut Selector, event: Event) {
        match self.process(selector, event) {
            Ok(_) => (),
            Err(ref err) if err.kind() == io::ErrorKind::WouldBlock => {
                cx_debug!(target: TAG, self.id, "Spurious event, ignoring")
            }
            Err(_) => panic!("Unexpected unhandled error"),
        }
    }
    // return Err(err) with err.kind() == io::ErrorKind::WouldBlock on spurious event
    fn process(&mut self, selector: &mut Selector, event: Event) -> io::Result<()> {
        if !self.closed {
            let ready = event.readiness();
            if ready.is_readable() || ready.is_writable() {
                if ready.is_writable() {
                    if self.tcb.state == TcpState::SynSent {
                        // writable is first triggered when the stream is connected
                        self.process_connect(selector);
                    } else {
                        self.process_send(selector)?;
                    }
                }
                if !self.closed && ready.is_readable() {
                    self.process_receive(selector)?;
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
                if w == 0 {
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
        let max_payload_length = Some(cmp::min(remaining_client_window, MAX_PAYLOAD_LENGTH) as
            usize);
        Self::update_headers(
            &mut self.network_to_client,
            &self.tcb,
            tcp_header::FLAG_ACK | tcp_header::FLAG_PSH,
        );
        // the packet is bound to the lifetime of self, so we cannot borrow self to call methods
        // defer the other branches in a separate match-block
        let non_lexical_lifetime_workaround = match self.network_to_client.packetize_read(
            &mut self.stream,
            max_payload_length,
        ) {
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
                Ok(Some(()))
            }
            Ok(None) => Ok(None),
            Err(err) => Err(err),
        };
        match non_lexical_lifetime_workaround {
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
            Ok(Some(_)) => (), // already handled
        }
        Ok(())
    }

    fn process_connect(&mut self, selector: &mut Selector) {
        assert_eq!(self.tcb.state, TcpState::SynSent);
        self.tcb.state = TcpState::SynReceived;
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
        self.tcb.remote_closed = true;
        if self.tcb.state == TcpState::CloseWait {
            self.send_empty_packet_to_client(selector, tcp_header::FLAG_FIN);
            self.tcb.sequence_number += Wrapping(1); // FIN counts for 1 byte
        }
    }

    #[inline]
    fn tcp_header_of_transport<'a>(transport_header: TransportHeader<'a>) -> TcpHeader<'a> {
        if let TransportHeader::Tcp(tcp_header) = transport_header {
            tcp_header
        } else {
            panic!("Not a TCP header");
        }
    }

    #[inline]
    fn tcp_header_of_transport_mut<'a>(
        transport_header: TransportHeaderMut<'a>,
    ) -> TcpHeaderMut<'a> {
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

        if tcp_header.sequence_number() != self.tcb.acknowledgement_number.0 {
            // ignore packet already received or out-of-order, retransmission is already
            // managed by both sides
            cx_warn!(
                target: TAG,
                self.id,
                "Ignoring packet {}; expecting {}; flags={}",
                tcp_header.sequence_number(),
                self.tcb.acknowledgement_number.0,
                tcp_header.flags()
            );
            // re-ack
            self.reply_empty_packet_to_client(selector, client_channel, tcp_header::FLAG_ACK);
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
        }

        if tcp_header.is_fin() {
            self.handle_fin(selector, client_channel, ipv4_packet);
        } else if tcp_header.is_ack() {
            self.handle_ack(selector, client_channel, ipv4_packet);
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

    fn handle_fin(
        &mut self,
        selector: &mut Selector,
        client_channel: &mut ClientChannel,
        ipv4_packet: &Ipv4Packet,
    ) {
        let tcp_header = Self::tcp_header_of_packet(ipv4_packet);
        self.tcb.acknowledgement_number = Wrapping(tcp_header.sequence_number()) + Wrapping(1);
        if self.tcb.remote_closed {
            self.tcb.state = TcpState::LastAck;
            cx_debug!(
                target: TAG,
                self.id,
                "Received a FIN from the client, sending ACK+FIN {}",
                self.tcb.numbers()
            );
            self.reply_empty_packet_to_client(
                selector,
                client_channel,
                tcp_header::FLAG_FIN | tcp_header::FLAG_ACK,
            );
            self.tcb.sequence_number += Wrapping(1); // FIN counts for 1 byte
        } else {
            self.tcb.state = TcpState::CloseWait;
            self.reply_empty_packet_to_client(selector, client_channel, tcp_header::FLAG_ACK);
        }
    }

    fn handle_ack(
        &mut self,
        selector: &mut Selector,
        client_channel: &mut ClientChannel,
        ipv4_packet: &Ipv4Packet,
    ) {
        cx_debug!(target: TAG, self.id, "handle_ack()");
        if self.tcb.state == TcpState::SynReceived {
            self.tcb.state = TcpState::Established;
            return;
        }
        if self.tcb.state == TcpState::LastAck {
            cx_debug!(target: TAG, self.id, "LAST_ACK");
            self.close(selector);
            return;
        }

        if log_enabled!(target: TAG, LogLevel::Trace) {
            cx_trace!(
                target: TAG,
                self.id,
                "{}",
                binary::to_string(ipv4_packet.raw())
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
        self.tcb.acknowledgement_number += Wrapping(payload.len() as u32);

        // send ACK to client
        cx_debug!(
            target: TAG,
            self.id,
            "Received a payload from the client ({} bytes), sending ACK {}",
            payload.len(),
            self.tcb.numbers()
        );
        self.reply_empty_packet_to_client(selector, client_channel, tcp_header::FLAG_ACK);
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
        if log_enabled!(target: TAG, LogLevel::Trace) {
            cx_trace!(target: TAG, id, "{}", binary::to_string(ipv4_packet.raw()));
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
                ready = ready | Ready::readable()
            }
            if self.may_write() {
                ready = ready | Ready::writable()
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
        if self.tcb.remote_closed {
            return false;
        }
        if self.tcb.state == TcpState::SynSent || self.tcb.state == TcpState::SynReceived {
            // not connected yet
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
        selector.deregister(&self.stream, self.token).unwrap();
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
        let len = self.packet_for_client_length.expect(
            "next() called on empty packet source",
        );
        cx_debug!(
            target: TAG,
            self.id,
            "Deferred packet ({} bytes) sent to client {}",
            len,
            self.tcb.numbers()
        );
        self.tcb.sequence_number += Wrapping(len as u32);
        self.packet_for_client_length = None;
        self.update_interests(selector);
    }
}
