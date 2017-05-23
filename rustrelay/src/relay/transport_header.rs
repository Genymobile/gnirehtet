use super::tcp_header::TCPHeader;
use super::udp_header::UDPHeader;

pub enum TransportHeader {
    TCP(TCPHeader),
    UDP(UDPHeader),
}

impl From<TCPHeader> for TransportHeader {
    fn from(tcp_header: TCPHeader) -> TransportHeader {
        TransportHeader::TCP(tcp_header)
    }
}

impl From<UDPHeader> for TransportHeader {
    fn from(udp_header: UDPHeader) -> TransportHeader {
        TransportHeader::UDP(udp_header)
    }
}
