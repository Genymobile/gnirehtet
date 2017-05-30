use byteorder::{BigEndian, ByteOrder};
use std::io;

const HEADER_LENGTH: usize = 2;
const MAX_DATAGRAM_LENGTH: usize = 1 << 16;
const MAX_BLOCK_LENGTH: usize = HEADER_LENGTH + MAX_DATAGRAM_LENGTH;

const TAG: &'static str = "DatagramBuffer";

/// Circular buffer to store datagrams (preserving their boundaries).
///
/// ```text
///     circularBufferLength
/// |<------------------------->| extra space for storing the last datagram in one block
/// +---------------------------+------+
/// |                           |      |
/// |[D4]     [  D1  ][ D2 ][  D3  ]   |
/// +---------------------------+------+
///     ^     ^
///  head     tail
/// ```
pub struct DatagramBuffer {
    buf: Box<[u8]>,
    head: usize,
    tail: usize,
    circular_buffer_length: usize,
}

impl DatagramBuffer {
    pub fn new(capacity: usize) -> DatagramBuffer {
        DatagramBuffer {
            buf: Vec::with_capacity(capacity + MAX_BLOCK_LENGTH).into_boxed_slice(),
            head: 0,
            tail: 0,
            circular_buffer_length: capacity + 1,
        }
    }

    pub fn is_empty(&self) -> bool {
        self.head == self.tail
    }

    pub fn has_enough_space_for(&self, datagram_length: usize) -> bool {
        if self.head >= self. tail {
            // there is at least the extra space for storing 1 packet
            return true;
        }
        let remaining = self.tail - self.head + 1;
        HEADER_LENGTH + datagram_length < remaining
    }

    pub fn write_to<W: io::Write>(&mut self, destination: &mut W) -> io::Result<()> {
        let length = self.read_length() as usize;
        self.tail += length;
        if self.tail >= self.circular_buffer_length {
            self.tail = 0;
        }
        let source_slice = &self.buf[self.tail..self.tail + length];
        let w = destination.write(source_slice)?;
        if w != length {
            error!(target: TAG, "Cannot write the whole datagram to the buffer (only {}/{})", w, length);
            return Err(io::Error::new(io::ErrorKind::Other, "Cannot write the whole datagram"))
        }
        Ok(())
    }

    pub fn read_from(&mut self, source: &mut [u8]) -> io::Result<()> {
        let length = source.len();
        assert!(length <= MAX_DATAGRAM_LENGTH, "Datagram length may not be greater than {} bytes", MAX_DATAGRAM_LENGTH);
        if !self.has_enough_space_for(length) {
            return Err(io::Error::new(io::ErrorKind::Other, "Datagram buffer is full"));
        }
        self.write_length(length as u16);
        let target_slice = &mut self.buf[self.head..self.head + length];
        target_slice.copy_from_slice(source);
        self.head += length;
        if self.head >= self.circular_buffer_length {
            self.head = 0;
        }
        Ok(())
    }

    fn read_length(&mut self) -> u16 {
        self.tail += 2;
        BigEndian::read_u16(&self.buf[self.tail - 2..self.tail])
    }

    fn write_length(&mut self, length: u16) {
        BigEndian::write_u16(&mut self.buf[self.head..self.head + 2], length);
        self.head += 2;
    }
}
