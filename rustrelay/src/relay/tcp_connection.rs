use std::cell::RefCell;
use std::io;
use std::net::{Ipv4Addr, SocketAddr};
use std::rc::{Rc, Weak};
use mio::{Event, PollOpt, Ready, Token};
use mio::net::TcpStream;

use super::client::Client;
use super::connection::{self, Connection, ConnectionId};
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::packetizer::Packetizer;
use super::selector::Selector;
use super::stream_buffer::StreamBuffer;
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
/*    pub fn new(selector: &mut Selector, id: ConnectionId, client: Weak<RefCell<Client>>, reference_packet: &IPv4Packet) -> io::Result<Rc<RefCell<Self>>> {
        let stream = TCPConnection::create_stream(&id)?;
        let raw = reference_packet.raw();
        let ipv4_header = reference_packet.ipv4_header().clone();
        let transport_header = reference_packet.transport_header().as_ref().unwrap().clone();
        if let TransportHeader::TCP(ref mut tcp_header) = transport_header {
            tcp_header.shrink_options(raw);
        }
        Err()
    }

    fn create_stream(id: &ConnectionId) -> io::Result<TcpStream> {
        let rewritten_destination = connection::rewritten_destination(id.destination_ip(), id.destination_port()).into();
        TcpStream::connect(&rewritten_destination)
    }*/
}
