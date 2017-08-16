use super::ipv4_packet::Ipv4Packet;
use super::selector::Selector;

pub trait PacketSource {
    fn get(&mut self) -> Option<Ipv4Packet>;
    fn next(&mut self, selector: &mut Selector);
}
