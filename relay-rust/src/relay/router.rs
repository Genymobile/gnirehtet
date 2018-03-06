/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use std::cell::RefCell;
use std::io;
use std::ptr;
use std::rc::{Rc, Weak};
use log::Level;

use super::binary;
use super::client::{Client, ClientChannel};
use super::connection::{Connection, ConnectionId};
use super::ipv4_header::Protocol;
use super::ipv4_packet::Ipv4Packet;
use super::selector::Selector;
use super::tcp_connection::TcpConnection;
use super::udp_connection::UdpConnection;

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

    pub fn send_to_network(
        &mut self,
        selector: &mut Selector,
        client_channel: &mut ClientChannel,
        ipv4_packet: &Ipv4Packet,
    ) {
        if ipv4_packet.is_valid() {
            match self.connection(selector, ipv4_packet) {
                Ok(index) => {
                    let closed = {
                        let connection_ref = self.connections.get_mut(index).unwrap();
                        let mut connection = connection_ref.borrow_mut();
                        debug!(
                            target: TAG,
                            "connection already closed on send_to_network? {} {}",
                            connection.id(),
                            connection.is_closed()
                        );
                        connection.send_to_network(selector, client_channel, ipv4_packet);
                        if connection.is_closed() {
                            debug!(
                                target: TAG,
                                "Removing connection from router: {}",
                                connection.id()
                            );
                            true
                        } else {
                            false
                        }
                    };
                    if closed {
                        // the connection is closed, remove it
                        self.connections.swap_remove(index);
                    }
                }
                Err(err) => error!(target: TAG, "Cannot create route, dropping packet: {}", err),
            }
        } else {
            warn!(target: TAG, "Dropping invalid packet");
            if log_enabled!(target: TAG, Level::Trace) {
                trace!(target: TAG, "{}", binary::to_string(ipv4_packet.raw()));
            }
        }
    }

    fn connection(
        &mut self,
        selector: &mut Selector,
        ipv4_packet: &Ipv4Packet,
    ) -> io::Result<usize> {
        let (ipv4_header_data, transport_header_data) = ipv4_packet.headers_data();
        let transport_header_data = transport_header_data.expect("No transport");
        let id = ConnectionId::from_headers(ipv4_header_data, transport_header_data);
        let index = match self.find_index(&id) {
            Some(index) => index,
            None => {
                let connection =
                    Self::create_connection(selector, id, self.client.clone(), ipv4_packet)?;
                let index = self.connections.len();
                self.connections.push(connection);
                index
            }
        };
        Ok(index)
    }

    fn create_connection(
        selector: &mut Selector,
        id: ConnectionId,
        client: Weak<RefCell<Client>>,
        ipv4_packet: &Ipv4Packet,
    ) -> io::Result<Rc<RefCell<Connection>>> {
        let (ipv4_header, transport_header) = ipv4_packet.headers();
        let transport_header = transport_header.expect("No transport");
        match id.protocol() {
            Protocol::Tcp => Ok(TcpConnection::new(
                selector,
                id,
                client,
                ipv4_header,
                transport_header,
            )?),
            Protocol::Udp => Ok(UdpConnection::new(
                selector,
                id,
                client,
                ipv4_header,
                transport_header,
            )?),
            p => Err(io::Error::new(
                io::ErrorKind::Other,
                format!("Unsupported protocol: {:?}", p),
            )),
        }
    }

    fn find_index(&self, id: &ConnectionId) -> Option<usize> {
        self.connections.iter().position(|connection| {
            connection.borrow().id() == id
        })
    }

    pub fn remove(&mut self, connection: &Connection) {
        debug!(
            target: TAG,
            "connection already closed on self-remove? {} {}",
            connection.id(),
            connection.is_closed()
        );
        // ===== for debugging purpose only
        for c in &self.connections {
            let x = connection;
            let y = c.as_ptr();
            debug!("===== {:p} == {:p} ? {}", x, y, ptr::eq(x, y));
        }
        // =====
        let index = self.connections
            .iter()
            .position(|item| {
                // compare pointers to find the connection to remove
                ptr::eq(connection, item.as_ptr())
            })
            .expect("Removing an unknown connection");
        debug!(
            target: TAG,
            "Self-removing connection from router: {}",
            connection.id()
        );
        self.connections.swap_remove(index);
    }

    pub fn clear(&mut self, selector: &mut Selector) {
        for connection in &mut self.connections {
            connection.borrow_mut().close(selector);
        }
        self.connections.clear();
    }

    pub fn clean_expired_connections(&mut self, selector: &mut Selector) {
        // remove the last items first, otherwise i might not be less than len() on swap_remove(i)
        for i in (0..self.connections.len()).rev() {
            let expired = {
                let mut connection = self.connections[i].borrow_mut();
                if connection.is_expired() {
                    debug!(
                        target: TAG,
                        "connection already closed on expiration? {} {}",
                        connection.id(),
                        connection.is_closed()
                    );
                    debug!(
                        target: TAG,
                        "Removing expired connection from router: {}",
                        connection.id()
                    );
                    connection.close(selector);
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
