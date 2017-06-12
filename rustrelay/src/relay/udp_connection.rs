use super::datagram_buffer::DatagramBuffer;
use super::ipv4_packet::IPv4Packet;

pub struct UDPConnection {
    client_to_network: DatagramBuffer,
}

impl UDPConnection {
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
