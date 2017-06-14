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
use super::selector::Selector;
use super::transport_header::TransportHeader;
use super::udp_connection::UDPConnection;

pub struct Route {
    client: Weak<RefCell<Client>>,
    key: RouteKey,
    connection: Rc<RefCell<Connection>>,
}

impl Route {
    pub fn new(selector: &mut Selector, client: Weak<RefCell<Client>>, route_key: RouteKey, ipv4_packet: &IPv4Packet) -> io::Result<Self> {
        let connection = Route::create_connection(selector, client.clone(), route_key.clone(), ipv4_packet)?;
        Ok(Self {
            client: client,
            key: route_key,
            connection: connection,
        })
    }

    fn create_connection(selector: &mut Selector, client: Weak<RefCell<Client>>, route_key: RouteKey, reference_packet: &IPv4Packet) -> io::Result<Rc<RefCell<Connection>>> {
        match route_key.protocol() {
            Protocol::TCP => Err(io::Error::new(io::ErrorKind::Other, "Not implemented yet")),
            Protocol::UDP => Ok(UDPConnection::new(selector, client, route_key, reference_packet)?),
            p => Err(io::Error::new(io::ErrorKind::Other, format!("Unsupported protocol: {:?}", p))),
        }
    }

    pub fn key(&self) -> &RouteKey {
        &self.key
    }

    pub fn send_to_network(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        self.connection.borrow_mut().send_to_network(selector, ipv4_packet);
    }

    pub fn close(&mut self) {
        // TODO remove route class
        self.disconnect();

        // route is embedded in router which is embedded in client: the client necessarily exists
        let client_rc = self.client.upgrade().expect("expected client not found");
        let mut client = client_rc.borrow_mut();
        client.router().remove_route(&self.key);
    }

    pub fn disconnect(&mut self) {
        self.connection.borrow_mut().disconnect();
    }

    pub fn is_connection_expired(&self) -> bool {
        self.connection.borrow_mut().is_expired()
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
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

    pub fn source_ip(&self) -> u32 {
        self.source_ip
    }

    pub fn source_port(&self) -> u16 {
        self.source_port
    }

    pub fn destination_ip(&self) -> u32 {
        self.destination_ip
    }

    pub fn destination_port(&self) -> u16 {
        self.destination_port
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
