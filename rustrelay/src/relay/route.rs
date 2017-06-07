use std::fmt;
use std::net::SocketAddrV4;

use super::ipv4_header::Protocol;
use super::net;

#[derive(Debug, PartialEq, Eq)]
pub struct RouteKey {
    protocol: Protocol,
    source_ip: u32,
    source_port: u16,
    destination_ip: u32,
    destination_port: u16,
}

impl RouteKey {
    fn new(protocol: Protocol, source_ip: u32, source_port: u16, destination_ip: u32, destination_port: u16) -> Self {
        Self {
            protocol: protocol,
            source_ip: source_ip,
            source_port: source_port,
            destination_ip: destination_ip,
            destination_port: destination_port,
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

pub struct Route {
    key: RouteKey,
}
