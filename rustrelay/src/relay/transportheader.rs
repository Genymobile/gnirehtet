use super::tcpheader::TCPHeader;
use super::udpheader::UDPHeader;

pub enum TransportHeader {
    TCP(TCPHeader),
    UDP(UDPHeader),
}
