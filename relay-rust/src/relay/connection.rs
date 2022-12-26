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
use std::net::SocketAddrV4;

use super::client::ClientChannel;
use super::ipv4_header::{Ipv4HeaderData, Protocol};
use super::ipv4_packet::Ipv4Packet;
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
        ipv4_packet: &Ipv4Packet,
    );
    fn close(&mut self, selector: &mut Selector);
    fn is_expired(&self) -> bool;
    fn is_closed(&self) -> bool;
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ConnectionId {
    protocol: Protocol,
    source_ip: u32,
    source_port: u16,
    destination_ip: u32,
    destination_port: u16,
    id_string: String,
}

impl ConnectionId {
    pub fn from_headers(
        ipv4_header_data: &Ipv4HeaderData,
        transport_header_data: &TransportHeaderData,
    ) -> Self {
        let source_ip = ipv4_header_data.source();
        let source_port = transport_header_data.source_port();
        let destination_ip = ipv4_header_data.destination();
        let destination_port = transport_header_data.destination_port();
        let id_string = format!(
            "{} -> {}",
            net::to_socket_addr(source_ip, source_port),
            net::to_socket_addr(destination_ip, destination_port)
        );
        Self {
            protocol: ipv4_header_data.protocol(),
            source_ip,
            source_port,
            destination_ip,
            destination_port,
            id_string,
        }
    }

    pub fn protocol(&self) -> Protocol {
        self.protocol
    }

    pub fn rewritten_destination(&self) -> SocketAddrV4 {
        let ip = if self.destination_ip == LOCALHOST_FORWARD {
            LOCALHOST
        } else {
            self.destination_ip
        };
        net::to_socket_addr(ip, self.destination_port)
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
