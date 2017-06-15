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
}
