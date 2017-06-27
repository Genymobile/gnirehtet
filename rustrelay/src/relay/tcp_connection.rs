use std::cell::RefCell;
use std::io;
use std::net::{Ipv4Addr, SocketAddr};
use std::rc::{Rc, Weak};
use mio::{Event, PollOpt, Ready, Token};
use mio::net::TcpStream;

use super::client::Client;
use super::connection::{self, Connection, ConnectionId};
use super::ipv4_header::IPv4Header;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::packetizer::Packetizer;
use super::selector::Selector;
use super::stream_buffer::StreamBuffer;
use super::tcp_header::TCPHeader;
use super::transport_header::TransportHeader;

const TAG: &'static str = "TCPConnection";

enum TCPState {
    Init,
    SynSent,
    SynReceived,
    Established,
    CloseWait,
    LastAck,
}

struct TCPConnection {
    id: ConnectionId,
    client: Weak<RefCell<Client>>,
    stream: TcpStream,
    token: Token,
    client_to_network: StreamBuffer,
    network_to_client: Packetizer,
    closed: bool,
    state: TCPState,
    syn_sequence_number: u32,
    sequence_number: u32,
    acknowledgement_number: u32,
    their_acknowledgement_number: u32,
    client_window: u16,
    remote_closed: bool,
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
                closed: false,
                state: TCPState::Init,
                syn_sequence_number: 0,
                sequence_number: 0,
                acknowledgement_number: 0,
                their_acknowledgement_number: 0,
                client_window: 0,
                remote_closed: false,
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

    fn process_send(&mut self, selector: &mut Selector) {
        // TODO
    }

    fn process_receive(&mut self, selector: &mut Selector) {
        // TODO
    }

    fn update_interests(&mut self, selector: &mut Selector) {
        // TODO
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
}
