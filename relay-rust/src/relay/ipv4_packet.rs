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

use super::ipv4_header::{Ipv4Header, Ipv4HeaderData, Ipv4HeaderMut};
use super::transport_header::{TransportHeader, TransportHeaderData, TransportHeaderMut};

pub const MAX_PACKET_LENGTH: usize = 1 << 16;

pub struct Ipv4Packet<'a> {
    raw: &'a mut [u8],
    ipv4_header_data: Ipv4HeaderData,
    transport_header_data: Option<TransportHeaderData>,
}

impl<'a> Ipv4Packet<'a> {
    pub fn parse(raw: &'a mut [u8]) -> Self {
        let ipv4_header_data = Ipv4HeaderData::parse(raw);
        let transport_header_data = {
            let payload = &raw[ipv4_header_data.header_length() as usize..];
            TransportHeaderData::parse(ipv4_header_data.protocol(), payload)
        };
        Self {
            raw: &mut raw[..ipv4_header_data.total_length() as usize],
            ipv4_header_data: ipv4_header_data,
            transport_header_data: transport_header_data,
        }
    }

    pub fn new(
        raw: &'a mut [u8],
        ipv4_header_data: Ipv4HeaderData,
        transport_header_data: TransportHeaderData,
    ) -> Self {
        Self {
            raw: raw,
            ipv4_header_data: ipv4_header_data,
            transport_header_data: Some(transport_header_data),
        }
    }

    #[inline]
    pub fn raw(&self) -> &[u8] {
        self.raw
    }

    #[inline]
    #[allow(dead_code)]
    pub fn ipv4_header_data(&self) -> &Ipv4HeaderData {
        &self.ipv4_header_data
    }

    #[inline]
    #[allow(dead_code)]
    pub fn ipv4_header(&self) -> Ipv4Header {
        let slice = &self.raw[..self.ipv4_header_data.header_length() as usize];
        self.ipv4_header_data.bind(slice)
    }

    #[inline]
    #[allow(dead_code)]
    pub fn ipv4_header_mut(&mut self) -> Ipv4HeaderMut {
        let slice = &mut self.raw[..self.ipv4_header_data.header_length() as usize];
        self.ipv4_header_data.bind_mut(slice)
    }

    #[inline]
    #[allow(dead_code)]
    pub fn transport_header_data(&self) -> &Option<TransportHeaderData> {
        &self.transport_header_data
    }

    #[inline]
    pub fn transport_header(&self) -> Option<TransportHeader> {
        if let Some(ref transport_header_data) = self.transport_header_data {
            let start = self.ipv4_header_data.header_length() as usize;
            let end = start + transport_header_data.header_length() as usize;
            let slice = &self.raw[start..end];
            Some(transport_header_data.bind(slice))
        } else {
            None
        }
        /*        self.transport_header_data.as_ref().map(|transport_header_data| {
            let start = self.ipv4_header_data.header_length() as usize;
            let end = start + transport_header_data.header_length() as usize;
            let slice = &self.raw[start..end];
            transport_header_data.bind(slice)
        })*/
    }

    #[inline]
    #[allow(dead_code)]
    fn transport_header_mut(&mut self) -> Option<TransportHeaderMut> {
        if let Some(ref mut transport_header_data) = self.transport_header_data {
            let start = self.ipv4_header_data.header_length() as usize;
            let end = start + transport_header_data.header_length() as usize;
            let slice = &mut self.raw[start..end];
            Some(transport_header_data.bind_mut(slice))
        } else {
            None
        }
        /*        self.transport_header_data.as_mut().map(|transport_header_data| {
            let start = self.ipv4_header_data.header_length() as usize;
            let end = start + transport_header_data.header_length() as usize;
            let slice = &mut self.raw[start..end];
            transport_header_data.bind_mut(slice)
        })*/
    }

    /// Devide the packet into parts:
    ///  - the IP v4 header
    ///  - the transport header (if any)
    ///  - the payload (if there is a transport at all)
    pub fn split(&self) -> (Ipv4Header, Option<(TransportHeader, &[u8])>) {
        let transport_index = self.ipv4_header_data.header_length() as usize;
        if let Some(ref transport_header_data) = self.transport_header_data {
            // payload_index is relative to transport
            let payload_index = transport_header_data.header_length() as usize;
            let (ipv4_header_slice, transport_slice) = self.raw.split_at(transport_index);
            let (transport_header_slice, payload_slice) = transport_slice.split_at(payload_index);
            let ipv4_header = self.ipv4_header_data.bind(ipv4_header_slice);
            let transport_header = transport_header_data.bind(transport_header_slice);
            (ipv4_header, Some((transport_header, payload_slice)))
        } else {
            let ipv4_header_slice = &self.raw[..transport_index];
            let ipv4_header = self.ipv4_header_data.bind(ipv4_header_slice);
            (ipv4_header, None)
        }
    }

    /// Devide the packet into mutable parts:
    ///  - the IP v4 header
    ///  - the transport header (if any)
    ///  - the payload (if there is a transport at all)
    pub fn split_mut(&mut self) -> (Ipv4HeaderMut, Option<(TransportHeaderMut, &mut [u8])>) {
        let transport_index = self.ipv4_header_data.header_length() as usize;
        if let Some(ref mut transport_header_data) = self.transport_header_data {
            // payload_index is relative to transport
            let payload_index = transport_header_data.header_length() as usize;
            let (ipv4_header_slice, transport_slice) = self.raw.split_at_mut(transport_index);
            let (transport_header_slice, payload_slice) =
                transport_slice.split_at_mut(payload_index);
            let ipv4_header = self.ipv4_header_data.bind_mut(ipv4_header_slice);
            let transport_header = transport_header_data.bind_mut(transport_header_slice);
            (ipv4_header, Some((transport_header, payload_slice)))
        } else {
            let ipv4_header_slice = &mut self.raw[..transport_index];
            let ipv4_header = self.ipv4_header_data.bind_mut(ipv4_header_slice);
            (ipv4_header, None)
        }
    }

    #[inline]
    pub fn is_valid(&self) -> bool {
        self.transport_header_data.is_some()
    }

    #[inline]
    pub fn length(&self) -> u16 {
        self.ipv4_header_data.total_length()
    }

    pub fn payload(&self) -> Option<&[u8]> {
        self.transport_header_data.as_ref().map(
            |transport_header_data| {
                let range = self.ipv4_header_data.header_length() as usize +
                    transport_header_data.header_length() as usize..;
                &self.raw[range]
            },
        )
    }

    pub fn compute_checksums(&mut self) {
        let (mut ipv4_header, transport) = self.split_mut();
        ipv4_header.update_checksum();
        if let Some((mut transport_header, payload)) = transport {
            transport_header.update_checksum(ipv4_header.data(), payload);
        }
    }

    /*#[inline]
    pub fn swap_source_and_destination(&mut self) {
        self.ipv4_header_mut().swap_source_and_destination();
        if let Some(mut transport_header) = self.transport_header_mut() {
            transport_header.swap_source_and_destination();
        }
    }*/
}

#[cfg(test)]
mod tests {
    use super::*;
    use byteorder::{BigEndian, WriteBytesExt};
    use relay::ipv4_header::Protocol;

    fn create_packet() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.reserve(32);

        raw.write_u8(4u8 << 4 | 5).unwrap(); // version_and_ihl
        raw.write_u8(0).unwrap(); //ToS
        raw.write_u16::<BigEndian>(32).unwrap(); // total length 20 + 8 + 4
        raw.write_u32::<BigEndian>(0).unwrap(); // id_flags_fragment_offset
        raw.write_u8(0).unwrap(); // TTL
        raw.write_u8(17).unwrap(); // protocol (UDP)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u32::<BigEndian>(0x12345678).unwrap(); // source address
        raw.write_u32::<BigEndian>(0x42424242).unwrap(); // destination address

        raw.write_u16::<BigEndian>(1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(5678).unwrap(); // destination port
        raw.write_u16::<BigEndian>(4).unwrap(); // length
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum

        raw.write_u32::<BigEndian>(0x11223344).unwrap(); // payload

        raw
    }

    #[test]
    fn parse_headers() {
        let raw = &mut create_packet()[..];
        let ipv4_packet = Ipv4Packet::parse(raw);

        {
            let ipv4_header = ipv4_packet.ipv4_header();
            assert_eq!(20, ipv4_header.header_length());
            assert_eq!(32, ipv4_header.total_length());
            assert_eq!(Protocol::Udp, ipv4_header.protocol());
            assert_eq!(0x12345678, ipv4_header.source());
            assert_eq!(0x42424242, ipv4_header.destination());

            if let Some(TransportHeaderData::Udp(ref udp_header)) =
                *ipv4_packet.transport_header_data()
            {
                assert_eq!(1234, udp_header.source_port());
                assert_eq!(5678, udp_header.destination_port());
            } else {
                panic!("No UDP transport header");
            }
        }
    }

    #[test]
    fn payload() {
        let raw = &mut create_packet()[..];
        let ipv4_packet = Ipv4Packet::parse(raw);
        assert_eq!([0x11, 0x22, 0x33, 0x44], ipv4_packet.payload().unwrap());
    }
}
