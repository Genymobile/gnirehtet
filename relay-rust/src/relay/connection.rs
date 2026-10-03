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

use std::fmt;
use std::net::{IpAddr, SocketAddr};

use super::client::ClientChannel;
use super::ip_packet::IpPacket;
use super::ipv4_header::{Ipv4HeaderData, Protocol};
use super::ipv4_packet::Ipv4Packet;
use super::ipv6_packet::Ipv6Packet;
use super::net;
use super::selector::Selector;
use super::transport_header::TransportHeaderData;

const LOCALHOST_FORWARD: u32 = 0x0A_00_02_02; // 10.0.2.2
const LOCALHOST: u32 = 0x7F_00_00_01; // 127.0.0.1

pub trait Connection {
    fn id(&self) -> &ConnectionId;
    fn send_to_network(
        &mut self,
        selector: &mut Selector,
        client_channel: &mut ClientChannel,
        ip_packet: &IpPacket,
    );
    fn close(&mut self, selector: &mut Selector);
    fn is_expired(&self) -> bool;
    fn is_closed(&self) -> bool;
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ConnectionId {
    protocol: Protocol,
    source_ip: IpAddr,
    source_port: u16,
    destination_ip: IpAddr,
    destination_port: u16,
    id_string: String,
}

impl ConnectionId {
    pub fn from_headers(
        ipv4_header_data: &Ipv4HeaderData,
        transport_header_data: &TransportHeaderData,
    ) -> Self {
        Self::from_ip(
            ipv4_header_data.protocol(),
            IpAddr::from(net::to_addr(ipv4_header_data.source())),
            transport_header_data.source_port(),
            IpAddr::from(net::to_addr(ipv4_header_data.destination())),
            transport_header_data.destination_port(),
        )
    }

    pub fn from_ip(
        protocol: Protocol,
        source_ip: IpAddr,
        source_port: u16,
        destination_ip: IpAddr,
        destination_port: u16,
    ) -> Self {
        let id_string = format!(
            "{} -> {}",
            net::to_socket_addr_ip(source_ip, source_port),
            net::to_socket_addr_ip(destination_ip, destination_port)
        );
        Self {
            protocol,
            source_ip,
            source_port,
            destination_ip,
            destination_port,
            id_string,
        }
    }

    pub fn from_ipv4_packet(packet: &Ipv4Packet) -> Option<Self> {
        let (ip_data, transport) = packet.headers_data();
        transport.map(|t| {
            Self::from_headers(ip_data, t)
        })
    }

    pub fn from_ipv6_packet(packet: &Ipv6Packet) -> Option<Self> {
        let (ip_data, transport) = packet.headers_data();
        transport.map(|t| {
            Self::from_ip(
                ip_data.protocol(),
                IpAddr::from(net::to_ipv6_addr(&ip_data.source())),
                t.source_port(),
                IpAddr::from(net::to_ipv6_addr(&ip_data.destination())),
                t.destination_port(),
            )
        })
    }

    pub fn from_ip_packet(packet: &IpPacket) -> Option<Self> {
        match *packet {
            IpPacket::V4(ref p) => Self::from_ipv4_packet(p),
            IpPacket::V6(ref p) => Self::from_ipv6_packet(p),
        }
    }

    pub fn protocol(&self) -> Protocol {
        self.protocol
    }

    pub fn is_ipv6(&self) -> bool {
        self.destination_ip.is_ipv6()
    }

    pub fn rewritten_destination(&self) -> SocketAddr {
        // Preserve the emulator special-case for IPv4; IPv6 passes through
        if let IpAddr::V4(v4) = self.destination_ip {
            let ip_u32 = u32::from(v4);
            if ip_u32 == LOCALHOST_FORWARD {
                return SocketAddr::from(net::to_socket_addr(LOCALHOST, self.destination_port));
            }
        }
        net::to_socket_addr_ip(self.destination_ip, self.destination_port)
    }
}

impl fmt::Display for ConnectionId {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{}", self.id_string)
    }
}

// macros to log connection id along with the message

macro_rules! cx_format {
    ($id:tt, $str:tt, $($arg:tt)+) => {
        format!(concat!("{} ", $str), $id, $($arg)+)
    };
    ($id:tt, $str:tt) => {
        format!(concat!("{} ", $str), $id)
    };
}

macro_rules! cx_trace {
    (target: $target:expr, $id:expr, $($arg:tt)*) => {
        log::trace!(target: $target, "{}", cx_format!($id, $($arg)+))
    }
}

macro_rules! cx_debug {
    (target: $target:expr, $id:expr, $($arg:tt)*) => {
        log::debug!(target: $target, "{}", cx_format!($id, $($arg)+))
    }
}

macro_rules! cx_info {
    (target: $target:expr, $id:expr, $($arg:tt)*) => {
        log::info!(target: $target, "{}", cx_format!($id, $($arg)+))
    }
}

macro_rules! cx_warn {
    (target: $target:expr, $id:expr, $($arg:tt)*) => {
        log::warn!(target: $target, "{}", cx_format!($id, $($arg)+))
    }
}

macro_rules! cx_error {
    (target: $target:expr, $id:expr, $($arg:tt)*) => {
        log::error!(target: $target, "{}", cx_format!($id, $($arg)+))
    }
}
