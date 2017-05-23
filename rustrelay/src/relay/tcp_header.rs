use byteorder::{BigEndian, ByteOrder};

pub struct TCPHeader {
    source_port: u16,
    destination_port: u16,
    sequence_number: u32,
    acknowledgment_number: u32,
    header_length: u8,
    flags: u16,
    window: u16,
}

impl TCPHeader {
    pub fn parse(raw: &[u8]) -> TCPHeader {
        let data_offset_and_flags = BigEndian::read_u16(&raw[12..14]);
        TCPHeader {
            source_port: BigEndian::read_u16(&raw[0..2]),
            destination_port: BigEndian::read_u16(&raw[2..4]),
            sequence_number: BigEndian::read_u32(&raw[4..8]),
            acknowledgment_number: BigEndian::read_u32(&raw[8..12]),
            header_length: (data_offset_and_flags & 0xF000 >> 10) as u8,
            flags: data_offset_and_flags & 0x1FF,
            window: BigEndian::read_u16(&raw[14..16]),
        }
    }
}
