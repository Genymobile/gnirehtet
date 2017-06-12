use super::ipv4_packet::IPv4Packet;
use super::udp_connection::UDPConnection;

pub enum Connection {
    UDP(UDPConnection),
}

impl Connection {
    fn send_to_network(&mut self, ipv4_packet: &IPv4Packet) {
        match *self {
            Connection::UDP(ref mut udp_connection) => udp_connection.send_to_network(ipv4_packet),
        }
    }

    fn disconnect(&mut self) {
        match *self {
            Connection::UDP(ref mut udp_connection) => udp_connection.disconnect(),
        }
    }

    fn is_expired(&self) -> bool {
        match *self {
            Connection::UDP(ref udp_connection) => udp_connection.is_expired(),
        }
    }
}

impl From<UDPConnection> for Connection {
    fn from(udp_connection: UDPConnection) -> Connection {
        Connection::UDP(udp_connection)
    }
}
