use byteorder::{BigEndian, ByteOrder};

pub struct IPv4Header<'a> {
    raw: &'a mut [u8],
    data: Data,
}

struct Data {
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

impl<'a> IPv4Header<'a> {
    fn new(raw: &'a mut [u8]) -> IPv4Header<'a> {
        let data = IPv4Header::parse(raw);
        IPv4Header {
            raw: raw,
            data: data,
        }
    }

    fn parse(raw: &[u8]) -> Data {
        Data {
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

    fn set_total_length(&mut self, total_length: u16) {
        self.data.total_length = total_length;
        BigEndian::write_u16(&mut self.raw[2..4], total_length);
    }

    fn set_source(&mut self, source: u32) {
        self.data.source = source;
        BigEndian::write_u32(&mut self.raw[12..16], source);
    }

    fn set_destination(&mut self, destination: u32) {
        self.data.destination = destination;
        BigEndian::write_u32(&mut self.raw[16..20], destination);
    }

    fn switch_source_and_destination(&mut self) {
        let source = self.data.source;
        let destination = self.data.destination;
        self.set_source(destination);
        self.set_destination(source);
    }

    fn set_checksum(&mut self, checksum: u16) {
        BigEndian::write_u16(&mut self.raw[10..12], checksum);
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
        let mut raw = create_header();
        let mut header = IPv4Header::new(&mut raw[..]);

        header.set_source(0x87654321);
        header.set_destination(0x24242424);
        header.set_total_length(42);
        assert_eq!(0x87654321, header.data.source);
        assert_eq!(0x24242424, header.data.destination);
        assert_eq!(42, header.data.total_length);

        // assert that the buffer has been modified
        let raw_source = BigEndian::read_u32(&header.raw[12..16]);
        let raw_destination = BigEndian::read_u32(&header.raw[16..20]);
        let raw_total_length = BigEndian::read_u16(&header.raw[2..4]);
        assert_eq!(0x87654321, raw_source);
        assert_eq!(0x24242424, raw_destination);
        assert_eq!(42, raw_total_length);

        header.switch_source_and_destination();

        assert_eq!(0x24242424, header.data.source);
        assert_eq!(0x87654321, header.data.destination);

        let raw_source = BigEndian::read_u32(&header.raw[12..16]);
        let raw_destination = BigEndian::read_u32(&header.raw[16..20]);
        assert_eq!(0x24242424, raw_source);
        assert_eq!(0x87654321, raw_destination);
    }
}
