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
use super::client::Client;
use super::connection::{self, Connection, ConnectionId};
use super::ipv4_header::IPv4Header;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::packet_source::PacketSource;
use super::packetizer::Packetizer;
use super::selector::{EventHandler, Selector};
use super::stream_buffer::StreamBuffer;
use super::tcp_header::{self, TCPHeader, TCPHeaderMut};
use super::transport_header::{TransportHeader, TransportHeaderMut};

const TAG: &'static str = "TCPConnection";

const MAX_PAYLOAD_LENGTH: u16 = 1400;

pub struct TCPConnection {
    self_weak: Weak<RefCell<TCPConnection>>,
    id: ConnectionId,
    client: Weak<RefCell<Client>>,
    stream: TcpStream,
    token: Token,
    client_to_network: StreamBuffer,
    network_to_client: Packetizer,
    packet_for_client_length: Option<u16>,
    closed: bool,
    tcb: TCB,
}

struct TCB {
    state: TCPState,
    syn_sequence_number: u32,
    sequence_number: Wrapping<u32>,
    acknowledgement_number: Wrapping<u32>,
    their_acknowledgement_number: u32,
    client_window: u16,
    remote_closed: bool,
}

#[derive(Debug, PartialEq, Eq)]
enum TCPState {
    Init,
    SynSent,
    SynReceived,
    Established,
    CloseWait,
    LastAck,
}

impl TCB {
    fn new() -> Self {
        Self {
            state: TCPState::Init,
            syn_sequence_number: 0,
            sequence_number: Wrapping(0),
            acknowledgement_number: Wrapping(0),
            their_acknowledgement_number: 0,
            client_window: 0,
            remote_closed: false,
        }
    }

    fn numbers(&self) -> String {
        format!("(seq={}, ack={})", self.sequence_number, self.acknowledgement_number)
    }
}

impl TCPConnection {
    pub fn new(selector: &mut Selector, id: ConnectionId, client: Weak<RefCell<Client>>, ipv4_header: &IPv4Header, transport_header: &TransportHeader) -> io::Result<Rc<RefCell<Self>>> {
        let stream = Self::create_stream(&id)?;

        if let TransportHeader::TCP(ref tcp_header) = *transport_header {
            // shrink the TCP options to pass a minimal refrence header to the packetizer
            let mut shrinked_tcp_header_raw = [0u8; 20];
            shrinked_tcp_header_raw.copy_from_slice(&tcp_header.raw()[..20]);
            let mut shrinked_tcp_header_data = tcp_header.data().clone();
            {
                let mut shrinked_tcp_header = shrinked_tcp_header_data.bind_mut(&mut shrinked_tcp_header_raw);
                shrinked_tcp_header.shrink_options();
                assert_eq!(20, shrinked_tcp_header.header_length());
            }

            let shrinked_transport_header = shrinked_tcp_header_data.bind(&shrinked_tcp_header_raw).into();

            let packetizer = Packetizer::new(&ipv4_header, &shrinked_transport_header);

            let rc = Rc::new(RefCell::new(Self {
                self_weak: Weak::new(),
                id: id,
                client: client,
                stream: stream,
                token: Token(0), // default value, will be set afterwards
                client_to_network: StreamBuffer::new(4 * MAX_PACKET_LENGTH),
                network_to_client: packetizer,
                packet_for_client_length: None,
                closed: false,
                tcb: TCB::new(),
            }));

            {
                let mut self_ref = rc.borrow_mut();

                // keep a shared reference to this
                self_ref.self_weak = Rc::downgrade(&rc);

                // rc is an EventHandler, register() expects a Box<EventHandler>
                let handler = Box::new(rc.clone());
                // writable to detect when the stream is connected
                let token = selector.register(&self_ref.stream, handler, Ready::writable(), PollOpt::level())?;
                self_ref.token = token;
            }
            Ok(rc)
        } else {
            panic!("Not a TCP header");
        }
    }

    fn create_stream(id: &ConnectionId) -> io::Result<TcpStream> {
        let rewritten_destination = connection::rewritten_destination(id.destination_ip(), id.destination_port()).into();
        TcpStream::connect(&rewritten_destination)
    }

    fn close(&mut self, selector: &mut Selector) {
        self.closed = true;
        self.disconnect(selector);
    }

    fn remove_from_router(&self) {
        // route is embedded in router which is embedded in client: the client necessarily exists
        let client_rc = self.client.upgrade().expect("Expected client not found");
        let mut client = client_rc.borrow_mut();
        client.router().remove(self);
    }

    fn process_send(&mut self, selector: &mut Selector) {
        match self.client_to_network.write_to(&mut self.stream) {
            Ok(w) => if w == 0 {
                self.close(selector);
            },
            Err(err) => {
                cx_error!(target: TAG, self.id, "Cannot write: {}", err);
                self.reset_connection(selector);
            },
        }
    }

    fn process_receive(&mut self, selector: &mut Selector) {
        assert!(self.packet_for_client_length.is_none(), "A pending packet was not sent");
        let remaining_client_window = self.get_remaining_client_window();
        assert!(remaining_client_window > 0, "process_received() must not be called when window == 0");
        let max_payload_length = cmp::min(remaining_client_window, MAX_PAYLOAD_LENGTH) as usize;
        Self::update_headers(&mut self.network_to_client, &self.tcb, tcp_header::FLAG_ACK | tcp_header::FLAG_PSH);
        // the packet is bound to the lifetime of self, so we cannot borrow self to call methods
        // defer the other branches in a separate match-block
        let non_lexical_lifetime_workaround = match self.network_to_client.packetize_read(&mut self.stream, Some(max_payload_length)) {
            Ok(Some(ipv4_packet)) => {
                match Self::send_to_client(&self.client, selector, &ipv4_packet) {
                    Ok(_) => self.packet_for_client_length = None, // packet consumed
                    Err(_) => {
                        // ask to the client to pull when its buffer is not full
                        let client_rc = self.client.upgrade().expect("Expected client not found");
                        let mut client = client_rc.borrow_mut();
                        let self_rc = self.self_weak.upgrade().unwrap();
                        client.register_pending_packet_source(self_rc);
                    }
                }
                Ok(Some(()))
            },
            Ok(None) => Ok(None),
            Err(err) => Err(err),
        };
        match non_lexical_lifetime_workaround {
            Ok(None) => self.eof(selector),
            Err(err) => {
                cx_error!(target: TAG, self.id, "Cannot read: {}", err);
                self.reset_connection(selector);
            },
            Ok(Some(_)) => (), // already handled
        }
    }

    fn process_connect(&mut self, selector: &mut Selector) {
        assert_eq!(self.tcb.state, TCPState::SynSent);
        self.tcb.state = TCPState::SynReceived;
        self.send_empty_packet_to_client(selector, tcp_header::FLAG_SYN |tcp_header::FLAG_ACK);
        self.tcb.sequence_number += Wrapping(1); // FIN counts for 1 byte
    }

    fn send_to_client(client: &Weak<RefCell<Client>>, selector: &mut Selector, ipv4_packet: &IPv4Packet) -> io::Result<()> {
        let client_rc = client.upgrade().expect("Expected client not found");
        let mut client = client_rc.borrow_mut();
        client.send_to_client(selector, &ipv4_packet)
    }

    fn send_empty_packet_to_client(&mut self, selector: &mut Selector, flags: u16) {
        let ipv4_packet = Self::create_empty_response_packet(&self.id, &mut self.network_to_client, &self.tcb, flags);
        if let Err(err) = Self::send_to_client(&self.client, selector, &ipv4_packet) {
            // losing such an empty packet will not break the TCP connection
            cx_warn!(target: TAG, self.id, "Cannot send packet to client: {}", err);
        }
    }

    fn eof(&mut self, selector: &mut Selector) {
        self.tcb.remote_closed = true;
        if self.tcb.state == TCPState::CloseWait {
            self.send_empty_packet_to_client(selector, tcp_header::FLAG_FIN);
            self.tcb.sequence_number += Wrapping(1); // FIN counts for 1 byte
        }
    }

    #[inline]
    fn tcp_header_of_transport_mut<'a>(transport_header: TransportHeaderMut<'a>) -> TCPHeaderMut<'a> {
        if let TransportHeaderMut::TCP(tcp_header) = transport_header {
            tcp_header
        } else {
            panic!("Not a TCP header");
        }
    }

    #[inline]
    fn tcp_header_of_packet<'a>(ipv4_packet: &'a IPv4Packet) -> TCPHeader<'a> {
        if let Some(TransportHeader::TCP(tcp_header)) = ipv4_packet.transport_header() {
            tcp_header
        } else {
            panic!("Not a TCP packet");
        }
    }

    fn update_headers(packetizer: &mut Packetizer, tcb: &TCB, flags: u16) {
        let mut tcp_header = Self::tcp_header_of_transport_mut(packetizer.transport_header_mut());
        tcp_header.set_sequence_number(tcb.sequence_number.0);
        tcp_header.set_acknowledgement_number(tcb.acknowledgement_number.0);
        tcp_header.set_flags(flags);
    }

    fn handle_packet(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        let tcp_header = Self::tcp_header_of_packet(ipv4_packet);
        if self.tcb.state == TCPState::Init {
            self.handle_first_packet(selector, ipv4_packet);
            return;
        }

        if tcp_header.is_syn() {
            self.handle_duplicate_syn(selector, ipv4_packet);
            return;
        }

        if tcp_header.sequence_number() != self.tcb.acknowledgement_number.0 {
            // ignore packet already received or out-of-order, retransmission is already
            // managed by both sides
            cx_warn!(target: TAG, self.id, "Ignoring packet {}; expecting {}; flags={}", tcp_header.sequence_number(), tcp_header.acknowledgement_number(), tcp_header.flags());
            self.send_empty_packet_to_client(selector, tcp_header::FLAG_ACK); // re-ack
            return;
        }

        self.tcb.client_window = tcp_header.window();
        self.tcb.their_acknowledgement_number = tcp_header.acknowledgement_number();

        cx_debug!(target: TAG, self.id, "Receiving expected packet {} (flags={})", tcp_header.sequence_number(), tcp_header.flags());

        if tcp_header.is_rst() {
            self.close(selector);
            return;
        }

        if tcp_header.is_ack() {
            cx_debug!(target: TAG, self.id, "Client acked {}", tcp_header.acknowledgement_number());
        }

        if tcp_header.is_fin() {
            self.handle_fin(selector, ipv4_packet);
        } else if tcp_header.is_ack() {
            self.handle_ack(selector, ipv4_packet);
        }
    }

    fn handle_first_packet(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        cx_debug!(target: TAG, self.id, "handle_first_packet()");
        let tcp_header = Self::tcp_header_of_packet(ipv4_packet);
        if tcp_header.is_syn() {
            let their_sequence_number = tcp_header.sequence_number();
            self.tcb.acknowledgement_number = Wrapping(their_sequence_number) + Wrapping(1);
            self.tcb.syn_sequence_number = their_sequence_number;

            self.tcb.sequence_number = Wrapping(random::<u32>());
            cx_debug!(target: TAG, self.id, "Initialized seq={}; ack={}", self.tcb.sequence_number, self.tcb.acknowledgement_number);
            self.tcb.client_window = tcp_header.window();
            self.tcb.state = TCPState::SynSent;
        } else {
            cx_warn!(target: TAG, self.id, "Unexpected first packet {}; acking {}; flags={}",
                     tcp_header.sequence_number(), tcp_header.acknowledgement_number(), tcp_header.flags());
            self.tcb.sequence_number = Wrapping(tcp_header.acknowledgement_number()); // make a RST in the window client
            self.reset_connection(selector);
        }
    }

    fn handle_duplicate_syn(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        let tcp_header = Self::tcp_header_of_packet(ipv4_packet);
        let their_sequence_number = tcp_header.sequence_number();
        if self.tcb.state == TCPState::SynSent {
            // the connection is not established yet, we can accept this packet as if it were the
            // first SYN
            self.tcb.syn_sequence_number = their_sequence_number;
            self.tcb.acknowledgement_number = Wrapping(their_sequence_number) + Wrapping(1);
        } else if their_sequence_number != self.tcb.syn_sequence_number {
            // duplicate SYN with different sequence number
            self.reset_connection(selector);
        }
    }

    fn handle_fin(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        let tcp_header = Self::tcp_header_of_packet(ipv4_packet);
        self.tcb.acknowledgement_number = Wrapping(tcp_header.sequence_number()) + Wrapping(1);
        if self.tcb.remote_closed {
            self.tcb.state = TCPState::LastAck;
            cx_debug!(target: TAG, self.id, "Received a FIN from the client, sending ACK+FIN {}", self.tcb.numbers());
            self.send_empty_packet_to_client(selector, tcp_header::FLAG_FIN | tcp_header::FLAG_ACK);
            self.tcb.sequence_number += Wrapping(1); // FIN counts for 1 byte
        } else {
            self.tcb.state = TCPState::CloseWait;
            self.send_empty_packet_to_client(selector, tcp_header::FLAG_ACK);
        }
    }

    fn handle_ack(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        cx_debug!(target: TAG, self.id, "handle_ack()");
        if self.tcb.state == TCPState::SynReceived {
            self.tcb.state == TCPState::Established;
            return;
        }
        if self.tcb.state == TCPState::LastAck {
            cx_debug!(target: TAG, self.id, "LAST_ACK");
            self.close(selector);
            return;
        }

        if log_enabled!(target: TAG, LogLevel::Trace) {
            cx_trace!(target: TAG, self.id, "{}", binary::to_string(ipv4_packet.raw()));
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
        cx_debug!(target: TAG, self.id, "Received a payload from the client ({} bytes), sending ACK {}",
                  payload.len(), self.tcb.numbers());
        self.send_empty_packet_to_client(selector, tcp_header::FLAG_ACK);
    }

    fn create_empty_response_packet<'a>(id: &ConnectionId, packetizer: &'a mut Packetizer, tcb: &TCB, flags: u16) -> IPv4Packet<'a> {
        Self::update_headers(packetizer, tcb, flags);
        cx_debug!(target: TAG, id, "Forging empty response (flags={}) {}", flags, tcb.numbers());
        if (flags & tcp_header::FLAG_ACK) != 0 {
            cx_debug!(target: TAG, id, "Acking {}", tcb.numbers());
        }
        let ipv4_packet = packetizer.packetize_empty_payload();
        if log_enabled!(target: TAG, LogLevel::Trace) {
            cx_trace!(target: TAG, id, "{}", binary::to_string(ipv4_packet.raw()));
        }
        ipv4_packet
    }

    fn reset_connection(&mut self, selector: &mut Selector) {
        self.send_empty_packet_to_client(selector, tcp_header::FLAG_RST);
        self.close(selector);
    }

    fn update_interests(&mut self, selector: &mut Selector) {
        assert!(!self.closed);
        let mut ready = Ready::empty();
        if self.tcb.state == TCPState::SynSent {
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
        debug!(target: TAG, "interests: {:?}", ready);
        selector.reregister(&self.stream, self.token, ready, PollOpt::level()).expect("Cannot register on poll");
    }

    fn may_read(&self) -> bool {
        !self.tcb.remote_closed &&
                self.packet_for_client_length.is_none() &&
                self.get_remaining_client_window() > 0
    }

    fn may_write(&self) -> bool {
        !self.client_to_network.is_empty()
    }

    fn get_remaining_client_window(&self) -> u16 {
        // TODO
        42
    }
}

impl Connection for TCPConnection {
    fn id(&self) -> &ConnectionId {
        &self.id
    }

    fn send_to_network(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        self.handle_packet(selector, ipv4_packet);
        if !self.closed {
            self.update_interests(selector);
        }
    }

    fn disconnect(&mut self, selector: &mut Selector) {
        cx_info!(target: TAG, self.id, "Close");
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

impl EventHandler for TCPConnection {
    fn on_ready(&mut self, selector: &mut Selector, event: Event) {
        assert!(!self.closed);
        let ready = event.readiness();
        if ready.is_writable() {
            if self.tcb.state == TCPState::SynSent {
                // writable is first triggered when the stream is connected
                self.process_connect(selector);
            } else {
                self.process_send(selector);
            }
        }
        if !self.closed && ready.is_readable() {
            self.process_receive(selector);
        }
        if !self.closed {
            self.update_interests(selector);
        } else {
            // on_ready is not called from the router, so the connection must remove itself
            self.remove_from_router();
        }
    }
}

impl PacketSource for TCPConnection {
    fn get(&mut self) -> Option<IPv4Packet> {
        if let Some(len) = self.packet_for_client_length {
            Some(self.network_to_client.inflate(len))
        } else {
            None
        }
    }

    fn next(&mut self, selector: &mut Selector) {
        let len = self.packet_for_client_length.expect("next() called on empty packet source");
        self.tcb.sequence_number += Wrapping(len as u32);
        self.packet_for_client_length = None;
        self.update_interests(selector);
    }
}
