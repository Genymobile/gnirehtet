use super::ipv4_header::{IPv4Header, Protocol};
use super::tcp_header::TCPHeader;
use super::transport_header::TransportHeader;
use super::udp_header::UDPHeader;

pub const MAX_PACKET_LENGTH: usize = 1 << 16;

pub struct IPv4Packet<'a> {
    pub raw: &'a mut [u8],
    pub ipv4_header: IPv4Header,
    pub transport_header: Option<TransportHeader>,
}

impl<'a> IPv4Packet<'a> {
    pub fn new(raw: &'a mut [u8]) -> Self {
        let ipv4_header = IPv4Header::parse(raw);
        let transport_header = {
            let payload = &raw[ipv4_header.header_length as usize..];
            match ipv4_header.protocol {
                Protocol::UDP => Some(UDPHeader::parse(payload).into()),
                Protocol::TCP => Some(TCPHeader::parse(payload).into()),
                _ => None
            }
        };
        Self {
            raw: raw,
            ipv4_header: ipv4_header,
            transport_header: transport_header,
        }
    }

    fn compute_checksum(&mut self) {
        if let Some(TransportHeader::TCP(ref tcp_header)) = self.transport_header {
            tcp_header.compute_checksum(self.raw, &self.ipv4_header);
        }
    }
}

