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

    pub fn is_empty(&self) -> bool{
        self.head == self.tail
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
        if self.head == self.tail {
            // buffer is empty, nothing to do
            Ok(0)
        } else {
            let w;
            if self.head > self.tail {
                let source_slice = &self.buf[self.tail..self.head];
                w = destination.write(source_slice)?;
                self.tail += w;
            } else { // self.head < self.tail
                let source_slice = &self.buf[self.tail..];
                w = destination.write(source_slice)?;
                self.tail = (self.tail + w) % self.buf.len();
            }
            self.optimize();
            Ok(w)
        }
    }

    pub fn read_from(&mut self, source: &[u8]) -> io::Result<()> {
        if self.remaining() < source.len() {
            return Err(io::Error::new(io::ErrorKind::Other, "StreamBuffer is full"));
        }
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
        Ok(())
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
