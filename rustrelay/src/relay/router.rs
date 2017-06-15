use std::cell::RefCell;
use std::io;
use std::rc::{Rc, Weak};
use log::LogLevel;

use super::client::Client;
use super::connection::{Connection, ConnectionId};
use super::ipv4_header::Protocol;
use super::ipv4_packet::IPv4Packet;
use super::selector::Selector;
use super::udp_connection::UDPConnection;

const TAG: &'static str = "Router";

pub struct Router {
    client: Weak<RefCell<Client>>,
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
        if !ipv4_packet.is_valid() {
            warn!(target: TAG, "Dropping invalid packet");
            if log_enabled!(target: TAG, LogLevel::Trace) {
                // TODO log binary
            }
        } else {
            if let Ok(mut connection) = self.connection(selector, ipv4_packet) {
                connection.borrow_mut().send_to_network(selector, ipv4_packet);
            } else {
                error!(target: TAG, "Cannot create route, dropping packet");
            }
        }
    }

    fn connection(&mut self, selector: &mut Selector, reference_packet: &IPv4Packet) -> io::Result<&Rc<RefCell<Connection>>> {
        let id = ConnectionId::from_packet(reference_packet);
        let index = match self.find_index(&id) {
            Some(index) => index,
            None => {
                let weak = self.client.clone();
                let connection = Router::create_connection(selector, id, self.client.clone(), reference_packet)?;
                let index = self.connections.len();
                self.connections.push(connection);
                index
            }
        };
        Ok(self.connections.get_mut(index).unwrap())
    }

    fn create_connection(selector: &mut Selector, id: ConnectionId, client: Weak<RefCell<Client>>, reference_packet: &IPv4Packet) -> io::Result<Rc<RefCell<Connection>>> {
        match id.protocol() {
            Protocol::TCP => Err(io::Error::new(io::ErrorKind::Other, "Not implemented yet")),
            Protocol::UDP => Ok(UDPConnection::new(selector, id, client, reference_packet)?),
            p => Err(io::Error::new(io::ErrorKind::Other, format!("Unsupported protocol: {:?}", p))),
        }
    }

    fn find_index(&self, id: &ConnectionId) -> Option<usize> {
        self.connections.iter().position(|connection| connection.borrow_mut().id() == id)
    }

    pub fn remove(&mut self, id: &ConnectionId) {
        let index = self.find_index(id).expect("Removing an unknown connection");
        self.connections.swap_remove(index);
    }

    pub fn clear(&mut self) {
        for connection in &mut self.connections {
            connection.borrow_mut().disconnect();
        }
        self.connections.clear();
    }
}
