use super::ipv4_header::Protocol;
use super::tcp_header::TCPHeader;
use super::udp_header::{UDPHeader, UDP_HEADER_LENGTH};

#[derive(Clone)]
pub enum TransportHeader {
    TCP(TCPHeader),
    UDP(UDPHeader),
}

impl TransportHeader {
    pub fn parse(protocol: Protocol, raw: &[u8]) -> Option<Self> {
        match protocol {
            Protocol::UDP => Some(UDPHeader::parse(raw).into()),
            Protocol::TCP => Some(TCPHeader::parse(raw).into()),
            _ => None
        }
    }

    pub fn source_port(&self) -> u16 {
        match *self {
            TransportHeader::TCP(ref tcp_header) => tcp_header.source_port(),
            TransportHeader::UDP(ref udp_header) => udp_header.source_port(),
        }
    }

    pub fn destination_port(&self) -> u16 {
        match *self {
            TransportHeader::TCP(ref tcp_header) => tcp_header.destination_port(),
            TransportHeader::UDP(ref udp_header) => udp_header.destination_port(),
        }
    }

    pub fn swap_source_and_destination(&mut self, raw: &mut [u8]) {
        match *self {
            TransportHeader::TCP(ref mut tcp_header) => tcp_header.swap_source_and_destination(raw),
            TransportHeader::UDP(ref mut udp_header) => udp_header.swap_source_and_destination(raw),
        }
    }

    pub fn set_payload_length(&mut self, raw: &mut [u8], payload_length: u16) {
        match *self {
            TransportHeader::UDP(ref mut udp_header) => udp_header.set_payload_length(raw, payload_length),
            _ => (), // TCP does not store its payload length
        }
    }

    pub fn header_length(&self) -> u8 {
        match *self {
            TransportHeader::TCP(ref tcp_header) => tcp_header.header_length(),
            TransportHeader::UDP(_) => UDP_HEADER_LENGTH,
        }
    }
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
