use super::ipv4_packet::IPv4Packet;

pub trait Connection {
    fn send_to_network(&mut self, ipv4_packet: &IPv4Packet);
    fn disconnect();
    fn is_expired() -> bool;
}
