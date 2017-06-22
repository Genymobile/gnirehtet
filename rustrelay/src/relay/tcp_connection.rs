use std::cell::RefCell;
use std::rc::{Rc, Weak};
use mio::{Event, PollOpt, Ready, Token};
use mio::net::TcpStream;

use super::client::Client;
use super::connection::{self, Connection, ConnectionId};
use super::packetizer::Packetizer;
use super::stream_buffer::StreamBuffer;

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
