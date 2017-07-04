use super::ipv4_packet::IPv4Packet;
use super::selector::Selector;

pub trait PacketSource {
    fn get(&mut self) -> Option<IPv4Packet>;
    fn next(&mut self, selector: &mut Selector);
}
