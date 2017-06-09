use super::ipv4_header::IPv4Header;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::source_destination::SourceDestination;
use super::transport_header::TransportHeader;

pub struct Packetizer {
    buffer: Box<[u8; MAX_PACKET_LENGTH]>,
    payload_index: usize,
    ipv4_header: IPv4Header,
    transport_header: TransportHeader,
}

impl Packetizer {
    pub fn new(ipv4_packet: &IPv4Packet) -> Self {
        assert!(ipv4_packet.is_valid());
        let mut buffer = Box::new([0; MAX_PACKET_LENGTH]);

        let mut ipv4_header = ipv4_packet.ipv4_header.clone();
        let mut transport_header = ipv4_packet.transport_header.as_ref().unwrap().clone();

        let headers_length = ipv4_header.header_length as usize +
                             transport_header.get_header_length() as usize;
        &mut buffer[0..headers_length].copy_from_slice(&ipv4_packet.raw[0..headers_length]);

        ipv4_header.switch_source_and_destination(&mut buffer[..]);
        transport_header.switch_source_and_destination(&mut buffer[ipv4_header.header_length as usize..]);
        Self {
            buffer: buffer,
            payload_index: headers_length,
            ipv4_header: ipv4_header,
            transport_header: transport_header,
        }
    }
}
