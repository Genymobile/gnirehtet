use super::ipv4_header::{IPv4Header, Protocol};
use super::source_destination::SourceDestination;
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
    pub fn parse(raw: &'a mut [u8]) -> Self {
        let ipv4_header = IPv4Header::parse(raw);
        let transport_header = {
            let payload = &raw[ipv4_header.header_length as usize..];
            TransportHeader::parse(ipv4_header.protocol, payload)
        };
        Self {
            raw: raw,
            ipv4_header: ipv4_header,
            transport_header: transport_header,
        }
    }

    pub fn new(raw: &'a mut [u8], ipv4_header: IPv4Header, transport_header: TransportHeader) -> Self {
        Self {
            raw: raw,
            ipv4_header: ipv4_header,
            transport_header: Some(transport_header),
        }
    }

    pub fn is_valid(&self) -> bool {
        self.transport_header.is_some()
    }

    pub fn get_packet_length(&self) -> u16 {
        self.ipv4_header.total_length
    }

    pub fn compute_checksums(&mut self) {
        self.ipv4_header.compute_checksum(self.raw);
        if let Some(TransportHeader::TCP(ref tcp_header)) = self.transport_header {
            tcp_header.compute_checksum(self.raw, &self.ipv4_header);
        }
    }

    pub fn switch_source_and_destination(&mut self) {
        self.ipv4_header.switch_source_and_destination(&mut self.raw);
        if let Some(ref mut transport_header) = self.transport_header {
            let raw_payload = &mut self.raw[self.ipv4_header.header_length as usize..];
            transport_header.switch_source_and_destination(raw_payload);
        }
    }
}

