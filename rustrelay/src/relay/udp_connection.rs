use super::datagram_buffer::DatagramBuffer;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::packetizer::Packetizer;

pub struct UDPConnection {
    client_to_network: DatagramBuffer,
    network_to_client: Packetizer,
}

impl UDPConnection {
    pub fn new(reference_packet: &IPv4Packet) -> Self {
        let raw: &[u8] = reference_packet.raw();
        let ipv4_header = reference_packet.ipv4_header().clone();
        let transport_header = reference_packet.transport_header().as_ref().unwrap().clone();
        Self {
            client_to_network: DatagramBuffer::new(4 * MAX_PACKET_LENGTH),
            network_to_client: Packetizer::new(raw, ipv4_header, transport_header),
        }
    }

    pub fn send_to_network(&mut self, ipv4_packet: &IPv4Packet) {
        // TODO
    }

    pub fn disconnect(&mut self) {
        // TODO
    }

    pub fn is_expired(&self) -> bool {
        // TODO
        false
    }
}
