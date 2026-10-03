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

use super::ipv4_packet::Ipv4Packet;
use super::ipv6_header::IPV6_HEADER_LENGTH;
use super::ipv6_packet::Ipv6Packet;

pub const MAX_IP_PACKET_LENGTH: usize = IPV6_HEADER_LENGTH + 65535;

pub enum IpPacket<'a> {
    V4(Ipv4Packet<'a>),
    V6(Ipv6Packet<'a>),
}

impl<'a> IpPacket<'a> {
    #[inline]
    pub fn raw(&self) -> &[u8] {
        match *self {
            IpPacket::V4(ref p) => p.raw(),
            IpPacket::V6(ref p) => p.raw(),
        }
    }

    #[inline]
    pub fn length(&self) -> u16 {
        match *self {
            IpPacket::V4(ref p) => p.length(),
            IpPacket::V6(ref p) => p.length(),
        }
    }

    #[inline]
    pub fn is_valid(&self) -> bool {
        match *self {
            IpPacket::V4(ref p) => p.is_valid(),
            IpPacket::V6(ref p) => p.is_valid(),
        }
    }

    #[inline]
    pub fn version(&self) -> u8 {
        match *self {
            IpPacket::V4(_) => 4,
            IpPacket::V6(_) => 6,
        }
    }
}

/// Peek IP version + total packet length for either v4 or v6.
pub fn peek_version_length(raw: &[u8]) -> Option<(u8, u16)> {
    if raw.is_empty() {
        return None;
    }
    match raw[0] >> 4 {
        4 => super::ipv4_header::peek_version_length(raw),
        6 => super::ipv6_header::peek_version_length(raw),
        _ => None,
    }
}
