use std::io;

/// Circular buffer to store a stream. Read/write boundaries are not preserved.
pub struct StreamBuffer {
    buf: Box<[u8]>,
    head: usize,
    tail: usize,
}

impl StreamBuffer {
    pub fn new(capacity: usize) -> Self {
        Self {
            buf: vec![0; capacity + 1].into_boxed_slice(),
            head: 0,
            tail: 0,
        }
    }

    pub fn is_empty(&self) -> bool {
        self.head == self.tail
    }

    pub fn size(&self) -> usize {
        if self.head < self.tail {
            self.head + self.buf.len() - self.tail
        } else {
            self.head - self.tail
        }
    }

    pub fn capacity(&self) -> usize {
        self.buf.len() - 1
    }

    pub fn remaining(&self) -> usize {
        self.capacity() - self.size()
    }

    pub fn write_to<W: io::Write>(&mut self, destination: &mut W) -> io::Result<usize> {
        if self.head == self.tail {
            // buffer is empty, nothing to do
            Ok(0)
        } else {
            let w;
            if self.head > self.tail {
                let source_slice = &self.buf[self.tail..self.head];
                w = destination.write(source_slice)?;
                self.tail += w;
            } else {
                // self.head < self.tail
                let source_slice = &self.buf[self.tail..];
                w = destination.write(source_slice)?;
                self.tail = (self.tail + w) % self.buf.len();
            }
            self.optimize();
            Ok(w)
        }
    }

    pub fn read_from(&mut self, source: &[u8]) {
        assert!(
            source.len() <= self.remaining(),
            "StreamBuffer is full, check remaining() before calling read_from()"
        );
        let source_len = source.len();
        let buf_len = self.buf.len();
        if source_len <= buf_len - self.head {
            let target_slice = &mut self.buf[self.head..self.head + source_len];
            target_slice.copy_from_slice(source);
        } else {
            {
                // fill until the right-end of the buffer
                let target_slice = &mut self.buf[self.head..];
                let source_slice = &source[..buf_len - self.head];
                target_slice.copy_from_slice(source_slice);
            }
            // fill the remaining from the left-end of the buffer
            let target_slice = &mut self.buf[..self.head + source_len - buf_len];
            let source_slice = &source[buf_len - self.head..];
            target_slice.copy_from_slice(source_slice);
        }
        self.head = (self.head + source_len) % buf_len;
    }

    /// To avoid unnecessary copies, StreamBuffer writes at most until the "end" of the circular
    /// buffer, which is suboptimal (it could have written more data if they have been contiguous).
    ///
    /// In order to minimize the occurrence of this event, reset the head and tail to 0 when the
    /// buffer is empty (no copy is involved).
    ///
    /// This is especially useful when the StreamBuffer is used to read/write one packet at a time,
    /// so the "end" of the buffer is guaranteed to never be reached.
    fn optimize(&mut self) {
        if self.is_empty() {
            self.head = 0;
            self.tail = 0;
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn create_data() -> Vec<u8> {
        (0..6).collect()
    }

    #[test]
    fn bufferize_data() {
        let data = create_data();
        let mut stream_buffer = StreamBuffer::new(9);

        let mut cursor = io::Cursor::new(Vec::new());
        stream_buffer.read_from(&data);
        stream_buffer.write_to(&mut cursor).unwrap();

        assert_eq!(cursor.get_ref(), &data);
    }

    #[test]
    fn circular() {
        let data = create_data();
        let mut stream_buffer = StreamBuffer::new(9);

        // put 6 bytes
        stream_buffer.read_from(&data);
        // consume 3 bytes
        read_some(&mut stream_buffer, 3);

        // put test data
        stream_buffer.read_from(&data);
        // consume 3 bytes (so that the first 6 bytes are consumed, and the "tail" position is 6)
        read_some(&mut stream_buffer, 3);

        // consume test data
        let result = read(&mut stream_buffer);

        // StreamBuffer is expected to break writes at circular buffer boundaries (capacity + 1)
        // This is not a requirement, but this verifies that the implementation works as expected
        assert_eq!([0u8, 1, 2, 3], &result[..]);

        // consume the remaining
        let result = read(&mut stream_buffer);
        assert_eq!([4u8, 5], &result[..]);
    }

    #[test]
    fn just_enough_space() {
        let data = create_data();
        let mut stream_buffer = StreamBuffer::new(9);

        // fill the buffer twice
        stream_buffer.read_from(&data);
        assert_eq!(3, stream_buffer.remaining());
        stream_buffer.read_from(&[0, 1, 2]);

        let result = read(&mut stream_buffer);
        assert_eq!([0, 1, 2, 3, 4, 5, 0, 1, 2], &result[..]);
    }

    fn read_some(stream_buffer: &mut StreamBuffer, bytes: usize) -> Vec<u8> {
        let mut vec = vec![0u8; bytes];
        {
            let mut cursor = io::Cursor::new(&mut vec[..bytes]);
            stream_buffer.write_to(&mut cursor).unwrap();
        }
        vec
    }

    fn read(stream_buffer: &mut StreamBuffer) -> Vec<u8> {
        let mut cursor = io::Cursor::new(Vec::new());
        stream_buffer.write_to(&mut cursor).unwrap();
        cursor.into_inner()
    }
}
