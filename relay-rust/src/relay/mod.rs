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

pub use self::relay::Relay;
pub mod byte_buffer;

mod binary;
mod client;
mod close_listener;
#[macro_use]
mod connection;
mod datagram;
mod datagram_buffer;
#[macro_use]
mod interrupt;
mod ipv4_header;
mod ipv4_packet;
mod ipv4_packet_buffer;
mod net;
mod packet_source;
mod packetizer;
#[allow(clippy::module_inception)] // relay.rs is in relay/
mod relay;
mod router;
mod selector;
mod stream_buffer;
mod tcp_connection;
mod tcp_header;
mod transport_header;
mod tunnel_server;
mod udp_connection;
mod udp_header;
