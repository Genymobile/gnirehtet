use byteorder::{BigEndian, ByteOrder};
use std::io;
use std::io::Read;
use super::ipv4_header::IPv4Header;
use super::ipv4_packet::MAX_PACKET_LENGTH;

struct IPv4PacketBuffer {
    buf: [u8; MAX_PACKET_LENGTH],
    head: usize,
}

impl IPv4PacketBuffer {
    fn new() -> IPv4PacketBuffer {
        IPv4PacketBuffer {
            buf: [0; MAX_PACKET_LENGTH],
            head: 0,
        }
    }
    fn read_from<R: Read>(&mut self, source: &mut R) -> io::Result<()> {
        source.read(&mut self.buf[self.head..]).map(|_| ())
    }
}
