use std::cell::RefCell;
use std::io;
use std::rc::{Rc, Weak};
use log::LogLevel;

use super::binary;
use super::client::Client;
use super::connection::{Connection, ConnectionId};
use super::ipv4_header::{IPv4Header, Protocol};
use super::ipv4_packet::IPv4Packet;
use super::selector::Selector;
use super::transport_header::TransportHeader;
use super::udp_connection::UDPConnection;

const TAG: &'static str = "Router";

pub struct Router {
    client: Weak<RefCell<Client>>,
    // there are typically only few connections per client, HashMap would be less efficient
    connections: Vec<Rc<RefCell<Connection>>>,
}

impl Router {
    pub fn new() -> Self {
        Self {
            client: Weak::new(),
            connections: Vec::new(),
        }
    }

    // expose client initialization after construction to break cyclic initialization dependencies
    pub fn set_client(&mut self, client: Weak<RefCell<Client>>) {
        self.client = client;
    }

    pub fn send_to_network(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        if ipv4_packet.is_valid() {
            let (ipv4_header, transport) = ipv4_packet.split();
            let (transport_header, _) = transport.expect("No transport");
            if let Ok(connection) = self.connection(selector, &ipv4_header, &transport_header) {
                connection.borrow_mut().send_to_network(selector, ipv4_packet);
            } else {
                error!(target: TAG, "Cannot create route, dropping packet");
            }
        } else {
            warn!(target: TAG, "Dropping invalid packet");
            if log_enabled!(target: TAG, LogLevel::Trace) {
                trace!(target:TAG, "{}", binary::to_string(ipv4_packet.raw()));
            }
        }
    }

    fn connection(&mut self, selector: &mut Selector, ipv4_header: &IPv4Header, transport_header: &TransportHeader) -> io::Result<&Rc<RefCell<Connection>>> {
        // TODO avoid cloning transport_header
        let id = ConnectionId::from_headers(ipv4_header.data(), &transport_header.data_clone());
        let index = match self.find_index(&id) {
            Some(index) => index,
            None => {
                let connection = Router::create_connection(selector, id, self.client.clone(), ipv4_header, transport_header)?;
                let index = self.connections.len();
                self.connections.push(connection);
                index
            }
        };
        Ok(self.connections.get_mut(index).unwrap())
    }

    fn create_connection(selector: &mut Selector, id: ConnectionId, client: Weak<RefCell<Client>>, ipv4_header: &IPv4Header, transport_header: &TransportHeader) -> io::Result<Rc<RefCell<Connection>>> {
        match id.protocol() {
            Protocol::TCP => Err(io::Error::new(io::ErrorKind::Other, "Not implemented yet")),
            Protocol::UDP => Ok(UDPConnection::new(selector, id, client, ipv4_header, transport_header)?),
            p => Err(io::Error::new(io::ErrorKind::Other, format!("Unsupported protocol: {:?}", p))),
        }
    }

    fn find_index(&self, id: &ConnectionId) -> Option<usize> {
        self.connections.iter().position(|connection| connection.borrow().id() == id)
    }

    pub fn remove(&mut self, connection: &Connection) {
        let index = self.connections.iter().position(|item| {
            // compare pointers to find the connection to remove
            binary::ptr_eq(connection, item.as_ptr())
        }).expect("Removing an unknown connection");
        self.connections.swap_remove(index);
    }

    pub fn clear(&mut self, selector: &mut Selector) {
        for connection in &mut self.connections {
            connection.borrow_mut().disconnect(selector);
        }
        self.connections.clear();
    }

    pub fn clean_expired_connections(&mut self, selector: &mut Selector) {
        for i in 0..self.connections.len() {
            let expired = {
                let mut connection = self.connections[i].borrow_mut();
                if connection.is_expired() {
                    debug!(target: TAG, "Removed expired connection: {}", connection.id());
                    connection.disconnect(selector);
                    true
                } else {
                    false
                }
            };
            if expired {
                self.connections.swap_remove(i);
            }
        }
    }
}
