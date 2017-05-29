use byteorder::{BigEndian, ByteOrder};
use std::io;
use std::ptr;
use super::ipv4_header::IPv4Header;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};

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

    fn read_from<R: io::Read>(&mut self, source: &mut R) -> io::Result<()> {
        let r = source.read(&mut self.buf[self.head..])?;
        self.head += r;
        Ok(())
    }

    fn get_available_packet_length(&self) -> Option<u16> {
        let length = IPv4Header::read_length(&self.buf);
        match length {
            // no packet
            None => None,
            // no full packet available
            Some(len) if len > self.head as u16 => None,
            // full packet available
            length => length
        }
    }

    pub fn as_ipv4_packet<'a>(&'a mut self) -> Option<IPv4Packet<'a>> {
        let length = self.get_available_packet_length();
        if let Some(len) = length {
            Some(IPv4Packet::new(&mut self.buf[..len as usize]))
        } else {
            None
        }
    }

    pub fn next(&mut self) {
        // remove the packet in front of the buffer
        let length = self.get_available_packet_length()
                .expect("next() called while there was no packet") as usize;
        self.head -= length;
        unsafe {
            let buf_ptr = self.buf.as_mut_ptr();
            // semantically equivalent to memmove()
            ptr::copy(buf_ptr.offset(length as isize), buf_ptr, length);
        }
    }
}
