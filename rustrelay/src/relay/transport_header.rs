use super::ipv4_header::Protocol;
use super::source_destination::SourceDestination;
use super::tcp_header::TCPHeader;
use super::udp_header::{UDPHeader, UDP_HEADER_LENGTH};

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

    fn get_header_length(&self) -> u8 {
        match *self {
            TransportHeader::TCP(ref tcp_header) => tcp_header.get_header_length(),
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

impl SourceDestination<u16> for TransportHeader {
    fn get_source(&self, raw: &[u8]) -> u16 {
        match *self {
            TransportHeader::TCP(ref tcp_header) => tcp_header.get_source(raw),
            TransportHeader::UDP(ref udp_header) => udp_header.get_source(raw),
        }
    }

    fn get_destination(&self, raw: &[u8]) -> u16 {
        match *self {
            TransportHeader::TCP(ref tcp_header) => tcp_header.get_destination(raw),
            TransportHeader::UDP(ref udp_header) => udp_header.get_destination(raw),
        }
    }

    fn set_source(&mut self, raw: &mut [u8], source: u16) {
        match *self {
            TransportHeader::TCP(ref mut tcp_header) => tcp_header.set_source(raw, source),
            TransportHeader::UDP(ref mut udp_header) => udp_header.set_source(raw, source),
        }
    }

    fn set_destination(&mut self, raw: &mut [u8], source: u16) {
        match *self {
            TransportHeader::TCP(ref mut tcp_header) => tcp_header.set_destination(raw, source),
            TransportHeader::UDP(ref mut udp_header) => udp_header.set_destination(raw, source),
        }
    }
}
