/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use byteorder::{BigEndian, ByteOrder};
use log::*;
use std::io;

use super::datagram::{DatagramSender, MAX_DATAGRAM_LENGTH};

const HEADER_LENGTH: usize = 2;
const MAX_BLOCK_LENGTH: usize = HEADER_LENGTH + MAX_DATAGRAM_LENGTH;

const TAG: &str = "DatagramBuffer";

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
    pub fn new(capacity: usize) -> Self {
        Self {
            buf: vec![0; capacity + MAX_BLOCK_LENGTH].into_boxed_slice(),
            head: 0,
            tail: 0,
            circular_buffer_length: capacity + 1,
        }
    }

    pub fn is_empty(&self) -> bool {
        self.head == self.tail
    }

    pub fn has_enough_space_for(&self, datagram_length: usize) -> bool {
        if self.head >= self.tail {
            // there is at least the extra space for storing 1 packet
            return true;
        }
        let remaining = self.tail - self.head + 1;
        HEADER_LENGTH + datagram_length < remaining
    }

    pub fn write_to<S: DatagramSender>(&mut self, destination: &mut S) -> io::Result<()> {
        assert!(
            !self.is_empty(),
            "DatagramBuffer.write_to() called while empty"
        );
        let length = self.read_length() as usize;
        let source_slice = &self.buf[self.tail..self.tail + length];
        self.tail += length;
        if self.tail >= self.circular_buffer_length {
            self.tail = 0;
        }
        let w = destination.send(source_slice)?;
        if w != length {
            error!(
                target: TAG,
                "Cannot write the whole datagram to the buffer (only {}/{})", w, length
            );
            return Err(io::Error::new(
                io::ErrorKind::Other,
                "Cannot write the whole datagram",
            ));
        }
        Ok(())
    }

    pub fn read_from(&mut self, source: &[u8]) -> io::Result<()> {
        let length = source.len();
        assert!(
            length <= MAX_DATAGRAM_LENGTH,
            "Datagram length may not be greater than {} bytes",
            MAX_DATAGRAM_LENGTH
        );
        if !self.has_enough_space_for(length) {
            return Err(io::Error::new(
                io::ErrorKind::Other,
                "Datagram buffer is full",
            ));
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::relay::datagram::tests::MockDatagramSocket;

    fn create_datagram(length: u8) -> Vec<u8> {
        (0..length).collect()
    }

    #[test]
    fn bufferize_datagram() {
        let datagram = create_datagram(5);
        let mut datagram_buffer = DatagramBuffer::new(9);

        datagram_buffer.read_from(&datagram).unwrap();
        assert_eq!(read_datagram(&mut datagram_buffer), datagram);
    }

    #[test]
    fn split_datagrams_at_boundaries() {
        let mut datagram_buffer = DatagramBuffer::new(32);

        let datagram5 = create_datagram(5);
        let datagram0 = create_datagram(0);
        let datagram3 = create_datagram(3);
        let datagram4 = create_datagram(4);

        datagram_buffer.read_from(&datagram5).unwrap();
        datagram_buffer.read_from(&datagram0).unwrap();
        datagram_buffer.read_from(&datagram3).unwrap();
        datagram_buffer.read_from(&datagram4).unwrap();

        assert_eq!(read_datagram(&mut datagram_buffer), datagram5);
        assert_eq!(read_datagram(&mut datagram_buffer), datagram0);
        assert_eq!(read_datagram(&mut datagram_buffer), datagram3);
        assert_eq!(read_datagram(&mut datagram_buffer), datagram4);
    }

    #[test]
    fn circular() {
        let datagram5 = create_datagram(5);
        let datagram3 = create_datagram(3);

        let mut datagram_buffer = DatagramBuffer::new(14);

        // write and consume 10 bytes
        datagram_buffer.read_from(&create_datagram(10)).unwrap();
        {
            // write and forget
            let mut mock = MockDatagramSocket::new();
            datagram_buffer.write_to(&mut mock).unwrap();
        }

        // DatagramBuffer is expected to store the whole datagram, even if it exceeds its "capacity"
        datagram_buffer.read_from(&datagram5).unwrap();
        datagram_buffer.read_from(&datagram3).unwrap();

        assert_eq!(read_datagram(&mut datagram_buffer), datagram5);
        assert_eq!(read_datagram(&mut datagram_buffer), datagram3);
    }

    fn read_datagram(datagram_buffer: &mut DatagramBuffer) -> Vec<u8> {
        let mut mock = MockDatagramSocket::new();
        datagram_buffer.write_to(&mut mock).unwrap();
        mock.data().to_vec()
    }
}
