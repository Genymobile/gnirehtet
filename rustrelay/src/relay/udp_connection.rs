use super::datagram_buffer::DatagramBuffer;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};

pub struct UDPConnection {
    client_to_network: DatagramBuffer,
}

impl UDPConnection {
    pub fn new() -> Self {
        // TODO
        Self {
            client_to_network: DatagramBuffer::new(4 * MAX_PACKET_LENGTH),
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
