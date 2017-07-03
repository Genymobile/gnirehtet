use std::cell::RefCell;
use std::cmp;
use std::io;
use std::net::{Ipv4Addr, SocketAddr};
use std::rc::{Rc, Weak};
use log::LogLevel;
use mio::{Event, PollOpt, Ready, Token};
use mio::net::TcpStream;

use super::binary;
use super::client::Client;
use super::connection::{self, Connection, ConnectionId};
use super::ipv4_header::IPv4Header;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::packetizer::Packetizer;
use super::selector::Selector;
use super::stream_buffer::StreamBuffer;
use super::tcp_header::{self, TCPHeader};
use super::transport_header::{TransportHeader, TransportHeaderMut};

const TAG: &'static str = "TCPConnection";

const MAX_PAYLOAD_LENGTH: u16 = 1400;

pub struct TCPConnection {
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
    sequence_number: u32,
    acknowledgement_number: u32,
    their_acknowledgement_number: u32,
    client_window: u16,
    remote_closed: bool,
}

#[derive(PartialEq, Eq)]
enum TCPState {
    Init,
    SynSent,
//    SynReceived,
    Established,
    CloseWait,
    LastAck,
}

impl TCB {
    fn new() -> Self {
        Self {
            state: TCPState::Init,
            syn_sequence_number: 0,
            sequence_number: 0,
            acknowledgement_number: 0,
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
        let stream = TCPConnection::create_stream(&id)?;

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
                let rc_clone = rc.clone();
                let handler = move |selector: &mut Selector, ready| {
                    let mut self_ref = rc_clone.borrow_mut();
                    self_ref.on_ready(selector, ready);
                };
                let mut self_ref = rc.borrow_mut();
                let token = selector.register(&self_ref.stream, handler, Ready::readable(), PollOpt::level())?;
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

    }

    fn process_send(&mut self, selector: &mut Selector) {
        match self.client_to_network.write_to(&mut self.stream) {
            Ok(w) => if w == 0 {
                self.close(selector);
            },
            Err(err) => {
                error!(target: TAG, "{} Cannot write: {}", self.id, err);
                self.reset_connection(selector);
            }
        }
    }

    fn process_receive(&mut self, selector: &mut Selector) {
        assert!(self.packet_for_client_length.is_none(), "A pending packet was not sent");
        let remaining_client_window = self.get_remaining_client_window();
        assert!(remaining_client_window > 0, "process_received() must not be called when window == 0");
        let max_payload_length = cmp::min(remaining_client_window, MAX_PAYLOAD_LENGTH) as usize;
        TCPConnection::update_headers(&mut self.network_to_client, &self.tcb, tcp_header::FLAG_ACK | tcp_header::FLAG_PSH);
        // the packet is bound to the lifetime of self, so we cannot borrow self to call methods
        // defer the other branches in a separate match-block
        let non_lexical_lifetime_workaround = match self.network_to_client.packetize_read(&mut self.stream, Some(max_payload_length)) {
            Ok(Some(ipv4_packet)) => {
                // TODO send packet
                Ok(Some(()))
            },
            Ok(None) => Ok(None),
            Err(err) => Err(err),
        };
        match non_lexical_lifetime_workaround {
            Ok(None) => self.eof(selector),
            Err(err) => {
                error!(target: TAG, "{} Cannot read: {}", self.id, err);
                self.reset_connection(selector);
            },
            Ok(Some(_)) => () // already handled
        }
        // TODO
    }

    fn send_to_client(client: &Weak<RefCell<Client>>, selector: &mut Selector, ipv4_packet: &IPv4Packet) -> io::Result<()> {
        let client_rc = client.upgrade().expect("expected client not found");
        let mut client = client_rc.borrow_mut();
        client.send_to_client(selector, &ipv4_packet)
    }

    fn eof(&mut self, selector: &mut Selector) {
        self.tcb.remote_closed = true;
        if self.tcb.state == TCPState::CloseWait {
            let ipv4_packet = TCPConnection::create_empty_response_packet(&self.id, &mut self.network_to_client, &self.tcb, tcp_header::FLAG_FIN);
            self.tcb.sequence_number += 1; // FIN counts for 1 byte

            if let Err(err) = TCPConnection::send_to_client(&self.client, selector, &ipv4_packet) {
                warn!(target: TAG, "{} Cannot send packet to client: {}", &self.id, err);
            }
        }
    }

    fn update_headers(packetizer: &mut Packetizer, tcb: &TCB, flags: u16) {
        if let TransportHeaderMut::TCP(ref mut tcp_header) = packetizer.transport_header_mut() {
            tcp_header.set_sequence_number(tcb.sequence_number);
            tcp_header.set_acknowledgement_number(tcb.acknowledgement_number);
            tcp_header.set_flags(flags);
        } else {
            panic!("Not a TCP header");
        }
    }

    fn create_empty_response_packet<'a>(id: &ConnectionId, packetizer: &'a mut Packetizer, tcb: &TCB, flags: u16) -> IPv4Packet<'a> {
        TCPConnection::update_headers(packetizer, tcb, flags);
        debug!(target: TAG, "{} Forging empty response (flags={}) {}", id, flags, tcb.numbers());
        if (flags & tcp_header::FLAG_ACK) != 0 {
            debug!(target: TAG, "{} Acking {}", id, tcb.numbers());
        }
        let ipv4_packet = packetizer.packetize_empty_payload();
        if log_enabled!(target: TAG, LogLevel::Trace) {
            binary::to_string(ipv4_packet.raw());
        }
        ipv4_packet
    }

    fn reset_connection(&mut self, selector: &mut Selector) {
        // TODO
    }

    fn update_interests(&mut self, selector: &mut Selector) {
        if !self.closed {
            let mut ready = Ready::empty();
            if self.may_read() {
                ready = ready | Ready::readable()
            }
            if self.may_write() {
                ready = ready | Ready::writable()
            }
            selector.reregister(&self.stream, self.token, ready, PollOpt::level()).expect("Cannot register on poll");
        }
    }

    fn may_read(&self) -> bool {
        !self.tcb.remote_closed &&
                self.packet_for_client_length.is_some() &&
                self.get_remaining_client_window() > 0
    }

    fn may_write(&self) -> bool {
        !self.client_to_network.is_empty()
    }

    fn on_ready(&mut self, selector: &mut Selector, event: Event) {
        assert!(!self.closed);
        let ready = event.readiness();
        if ready.is_writable() {
            self.process_send(selector);
        }
        if !self.closed && ready.is_readable() {
            self.process_receive(selector);
        }
        if !self.closed {
            self.update_interests(selector);
        }
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
        // TODO
    }

    fn disconnect(&mut self, selector: &mut Selector) {
        info!(target: TAG, "{} Close", self.id);
        selector.deregister(&self.stream, self.token).unwrap();
        // socket will be closed by RAII
    }

    fn is_expired(&self) -> bool {
        // no external timeout expiration
        false
    }
}
