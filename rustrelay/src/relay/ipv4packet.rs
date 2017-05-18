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
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;
    use byteorder::{BigEndian, WriteBytesExt};

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
}
