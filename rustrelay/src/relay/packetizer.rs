use super::ipv4_header::IPv4Header;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::source_destination::SourceDestination;
use super::transport_header::TransportHeader;

/// Convert from level 5 to level 3 by appending correct IP and transport headers.
pub struct Packetizer {
    buffer: Box<[u8; MAX_PACKET_LENGTH]>,
    transport_index: usize,
    payload_index: usize,
    ipv4_header: IPv4Header,
    transport_header: TransportHeader,
}

impl Packetizer {
    pub fn new(raw: &mut [u8], mut ipv4_header: IPv4Header, mut transport_header: TransportHeader) -> Self {
        let mut buffer = Box::new([0; MAX_PACKET_LENGTH]);

        let mut ipv4_header = ipv4_header.clone();
        let mut transport_header = transport_header.clone();

        let transport_index = ipv4_header.header_length as usize;
        let payload_index = transport_index + transport_header.get_header_length() as usize;
        &mut buffer[..payload_index].copy_from_slice(&raw[..payload_index]);

        ipv4_header.switch_source_and_destination(&mut buffer[..]);
        transport_header.switch_source_and_destination(&mut buffer[transport_index..]);

        Self {
            buffer: buffer,
            transport_index: transport_index,
            payload_index: payload_index,
            ipv4_header: ipv4_header,
            transport_header: transport_header,
        }
    }

    pub fn packetize_empty_payload(&mut self) -> IPv4Packet {
        self.inflate(0)
    }

    fn inflate(&mut self, payload_length: u16) -> IPv4Packet {
        let total_length = self.payload_index as u16 + payload_length;

        self.ipv4_header.set_total_length(&mut self.buffer[..], total_length);
        self.transport_header.set_payload_length(&mut self.buffer[self.transport_index..], payload_length);

        let mut ipv4_packet = IPv4Packet::new(&mut self.buffer[..total_length as usize], self.ipv4_header, self.transport_header);
        ipv4_packet.compute_checksums();
        ipv4_packet
    }
}
