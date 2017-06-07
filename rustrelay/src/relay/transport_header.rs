use super::source_destination::SourceDestination;
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
