use std::cmp;
use std::io;
use mio::net::UdpSocket;

pub const MAX_DATAGRAM_LENGTH: usize = 1 << 16;

pub trait DatagramSender {
    fn send(&mut self, buf: &[u8]) -> io::Result<usize>;
}

pub trait DatagramReceiver {
    fn recv(&mut self, buf: &mut [u8]) -> io::Result<usize>;
}

// Expose UdpSocket as DatagramSender
impl DatagramSender for UdpSocket {
    fn send(&mut self, buf: &[u8]) -> io::Result<usize> {
        // call the Self implementation
        (self as &Self).send(buf)
    }
}

// Expose UdpSocket as DatagramReceiver
impl DatagramReceiver for UdpSocket {
    fn recv(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        // call the Self implementation
        (self as &Self).recv(buf)
    }
}

// Convert a Read to a DatagramReceiver
pub struct ReadAdapter<'a, R> where R: io::Read + 'a {
    read: &'a mut R,
    max_chunk_size: Option<usize>,
}

impl<'a, R> ReadAdapter<'a, R> where R: io::Read + 'a {
    pub fn new(read: &'a mut R, max_chunk_size: Option<usize>) -> Self {
        Self {
            read: read,
            max_chunk_size: max_chunk_size,
        }
    }

    pub fn set_max_chunk_size(&mut self, max_chunk_size: Option<usize>) {
        self.max_chunk_size = max_chunk_size;
    }
}

impl<'a, R> DatagramReceiver for ReadAdapter<'a, R> where R: io::Read + 'a {
    fn recv(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let len = if let Some(max_chunk_size) = self.max_chunk_size {
            cmp::min(max_chunk_size, buf.len())
        } else {
            buf.len()
        };
        self.read.read(&mut buf[..len])
    }
}

#[cfg(test)]
pub mod tests {
    use super::*;

    // Mock datagram socket to be used in other tests
    pub struct MockDatagramSocket {
        buf: [u8; MAX_DATAGRAM_LENGTH],
        len: usize,
    }

    impl MockDatagramSocket {
        pub fn new() -> Self {
            Self {
                buf: [0; MAX_DATAGRAM_LENGTH],
                len: 0,
            }
        }

        pub fn from_data(data: &[u8]) -> Self {
            let mut mock = MockDatagramSocket::new();
            mock.send(data).unwrap();
            mock
        }

        pub fn data(&self) -> &[u8] {
            &self.buf[..self.len]
        }
    }

    impl DatagramSender for MockDatagramSocket {
        fn send(&mut self, buf: &[u8]) -> io::Result<usize> {
            let len = cmp::min(self.buf.len(), buf.len());
            &mut self.buf[..len].copy_from_slice(&buf[..len]);
            self.len = len;
            Ok(len)
        }
    }

    impl DatagramReceiver for MockDatagramSocket {
        fn recv(&mut self, buf: &mut [u8]) -> io::Result<usize> {
            let len = cmp::min(self.len, buf.len());
            &mut buf[..len].copy_from_slice(&self.buf[..len]);
            Ok(len)
        }
    }

    #[test]
    fn mock_send() {
        let mut mock = MockDatagramSocket::new();
        let data = [ 1, 2, 3, 4, 5 ];
        let sent = mock.send(&data).unwrap();
        assert_eq!(5, sent);
        assert_eq!([1, 2, 3, 4, 5], mock.data());
    }

    #[test]
    fn mock_recv() {
        let mut mock = MockDatagramSocket::from_data(&[1, 2, 3, 4, 5]);
        let mut buf = [0u8; 10];
        let recved = mock.recv(&mut buf).unwrap();
        assert_eq!(5, recved);
        assert_eq!([1, 2, 3, 4, 5], &buf[..5]);
    }

    #[test]
    fn read_adapter() {
        let mut cursor = io::Cursor::new([1, 2, 3, 4, 5]);
        let mut buf = [0u8; 10];
        let mut adapter = ReadAdapter::new(&mut cursor, None);
        let recved = adapter.recv(&mut buf).unwrap();
        assert_eq!(5, recved);
        assert_eq!([1, 2, 3, 4, 5], &buf[..5]);
    }

    #[test]
    fn read_adapter_chunks() {
        let mut cursor = io::Cursor::new([1, 2, 3, 4, 5]);
        let mut buf = [0u8; 10];
        let mut adapter = ReadAdapter::new(&mut cursor, Some(2));
        let recved = adapter.recv(&mut buf).unwrap();
        assert_eq!(2, recved);
        assert_eq!([1, 2], &buf[..2]);

        adapter.set_max_chunk_size(Some(1));
        let recved = adapter.recv(&mut buf).unwrap();
        assert_eq!(1, recved);
        assert_eq!([3], &buf[..1]);

        adapter.set_max_chunk_size(None);
        let recved = adapter.recv(&mut buf).unwrap();
        assert_eq!(2, recved);
        assert_eq!([4, 5], &buf[..2]);
    }
}
