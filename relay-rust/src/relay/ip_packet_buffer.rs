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
use super::byte_buffer::ByteBuffer;
use super::ip_packet::{peek_version_length, IpPacket, MAX_IP_PACKET_LENGTH};
use super::ipv4_packet::Ipv4Packet;
use super::ipv6_packet::Ipv6Packet;

use log::*;
use std::io;

pub struct IpPacketBuffer {
    buf: ByteBuffer,
}

impl IpPacketBuffer {
    pub fn new() -> Self {
        Self {
            buf: ByteBuffer::new(MAX_IP_PACKET_LENGTH),
        }
    }

    pub fn read_from<R: io::Read>(&mut self, source: &mut R) -> io::Result<bool> {
        self.buf.read_from(source)
    }

    fn available_packet_length(&self) -> Option<u16> {
        let data = self.buf.peek();
        trace!("Parse packet: {}", binary::build_packet_string(data));
        if let Some((version, length)) = peek_version_length(data) {
            assert!(
                version == 4 || version == 6,
                "Unexpected IP version={}",
                version
            );
            if length as usize <= data.len() {
                Some(length)
            } else {
                None
            }
        } else {
            None
        }
    }

    pub fn as_ip_packet(&mut self) -> Option<IpPacket> {
        if self.available_packet_length().is_some() {
            let data = self.buf.peek_mut();
            let version = data[0] >> 4;
            match version {
                4 => Some(IpPacket::V4(Ipv4Packet::parse(data))),
                6 => Some(IpPacket::V6(Ipv6Packet::parse(data))),
                _ => None,
            }
        } else {
            None
        }
    }

    // Kept for backward compatibility during migration
    pub fn as_ipv4_packet(&mut self) -> Option<Ipv4Packet> {
        if self.available_packet_length().is_some() {
            let data = self.buf.peek_mut();
            if data[0] >> 4 == 4 {
                Some(Ipv4Packet::parse(data))
            } else {
                None
            }
        } else {
            None
        }
    }

    pub fn next(&mut self) {
        let length = self
            .available_packet_length()
            .expect("next() called while there was no packet") as usize;
        self.buf.consume(length);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use byteorder::{BigEndian, WriteBytesExt};
    use std::io;

    fn write_ipv4_packet_to(raw: &mut Vec<u8>) {
        raw.write_u8(4u8 << 4 | 5).unwrap();
        raw.write_u8(0).unwrap();
        raw.write_u16::<BigEndian>(32).unwrap();
        raw.write_u32::<BigEndian>(0).unwrap();
        raw.write_u8(0).unwrap();
        raw.write_u8(17).unwrap();
        raw.write_u16::<BigEndian>(0).unwrap();
        raw.write_u32::<BigEndian>(0x12345678).unwrap();
        raw.write_u32::<BigEndian>(0x42424242).unwrap();
        raw.write_u16::<BigEndian>(1234).unwrap();
        raw.write_u16::<BigEndian>(5678).unwrap();
        raw.write_u16::<BigEndian>(12).unwrap();
        raw.write_u16::<BigEndian>(0).unwrap();
        raw.write_u32::<BigEndian>(0x11223344).unwrap();
    }

    fn write_ipv6_packet_to(raw: &mut Vec<u8>) {
        raw.write_u8(6u8 << 4).unwrap();
        raw.write_u8(0).unwrap();
        raw.write_u16::<BigEndian>(0).unwrap();
        raw.write_u16::<BigEndian>(12).unwrap();
        raw.write_u8(17).unwrap();
        raw.write_u8(64).unwrap();
        raw.extend_from_slice(&[0xfd, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2]);
        raw.extend_from_slice(&[
            0x20, 1, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
        ]);
        raw.write_u16::<BigEndian>(1234).unwrap();
        raw.write_u16::<BigEndian>(5678).unwrap();
        raw.write_u16::<BigEndian>(12).unwrap();
        raw.write_u16::<BigEndian>(0).unwrap();
        raw.write_u32::<BigEndian>(0x11223344).unwrap();
    }

    #[test]
    fn parse_mixed_packets() {
        let mut raw = Vec::new();
        write_ipv4_packet_to(&mut raw);
        write_ipv6_packet_to(&mut raw);
        let mut buf = IpPacketBuffer::new();
        let mut cursor = io::Cursor::new(raw);
        buf.read_from(&mut cursor).unwrap();

        let p1 = buf.as_ip_packet().unwrap();
        assert_eq!(4, p1.version());
        assert_eq!(32, p1.length());
        buf.next();
        let p2 = buf.as_ip_packet().unwrap();
        assert_eq!(6, p2.version());
        assert_eq!(52, p2.length());
        buf.next();
        assert!(buf.as_ip_packet().is_none());
    }

    #[test]
    fn parse_fragmented_ipv6_packet() {
        let mut raw = Vec::new();
        write_ipv6_packet_to(&mut raw);
        let mut buf = IpPacketBuffer::new();
        let mut cursor = io::Cursor::new(&raw[..20]);
        buf.read_from(&mut cursor).unwrap();
        assert!(buf.as_ip_packet().is_none());
        let mut cursor = io::Cursor::new(&raw[20..]);
        buf.read_from(&mut cursor).unwrap();
        let p = buf.as_ip_packet().unwrap();
        assert_eq!(6, p.version());
    }
}
