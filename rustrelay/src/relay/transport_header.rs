use super::ipv4_header::{Ipv4HeaderData, Protocol};
use super::tcp_header::{TcpHeader, TcpHeaderData, TcpHeaderMut};
use super::udp_header::{UdpHeader, UdpHeaderData, UdpHeaderMut, UDP_HEADER_LENGTH};

pub enum TransportHeader<'a> {
    Tcp(TcpHeader<'a>),
    Udp(UdpHeader<'a>),
}

pub enum TransportHeaderMut<'a> {
    Tcp(TcpHeaderMut<'a>),
    Udp(UdpHeaderMut<'a>),
}

#[derive(Clone)]
pub enum TransportHeaderData {
    Tcp(TcpHeaderData),
    Udp(UdpHeaderData),
}

#[allow(dead_code)]
impl TransportHeaderData {
    pub fn parse(protocol: Protocol, raw: &[u8]) -> Option<Self> {
        match protocol {
            Protocol::Udp => Some(UdpHeaderData::parse(raw).into()),
            Protocol::Tcp => Some(TcpHeaderData::parse(raw).into()),
            _ => None,
        }
    }

    #[inline]
    pub fn bind<'c, 'a: 'c, 'b: 'c>(&'a self, raw: &'b [u8]) -> TransportHeader<'c> {
        TransportHeader::new(raw, self)
    }

    #[inline]
    pub fn bind_mut<'c, 'a: 'c, 'b: 'c>(&'a mut self, raw: &'b mut [u8]) -> TransportHeaderMut<'c> {
        TransportHeaderMut::new(raw, self)
    }

    #[inline]
    pub fn source_port(&self) -> u16 {
        match *self {
            TransportHeaderData::Tcp(ref tcp_header_data) => tcp_header_data.source_port(),
            TransportHeaderData::Udp(ref udp_header_data) => udp_header_data.source_port(),
        }
    }

    #[inline]
    pub fn destination_port(&self) -> u16 {
        match *self {
            TransportHeaderData::Tcp(ref tcp_header_data) => tcp_header_data.destination_port(),
            TransportHeaderData::Udp(ref udp_header_data) => udp_header_data.destination_port(),
        }
    }

    #[inline]
    pub fn header_length(&self) -> u8 {
        match *self {
            TransportHeaderData::Tcp(ref tcp_header_data) => tcp_header_data.header_length(),
            TransportHeaderData::Udp(_) => UDP_HEADER_LENGTH,
        }
    }
}

impl<'a> TransportHeader<'a> {
    pub fn new(raw: &'a [u8], data: &'a TransportHeaderData) -> Self {
        match *data {
            TransportHeaderData::Tcp(ref tcp_header_data) => tcp_header_data.bind(raw).into(),
            TransportHeaderData::Udp(ref udp_header_data) => udp_header_data.bind(raw).into(),
        }
    }
}

impl<'a> TransportHeaderMut<'a> {
    pub fn new(raw: &'a mut [u8], data: &'a mut TransportHeaderData) -> Self {
        match *data {
            TransportHeaderData::Tcp(ref mut tcp_header_data) => {
                tcp_header_data.bind_mut(raw).into()
            }
            TransportHeaderData::Udp(ref mut udp_header_data) => {
                udp_header_data.bind_mut(raw).into()
            }
        }
    }
}

// shared definition for TransportHeader and TransportHeaderMut
macro_rules! transport_header_common {
    ($name:ident, $raw_type:ty, $data_type:ty) => {
        // for readability, declare structs manually outside the macro
        #[allow(dead_code)]
        impl<'a> $name<'a> {
            #[inline]
            pub fn raw(&self) -> &[u8] {
                match *self {
                    $name::Tcp(ref tcp_header) => tcp_header.raw(),
                    $name::Udp(ref udp_header) => udp_header.raw(),
                }
            }

            #[inline]
            pub fn data_clone(&self) -> TransportHeaderData {
                match *self {
                    $name::Tcp(ref tcp_header) => tcp_header.data().clone().into(),
                    $name::Udp(ref udp_header) => udp_header.data().clone().into(),
                }
            }

            #[inline]
            pub fn source_port(&self) -> u16 {
                match *self {
                    $name::Tcp(ref tcp_header) => tcp_header.data().source_port(),
                    $name::Udp(ref udp_header) => udp_header.data().source_port(),
                }
            }

            #[inline]
            pub fn destination_port(&self) -> u16 {
                match *self {
                    $name::Tcp(ref tcp_header) => tcp_header.data().destination_port(),
                    $name::Udp(ref udp_header) => udp_header.data().destination_port(),
                }
            }

            #[inline]
            pub fn header_length(&self) -> u8 {
                match *self {
                    $name::Tcp(ref tcp_header) => tcp_header.data().header_length(),
                    $name::Udp(_) => UDP_HEADER_LENGTH,
                }
            }
        }
    }
}

transport_header_common!(TransportHeader, &'a [u8], &'a TransportHeaderData);
transport_header_common!(TransportHeaderMut, &'a mut [u8], &'a mut TransportHeaderData);

// additional methods for the mutable version
#[allow(dead_code)]
impl<'a> TransportHeaderMut<'a> {
    #[inline]
    pub fn raw_mut(&mut self) -> &mut [u8] {
        match *self {
            TransportHeaderMut::Tcp(ref mut tcp_header) => tcp_header.raw_mut(),
            TransportHeaderMut::Udp(ref mut udp_header) => udp_header.raw_mut(),
        }
    }

    #[inline]
    pub fn swap_source_and_destination(&mut self) {
        match *self {
            TransportHeaderMut::Tcp(ref mut tcp_header) => tcp_header.swap_source_and_destination(),
            TransportHeaderMut::Udp(ref mut udp_header) => udp_header.swap_source_and_destination(),
        }
    }

    #[inline]
    pub fn set_payload_length(&mut self, payload_length: u16) {
        match *self {
            TransportHeaderMut::Udp(ref mut udp_header) => {
                udp_header.set_payload_length(payload_length)
            }
            _ => (), // TCP does not store its payload length
        }
    }

    #[inline]
    pub fn update_checksum(&mut self, ipv4_header_data: &Ipv4HeaderData, payload: &[u8]) {
        match *self {
            TransportHeaderMut::Tcp(ref mut tcp_header) => {
                tcp_header.update_checksum(ipv4_header_data, payload)
            }
            TransportHeaderMut::Udp(ref mut udp_header) => {
                udp_header.update_checksum(ipv4_header_data, payload)
            }
        }
    }
}

impl From<TcpHeaderData> for TransportHeaderData {
    fn from(tcp_header_data: TcpHeaderData) -> TransportHeaderData {
        TransportHeaderData::Tcp(tcp_header_data)
    }
}

impl From<UdpHeaderData> for TransportHeaderData {
    fn from(udp_header_data: UdpHeaderData) -> TransportHeaderData {
        TransportHeaderData::Udp(udp_header_data)
    }
}

impl<'a> From<TcpHeader<'a>> for TransportHeader<'a> {
    fn from(tcp_header: TcpHeader) -> TransportHeader {
        TransportHeader::Tcp(tcp_header)
    }
}

impl<'a> From<UdpHeader<'a>> for TransportHeader<'a> {
    fn from(udp_header: UdpHeader) -> TransportHeader {
        TransportHeader::Udp(udp_header)
    }
}

impl<'a> From<TcpHeaderMut<'a>> for TransportHeaderMut<'a> {
    fn from(tcp_header: TcpHeaderMut) -> TransportHeaderMut {
        TransportHeaderMut::Tcp(tcp_header)
    }
}

impl<'a> From<UdpHeaderMut<'a>> for TransportHeaderMut<'a> {
    fn from(udp_header: UdpHeaderMut) -> TransportHeaderMut {
        TransportHeaderMut::Udp(udp_header)
    }
}
