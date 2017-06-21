use super::ipv4_header::IPv4Header;
use super::transport_header::TransportHeader;

pub const MAX_PACKET_LENGTH: usize = 1 << 16;

pub struct IPv4Packet<'a> {
    raw: &'a mut [u8],
    ipv4_header: IPv4Header,
    transport_header: Option<TransportHeader>,
}

impl<'a> IPv4Packet<'a> {
    pub fn parse(raw: &'a mut [u8]) -> Self {
        let ipv4_header = IPv4Header::parse(raw);
        let transport_header = {
            let payload = &raw[ipv4_header.header_length() as usize..];
            TransportHeader::parse(ipv4_header.protocol(), payload)
        };
        Self {
            raw: &mut raw[..ipv4_header.total_length() as usize],
            ipv4_header: ipv4_header,
            transport_header: transport_header,
        }
    }

    pub fn new(raw: &'a mut [u8], ipv4_header: IPv4Header, transport_header: TransportHeader) -> Self {
        Self {
            raw: raw,
            ipv4_header: ipv4_header,
            transport_header: Some(transport_header),
        }
    }

    pub fn raw(&self) -> &[u8] {
        self.raw
    }

    pub fn raw_mut(&mut self) -> &mut [u8] {
        self.raw
    }

    pub fn ipv4_header(&self) -> &IPv4Header {
        &self.ipv4_header
    }

    pub fn ipv4_header_mut(&mut self) -> &mut IPv4Header {
        &mut self.ipv4_header
    }

    pub fn transport_header(&self) -> &Option<TransportHeader> {
        &self.transport_header
    }

    pub fn transport_header_mut(&mut self) -> &mut Option<TransportHeader> {
        &mut self.transport_header
    }

    pub fn destructure(&self) -> (&[u8], &IPv4Header, &Option<TransportHeader>) {
        (self.raw, &self.ipv4_header, &self.transport_header)
    }

    pub fn destructure_mut(&mut self) -> (&mut [u8], &mut IPv4Header, &mut Option<TransportHeader>) {
        (self.raw, &mut self.ipv4_header, &mut self.transport_header)
    }

    pub fn is_valid(&self) -> bool {
        self.transport_header.is_some()
    }

    pub fn length(&self) -> u16 {
        self.ipv4_header.total_length()
    }

    pub fn payload_index(&self) -> Option<u16> {
        if let Some(ref transport_header) = self.transport_header {
            Some(self.ipv4_header.header_length() as u16 + transport_header.header_length() as u16)
        } else {
            None
        }
    }

    pub fn payload_length(&self) -> Option<u16> {
        if let Some(payload_index) = self.payload_index() {
            Some(self.length() - payload_index)
        } else {
            None
        }
    }

    pub fn payload(&self) -> &[u8] {
        &self.raw[self.payload_index().unwrap() as usize..]
    }

    pub fn compute_checksums(&mut self) {
        self.ipv4_header.compute_checksum(self.raw);
        let mut transport = self.transport_header.as_mut().expect("No known transport header");
        let transport_raw = &mut self.raw[self.ipv4_header.header_length() as usize..];
        transport.compute_checksum(transport_raw, &self.ipv4_header);
    }

    pub fn swap_source_and_destination(&mut self) {
        self.ipv4_header.swap_source_and_destination(&mut self.raw);
        if let Some(ref mut transport_header) = self.transport_header {
            let raw_payload = &mut self.raw[self.ipv4_header.header_length() as usize..];
            transport_header.swap_source_and_destination(raw_payload);
        }
    }
}

