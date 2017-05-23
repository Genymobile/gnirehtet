use super::ipv4header::IPv4Header;

pub struct IPv4Packet<'a> {
    raw: &'a mut [u8],
    header: IPv4Header,
}

impl<'a> IPv4Packet<'a> {
    fn new(raw: &'a mut [u8]) -> IPv4Packet<'a> {
        let header = IPv4Header::parse(raw);
        IPv4Packet {
            raw: raw,
            header: header,
        }
    }
}

