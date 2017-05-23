use super::tcp_header::TCPHeader;
use super::udp_header::UDPHeader;

pub enum TransportHeader {
    TCP(TCPHeader),
    UDP(UDPHeader),
}
