use std::net::{Ipv4Addr, SocketAddrV4};

use super::ipv4_packet::IPv4Packet;
use super::net;
use super::route::RouteKey;
use super::selector::Selector;

const LOCALHOST_FORWARD: u32 = 0x0A000202;

pub trait Connection {
    fn send_to_network(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet);
    fn disconnect(&mut self);
    fn is_expired(&self) -> bool;
}

pub fn rewritten_destination(ipv4: u32, port: u16) -> SocketAddrV4  {
    let addr = if ipv4 == LOCALHOST_FORWARD {
        Ipv4Addr::new(127, 0, 0, 1)
    } else {
        net::to_addr(ipv4)
    };
    SocketAddrV4::new(addr, port)
}
