use super::binary;
use std::net::{Ipv4Addr, SocketAddrV4};

pub fn to_addr(ipv4: u32) -> Ipv4Addr {
    let raw = binary::to_byte_array(ipv4);
    Ipv4Addr::new(raw[0], raw[1], raw[2], raw[3])
}

pub fn to_socket_addr(ipv4: u32, port: u16) -> SocketAddrV4 {
    let addr = to_addr(ipv4);
    SocketAddrV4::new(addr, port)
}
