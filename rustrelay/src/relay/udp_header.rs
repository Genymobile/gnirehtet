use std::mem;
use byteorder::{BigEndian, ByteOrder};
use super::ipv4_header::IPv4HeaderData;

pub const UDP_HEADER_LENGTH: u8 = 8;

pub struct UDPHeader<'a> {
    raw: &'a [u8],
    data: &'a UDPHeaderData,
}

pub struct UDPHeaderMut<'a> {
    raw: &'a mut [u8],
    data: &'a mut UDPHeaderData,
}

#[derive(Clone)]
pub struct UDPHeaderData {
    source_port: u16,
    destination_port: u16,
}

impl UDPHeaderData {
    pub fn parse(raw: &[u8]) -> Self {
        Self {
            source_port: BigEndian::read_u16(&raw[0..2]),
            destination_port: BigEndian::read_u16(&raw[2..4]),
        }
    }

    pub fn bind<'c, 'a: 'c, 'b: 'c>(&'a self, raw: &'b [u8]) -> UDPHeader<'c> {
        UDPHeader::new(raw, self)
    }

    pub fn bind_mut<'c, 'a: 'c, 'b: 'c>(&'a mut self, raw: &'b mut [u8]) -> UDPHeaderMut<'c> {
        UDPHeaderMut::new(raw, self)
    }

    pub fn source_port(&self) -> u16 {
        self.source_port
    }

    pub fn destination_port(&self) -> u16 {
        self.destination_port
    }
}

// shared definition for UDPHeader and UDPHeaderMut
macro_rules! udp_header_common {
    ($name:ident, $raw_type:ty, $data_type:ty) => {
        // for readability, declare structs manually outside the macro
        impl<'a> $name<'a> {
            pub fn new(raw: $raw_type, data: $data_type) -> Self {
                Self {
                    raw: raw,
                    data: data,
                }
            }

            pub fn raw(&self) -> &[u8] {
                self.raw
            }

            pub fn data(&self) -> &UDPHeaderData {
                self.data
            }

            pub fn source_port(&self) -> u16 {
                self.data.source_port
            }

            pub fn destination_port(&self) -> u16 {
                self.data.destination_port
            }
        }
    }
}

udp_header_common!(UDPHeader, &'a [u8], &'a UDPHeaderData);
udp_header_common!(UDPHeaderMut, &'a mut [u8], &'a mut UDPHeaderData);

// additional methods for the mutable version
impl<'a> UDPHeaderMut<'a> {
    pub fn raw_mut(&mut self) -> &mut [u8] {
        self.raw
    }

    pub fn data_mut(&mut self) -> &mut UDPHeaderData {
        self.data
    }

    pub fn set_source_port(&mut self, source_port: u16) {
        self.data.source_port = source_port;
        BigEndian::write_u16(&mut self.raw[0..2], source_port);
    }

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

    pub fn set_payload_length(&mut self, payload_length: u16) {
        let total_length = UDP_HEADER_LENGTH as u16 + payload_length;
        BigEndian::write_u16(&mut self.raw[4..6], total_length);
    }

    pub fn compute_checksum(&mut self, _ipv4_header_data: &IPv4HeaderData, _payload: &mut [u8]) {
        // disable checksum validation
        BigEndian::write_u16(&mut self.raw[6..8], 0);
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
        let data = UDPHeaderData::parse(raw);
        assert_eq!(1234, data.source_port());
        assert_eq!(5678, data.destination_port());
    }

    #[test]
    fn edit_header() {
        let raw = &mut create_header()[..];
        let mut header = UDPHeaderData::parse(raw).bind(raw);

        header.set_source_port(1111);
        header.set_destination_port(2222);
        header.set_payload_length(34);
        assert_eq!(1111, header.source_port());
        assert_eq!(2222, header.destination_port());

        // assert that the buffer has been modified
        let raw_source_port = BigEndian::read_u16(&raw[0..2]);
        let raw_destination_port = BigEndian::read_u16(&raw[2..4]);
        let raw_total_length = BigEndian::read_u16(&raw[4..6]);
        assert_eq!(1111, raw_source_port);
        assert_eq!(2222, raw_destination_port);
        assert_eq!(34 + 8, raw_total_length);

        header.swap_source_and_destination();

        assert_eq!(2222, header.source_port());
        assert_eq!(1111, header.destination_port());

        let raw_source_port = BigEndian::read_u16(&raw[0..2]);
        let raw_destination_port = BigEndian::read_u16(&raw[2..4]);
        assert_eq!(2222, raw_source_port);
        assert_eq!(1111, raw_destination_port);
    }
}
