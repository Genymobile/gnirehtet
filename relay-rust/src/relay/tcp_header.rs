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

pub struct TcpHeader<'a> {
    raw: &'a [u8],
    data: &'a TcpHeaderData,
}

pub struct TcpHeaderMut<'a> {
    raw: &'a mut [u8],
    data: &'a mut TcpHeaderData,
}

#[derive(Clone)]
pub struct TcpHeaderData {
    source_port: u16,
    destination_port: u16,
    sequence_number: u32,
    acknowledgement_number: u32,
    header_length: u8,
    flags: u16,
    window: u16,
}

pub const FLAG_FIN: u16 = 1 << 0;
pub const FLAG_SYN: u16 = 1 << 1;
pub const FLAG_RST: u16 = 1 << 2;
pub const FLAG_PSH: u16 = 1 << 3;
pub const FLAG_ACK: u16 = 1 << 4;

#[allow(dead_code)]
impl TcpHeaderData {
    pub fn parse(raw: &[u8]) -> Self {
        let data_offset_and_flags = BigEndian::read_u16(&raw[12..14]);
        Self {
            source_port: BigEndian::read_u16(&raw[0..2]),
            destination_port: BigEndian::read_u16(&raw[2..4]),
            sequence_number: BigEndian::read_u32(&raw[4..8]),
            acknowledgement_number: BigEndian::read_u32(&raw[8..12]),
            header_length: ((data_offset_and_flags & 0xF000) >> 10) as u8,
            flags: data_offset_and_flags & 0x1FF,
            window: BigEndian::read_u16(&raw[14..16]),
        }
    }

    #[inline]
    pub fn bind<'c, 'a: 'c, 'b: 'c>(&'a self, raw: &'b [u8]) -> TcpHeader<'c> {
        TcpHeader::new(raw, self)
    }

    #[inline]
    pub fn bind_mut<'c, 'a: 'c, 'b: 'c>(&'a mut self, raw: &'b mut [u8]) -> TcpHeaderMut<'c> {
        TcpHeaderMut::new(raw, self)
    }

    #[inline]
    pub fn header_length(&self) -> u8 {
        self.header_length
    }

    #[inline]
    pub fn source_port(&self) -> u16 {
        self.source_port
    }

    #[inline]
    pub fn destination_port(&self) -> u16 {
        self.destination_port
    }

    #[inline]
    pub fn sequence_number(&self) -> u32 {
        self.sequence_number
    }

    #[inline]
    pub fn acknowledgement_number(&self) -> u32 {
        self.acknowledgement_number
    }

    #[inline]
    pub fn window(&self) -> u16 {
        self.window
    }

    #[inline]
    pub fn flags(&self) -> u16 {
        self.flags
    }

    #[inline]
    pub fn is_fin(&self) -> bool {
        self.flags & FLAG_FIN != 0
    }

    #[inline]
    pub fn is_syn(&self) -> bool {
        self.flags & FLAG_SYN != 0
    }

    #[inline]
    pub fn is_rst(&self) -> bool {
        self.flags & FLAG_RST != 0
    }

    #[inline]
    pub fn is_psh(&self) -> bool {
        self.flags & FLAG_PSH != 0
    }

    #[inline]
    pub fn is_ack(&self) -> bool {
        self.flags & FLAG_ACK != 0
    }
}

// shared definition for UdpHeader and UdpHeaderMut
macro_rules! tcp_header_common {
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
            pub fn data(&self) -> &TcpHeaderData {
                self.data
            }

            #[inline]
            pub fn header_length(&self) -> u8 {
                self.data.header_length
            }

            #[inline]
            pub fn source_port(&self) -> u16 {
                self.data.source_port
            }

            #[inline]
            pub fn destination_port(&self) -> u16 {
                self.data.destination_port
            }

            #[inline]
            pub fn sequence_number(&self) -> u32 {
                self.data.sequence_number
            }

            #[inline]
            pub fn acknowledgement_number(&self) -> u32 {
                self.data.acknowledgement_number
            }

            #[inline]
            pub fn window(&self) -> u16 {
                self.data.window
            }

            #[inline]
            pub fn flags(&self) -> u16 {
                self.data.flags
            }

            #[inline]
            pub fn is_fin(&self) -> bool {
                self.data.is_fin()
            }

            #[inline]
            pub fn is_syn(&self) -> bool {
                self.data.is_syn()
            }

            #[inline]
            pub fn is_rst(&self) -> bool {
                self.data.is_rst()
            }

            #[inline]
            pub fn is_psh(&self) -> bool {
                self.data.is_psh()
            }

            #[inline]
            pub fn is_ack(&self) -> bool {
                self.data.is_ack()
            }
        }
    };
}

tcp_header_common!(TcpHeader, &'a [u8], &'a TcpHeaderData);
tcp_header_common!(TcpHeaderMut, &'a mut [u8], &'a mut TcpHeaderData);

// additional methods for the mutable version
#[allow(dead_code)]
impl<'a> TcpHeaderMut<'a> {
    #[inline]
    pub fn raw_mut(&mut self) -> &mut [u8] {
        self.raw
    }

    #[inline]
    pub fn data_mut(&mut self) -> &mut TcpHeaderData {
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
    pub fn set_sequence_number(&mut self, sequence_number: u32) {
        self.data.sequence_number = sequence_number;
        BigEndian::write_u32(&mut self.raw[4..8], sequence_number);
    }

    #[inline]
    pub fn set_acknowledgement_number(&mut self, acknowledgement_number: u32) {
        self.data.acknowledgement_number = acknowledgement_number;
        BigEndian::write_u32(&mut self.raw[8..12], acknowledgement_number);
    }

    #[inline]
    pub fn set_flags(&mut self, flags: u16) {
        self.data.flags = flags;
        let mut data_offset_and_flags = BigEndian::read_u16(&mut self.raw[12..14]);
        data_offset_and_flags = data_offset_and_flags & 0xFE00 | flags & 0x1FF;

        BigEndian::write_u16(&mut self.raw[12..14], data_offset_and_flags);
    }

    #[inline]
    pub fn shrink_options(&mut self) {
        self.set_data_offset(5);
    }

    #[inline]
    fn set_data_offset(&mut self, data_offset: u8) {
        let mut data_offset_and_flags = BigEndian::read_u16(&mut self.raw[12..14]);
        data_offset_and_flags = data_offset_and_flags & 0x0FFF | ((data_offset as u16) << 12);
        BigEndian::write_u16(&mut self.raw[12..14], data_offset_and_flags);
        self.data.header_length = data_offset << 2;
    }

    #[inline]
    fn checksum(&self) -> u16 {
        BigEndian::read_u16(&self.raw[16..18])
    }

    #[inline]
    fn set_checksum(&mut self, checksum: u16) {
        BigEndian::write_u16(&mut self.raw[16..18], checksum);
    }

    pub fn update_checksum(&mut self, ipv4_header_data: &Ipv4HeaderData, payload: &[u8]) {
        // pseudo-header checksum (cf rfc793 section 3.1)
        let source = ipv4_header_data.source();
        let destination = ipv4_header_data.destination();
        let transport_length =
            ipv4_header_data.total_length() - ipv4_header_data.header_length() as u16;

        let header_length = self.header_length();
        debug_assert!(header_length % 2 == 0 && header_length >= 20);

        let payload_length = transport_length - header_length as u16;
        debug_assert_eq!(
            payload_length as usize,
            payload.len(),
            "Payload length does not match"
        );

        let mut sum = 6u32; // protocol: TCP = 6
        sum += source >> 16;
        sum += source & 0xFFFF;
        sum += destination >> 16;
        sum += destination & 0xFFFF;
        sum += transport_length as u32;

        // reset checksum field, so that it can be added with other bytes
        self.set_checksum(0);

        // checksum computation is the most CPU-intensive task in gnirehtet
        // prefer optimization over readability/safety

        let mut hsum = 0; // high-order bytes sum

        unsafe {
            let mut p = self.raw.as_ptr();
            let end = p.offset(header_length as isize);
            while p < end {
                hsum += *p as u32;
                sum += *p.offset(1) as u32;
                p = p.offset(2);
            }

            let mut p = payload.as_ptr();
            // -1 to ignore the last if payload_length is odd
            let end = p.offset(payload_length as isize - 1);
            while p < end {
                hsum += *p as u32;
                sum += *p.offset(1) as u32;
                p = p.offset(2);
            }
            if payload_length % 2 != 0 {
                // if payload length is odd, the last byte is considered high-order
                hsum += *payload.get_unchecked((payload_length - 1) as usize) as u32;
            }
        }

        // add high-order bytes sum to the global sum
        sum += hsum << 8;

        while (sum & !0xFFFF) != 0 {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        self.set_checksum(!sum as u16);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::relay::ipv4_packet::Ipv4Packet;
    use crate::relay::transport_header::TransportHeaderMut;
    use byteorder::{BigEndian, WriteBytesExt};

    fn create_packet() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.reserve(44);

        raw.write_u8(4u8 << 4 | 5).unwrap(); // version_and_ihl
        raw.write_u8(0).unwrap(); //ToS
        raw.write_u16::<BigEndian>(44).unwrap(); // total length
        raw.write_u32::<BigEndian>(0).unwrap(); // id_flags_fragment_offset
        raw.write_u8(0).unwrap(); // TTL
        raw.write_u8(6).unwrap(); // protocol (TCP)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u32::<BigEndian>(0x12345678).unwrap(); // source address
        raw.write_u32::<BigEndian>(0xA2A24242).unwrap(); // destination address

        raw.write_u16::<BigEndian>(0x1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(0x5678).unwrap(); // destination port
        raw.write_u32::<BigEndian>(0x111).unwrap(); // sequence number
        raw.write_u32::<BigEndian>(0x222).unwrap(); // acknowledgement number
        raw.write_u16::<BigEndian>(5 << 12).unwrap(); // data offset + flags(0)
        raw.write_u16::<BigEndian>(0).unwrap(); // window (don't care for these tests)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u16::<BigEndian>(0).unwrap(); // urgent pointer

        raw.write_u32::<BigEndian>(0x1122EEFF).unwrap(); // payload

        raw
    }

    fn create_odd_packet() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.reserve(45);

        raw.write_u8(4u8 << 4 | 5).unwrap(); // version_and_ihl
        raw.write_u8(0).unwrap(); //ToS
        raw.write_u16::<BigEndian>(45).unwrap(); // total length
        raw.write_u32::<BigEndian>(0).unwrap(); // id_flags_fragment_offset
        raw.write_u8(0).unwrap(); // TTL
        raw.write_u8(6).unwrap(); // protocol (TCP)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u32::<BigEndian>(0x12345678).unwrap(); // source address
        raw.write_u32::<BigEndian>(0xA2A24242).unwrap(); // destination address

        raw.write_u16::<BigEndian>(0x1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(0x5678).unwrap(); // destination port
        raw.write_u32::<BigEndian>(0x111).unwrap(); // sequence number
        raw.write_u32::<BigEndian>(0x222).unwrap(); // acknowledgement number
        raw.write_u16::<BigEndian>(5 << 12).unwrap(); // data offset + flags(0)
        raw.write_u16::<BigEndian>(0).unwrap(); // window (don't care for these tests)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u16::<BigEndian>(0).unwrap(); // urgent pointer

        // payload
        raw.write_u32::<BigEndian>(0x1122EEFF).unwrap();
        raw.write_u8(0x88).unwrap();

        raw
    }

    fn create_empty_packet() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.reserve(40);

        raw.write_u8(4u8 << 4 | 5).unwrap(); // version_and_ihl
        raw.write_u8(0).unwrap(); //ToS
        raw.write_u16::<BigEndian>(40).unwrap(); // total length
        raw.write_u32::<BigEndian>(0).unwrap(); // id_flags_fragment_offset
        raw.write_u8(0).unwrap(); // TTL
        raw.write_u8(6).unwrap(); // protocol (TCP)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u32::<BigEndian>(0x12345678).unwrap(); // source address
        raw.write_u32::<BigEndian>(0xA2A24242).unwrap(); // destination address

        raw.write_u16::<BigEndian>(0x1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(0x5678).unwrap(); // destination port
        raw.write_u32::<BigEndian>(0x111).unwrap(); // sequence number
        raw.write_u32::<BigEndian>(0x222).unwrap(); // acknowledgement number
        raw.write_u16::<BigEndian>(5 << 12).unwrap(); // data offset + flags(0)
        raw.write_u16::<BigEndian>(0).unwrap(); // window (don't care for these tests)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u16::<BigEndian>(0).unwrap(); // urgent pointer

        raw
    }

    fn create_tcp_header() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.reserve(20);

        raw.write_u16::<BigEndian>(0x1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(0x5678).unwrap(); // destination port
        raw.write_u32::<BigEndian>(0x111).unwrap(); // sequence number
        raw.write_u32::<BigEndian>(0x222).unwrap(); // acknowledgement number
        raw.write_u16::<BigEndian>(5 << 12).unwrap(); // data offset + flags(0)
        raw.write_u16::<BigEndian>(0).unwrap(); // window (don't care for these tests)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u16::<BigEndian>(0).unwrap(); // urgent pointer

        raw
    }

    #[test]
    fn edit_header() {
        let raw = &mut create_tcp_header()[..];
        let mut header_data = TcpHeaderData::parse(raw);
        let mut header = header_data.bind_mut(raw);

        header.set_source_port(1111);
        header.set_destination_port(2222);
        header.set_sequence_number(300);
        header.set_acknowledgement_number(101);
        header.set_flags(FLAG_FIN | FLAG_ACK);

        assert_eq!(1111, header.source_port());
        assert_eq!(2222, header.destination_port());
        assert_eq!(300, header.sequence_number());
        assert_eq!(101, header.acknowledgement_number());
        assert_eq!(FLAG_FIN | FLAG_ACK, header.flags());

        {
            let raw = header.raw();
            // assert that the buffer has been modified
            let raw_source_port = BigEndian::read_u16(&raw[0..2]);
            let raw_destination_port = BigEndian::read_u16(&raw[2..4]);
            let raw_sequence_number = BigEndian::read_u32(&raw[4..8]);
            let raw_acknowledgement_number = BigEndian::read_u32(&raw[8..12]);
            let raw_data_offset_and_flags = BigEndian::read_u16(&raw[12..14]);

            assert_eq!(1111, raw_source_port);
            assert_eq!(2222, raw_destination_port);
            assert_eq!(300, raw_sequence_number);
            assert_eq!(101, raw_acknowledgement_number);
            assert_eq!(0x5011, raw_data_offset_and_flags);
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

    #[test]
    fn compute_checksum() {
        let raw = &mut create_packet()[..];
        let mut ipv4_packet = Ipv4Packet::parse(raw);
        let (ipv4_header, mut transport) = ipv4_packet.split_mut();
        if let Some((TransportHeaderMut::Tcp(ref mut tcp_header), ref payload)) = transport {
            // set a fake checksum value to assert that it is correctly computed
            tcp_header.set_checksum(0x79);
            tcp_header.update_checksum(ipv4_header.data(), payload);
            let checksum = tcp_header.checksum();

            let expected_checksum = {
                // pseudo-header
                let mut sum: u32 = 0x1234 + 0x5678 + 0xA2A2 + 0x4242 + 0x0006 + 0x0018;

                // header
                sum += 0x1234
                    + 0x5678
                    + 0x0000
                    + 0x0111
                    + 0x0000
                    + 0x0222
                    + 0x5000
                    + 0x0000
                    + 0x0000
                    + 0x0000;

                // payload
                sum += 0x1122 + 0xEEFF;

                while (sum & !0xFFFF) != 0 {
                    sum = (sum & 0xFFFF) + (sum >> 16);
                }
                !sum as u16
            };

            assert_eq!(expected_checksum, checksum);
        } else {
            panic!("Not a TCP packet");
        }
    }

    #[test]
    fn compute_checksum_odd() {
        let raw = &mut create_odd_packet()[..];
        let mut ipv4_packet = Ipv4Packet::parse(raw);
        let (ipv4_header, mut transport) = ipv4_packet.split_mut();
        if let Some((TransportHeaderMut::Tcp(ref mut tcp_header), ref payload)) = transport {
            // set a fake checksum value to assert that it is correctly computed
            tcp_header.set_checksum(0x79);
            tcp_header.update_checksum(ipv4_header.data(), payload);
            let checksum = tcp_header.checksum();

            let expected_checksum = {
                // pseudo-header
                let mut sum: u32 = 0x1234 + 0x5678 + 0xA2A2 + 0x4242 + 0x0006 + 0x0019;

                // header
                sum += 0x1234
                    + 0x5678
                    + 0x0000
                    + 0x0111
                    + 0x0000
                    + 0x0222
                    + 0x5000
                    + 0x0000
                    + 0x0000
                    + 0x0000;

                // payload
                sum += 0x1122 + 0xEEFF + 0x8800;

                while (sum & !0xFFFF) != 0 {
                    sum = (sum & 0xFFFF) + (sum >> 16);
                }
                !sum as u16
            };

            assert_eq!(expected_checksum, checksum);
        } else {
            panic!("Not a TCP packet");
        }
    }

    #[test]
    fn compute_checksum_empty_payload() {
        let raw = &mut create_empty_packet()[..];
        let mut ipv4_packet = Ipv4Packet::parse(raw);
        let (ipv4_header, mut transport) = ipv4_packet.split_mut();
        if let Some((TransportHeaderMut::Tcp(ref mut tcp_header), ref payload)) = transport {
            // set a fake checksum value to assert that it is correctly computed
            tcp_header.set_checksum(0x79);
            tcp_header.update_checksum(ipv4_header.data(), payload);
            let checksum = tcp_header.checksum();

            let expected_checksum = {
                // pseudo-header
                let mut sum: u32 = 0x1234 + 0x5678 + 0xA2A2 + 0x4242 + 0x0006 + 0x0014;

                // header
                sum += 0x1234
                    + 0x5678
                    + 0x0000
                    + 0x0111
                    + 0x0000
                    + 0x0222
                    + 0x5000
                    + 0x0000
                    + 0x0000
                    + 0x0000;

                while (sum & !0xFFFF) != 0 {
                    sum = (sum & 0xFFFF) + (sum >> 16);
                }
                !sum as u16
            };

            assert_eq!(expected_checksum, checksum);
        } else {
            panic!("Not a TCP packet");
        }
    }

    fn create_long_packet() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.reserve(45);

        raw.write_u8(4u8 << 4 | 5).unwrap(); // version_and_ihl
        raw.write_u8(0).unwrap(); //ToS
        raw.write_u16::<BigEndian>(1240).unwrap(); // total length
        raw.write_u32::<BigEndian>(0).unwrap(); // id_flags_fragment_offset
        raw.write_u8(0).unwrap(); // TTL
        raw.write_u8(6).unwrap(); // protocol (TCP)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u32::<BigEndian>(0x12345678).unwrap(); // source address
        raw.write_u32::<BigEndian>(0xA2A24242).unwrap(); // destination address

        raw.write_u16::<BigEndian>(0x1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(0x5678).unwrap(); // destination port
        raw.write_u32::<BigEndian>(0x111).unwrap(); // sequence number
        raw.write_u32::<BigEndian>(0x222).unwrap(); // acknowledgement number
        raw.write_u16::<BigEndian>(5 << 12).unwrap(); // data offset + flags(0)
        raw.write_u16::<BigEndian>(0).unwrap(); // window (don't care for these tests)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u16::<BigEndian>(0).unwrap(); // urgent pointer

        // payload
        for i in 0u16..1200 {
            raw.write_u8(i as u8).unwrap();
        }

        raw
    }

    // built-in rust bench is still unstable, use a simple test instead
    // run with: cargo test bench_checksum --release -- --nocapture
    // manual benchmark
    #[ignore]
    #[test]
    fn bench_checksum() {
        let raw = &mut create_long_packet()[..];
        let mut ipv4_packet = Ipv4Packet::parse(raw);
        let (ipv4_header, mut transport) = ipv4_packet.split_mut();
        if let Some((TransportHeaderMut::Tcp(ref mut tcp_header), ref payload)) = transport {
            use std::time::Instant;
            let start = Instant::now();
            for _ in 0..5000000 {
                tcp_header.update_checksum(ipv4_header.data(), payload);
            }
            let duration = start.elapsed();
            let ms = duration.as_secs() * 1000 + duration.subsec_nanos() as u64 / 1000000;
            println!("5000000 TCP checksums: {}ms", ms);
        } else {
            panic!("Not a TCP packet");
        }
    }
}
