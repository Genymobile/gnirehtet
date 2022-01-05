//! Socks5 protocol definition (RFC1928)
//!
//! Implements [SOCKS Protocol Version 5](https://www.ietf.org/rfc/rfc1928.txt) proxy protocol

use std::{
    convert::From,
    fmt::{self, Debug},
    io::{self, ErrorKind, Read},
    u8,
};
use std::net::{SocketAddr, SocketAddrV4, Ipv4Addr};
use mio::net::TcpStream;
use byteorder::{ReadBytesExt, BigEndian};

pub use self::consts::{
    SOCKS5_AUTH_METHOD_GSSAPI,
    SOCKS5_AUTH_METHOD_NONE,
    SOCKS5_AUTH_METHOD_NOT_ACCEPTABLE,
    SOCKS5_AUTH_METHOD_PASSWORD,
};

pub const MAX_ADDR_LEN: usize = 260;

#[rustfmt::skip]
pub mod consts {
    pub const SOCKS5_VERSION:                          u8 = 0x05;

    pub const SOCKS5_AUTH_METHOD_NONE:                 u8 = 0x00;
    pub const SOCKS5_AUTH_METHOD_GSSAPI:               u8 = 0x01;
    pub const SOCKS5_AUTH_METHOD_PASSWORD:             u8 = 0x02;
    pub const SOCKS5_AUTH_METHOD_NOT_ACCEPTABLE:       u8 = 0xff;

    pub const SOCKS5_CMD_TCP_CONNECT:                  u8 = 0x01;
    pub const SOCKS5_CMD_TCP_BIND:                     u8 = 0x02;
    pub const SOCKS5_CMD_UDP_ASSOCIATE:                u8 = 0x03;

    pub const SOCKS5_ADDR_TYPE_IPV4:                   u8 = 0x01;
    pub const SOCKS5_ADDR_TYPE_DOMAIN_NAME:            u8 = 0x03;
    pub const SOCKS5_ADDR_TYPE_IPV6:                   u8 = 0x04;

    pub const SOCKS5_REPLY_SUCCEEDED:                  u8 = 0x00;
    pub const SOCKS5_REPLY_GENERAL_FAILURE:            u8 = 0x01;
    pub const SOCKS5_REPLY_CONNECTION_NOT_ALLOWED:     u8 = 0x02;
    pub const SOCKS5_REPLY_NETWORK_UNREACHABLE:        u8 = 0x03;
    pub const SOCKS5_REPLY_HOST_UNREACHABLE:           u8 = 0x04;
    pub const SOCKS5_REPLY_CONNECTION_REFUSED:         u8 = 0x05;
    pub const SOCKS5_REPLY_TTL_EXPIRED:                u8 = 0x06;
    pub const SOCKS5_REPLY_COMMAND_NOT_SUPPORTED:      u8 = 0x07;
    pub const SOCKS5_REPLY_ADDRESS_TYPE_NOT_SUPPORTED: u8 = 0x08;
}

/// SOCKS5 command
#[derive(Clone, Debug, Copy)]
pub enum Command {
    /// CONNECT command (TCP tunnel)
    TcpConnect,
    /// BIND command (Not supported)
    TcpBind,
    /// UDP ASSOCIATE command
    UdpAssociate,
}

#[allow(dead_code)]
impl Command {
    #[inline]
    #[rustfmt::skip]
    fn as_u8(self) -> u8 {
        match self {
            Command::TcpConnect   => consts::SOCKS5_CMD_TCP_CONNECT,
            Command::TcpBind      => consts::SOCKS5_CMD_TCP_BIND,
            Command::UdpAssociate => consts::SOCKS5_CMD_UDP_ASSOCIATE,
        }
    }

    #[inline]
    #[rustfmt::skip]
    fn from_u8(code: u8) -> Option<Command> {
        match code {
            consts::SOCKS5_CMD_TCP_CONNECT   => Some(Command::TcpConnect),
            consts::SOCKS5_CMD_TCP_BIND      => Some(Command::TcpBind),
            consts::SOCKS5_CMD_UDP_ASSOCIATE => Some(Command::UdpAssociate),
            _                                => None,
        }
    }
}

/// SOCKS5 reply code
#[derive(Clone, Debug, Copy)]
pub enum Reply {
    Succeeded,
    GeneralFailure,
    ConnectionNotAllowed,
    NetworkUnreachable,
    HostUnreachable,
    ConnectionRefused,
    TtlExpired,
    CommandNotSupported,
    AddressTypeNotSupported,

    OtherReply(u8),
}

#[allow(dead_code)]
impl Reply {
    #[inline]
    #[rustfmt::skip]
    fn as_u8(self) -> u8 {
        match self {
            Reply::Succeeded               => consts::SOCKS5_REPLY_SUCCEEDED,
            Reply::GeneralFailure          => consts::SOCKS5_REPLY_GENERAL_FAILURE,
            Reply::ConnectionNotAllowed    => consts::SOCKS5_REPLY_CONNECTION_NOT_ALLOWED,
            Reply::NetworkUnreachable      => consts::SOCKS5_REPLY_NETWORK_UNREACHABLE,
            Reply::HostUnreachable         => consts::SOCKS5_REPLY_HOST_UNREACHABLE,
            Reply::ConnectionRefused       => consts::SOCKS5_REPLY_CONNECTION_REFUSED,
            Reply::TtlExpired              => consts::SOCKS5_REPLY_TTL_EXPIRED,
            Reply::CommandNotSupported     => consts::SOCKS5_REPLY_COMMAND_NOT_SUPPORTED,
            Reply::AddressTypeNotSupported => consts::SOCKS5_REPLY_ADDRESS_TYPE_NOT_SUPPORTED,
            Reply::OtherReply(c)           => c,
        }
    }

    #[inline]
    #[rustfmt::skip]
    fn from_u8(code: u8) -> Reply {
        match code {
            consts::SOCKS5_REPLY_SUCCEEDED                  => Reply::Succeeded,
            consts::SOCKS5_REPLY_GENERAL_FAILURE            => Reply::GeneralFailure,
            consts::SOCKS5_REPLY_CONNECTION_NOT_ALLOWED     => Reply::ConnectionNotAllowed,
            consts::SOCKS5_REPLY_NETWORK_UNREACHABLE        => Reply::NetworkUnreachable,
            consts::SOCKS5_REPLY_HOST_UNREACHABLE           => Reply::HostUnreachable,
            consts::SOCKS5_REPLY_CONNECTION_REFUSED         => Reply::ConnectionRefused,
            consts::SOCKS5_REPLY_TTL_EXPIRED                => Reply::TtlExpired,
            consts::SOCKS5_REPLY_COMMAND_NOT_SUPPORTED      => Reply::CommandNotSupported,
            consts::SOCKS5_REPLY_ADDRESS_TYPE_NOT_SUPPORTED => Reply::AddressTypeNotSupported,
            _                                               => Reply::OtherReply(code),
        }
    }
}

impl fmt::Display for Reply {
    #[rustfmt::skip]
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match *self {
            Reply::Succeeded               => write!(f, "Succeeded"),
            Reply::AddressTypeNotSupported => write!(f, "Address type not supported"),
            Reply::CommandNotSupported     => write!(f, "Command not supported"),
            Reply::ConnectionNotAllowed    => write!(f, "Connection not allowed"),
            Reply::ConnectionRefused       => write!(f, "Connection refused"),
            Reply::GeneralFailure          => write!(f, "General failure"),
            Reply::HostUnreachable         => write!(f, "Host unreachable"),
            Reply::NetworkUnreachable      => write!(f, "Network unreachable"),
            Reply::OtherReply(u)           => write!(f, "Other reply ({})", u),
            Reply::TtlExpired              => write!(f, "TTL expired"),
        }
    }
}

/// SOCKS5 protocol error
#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("{0}")]
    IoError(#[from] io::Error),
    #[error("address type {0:#x} not supported")]
    AddressTypeNotSupported(u8),
    #[error("address domain name must be UTF-8 encoding")]
    AddressDomainInvalidEncoding,
    #[error("unsupported socks version {0:#x}")]
    UnsupportedSocksVersion(u8),
    #[error("unsupported command {0:#x}")]
    UnsupportedCommand(u8),
    #[error("{0}")]
    Reply(Reply),
}

impl From<Error> for io::Error {
    fn from(err: Error) -> io::Error {
        match err {
            Error::IoError(err) => err,
            e => io::Error::new(ErrorKind::Other, e),
        }
    }
}

impl Error {
    /// Convert to `Reply` for responding
    pub fn as_reply(&self) -> Reply {
        match *self {
            Error::IoError(ref err) => match err.kind() {
                ErrorKind::ConnectionRefused => Reply::ConnectionRefused,
                _ => Reply::GeneralFailure,
            },
            Error::AddressTypeNotSupported(..) => Reply::AddressTypeNotSupported,
            Error::AddressDomainInvalidEncoding => Reply::GeneralFailure,
            Error::UnsupportedSocksVersion(..) => Reply::GeneralFailure,
            Error::UnsupportedCommand(..) => Reply::CommandNotSupported,
            Error::Reply(r) => r,
        }
    }
}

/// SOCKS5 address type
#[derive(Clone, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub enum Address {
    /// Socket address (IP Address)
    SocketAddress(SocketAddr),
    /// Domain name address
    DomainNameAddress(String, u16),
}

#[derive(Debug, PartialEq, Eq)]
pub enum Socks5State {
    NoProxy,
    Socks5HostNotConnected, // first we send auth request
    Socks5AuthSend,  // socks5 reply auth method
    Socks5AuthUsernamePasswordSend, // send username/password to socks5
    Socks5AuthUsernamePasswordDone, // socks5 username/password authorize done
    Socks5AuthDone,  // socks5 authorize done, send cmd connect with target addr/port
    TargetAddrSend,  // socks reply cmd connect status
    RemoteConnected  // socks5 reply X'00', succeeded
}

/// Authentication methods

#[derive(Debug)]
pub enum Authentication<'a> {
    Password { username: &'a str, password: &'a str },
    None
}

impl<'a> Authentication<'a> {
    pub fn id(&self) -> u8 {
        match *self {
            Authentication::Password { .. } => 2,
            Authentication::None => 0
        }
    }

    pub fn is_no_auth(&self) -> bool {
        if let Authentication::None = *self {
            true
        } else {
            false
        }
    }
}


fn read_addr<R: Read>(socket: &mut R) -> io::Result<SocketAddrV4> {
    match socket.read_u8()? {
        1 => { // socks5 ipv4
            let ip = Ipv4Addr::from(socket.read_u32::<BigEndian>()?);
            let port = socket.read_u16::<BigEndian>()?;
            Ok(SocketAddrV4::new(ip, port))
        }
        _ => Err(io::Error::new(io::ErrorKind::Other, "unsupported address type")),
    }
}

// must be called when socks5_state = TargetAddrSend and selector is readable
pub fn socks5_read_response(socket: &mut TcpStream) -> io::Result<SocketAddrV4> {
    if socket.read_u8()? != 5 {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "invalid response version"));
    }

    match socket.read_u8()? {
        0 => {}
        1 => return Err(io::Error::new(io::ErrorKind::Other, "general SOCKS server failure")),
        2 => return Err(io::Error::new(io::ErrorKind::Other, "connection not allowed by ruleset")),
        3 => return Err(io::Error::new(io::ErrorKind::Other, "network unreachable")),
        4 => return Err(io::Error::new(io::ErrorKind::Other, "host unreachable")),
        5 => return Err(io::Error::new(io::ErrorKind::Other, "connection refused")),
        6 => return Err(io::Error::new(io::ErrorKind::Other, "TTL expired")),
        7 => return Err(io::Error::new(io::ErrorKind::Other, "command not supported")),
        8 => return Err(io::Error::new(io::ErrorKind::Other, "address kind not supported")),
        _ => return Err(io::Error::new(io::ErrorKind::Other, "unknown error")),
    }

    if socket.read_u8()? != 0 {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "invalid reserved byte"));
    }

    read_addr(socket)
}

pub fn socks5_read_auth_method_response(socket : &mut TcpStream) -> io::Result<u8> {
    let mut buf = [0; 2];
    socket.read_exact(&mut buf)?;

    let response_version = buf[0];
    let selected_method = buf[1];

    if response_version != 5  {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "invalid auth response version"));
    } else  {
        Ok(selected_method)
    }
}

pub fn socks5_read_username_password_auth_response(socket : &mut TcpStream) -> io::Result<u8> {
    let mut buf = [0; 2];
    socket.read_exact(&mut buf)?;

    let response_version = buf[0];
    let authenticate_status = buf[1];

    if response_version != 1  {
        return Err(io::Error::new(io::ErrorKind::InvalidData, "invalid auth u/p response version"));
    } else  {
        Ok(authenticate_status)
    }
}