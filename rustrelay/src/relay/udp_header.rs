use byteorder::{BigEndian, ByteOrder};

const UDP_HEADER_LENGTH: u16 = 8;

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

    pub fn set_source_port(&mut self, raw: &mut [u8], source_port: u16) {
        self.source_port = source_port;
        BigEndian::write_u16(&mut raw[0..2], source_port);
    }

    pub fn set_destination_port(&mut self, raw: &mut [u8], destination_port: u16) {
        self.destination_port = destination_port;
        BigEndian::write_u16(&mut raw[2..4], destination_port);
    }

    pub fn set_payload_length(&mut self, raw: &mut [u8], payload_length: u16) {
        let total_length = UDP_HEADER_LENGTH + payload_length;
        BigEndian::write_u16(&mut raw[4..6], total_length);
    }

    pub fn compute_checksum(&mut self, raw: &mut [u8]) {
        // disable checksum validation
        BigEndian::write_u16(&mut raw[6..8], 0);
    }
}
