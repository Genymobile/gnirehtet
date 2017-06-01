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

    pub fn write_to<W: io::Write>(&mut self, destination: &mut W) -> io::Result<()> {
        // TODO
        Ok(())
    }

    pub fn read_from(&mut self, source: &[u8]) -> io::Result<()> {
        // TODO
        Ok(())
    }
}
