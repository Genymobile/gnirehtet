use std::ops::Range;

use super::ipv4_header::{IPv4Header, IPv4HeaderData};
use super::transport_header::TransportHeader;

pub const MAX_PACKET_LENGTH: usize = 1 << 16;

pub struct IPv4Packet<'a> {
    raw: &'a mut [u8],
    ipv4_header_data: IPv4HeaderData,
    transport_header: Option<TransportHeader>,
}

impl<'a> IPv4Packet<'a> {
    pub fn parse(raw: &'a mut [u8]) -> Self {
        let ipv4_header_data = IPv4HeaderData::parse(raw);
        let transport_header = {
            let payload = &raw[ipv4_header_data.header_length() as usize..];
            TransportHeader::parse(ipv4_header_data.protocol(), payload)
        };
        Self {
            raw: &mut raw[..ipv4_header_data.total_length() as usize],
            ipv4_header_data: ipv4_header_data,
            transport_header: transport_header,
        }
    }

    pub fn new(raw: &'a mut [u8], ipv4_header_data: IPv4HeaderData, transport_header: TransportHeader) -> Self {
        Self {
            raw: raw,
            ipv4_header_data: ipv4_header_data,
            transport_header: Some(transport_header),
        }
    }

    pub fn raw(&self) -> &[u8] {
        self.raw
    }

    pub fn raw_mut(&mut self) -> &mut [u8] {
        self.raw
    }

    pub fn ipv4_header(&mut self) -> IPv4Header {
        let slice = &mut self.raw[..self.ipv4_header_data.header_length() as usize];
        IPv4Header::new(slice, &mut self.ipv4_header_data)
    }

    pub fn ipv4_header_data(&self) -> &IPv4HeaderData {
        &self.ipv4_header_data
    }

    pub fn transport_header(&self) -> &Option<TransportHeader> {
        &self.transport_header
    }

    pub fn transport_header_mut(&mut self) -> &mut Option<TransportHeader> {
        &mut self.transport_header
    }

    pub fn is_valid(&self) -> bool {
        self.transport_header.is_some()
    }

    pub fn length(&self) -> u16 {
        self.ipv4_header_data.total_length()
    }

/*
    pub fn ipv4_header_range(&self) -> Range<usize> {
        let start = 0;
        let end = self.ipv4_header.header_length() as usize;
        start..end
    }

    pub fn transport_range(&self) -> Option<Range<usize>> {
        self.transport_header.as_ref().map(|_| {
            let start = self.ipv4_header.header_length() as usize;
            let end = self.raw.len();
            start..end
        })
    }

    pub fn transport_header_range(&self) -> Option<Range<usize>> {
        self.transport_header.as_ref().map(|transport_header| {
            let start = self.ipv4_header.header_length() as usize;
            let end = start + transport_header.header_length() as usize;
            start..end
        })
    }

    pub fn payload_range(&self) -> Option<Range<usize>> {
        self.transport_header.as_ref().map(|transport_header| {
            let start = self.ipv4_header.header_length() as usize + transport_header.header_length() as usize;
            let end = self.raw.len();
            start..end
        })
    }
*/
    // TODO delete function
    pub fn payload(&self) -> Option<&[u8]> {
        self.transport_header.as_ref().map(|transport_header| {
            let range = self.ipv4_header_data.header_length() as usize + transport_header.header_length() as usize..;
            &self.raw[range]
        })
    }

    pub fn compute_checksums(&mut self) {
        self.ipv4_header().compute_checksum();
        let mut transport = self.transport_header.as_mut().expect("No known transport header");
        let transport_raw = &mut self.raw[self.ipv4_header_data.header_length() as usize..];
        transport.compute_checksum(transport_raw, &self.ipv4_header_data);
    }

    pub fn swap_source_and_destination(&mut self) {
        self.ipv4_header().swap_source_and_destination();
        if let Some(ref mut transport_header) = self.transport_header {
            let raw_payload = &mut self.raw[self.ipv4_header_data.header_length() as usize..];
            transport_header.swap_source_and_destination(raw_payload);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use byteorder::{BigEndian, ByteOrder, WriteBytesExt};
    use ::relay::ipv4_header::Protocol;

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
        let mut ipv4_packet = IPv4Packet::parse(raw);

        {
            let ipv4_header = ipv4_packet.ipv4_header();
            assert_eq!(20, ipv4_header.header_length());
            assert_eq!(32, ipv4_header.total_length());
            assert_eq!(Protocol::UDP, ipv4_header.protocol());
            assert_eq!(0x12345678, ipv4_header.source());
            assert_eq!(0x42424242, ipv4_header.destination());

            if let Some(TransportHeader::UDP(ref udp_header)) = *ipv4_packet.transport_header() {
                assert_eq!(1234, udp_header.source_port());
                assert_eq!(5678, udp_header.destination_port());
            } else {
                panic!("No UDP transport header");
            }
        }

        ipv4_packet.swap_source_and_destination();

        {
            let ipv4_header = ipv4_packet.ipv4_header();
            assert_eq!(0x42424242, ipv4_header.source());
            assert_eq!(0x12345678, ipv4_header.destination());

            if let Some(TransportHeader::UDP(ref udp_header)) = *ipv4_packet.transport_header() {
                assert_eq!(5678, udp_header.source_port());
                assert_eq!(1234, udp_header.destination_port());
            } else {
                panic!("No UDP transport header");
            }
        }

        {
            let raw = ipv4_packet.raw();
            // assert that the buffer has been modified
            let raw_source = BigEndian::read_u32(&raw[12..16]);
            let raw_destination = BigEndian::read_u32(&raw[16..20]);
            assert_eq!(0x42424242, raw_source);
            assert_eq!(0x12345678, raw_destination);

            if let Some(TransportHeader::UDP(ref udp_header)) = *ipv4_packet.transport_header() {
                assert_eq!(5678, udp_header.source_port());
                assert_eq!(1234, udp_header.destination_port());
            } else {
                panic!("No UDP transport header");
            }
        }
    }

    #[test]
    fn payload() {
        let raw = &mut create_packet()[..];
        let ipv4_packet = IPv4Packet::parse(raw);
        assert_eq!([0x11, 0x22, 0x33, 0x44], ipv4_packet.payload().unwrap());
    }
}
