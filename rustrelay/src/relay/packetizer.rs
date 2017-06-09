use super::ipv4_header::IPv4Header;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::source_destination::SourceDestination;
use super::transport_header::TransportHeader;

/// Convert from level 5 to level 3 by appending correct IP and transport headers.
pub struct Packetizer {
    buffer: Box<[u8; MAX_PACKET_LENGTH]>,
    payload_index: usize,
    ipv4_header: IPv4Header,
    transport_header: TransportHeader,
}

impl Packetizer {
    pub fn new(raw: &mut [u8], mut ipv4_header: IPv4Header, mut transport_header: TransportHeader) -> Self {
        let mut buffer = Box::new([0; MAX_PACKET_LENGTH]);

        let mut ipv4_header = ipv4_header.clone();
        let mut transport_header = transport_header.clone();

        let headers_length = ipv4_header.header_length as usize +
                             transport_header.get_header_length() as usize;
        &mut buffer[..headers_length].copy_from_slice(&raw[..headers_length]);

        ipv4_header.switch_source_and_destination(&mut buffer[..]);
        {
            let transport_raw = &mut buffer[ipv4_header.header_length as usize..];
            transport_header.switch_source_and_destination(transport_raw);
        }

        Self {
            buffer: buffer,
            payload_index: headers_length,
            ipv4_header: ipv4_header,
            transport_header: transport_header,
        }
    }

    pub fn packetize_empty_payload(&mut self) -> IPv4Packet {
        self.inflate(0)
    }

    fn inflate(&mut self, payload_length: u16) -> IPv4Packet {
        let ipv4_header_length = self.ipv4_header.header_length as u16;
        let transport_header_length = self.transport_header.get_header_length() as u16;
        let total_length = ipv4_header_length + transport_header_length + payload_length;

        self.ipv4_header.set_total_length(&mut self.buffer[..], total_length);

        {
            let transport_raw = &mut self.buffer[self.ipv4_header.header_length as usize..];
            self.transport_header.set_payload_length(transport_raw, payload_length);
        }

        let mut ipv4_packet = IPv4Packet::new(&mut self.buffer[..total_length as usize], self.ipv4_header, self.transport_header);
        ipv4_packet.compute_checksums();
        ipv4_packet
    }
}
