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

use super::ipv4_header::Ipv4HeaderData;
use byteorder::{BigEndian, ByteOrder};
use std::mem;

pub const UDP_HEADER_LENGTH: u8 = 8;

pub struct UdpHeader<'a> {
    raw: &'a [u8],
    data: &'a UdpHeaderData,
}

pub struct UdpHeaderMut<'a> {
    raw: &'a mut [u8],
    data: &'a mut UdpHeaderData,
}

#[derive(Clone)]
pub struct UdpHeaderData {
    source_port: u16,
    destination_port: u16,
}

#[allow(dead_code)]
impl UdpHeaderData {
    pub fn parse(raw: &[u8]) -> Self {
        Self {
            source_port: BigEndian::read_u16(&raw[0..2]),
            destination_port: BigEndian::read_u16(&raw[2..4]),
        }
    }

    #[inline]
    pub fn bind<'c, 'a: 'c, 'b: 'c>(&'a self, raw: &'b [u8]) -> UdpHeader<'c> {
        UdpHeader::new(raw, self)
    }

    #[inline]
    pub fn bind_mut<'c, 'a: 'c, 'b: 'c>(&'a mut self, raw: &'b mut [u8]) -> UdpHeaderMut<'c> {
        UdpHeaderMut::new(raw, self)
    }

    #[inline]
    pub fn source_port(&self) -> u16 {
        self.source_port
    }

    #[inline]
    pub fn destination_port(&self) -> u16 {
        self.destination_port
    }
}

// shared definition for UdpHeader and UdpHeaderMut
macro_rules! udp_header_common {
    ($name:ident, $raw_type:ty, $data_type:ty) => {
        // for readability, declare structs manually outside the macro
        #[allow(dead_code)]
        impl<'a> $name<'a> {
            pub fn new(raw: $raw_type, data: $data_type) -> Self {
                Self {
                    raw: raw,
                    data: data,
                }
            }

            #[inline]
            pub fn raw(&self) -> &[u8] {
                self.raw
            }

            #[inline]
            pub fn data(&self) -> &UdpHeaderData {
                self.data
            }

            #[inline]
            pub fn source_port(&self) -> u16 {
                self.data.source_port
            }

            #[inline]
            pub fn destination_port(&self) -> u16 {
                self.data.destination_port
            }
        }
    };
}

udp_header_common!(UdpHeader, &'a [u8], &'a UdpHeaderData);
udp_header_common!(UdpHeaderMut, &'a mut [u8], &'a mut UdpHeaderData);

// additional methods for the mutable version
#[allow(dead_code)]
impl<'a> UdpHeaderMut<'a> {
    #[inline]
    pub fn raw_mut(&mut self) -> &mut [u8] {
        self.raw
    }

    #[inline]
    pub fn data_mut(&mut self) -> &mut UdpHeaderData {
        self.data
    }

    #[inline]
    pub fn set_source_port(&mut self, source_port: u16) {
        self.data.source_port = source_port;
        BigEndian::write_u16(&mut self.raw[0..2], source_port);
    }

    #[inline]
    pub fn set_destination_port(&mut self, destination_port: u16) {
        self.data.destination_port = destination_port;
        BigEndian::write_u16(&mut self.raw[2..4], destination_port);
    }

    pub fn swap_source_and_destination(&mut self) {
        mem::swap(&mut self.data.source_port, &mut self.data.destination_port);
        for i in 0..2 {
            self.raw.swap(i, i + 2);
        }
    }

    #[inline]
    pub fn set_payload_length(&mut self, payload_length: u16) {
        let total_length = UDP_HEADER_LENGTH as u16 + payload_length;
        BigEndian::write_u16(&mut self.raw[4..6], total_length);
    }

    #[inline]
    fn set_checksum(&mut self, checksum: u16) {
        BigEndian::write_u16(&mut self.raw[6..8], checksum);
    }

    #[inline]
    pub fn update_checksum(&mut self, _ipv4_header_data: &Ipv4HeaderData, _payload: &[u8]) {
        // disable checksum validation
        self.set_checksum(0);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use byteorder::{BigEndian, WriteBytesExt};

    fn create_header() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.reserve(8);
        raw.write_u16::<BigEndian>(1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(5678).unwrap(); // destination port
        raw.write_u16::<BigEndian>(42).unwrap(); // length
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw
    }

    #[test]
    fn parse_header() {
        let raw = &create_header()[..];
        let data = UdpHeaderData::parse(raw);
        assert_eq!(1234, data.source_port());
        assert_eq!(5678, data.destination_port());
    }

    #[test]
    fn edit_header() {
        let raw = &mut create_header()[..];
        let mut header_data = UdpHeaderData::parse(raw);
        let mut header = header_data.bind_mut(raw);

        header.set_source_port(1111);
        header.set_destination_port(2222);
        header.set_payload_length(34);
        assert_eq!(1111, header.source_port());
        assert_eq!(2222, header.destination_port());

        {
            let raw = header.raw();
            // assert that the buffer has been modified
            let raw_source_port = BigEndian::read_u16(&raw[0..2]);
            let raw_destination_port = BigEndian::read_u16(&raw[2..4]);
            let raw_total_length = BigEndian::read_u16(&raw[4..6]);
            assert_eq!(1111, raw_source_port);
            assert_eq!(2222, raw_destination_port);
            assert_eq!(34 + 8, raw_total_length);
        }

        header.swap_source_and_destination();

        assert_eq!(2222, header.source_port());
        assert_eq!(1111, header.destination_port());

        let raw = header.raw();
        let raw_source_port = BigEndian::read_u16(&raw[0..2]);
        let raw_destination_port = BigEndian::read_u16(&raw[2..4]);
        assert_eq!(2222, raw_source_port);
        assert_eq!(1111, raw_destination_port);
    }
}
