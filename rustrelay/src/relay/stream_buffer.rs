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

    pub fn size(&self) -> usize {
        if self.head < self.tail {
            self.head + self.buf.len() - self.tail
        } else {
            self.head - self.tail
        }
    }

    pub fn remaining(&self) -> usize {
        self.buf.len() - self.size()
    }

    pub fn write_to<W: io::Write>(&mut self, destination: &mut W) -> io::Result<usize> {
        if self.head > self.tail {
            let source_slice = &self.buf[self.tail..self.head];
            let w = destination.write(source_slice)?;
            self.tail += w;
            Ok(w)
        } else if self.head < self.tail {
            let source_slice = &self.buf[self.tail..];
            let w = destination.write(source_slice)?;
            self.tail = (self.tail + w) % self.buf.len();
            Ok(w)
        } else {
            // else head == tail, which means empty buffer, nothing to do
            Ok(0)
        }
    }

    pub fn read_from(&mut self, source: &[u8]) {
        assert!(self.remaining() >= source.len(), "StreamBuffer must have enough space, check remaining() space before calling read_from()");
        let source_len = source.len();
        let buf_len = self.buf.len();
        if source_len <= buf_len - self.head {
            let target_slice = &mut self.buf[self.head..self.head + source_len];
            target_slice.copy_from_slice(source);
        } else {
            {
                // fill until the right-end of the buffer
                let target_slice = &mut self.buf[self.head..];
                let source_slice = &source[..source_len - self.head];
                target_slice.copy_from_slice(source_slice);
            }
            // fill the remaining from the left-end of the buffer
            let target_slice = &mut self.buf[..self.head + source_len - buf_len];
            let source_slice = &source[source_len - self.head..];
            target_slice.copy_from_slice(source_slice);
        }
        self.head = (self.head + source_len) % buf_len;
    }
}
