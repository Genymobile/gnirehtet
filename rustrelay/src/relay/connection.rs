use super::ipv4_packet::IPv4Packet;
use super::udp_connection::UDPConnection;

pub trait Connection {
    fn send_to_network(&mut self, ipv4_packet: &IPv4Packet);
    fn disconnect(&mut self);
    fn is_expired(&self) -> bool;
}
