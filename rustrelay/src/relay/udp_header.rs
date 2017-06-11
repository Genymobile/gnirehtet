use byteorder::{BigEndian, ByteOrder};
use std::mem;

pub const UDP_HEADER_LENGTH: u8 = 8;

#[derive(Copy, Clone)]
pub struct UDPHeader {
    pub source_port: u16,
    pub destination_port: u16,
}

impl UDPHeader {
    pub fn parse(raw: &[u8]) -> Self {
        Self {
            source_port: BigEndian::read_u16(&raw[0..2]),
            destination_port: BigEndian::read_u16(&raw[2..4]),
        }
    }

    pub fn source_port(&self) -> u16 {
        self.source_port
    }

    pub fn destination_port(&self) -> u16 {
        self.destination_port
    }

    pub fn set_source_port(&mut self, raw: &mut [u8], source_port: u16) {
        self.source_port = source_port;
        BigEndian::write_u16(&mut raw[0..2], source_port);
    }

    pub fn set_destination_port(&mut self, raw: &mut [u8], destination_port: u16) {
        self.destination_port = destination_port;
        BigEndian::write_u16(&mut raw[2..4], destination_port);
    }

    pub fn swap_source_and_destination(&mut self, raw: &mut [u8]) {
        mem::swap(&mut self.source_port, &mut self.destination_port);
        for i in 0..2 {
            raw.swap(i, i + 2);
        }
    }

    pub fn set_payload_length(&mut self, raw: &mut [u8], payload_length: u16) {
        let total_length = UDP_HEADER_LENGTH as u16 + payload_length;
        BigEndian::write_u16(&mut raw[4..6], total_length);
    }

    pub fn compute_checksum(&mut self, raw: &mut [u8]) {
        // disable checksum validation
        BigEndian::write_u16(&mut raw[6..8], 0);
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
        let data = UDPHeader::parse(raw);
        assert_eq!(1234, data.source_port);
        assert_eq!(5678, data.destination_port);
    }

    #[test]
    fn edit_header() {
        let raw = &mut create_header()[..];
        let mut header = UDPHeader::parse(raw);

        header.set_source_port(raw, 1111);
        header.set_destination_port(raw, 2222);
        header.set_payload_length(raw, 34);
        assert_eq!(1111, header.source_port);
        assert_eq!(2222, header.destination_port);

        // assert that the buffer has been modified
        let raw_source_port = BigEndian::read_u16(&raw[0..2]);
        let raw_destination_port = BigEndian::read_u16(&raw[2..4]);
        let raw_total_length = BigEndian::read_u16(&raw[4..6]);
        assert_eq!(1111, raw_source_port);
        assert_eq!(2222, raw_destination_port);
        assert_eq!(34 + 8, raw_total_length);

        header.swap_source_and_destination(raw);

        assert_eq!(2222, header.source_port);
        assert_eq!(1111, header.destination_port);

        let raw_source_port = BigEndian::read_u16(&raw[0..2]);
        let raw_destination_port = BigEndian::read_u16(&raw[2..4]);
        assert_eq!(2222, raw_source_port);
        assert_eq!(1111, raw_destination_port);
    }
}
