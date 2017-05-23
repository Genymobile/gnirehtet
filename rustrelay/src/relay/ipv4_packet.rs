use super::ipv4_header::IPv4Header;

pub struct IPv4Packet<'a> {
    raw: &'a mut [u8],
    ipv4_header: IPv4Header,
}

impl<'a> IPv4Packet<'a> {
    fn new(raw: &'a mut [u8]) -> IPv4Packet<'a> {
        let ipv4_header = IPv4Header::parse(raw);
        IPv4Packet {
            raw: raw,
            ipv4_header: ipv4_header,
        }
    }
}

