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
    their_acknowledgement_numbe: u32,
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
            let mut shrinked_tcp_header = shrinked_tcp_header_data.bind_mut(&mut shrinked_tcp_header_raw);
            shrinked_tcp_header.shrink_options();
            assert_eq!(20, shrinked_tcp_header.header_length());
            let shrinked_transport_header = TCPHeader::from(shrinked_tcp_header).into();

            let packetizer = Packetizer::new(&ipv4_header, &shrinked_transport_header);
        } else {
            panic!("Not a TCP header");
        }
    }

    fn create_stream(id: &ConnectionId) -> io::Result<TcpStream> {
        let rewritten_destination = connection::rewritten_destination(id.destination_ip(), id.destination_port()).into();
        TcpStream::connect(&rewritten_destination)
    }
}
