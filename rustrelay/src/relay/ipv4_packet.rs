use super::ipv4_header::{IPv4Header, Protocol};
use super::tcp_header::TCPHeader;
use super::transport_header::TransportHeader;
use super::udp_header::UDPHeader;

pub struct IPv4Packet<'a> {
    raw: &'a mut [u8],
    ipv4_header: IPv4Header,
    transport_header: Option<TransportHeader>,
}

impl<'a> IPv4Packet<'a> {
    fn new(raw: &'a mut [u8]) -> IPv4Packet<'a> {
        let ipv4_header = IPv4Header::parse(raw);
        let transport_header = {
            let payload = &raw[ipv4_header.total_length as usize..];
            match ipv4_header.protocol {
                Protocol::UDP => Some(TransportHeader::UDP(UDPHeader::parse(payload))),
                Protocol::TCP => Some(TransportHeader::TCP(TCPHeader::parse(payload))),
                _ => None
            }
        };
        IPv4Packet {
            raw: raw,
            ipv4_header: ipv4_header,
            transport_header: transport_header,
        }
    }
}

