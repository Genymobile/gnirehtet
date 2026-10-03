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

use super::ipv6_header::{Ipv6Header, Ipv6HeaderData, Ipv6HeaderMut, IPV6_HEADER_LENGTH};
use super::transport_header::{TransportHeader, TransportHeaderData, TransportHeaderMut};

pub const MAX_IPV6_PACKET_LENGTH: usize = IPV6_HEADER_LENGTH + 65535;

pub struct Ipv6Packet<'a> {
    raw: &'a mut [u8],
    ipv6_header_data: Ipv6HeaderData,
    transport_header_data: Option<TransportHeaderData>,
}

impl<'a> Ipv6Packet<'a> {
    pub fn parse(raw: &'a mut [u8]) -> Self {
        let ipv6_header_data = Ipv6HeaderData::parse(raw);
        let transport_header_data = {
            let payload = &raw[IPV6_HEADER_LENGTH..];
            TransportHeaderData::parse(ipv6_header_data.protocol(), payload)
        };
        let total = ipv6_header_data.total_length() as usize;
        let len = total.min(raw.len());
        Self {
            raw: &mut raw[..len],
            ipv6_header_data,
            transport_header_data,
        }
    }

    pub fn new(
        raw: &'a mut [u8],
        ipv6_header_data: Ipv6HeaderData,
        transport_header_data: TransportHeaderData,
    ) -> Self {
        Self {
            raw,
            ipv6_header_data,
            transport_header_data: Some(transport_header_data),
        }
    }

    #[inline]
    pub fn raw(&self) -> &[u8] {
        self.raw
    }

    #[inline]
    pub fn headers_data(&self) -> (&Ipv6HeaderData, Option<&TransportHeaderData>) {
        (&self.ipv6_header_data, self.transport_header_data.as_ref())
    }

    pub fn headers(&self) -> (Ipv6Header, Option<TransportHeader>) {
        let transport_index = IPV6_HEADER_LENGTH;
        if let Some(ref transport_header_data) = self.transport_header_data {
            let (ipv6_header_slice, transport_slice) = self.raw.split_at(transport_index);
            let payload_index = transport_header_data.header_length() as usize;
            let transport_header_slice = &transport_slice[..payload_index];
            let ipv6_header = self.ipv6_header_data.bind(ipv6_header_slice);
            let transport_header = transport_header_data.bind(transport_header_slice);
            (ipv6_header, Some(transport_header))
        } else {
            let ipv6_header_slice = &self.raw[..transport_index];
            let ipv6_header = self.ipv6_header_data.bind(ipv6_header_slice);
            (ipv6_header, None)
        }
    }

    #[inline]
    #[allow(dead_code)]
    pub fn ipv6_header_data(&self) -> &Ipv6HeaderData {
        &self.ipv6_header_data
    }

    #[inline]
    #[allow(dead_code)]
    pub fn ipv6_header(&self) -> Ipv6Header {
        let slice = &self.raw[..IPV6_HEADER_LENGTH];
        self.ipv6_header_data.bind(slice)
    }

    #[inline]
    pub fn transport_header_data(&self) -> Option<&TransportHeaderData> {
        self.transport_header_data.as_ref()
    }

    #[inline]
    pub fn transport_header(&self) -> Option<TransportHeader> {
        if let Some(ref transport_header_data) = self.transport_header_data {
            let start = IPV6_HEADER_LENGTH;
            let end = start + transport_header_data.header_length() as usize;
            let slice = &self.raw[start..end];
            Some(transport_header_data.bind(slice))
        } else {
            None
        }
    }

    /// Divide the packet into parts:
    ///  - the IPv6 header
    ///  - the transport header (if any)
    ///  - the payload (if there is a transport at all)
    #[allow(dead_code)]
    pub fn split(&self) -> (Ipv6Header, Option<(TransportHeader, &[u8])>) {
        let transport_index = IPV6_HEADER_LENGTH;
        if let Some(ref transport_header_data) = self.transport_header_data {
            let payload_index = transport_header_data.header_length() as usize;
            let (ipv6_header_slice, transport_slice) = self.raw.split_at(transport_index);
            let (transport_header_slice, payload_slice) =
                transport_slice.split_at(payload_index);
            let ipv6_header = self.ipv6_header_data.bind(ipv6_header_slice);
            let transport_header = transport_header_data.bind(transport_header_slice);
            (ipv6_header, Some((transport_header, payload_slice)))
        } else {
            let ipv6_header_slice = &self.raw[..transport_index];
            let ipv6_header = self.ipv6_header_data.bind(ipv6_header_slice);
            (ipv6_header, None)
        }
    }

    /// Divide the packet into mutable parts.
    pub fn split_mut(&mut self) -> (Ipv6HeaderMut, Option<(TransportHeaderMut, &mut [u8])>) {
        let transport_index = IPV6_HEADER_LENGTH;
        if let Some(ref mut transport_header_data) = self.transport_header_data {
            let payload_index = transport_header_data.header_length() as usize;
            let (ipv6_header_slice, transport_slice) = self.raw.split_at_mut(transport_index);
            let (transport_header_slice, payload_slice) =
                transport_slice.split_at_mut(payload_index);
            let ipv6_header = self.ipv6_header_data.bind_mut(ipv6_header_slice);
            let transport_header = transport_header_data.bind_mut(transport_header_slice);
            (ipv6_header, Some((transport_header, payload_slice)))
        } else {
            let ipv6_header_slice = &mut self.raw[..transport_index];
            let ipv6_header = self.ipv6_header_data.bind_mut(ipv6_header_slice);
            (ipv6_header, None)
        }
    }

    #[inline]
    pub fn is_valid(&self) -> bool {
        self.transport_header_data.is_some()
    }

    #[inline]
    pub fn length(&self) -> u16 {
        self.ipv6_header_data.total_length()
    }

    pub fn payload(&self) -> Option<&[u8]> {
        self.transport_header_data
            .as_ref()
            .map(|transport_header_data| {
                let range = IPV6_HEADER_LENGTH + transport_header_data.header_length() as usize..;
                &self.raw[range]
            })
    }

    pub fn compute_checksums(&mut self) {
        let (ipv6_header, transport) = self.split_mut();
        if let Some((mut transport_header, payload)) = transport {
            transport_header.update_checksum_v6(ipv6_header.data(), payload);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::relay::ipv4_header::Protocol;
    use byteorder::{BigEndian, WriteBytesExt};

    fn create_packet() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.write_u8(6u8 << 4).unwrap();
        raw.write_u8(0).unwrap();
        raw.write_u16::<BigEndian>(0).unwrap();
        raw.write_u16::<BigEndian>(12).unwrap(); // payload length 8 + 4
        raw.write_u8(17).unwrap(); // UDP
        raw.write_u8(64).unwrap(); // hop limit
        raw.extend_from_slice(&[0xfd, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2]);
        raw.extend_from_slice(&[
            0x20, 1, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
        ]);

        raw.write_u16::<BigEndian>(1234).unwrap();
        raw.write_u16::<BigEndian>(5678).unwrap();
        raw.write_u16::<BigEndian>(12).unwrap();
        raw.write_u16::<BigEndian>(0).unwrap();

        raw.write_u32::<BigEndian>(0x11223344).unwrap();
        raw
    }

    #[test]
    fn parse_headers() {
        let raw = &mut create_packet()[..];
        let packet = Ipv6Packet::parse(raw);
        assert_eq!(52, packet.length());
        assert_eq!(Protocol::Udp, packet.ipv6_header_data().protocol());
        assert_eq!([0x11, 0x22, 0x33, 0x44], packet.payload().unwrap());
    }
}
