use byteorder::{BigEndian, ByteOrder};
use std::io::Cursor;

pub struct IPv4Packet<'a> {
    raw: &'a mut [u8],
    header: IPv4Header,
}

struct IPv4Header {
    version: u8,
    header_length: u8,
    total_length: u16,
    protocol: Protocol,
    source: u32,
    destination: u32,
}

#[derive(Debug, PartialEq)]
enum Protocol {
    TCP,
    UDP,
    OTHER,
}

impl<'a> IPv4Packet<'a> {
    fn new(raw: &'a mut [u8]) -> IPv4Packet<'a> {
        let header = IPv4Header::parse(raw);
        IPv4Packet {
            raw: raw,
            header: header,
        }
    }
}

impl IPv4Header {
    fn parse(raw: &[u8]) -> IPv4Header {
        IPv4Header {
            version: raw[0] >> 4,
            header_length: (raw[0] & 0xf) << 2,
            total_length: BigEndian::read_u16(&raw[2..4]),
            protocol: match raw[9] {
                6 => Protocol::TCP,
                17 => Protocol::UDP,
                _ => Protocol::OTHER
            },
            source: BigEndian::read_u32(&raw[12..16]),
            destination: BigEndian::read_u32(&raw[16..20]),
        }
    }

    fn set_total_length(&mut self, raw: &mut [u8], total_length: u16) {
        self.total_length = total_length;
        BigEndian::write_u16(&mut raw[2..4], total_length);
    }

    fn set_source(&mut self, raw: &mut [u8], source: u32) {
        self.source = source;
        BigEndian::write_u32(&mut raw[12..16], source);
    }

    fn set_destination(&mut self, raw: &mut [u8], destination: u32) {
        self.destination = destination;
        BigEndian::write_u32(&mut raw[16..20], destination);
    }

    fn switch_source_and_destination(&mut self, raw: &mut [u8]) {
        let source = self.source;
        let destination = self.destination;
        self.set_source(raw, destination);
        self.set_destination(raw, source);
    }

    fn compute_checksum(&mut self, raw: &mut [u8]) {
        // reset checksum field
        self.set_checksum(raw, 0);

        let j = self.header_length as usize / 2;
        let mut sum = (0..j).map(|i| {
            let range = 2*i..2*(i+1);
            BigEndian::read_u16(&raw[range]) as u32
        }).sum::<u32>();
        while (sum & !0xffff) != 0 {
            sum = (sum & 0xffff) + (sum >> 16);
        }

        self.set_checksum(raw, !sum as u16);
    }

    fn get_checksum(&mut self, raw: &mut [u8]) -> u16 {
        BigEndian::read_u16(&raw[10..12])
    }

    fn set_checksum(&mut self, raw: &mut [u8], checksum: u16) {
        BigEndian::write_u16(&mut raw[10..12], checksum);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;
    use byteorder::{BigEndian, WriteBytesExt};

    fn create_header() -> Vec<u8> {
        let mut raw: Vec<u8> = vec![];
        raw.reserve(20);
        raw.write_u8(4u8 << 4 | 5).unwrap(); // version_and_ihl
        raw.write_u8(0).unwrap(); //ToS
        raw.write_u16::<BigEndian>(28).unwrap(); // total length
        raw.write_u32::<BigEndian>(0).unwrap(); // id_flags_fragment_offset
        raw.write_u8(0).unwrap(); // TTL
        raw.write_u8(17).unwrap(); // protocol (UDP)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u32::<BigEndian>(0x12345678).unwrap(); // source address
        raw.write_u32::<BigEndian>(0x42424242).unwrap(); // destination address
        raw
    }

    #[test]
    fn parse_packet_header() {
        let raw = create_header();
        let data = IPv4Header::parse(&raw[..]);
        assert_eq!(4, data.version);
        assert_eq!(20, data.header_length);
        assert_eq!(28, data.total_length);
        assert_eq!(Protocol::UDP, data.protocol);
        assert_eq!(0x12345678, data.source);
        assert_eq!(0x42424242, data.destination);
    }

    #[test]
    fn edit_packet_header() {
        let raw = &mut create_header()[..];
        let mut header = IPv4Header::parse(raw);

        header.set_source(raw, 0x87654321);
        header.set_destination(raw, 0x24242424);
        header.set_total_length(raw, 42);
        assert_eq!(0x87654321, header.source);
        assert_eq!(0x24242424, header.destination);
        assert_eq!(42, header.total_length);

        // assert that the buffer has been modified
        let raw_source = BigEndian::read_u32(&raw[12..16]);
        let raw_destination = BigEndian::read_u32(&raw[16..20]);
        let raw_total_length = BigEndian::read_u16(&raw[2..4]);
        assert_eq!(0x87654321, raw_source);
        assert_eq!(0x24242424, raw_destination);
        assert_eq!(42, raw_total_length);

        header.switch_source_and_destination(raw);

        assert_eq!(0x24242424, header.source);
        assert_eq!(0x87654321, header.destination);

        let raw_source = BigEndian::read_u32(&raw[12..16]);
        let raw_destination = BigEndian::read_u32(&raw[16..20]);
        assert_eq!(0x24242424, raw_source);
        assert_eq!(0x87654321, raw_destination);
    }

    #[test]
    fn compute_checksum() {
        let mut raw = &mut create_header()[..];
        let mut header = IPv4Header::parse(raw);

        // set a fake checksum value to assert that it is correctly computed
        header.set_checksum(raw, 0x79);

        header.compute_checksum(raw);

        let mut sum = 0x4500u32 + 0x001Cu32 + 0x0000u32 + 0x0000u32 + 0x0011u32
                    + 0x0000u32 + 0x1234u32 + 0x5678u32 + 0x4242u32 + 0x4242u32;
        while (sum & !0xffff) != 0 {
            sum = (sum & 0xffff) + (sum >> 16);
        }
        let sum = !sum as u16;
        assert_eq!(sum, header.get_checksum(raw));
    }
}
