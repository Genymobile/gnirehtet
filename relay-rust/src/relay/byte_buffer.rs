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

use std::io;
use std::ptr;

pub struct ByteBuffer {
    buf: Box<[u8]>,
    head: usize,
}

impl ByteBuffer {
    pub fn new(length: usize) -> Self {
        Self {
            buf: vec![0; length].into_boxed_slice(),
            head: 0,
        }
    }

    pub fn read_from<R: io::Read>(&mut self, source: &mut R) -> io::Result<(bool)> {
        let target_slice = &mut self.buf[self.head..];
        let r = source.read(target_slice)?;
        self.head += r;
        Ok(r > 0)
    }

    pub fn peek(&self) -> &[u8] {
        &self.buf[..self.head]
    }

    pub fn peek_mut(&mut self) -> &mut [u8] {
        &mut self.buf[..self.head]
    }

    pub fn consume(&mut self, length: usize) {
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
                ptr::copy(buf_ptr.add(length), buf_ptr, self.head);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io;

    #[test]
    fn produce_consume_byte_buffer() {
        let raw = "hello, world!".as_bytes();
        let mut byte_buffer = ByteBuffer::new(64);

        let mut cursor = io::Cursor::new(raw);
        byte_buffer.read_from(&mut cursor).unwrap();
        assert_eq!("hello, world!".as_bytes(), byte_buffer.peek());

        byte_buffer.consume(7);
        assert_eq!("world!".as_bytes(), byte_buffer.peek());

        let mut cursor = io::Cursor::new(&raw[..5]);
        byte_buffer.read_from(&mut cursor).unwrap();
        assert_eq!("world!hello".as_bytes(), byte_buffer.peek());
    }
}
