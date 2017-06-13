use std::cell::RefCell;
use std::io;
use std::rc::{Rc, Weak};
use std::net::UdpSocket;

use super::client::Client;
use super::connection::{self, Connection};
use super::datagram_buffer::DatagramBuffer;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::packetizer::Packetizer;
use super::route::RouteKey;

pub struct UDPConnection {
    client: Weak<RefCell<Client>>,
    route_key: RouteKey,
    client_to_network: DatagramBuffer,
    network_to_client: Packetizer,
}

impl UDPConnection {
    pub fn new(client: Weak<RefCell<Client>>, route_key: RouteKey, reference_packet: &IPv4Packet) -> Rc<RefCell<Self>> {
        let raw: &[u8] = reference_packet.raw();
        let ipv4_header = reference_packet.ipv4_header().clone();
        let transport_header = reference_packet.transport_header().as_ref().unwrap().clone();
        Rc::new(RefCell::new(Self {
            client: client,
            route_key: route_key,
            client_to_network: DatagramBuffer::new(4 * MAX_PACKET_LENGTH),
            network_to_client: Packetizer::new(raw, ipv4_header, transport_header),
        }))
    }

    fn create_socket(route_key: &RouteKey) -> io::Result<UdpSocket> {
        let rewritten_destination = connection::rewritten_destination(route_key.destination_ip(), route_key.destination_port());
        Err(io::Error::new(io::ErrorKind::Other, "Not implemented yet"))
    }
}

impl Connection for UDPConnection {
    fn send_to_network(&mut self, ipv4_packet: &IPv4Packet) {
        // TODO
    }

    fn disconnect(&mut self) {
        // TODO
    }

    fn is_expired(&self) -> bool {
        // TODO
        false
    }
}
