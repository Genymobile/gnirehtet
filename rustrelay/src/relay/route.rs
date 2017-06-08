use std::cell::RefCell;
use std::fmt;
use std::net::SocketAddrV4;
use std::rc::{Rc, Weak};

use super::client::Client;
use super::ipv4_header::{IPv4Header, Protocol};
use super::ipv4_packet::IPv4Packet;
use super::source_destination::SourceDestination;
use super::transport_header::TransportHeader;
use super::net;

pub struct Route {
    client: Weak<RefCell<Client>>,
    key: RouteKey,
}

impl Route {
    pub fn new(client: Weak<RefCell<Client>>, route_key: RouteKey, ipv4_packet: &IPv4Packet) -> Self {
        Self {
            client: client,
            key: route_key,
            // TODO
        }
    }

    pub fn get_key(&self) -> &RouteKey {
        &self.key
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
        let raw = &ipv4_packet.raw;
        let ipv4_header = &ipv4_packet.ipv4_header;
        let transport_header = ipv4_packet.transport_header.as_ref().expect("Packet without transport header");
        Self {
            protocol: ipv4_header.protocol,
            source_ip: ipv4_header.source,
            source_port: transport_header.get_source(raw),
            destination_ip: ipv4_header.destination,
            destination_port: transport_header.get_destination(raw),
        }
    }

    pub fn get_source(&self) -> SocketAddrV4 {
        net::to_socket_addr(self.source_ip, self.source_port)
    }

    pub fn get_destination(&self) -> SocketAddrV4 {
        net::to_socket_addr(self.destination_ip, self.destination_port)
    }
}

impl fmt::Display for RouteKey {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{} -> {}", self.get_source(), self.get_destination())
    }
}
