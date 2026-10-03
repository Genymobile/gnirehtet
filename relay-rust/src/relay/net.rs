/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use super::binary;
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr, SocketAddrV4, SocketAddrV6};

pub fn to_addr(ipv4: u32) -> Ipv4Addr {
    let raw = binary::to_byte_array(ipv4);
    Ipv4Addr::new(raw[0], raw[1], raw[2], raw[3])
}

pub fn to_socket_addr(ipv4: u32, port: u16) -> SocketAddrV4 {
    let addr = to_addr(ipv4);
    SocketAddrV4::new(addr, port)
}

pub fn to_ipv6_addr(raw: &[u8; 16]) -> Ipv6Addr {
    Ipv6Addr::from(*raw)
}

pub fn to_socket_addr_ip(ip: IpAddr, port: u16) -> SocketAddr {
    SocketAddr::new(ip, port)
}

pub fn to_socket_addr_v6(raw: &[u8; 16], port: u16) -> SocketAddrV6 {
    SocketAddrV6::new(to_ipv6_addr(raw), port, 0, 0)
}
