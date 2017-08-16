use std::io;
use std::ptr;
use super::ipv4_header;
use super::ipv4_packet::{Ipv4Packet, MAX_PACKET_LENGTH};

pub struct Ipv4PacketBuffer {
    buf: [u8; MAX_PACKET_LENGTH],
    head: usize,
}

impl Ipv4PacketBuffer {
    pub fn new() -> Self {
        Self {
            buf: [0; MAX_PACKET_LENGTH],
            head: 0,
        }
    }

    pub fn read_from<R: io::Read>(&mut self, source: &mut R) -> io::Result<(bool)> {
        let target_slice = &mut self.buf[self.head..];
        let r = source.read(target_slice)?;
        self.head += r;
        Ok(r > 0)
    }

    fn available_packet_length(&self) -> Option<u16> {
        if let Some((version, length)) = ipv4_header::peek_version_length(&self.buf) {
            assert!(version == 4, "Not an Ipv4 packet, version={}", version);
            if length <= self.head as u16 {
                // full packet available
                Some(length)
            } else {
                // no full packet available
                None
            }
        } else {
            // no packet
            None
        }
    }

    pub fn as_ipv4_packet<'a>(&'a mut self) -> Option<Ipv4Packet<'a>> {
        let length = self.available_packet_length();
        if let Some(len) = length {
            Some(Ipv4Packet::parse(&mut self.buf[..len as usize]))
        } else {
            None
        }
    }

    pub fn next(&mut self) {
        // remove the packet in front of the buffer
        let length = self.available_packet_length().expect(
            "next() called while there was no packet",
        ) as usize;
        assert!(self.head >= length);
        self.head -= length;
        if self.head > 0 {
            // some data remaining, move them to the front of the buffer
            unsafe {
                let buf_ptr = self.buf.as_mut_ptr();

                // Before:
                //
                //  consumed                  old_head
                // |        |....................|
                //  <------>
                //   length
                //
                // After:
                //
                //                  new_head (= old_head - length)
                // |....................|
                //                       <------>
                //                        length
                //
                // move from [length..old_head] to [0..new_head]
                //
                // semantically equivalent to memmove()
                ptr::copy(buf_ptr.offset(length as isize), buf_ptr, self.head);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io;
    use byteorder::{BigEndian, WriteBytesExt};
    use relay::ipv4_header::Protocol;
    use relay::transport_header::TransportHeaderData;

    fn create_packet() -> Vec<u8> {
        let mut raw = Vec::new();
        write_packet_to(&mut raw);
        raw
    }

    fn write_packet_to(raw: &mut Vec<u8>) {
        raw.write_u8(4u8 << 4 | 5).unwrap();
        raw.write_u8(0).unwrap(); // ToS
        raw.write_u16::<BigEndian>(32).unwrap(); // total length 20 + 8 + 4
        raw.write_u32::<BigEndian>(0).unwrap(); // id_flags_fragment_offset
        raw.write_u8(0).unwrap(); // TTL
        raw.write_u8(17).unwrap(); // protocol (UDP)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u32::<BigEndian>(0x12345678).unwrap(); // source address
        raw.write_u32::<BigEndian>(0x42424242).unwrap(); // destination address

        raw.write_u16::<BigEndian>(1234).unwrap(); // source port
        raw.write_u16::<BigEndian>(5678).unwrap(); // destination port
        raw.write_u16::<BigEndian>(12).unwrap(); // length
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum

        raw.write_u32::<BigEndian>(0x11223344).unwrap(); // payload
    }

    fn write_another_packet_to(raw: &mut Vec<u8>) {
        raw.write_u8(4u8 << 4 | 5).unwrap();
        raw.write_u8(0).unwrap(); // ToS
        raw.write_u16::<BigEndian>(29).unwrap(); // total length 20 + 8 + 1
        raw.write_u32::<BigEndian>(0).unwrap(); // id_flags_fragment_offset
        raw.write_u8(0).unwrap(); // TTL
        raw.write_u8(17).unwrap(); // protocol (UDP)
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum
        raw.write_u32::<BigEndian>(0x11111111).unwrap(); // source address
        raw.write_u32::<BigEndian>(0x22222222).unwrap(); // destination address

        raw.write_u16::<BigEndian>(1111).unwrap(); // source port
        raw.write_u16::<BigEndian>(2222).unwrap(); // destination port
        raw.write_u16::<BigEndian>(9).unwrap(); // length
        raw.write_u16::<BigEndian>(0).unwrap(); // checksum

        raw.write_u8(0x99).unwrap(); // payload
    }

    fn check_packet_headers(ipv4_packet: &Ipv4Packet) {
        let ipv4_header = ipv4_packet.ipv4_header();
        assert_eq!(20, ipv4_header.header_length());
        assert_eq!(32, ipv4_header.total_length());
        assert_eq!(Protocol::Udp, ipv4_header.protocol());
        assert_eq!(0x12345678, ipv4_header.source());
        assert_eq!(0x42424242, ipv4_header.destination());

        if let Some(TransportHeaderData::Udp(ref udp_header)) =
            *ipv4_packet.transport_header_data()
        {
            assert_eq!(1234, udp_header.source_port());
            assert_eq!(5678, udp_header.destination_port());
        } else {
            panic!("No UDP transport header");
        }
    }

    fn check_another_packet_headers(ipv4_packet: &Ipv4Packet) {
        let ipv4_header = ipv4_packet.ipv4_header();
        assert_eq!(20, ipv4_header.header_length());
        assert_eq!(29, ipv4_header.total_length());
        assert_eq!(Protocol::Udp, ipv4_header.protocol());
        assert_eq!(0x11111111, ipv4_header.source());
        assert_eq!(0x22222222, ipv4_header.destination());

        if let Some(TransportHeaderData::Udp(ref udp_header)) =
            *ipv4_packet.transport_header_data()
        {
            assert_eq!(1111, udp_header.source_port());
            assert_eq!(2222, udp_header.destination_port());
        } else {
            panic!("No UDP transport header");
        }
    }

    #[test]
    fn parse_ipv4_packet_buffer() {
        let raw = create_packet();
        let mut packet_buffer = Ipv4PacketBuffer::new();

        let mut cursor = io::Cursor::new(raw);
        packet_buffer.read_from(&mut cursor).unwrap();

        let packet = packet_buffer.as_ipv4_packet().unwrap();
        check_packet_headers(&packet);
    }

    #[test]
    fn parse_fragmented_ipv4_packet_buffer() {
        let raw = create_packet();
        let mut packet_buffer = Ipv4PacketBuffer::new();

        let mut cursor = io::Cursor::new(&raw[..14]);
        packet_buffer.read_from(&mut cursor).unwrap();

        assert!(packet_buffer.as_ipv4_packet().is_none());

        let mut cursor = io::Cursor::new(&raw[14..]);
        packet_buffer.read_from(&mut cursor).unwrap();

        let packet = packet_buffer.as_ipv4_packet().unwrap();
        check_packet_headers(&packet);
    }

    fn create_multi_packets() -> Vec<u8> {
        let mut raw = Vec::new();
        write_packet_to(&mut raw);
        write_another_packet_to(&mut raw);
        write_packet_to(&mut raw);
        raw
    }

    #[test]
    fn parse_multi_packets() {
        let raw = create_multi_packets();
        let mut packet_buffer = Ipv4PacketBuffer::new();

        let mut cursor = io::Cursor::new(raw);
        packet_buffer.read_from(&mut cursor).unwrap();

        check_packet_headers(&packet_buffer.as_ipv4_packet().unwrap());
        packet_buffer.next();
        check_another_packet_headers(&packet_buffer.as_ipv4_packet().unwrap());
        packet_buffer.next();
        check_packet_headers(&packet_buffer.as_ipv4_packet().unwrap());
        packet_buffer.next();

        assert!(packet_buffer.as_ipv4_packet().is_none());
    }
}
