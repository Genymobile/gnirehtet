use std::cell::RefCell;
use std::fmt;
use std::io;
use std::net::SocketAddrV4;
use std::rc::{Rc, Weak};

use super::client::Client;
use super::close_listener::CloseListener;
use super::connection::Connection;
use super::ipv4_header::{IPv4Header, Protocol};
use super::ipv4_packet::IPv4Packet;
use super::net;
use super::transport_header::TransportHeader;
use super::udp_connection::UDPConnection;

pub struct Route {
    client: Weak<RefCell<Client>>,
    key: RouteKey,
    connection: Connection,
    close_listener: Box<CloseListener<RouteKey>>,
}

impl Route {
    pub fn new(client: Weak<RefCell<Client>>, route_key: RouteKey, ipv4_packet: &IPv4Packet, close_listener: Box<CloseListener<RouteKey>>) -> io::Result<Self> {
        let connection = Route::create_connection(&route_key, ipv4_packet)?;
        Ok(Self {
            client: client,
            key: route_key,
            connection: connection,
            close_listener: close_listener,
        })
    }

    fn create_connection(route_key: &RouteKey, reference_packet: &IPv4Packet) -> io::Result<Connection> {
        match route_key.protocol() {
            Protocol::TCP => Err(io::Error::new(io::ErrorKind::Other, "Not implemented yet")),
            Protocol::UDP => Ok(UDPConnection::new(reference_packet).into()),
            p => Err(io::Error::new(io::ErrorKind::Other, format!("Unsupported protocol: {:?}", p))),
        }
    }

    pub fn key(&self) -> &RouteKey {
        &self.key
    }

    pub fn send_to_network(&mut self, ipv4_packet: &IPv4Packet) {
        self.connection.send_to_network(ipv4_packet);
    }

    pub fn close(&mut self) {
        self.disconnect();
        self.close_listener.on_closed(&self.key);
    }

    pub fn disconnect(&mut self) {
        self.connection.disconnect();
    }

    pub fn is_connection_expired(&self) -> bool {
        self.connection.is_expired()
    }
}

#[derive(Debug, PartialEq, Eq)]
pub struct RouteKey {
    protocol: Protocol,
    source_ip: u32,
    source_port: u16,
    destination_ip: u32,
    destination_port: u16,
}

impl RouteKey {
    pub fn new(protocol: Protocol, source_ip: u32, source_port: u16, destination_ip: u32, destination_port: u16) -> Self {
        Self {
            protocol: protocol,
            source_ip: source_ip,
            source_port: source_port,
            destination_ip: destination_ip,
            destination_port: destination_port,
        }
    }

    pub fn from_packet(ipv4_packet: &IPv4Packet) -> Self {
        let raw = ipv4_packet.raw();
        let ipv4_header = ipv4_packet.ipv4_header();
        let transport_header = ipv4_packet.transport_header().as_ref().expect("Packet without transport header");
        Self {
            protocol: ipv4_header.protocol(),
            source_ip: ipv4_header.source(),
            source_port: transport_header.source_port(),
            destination_ip: ipv4_header.destination(),
            destination_port: transport_header.destination_port(),
        }
    }

    pub fn protocol(&self) -> Protocol {
        self.protocol
    }

    pub fn source(&self) -> SocketAddrV4 {
        net::to_socket_addr(self.source_ip, self.source_port)
    }

    pub fn destination(&self) -> SocketAddrV4 {
        net::to_socket_addr(self.destination_ip, self.destination_port)
    }
}

impl fmt::Display for RouteKey {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{} -> {}", self.source(), self.destination())
    }
}
