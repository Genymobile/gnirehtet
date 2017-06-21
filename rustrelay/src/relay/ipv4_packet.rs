use super::ipv4_header::IPv4Header;
use super::transport_header::TransportHeader;

pub const MAX_PACKET_LENGTH: usize = 1 << 16;

pub struct IPv4Packet<'a> {
    raw: &'a mut [u8],
    ipv4_header: IPv4Header,
    transport_header: Option<TransportHeader>,
}

impl<'a> IPv4Packet<'a> {
    pub fn parse(raw: &'a mut [u8]) -> Self {
        let ipv4_header = IPv4Header::parse(raw);
        let transport_header = {
            let payload = &raw[ipv4_header.header_length() as usize..];
            TransportHeader::parse(ipv4_header.protocol(), payload)
        };
        Self {
            raw: &mut raw[..ipv4_header.total_length() as usize],
            ipv4_header: ipv4_header,
            transport_header: transport_header,
        }
    }

    pub fn new(raw: &'a mut [u8], ipv4_header: IPv4Header, transport_header: TransportHeader) -> Self {
        Self {
            raw: raw,
            ipv4_header: ipv4_header,
            transport_header: Some(transport_header),
        }
    }

    pub fn raw(&self) -> &[u8] {
        self.raw
    }

    pub fn raw_mut(&mut self) -> &mut [u8] {
        self.raw
    }

    pub fn ipv4_header(&self) -> &IPv4Header {
        &self.ipv4_header
    }

    pub fn ipv4_header_mut(&mut self) -> &mut IPv4Header {
        &mut self.ipv4_header
    }

    pub fn transport_header(&self) -> &Option<TransportHeader> {
        &self.transport_header
    }

    pub fn transport_header_mut(&mut self) -> &mut Option<TransportHeader> {
        &mut self.transport_header
    }

    pub fn destructure(&self) -> (&[u8], &IPv4Header, &Option<TransportHeader>) {
        (self.raw, &self.ipv4_header, &self.transport_header)
    }

    pub fn destructure_mut(&mut self) -> (&mut [u8], &mut IPv4Header, &mut Option<TransportHeader>) {
        (self.raw, &mut self.ipv4_header, &mut self.transport_header)
    }

    pub fn is_valid(&self) -> bool {
        self.transport_header.is_some()
    }

    pub fn length(&self) -> u16 {
        self.ipv4_header.total_length()
    }

    pub fn payload_index(&self) -> Option<u16> {
        if let Some(ref transport_header) = self.transport_header {
            Some(self.ipv4_header.header_length() as u16 + transport_header.header_length() as u16)
        } else {
            None
        }
    }

    pub fn payload_length(&self) -> Option<u16> {
        if let Some(payload_index) = self.payload_index() {
            Some(self.length() - payload_index)
        } else {
            None
        }
    }

    pub fn payload(&self) -> &[u8] {
        &self.raw[self.payload_index().unwrap() as usize..]
    }

    pub fn compute_checksums(&mut self) {
        self.ipv4_header.compute_checksum(self.raw);
        let mut transport = self.transport_header.as_mut().expect("No known transport header");
        let transport_raw = &mut self.raw[self.ipv4_header.header_length() as usize..];
        transport.compute_checksum(transport_raw, &self.ipv4_header);
    }

    pub fn swap_source_and_destination(&mut self) {
        self.ipv4_header.swap_source_and_destination(&mut self.raw);
        if let Some(ref mut transport_header) = self.transport_header {
            let raw_payload = &mut self.raw[self.ipv4_header.header_length() as usize..];
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
        assert_eq!([0x11, 0x22, 0x33, 0x44], ipv4_packet.payload());
    }
}
