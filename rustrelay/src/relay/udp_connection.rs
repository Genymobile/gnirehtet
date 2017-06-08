use super::connection::Connection;
use super::datagram_buffer::DatagramBuffer;
use super::ipv4_packet::IPv4Packet;

pub struct UDPConnection {
    client_to_network: DatagramBuffer,
}

impl UDPConnection {

}

impl Connection for UDPConnection {
    fn send_to_network(&mut self, ipv4_packet: &IPv4Packet) {
        // TODO
    }

    fn disconnect() {
        // TODO
    }

    fn is_expired() -> bool {
        // TODO
        false
    }

}
