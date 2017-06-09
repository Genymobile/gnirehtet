use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};

pub struct Packetizer {
    buffer: Box<[u8; MAX_PACKET_LENGTH]>,
    payload_index: usize,
}

impl Packetizer {
    pub fn new(ipv4_packet: &IPv4Packet) -> Self {
        let buffer = Box::new([0; MAX_PACKET_LENGTH]);
        Self {
            buffer: buffer,
            payload_index: 0,
        }
    }
}
