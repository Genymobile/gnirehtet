use byteorder::{BigEndian, ByteOrder, ReadBytesExt};
use std::io::Cursor;
use std::mem;
use super::ipv4_header::IPv4Header;

#[derive(Copy, Clone)]
pub struct TCPHeader {
    source_port: u16,
    destination_port: u16,
    sequence_number: u32,
    acknowledgement_number: u32,
    header_length: u8,
    flags: u16,
    window: u16,
}

pub const TCP_FLAG_FIN: u16 = 1 << 0;
pub const TCP_FLAG_SYN: u16 = 1 << 1;
pub const TCP_FLAG_RST: u16 = 1 << 2;
pub const TCP_FLAG_PSH: u16 = 1 << 3;
pub const TCP_FLAG_ACK: u16 = 1 << 4;
pub const TCP_FLAG_URG: u16 = 1 << 5;

impl TCPHeader {
    pub fn parse(raw: &[u8]) -> Self {
        let data_offset_and_flags = BigEndian::read_u16(&raw[12..14]);
        Self {
            source_port: BigEndian::read_u16(&raw[0..2]),
            destination_port: BigEndian::read_u16(&raw[2..4]),
            sequence_number: BigEndian::read_u32(&raw[4..8]),
            acknowledgement_number: BigEndian::read_u32(&raw[8..12]),
            header_length: ((data_offset_and_flags & 0xF000) >> 10) as u8,
            flags: data_offset_and_flags & 0x1FF,
            window: BigEndian::read_u16(&raw[14..16]),
        }
    }

    pub fn header_length(&self) -> u8 {
        self.header_length
    }

    pub fn source_port(&self) -> u16 {
        self.source_port
    }

    pub fn destination_port(&self) -> u16 {
        self.destination_port
    }

    pub fn sequence_number(&self) -> u32 {
        self.sequence_number
    }

    pub fn acknowledgement_number(&self) -> u32 {
        self.acknowledgement_number
    }

    pub fn flags(&self) -> u16 {
        self.flags
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

    pub fn set_sequence_number(&mut self, raw: &mut [u8], sequence_number: u32) {
        self.sequence_number = sequence_number;
        BigEndian::write_u32(&mut raw[4..8], sequence_number);
    }

    pub fn set_acknowledgement_number(&mut self, raw: &mut [u8], acknowledgement_number: u32) {
        self.acknowledgement_number = acknowledgement_number;
        BigEndian::write_u32(&mut raw[8..12], acknowledgement_number);
    }

    pub fn set_flags(&mut self, raw: &mut [u8], flags: u16) {
        self.flags = flags;
        let mut data_offset_and_flags = BigEndian::read_u16(&mut raw[12..14]);
        data_offset_and_flags = data_offset_and_flags & 0xFE00 | flags & 0x1FF;
        BigEndian::write_u16(&mut raw[12..14], data_offset_and_flags);
    }

    pub fn shrink_options(&mut self, raw: &mut [u8]) {
        self.set_data_offset(raw, 5);
    }

    fn set_data_offset(&mut self, raw: &mut [u8], data_offset: u8) {
        let mut data_offset_and_flags = BigEndian::read_u16(&mut raw[12..14]);
        data_offset_and_flags = data_offset_and_flags & 0x0FFF | ((data_offset as u16) << 12);
        BigEndian::write_u16(&mut raw[12..14], data_offset_and_flags);
        self.header_length = data_offset << 2;
    }

    pub fn compute_checksum(&mut self, transport_raw: &mut [u8], ipv4_header: &IPv4Header) {
        // pseudo-header checksum (cf rfc793 section 3.1)
        let source = ipv4_header.source();
        let destination = ipv4_header.destination();
        let length = ipv4_header.total_length() - ipv4_header.header_length() as u16;
        assert_eq!(transport_raw.len(), ipv4_header.total_length() as usize - ipv4_header.header_length() as usize);

        let mut sum = 6u32; // protocol TCP = 6
        sum += source >> 16;
        sum += source & 0xFFFF;
        sum += destination >> 16;
        sum += destination & 0xFFFF;
        sum += length as u32;

        // reset checksum field
        self.set_checksum(transport_raw, 0);

        {
            let mut cursor = Cursor::new(&transport_raw);
            while length - cursor.position() as u16 > 1 {
                sum += cursor.read_u16::<BigEndian>().unwrap() as u32;
            }
            // if payload length is odd, pad last short with 0
            if cursor.position() as u16 != length {
                sum += (cursor.read_u8().unwrap() as u32) << 8;
            }
        }

        while (sum & !0xFFFF) != 0 {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        sum = !sum;

        self.set_checksum(transport_raw, sum as u16);
    }

    fn checksum(&self, raw: &[u8]) -> u16 {
        BigEndian::read_u16(&raw[16..18])
    }

    pub fn set_checksum(&mut self, raw: &mut [u8], checksum: u16) {
        BigEndian::write_u16(&mut raw[16..18], checksum);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use byteorder::{BigEndian, WriteBytesExt};
    use relay::ipv4_packet::IPv4Packet;
    use relay::transport_header::TransportHeader;

    fn create_packet() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.reserve(44);

        raw.write_u8(4u8 << 4 | 5).unwrap(); // version_and_ihl
        raw.write_u8(0).unwrap(); //ToS
        raw.write_u16::<BigEndian>(44).unwrap(); // total length
        raw.write_u32::<BigEndian>(0).unwrap(); // id_flags_fragment_offset
        raw.write_u8(0).unwrap(); // TTL
        raw.write_u8(6).unwrap(); // protocol (TCP)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u32::<BigEndian>(0x12345678).unwrap(); // source address
        raw.write_u32::<BigEndian>(0xA2A24242).unwrap(); // destination address

        raw.write_u16::<BigEndian>(0x1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(0x5678).unwrap(); // destination port
        raw.write_u32::<BigEndian>(0x111).unwrap(); // sequence number
        raw.write_u32::<BigEndian>(0x222).unwrap(); // acknowledgement number
        raw.write_u16::<BigEndian>(5 << 12).unwrap(); // data offset + flags(0)
        raw.write_u16::<BigEndian>(0).unwrap(); // window (don't care for these tests)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u16::<BigEndian>(0).unwrap(); // urgent pointer

        raw.write_u32::<BigEndian>(0x11223344).unwrap(); // payload

        raw
    }

    fn create_odd_packet() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.reserve(45);

        raw.write_u8(4u8 << 4 | 5).unwrap(); // version_and_ihl
        raw.write_u8(0).unwrap(); //ToS
        raw.write_u16::<BigEndian>(45).unwrap(); // total length
        raw.write_u32::<BigEndian>(0).unwrap(); // id_flags_fragment_offset
        raw.write_u8(0).unwrap(); // TTL
        raw.write_u8(6).unwrap(); // protocol (TCP)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u32::<BigEndian>(0x12345678).unwrap(); // source address
        raw.write_u32::<BigEndian>(0xA2A24242).unwrap(); // destination address

        raw.write_u16::<BigEndian>(0x1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(0x5678).unwrap(); // destination port
        raw.write_u32::<BigEndian>(0x111).unwrap(); // sequence number
        raw.write_u32::<BigEndian>(0x222).unwrap(); // acknowledgement number
        raw.write_u16::<BigEndian>(5 << 12).unwrap(); // data offset + flags(0)
        raw.write_u16::<BigEndian>(0).unwrap(); // window (don't care for these tests)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u16::<BigEndian>(0).unwrap(); // urgent pointer

        // payload
        raw.write_u32::<BigEndian>(0x11223344).unwrap();
        raw.write_u8(0x55).unwrap();

        raw
    }

    fn create_tcp_header() -> Vec<u8> {
        let mut raw = Vec::new();
        raw.reserve(20);

        raw.write_u16::<BigEndian>(0x1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(0x5678).unwrap(); // destination port
        raw.write_u32::<BigEndian>(0x111).unwrap(); // sequence number
        raw.write_u32::<BigEndian>(0x222).unwrap(); // acknowledgement number
        raw.write_u16::<BigEndian>(5 << 12).unwrap(); // data offset + flags(0)
        raw.write_u16::<BigEndian>(0).unwrap(); // window (don't care for these tests)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u16::<BigEndian>(0).unwrap(); // urgent pointer

        raw
    }

    #[test]
    fn edit_header() {
        let raw = &mut create_tcp_header()[..];
        let mut header = TCPHeader::parse(raw);

        header.set_source_port(raw, 1111);
        header.set_destination_port(raw, 2222);
        header.set_sequence_number(raw, 300);
        header.set_acknowledgement_number(raw, 101);
        header.set_flags(raw, TCP_FLAG_FIN | TCP_FLAG_ACK);

        assert_eq!(1111, header.source_port());
        assert_eq!(2222, header.destination_port());
        assert_eq!(300, header.sequence_number());
        assert_eq!(101, header.acknowledgement_number());
        assert_eq!(TCP_FLAG_FIN | TCP_FLAG_ACK, header.flags());

        // assert that the buffer has been modified
        let raw_source_port = BigEndian::read_u16(&raw[0..2]);
        let raw_destination_port = BigEndian::read_u16(&raw[2..4]);
        let raw_sequence_number = BigEndian::read_u32(&raw[4..8]);
        let raw_acknowledgement_number = BigEndian::read_u32(&raw[8..12]);
        let raw_data_offset_and_flags = BigEndian::read_u16(&raw[12..14]);

        assert_eq!(1111, raw_source_port);
        assert_eq!(2222, raw_destination_port);
        assert_eq!(300, raw_sequence_number);
        assert_eq!(101, raw_acknowledgement_number);
        assert_eq!(0x5011, raw_data_offset_and_flags);
    }

    #[test]
    fn compute_checksum() {
        let raw = &mut create_packet()[..];
        let mut ipv4_packet = IPv4Packet::parse(raw);
        if let Some(TransportHeader::TCP(mut tcp_header)) = *ipv4_packet.transport_header() {
            // set a fake checksum value to assert that it is correctly computed
            BigEndian::write_u16(&mut ipv4_packet.raw_mut()[36..38], 0x79);

            {
                let transport_range = ipv4_packet.transport_range().expect("No transport");
                let (raw, ipv4_header, _) = ipv4_packet.destructure_mut();
                let transport_raw = &mut raw[transport_range];
                tcp_header.compute_checksum(transport_raw, ipv4_header);
            }

            let expected_checksum = {
                // pseudo-header
                let mut sum: u32 = 0x1234 + 0x5678 + 0xA2A2 + 0x4242 + 0x0006 + 0x0018;

                // header
                sum += 0x1234 + 0x5678 + 0x0000 + 0x0111 + 0x0000 +
                       0x0222 + 0x5000 + 0x0000 + 0x0000 + 0x0000;

                // payload
                sum += 0x1122 + 0x3344;

                while (sum & !0xFFFF) != 0 {
                    sum = (sum & 0xFFFF) + (sum >> 16);
                }
                !sum as u16
            };

            {
                let transport_header_range = ipv4_packet.transport_header_range().expect("No transport");
                let transport_header_raw = &ipv4_packet.raw()[transport_header_range];
                let actual_checksum = tcp_header.checksum(transport_header_raw);

                assert_eq!(expected_checksum, actual_checksum);
            }

        } else {
            panic!("Packet is not TCP");
        }
    }

    #[test]
    fn compute_checksum_odd() {
        let raw = &mut create_odd_packet()[..];
        let mut ipv4_packet = IPv4Packet::parse(raw);
        if let Some(TransportHeader::TCP(mut tcp_header)) = *ipv4_packet.transport_header() {
            // set a fake checksum value to assert that it is correctly computed
            BigEndian::write_u16(&mut ipv4_packet.raw_mut()[36..38], 0x79);

            {
                let transport_range = ipv4_packet.transport_range().expect("No transport");
                let (raw, ipv4_header, _) = ipv4_packet.destructure_mut();
                let transport_raw = &mut raw[transport_range];
                tcp_header.compute_checksum(transport_raw, ipv4_header);
            }

            let expected_checksum = {
                // pseudo-header
                let mut sum: u32 = 0x1234 + 0x5678 + 0xA2A2 + 0x4242 + 0x0006 + 0x0019;

                // header
                sum += 0x1234 + 0x5678 + 0x0000 + 0x0111 + 0x0000 +
                       0x0222 + 0x5000 + 0x0000 + 0x0000 + 0x0000;

                // payload
                sum += 0x1122 + 0x3344 + 0x5500;

                while (sum & !0xFFFF) != 0 {
                    sum = (sum & 0xFFFF) + (sum >> 16);
                }
                !sum as u16
            };

            {
                let transport_header_range = ipv4_packet.transport_header_range().expect("No transport");
                let transport_header_raw = &ipv4_packet.raw()[transport_header_range];
                let actual_checksum = tcp_header.checksum(transport_header_raw);

                assert_eq!(expected_checksum, actual_checksum);
            }

        } else {
            panic!("Packet is not TCP");
        }
    }
}
