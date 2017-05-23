use byteorder::{BigEndian, ByteOrder};

pub struct UDPHeader {
    source_port: u16,
    destination_port: u16,
}

impl UDPHeader {
    pub fn parse(raw: &[u8]) -> UDPHeader {
        UDPHeader {
            source_port: BigEndian::read_u16(&raw[0..2]),
            destination_port: BigEndian::read_u16(&raw[2..4]),
        }
    }
}
