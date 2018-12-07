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

use log::*;
use mio::net::UdpSocket;
use mio::{Event, PollOpt, Ready, Token};
use std::cell::RefCell;
use std::io;
use std::net::{Ipv4Addr, SocketAddr};
use std::rc::{Rc, Weak};
use std::time::Instant;

use super::binary;
use super::client::{Client, ClientChannel};
use super::connection::{Connection, ConnectionId};
use super::datagram_buffer::DatagramBuffer;
use super::ipv4_header::Ipv4Header;
use super::ipv4_packet::{Ipv4Packet, MAX_PACKET_LENGTH};
use super::packetizer::Packetizer;
use super::selector::Selector;
use super::transport_header::TransportHeader;

const TAG: &str = "UdpConnection";

pub const IDLE_TIMEOUT_SECONDS: u64 = 2 * 60;

pub struct UdpConnection {
    id: ConnectionId,
    client: Weak<RefCell<Client>>,
    socket: UdpSocket,
    interests: Ready,
    token: Token,
    client_to_network: DatagramBuffer,
    network_to_client: Packetizer,
    closed: bool,
    idle_since: Instant,
}

impl UdpConnection {
    #[allow(clippy::needless_pass_by_value)] // semantically, headers are consumed
    pub fn create(
        selector: &mut Selector,
        id: ConnectionId,
        client: Weak<RefCell<Client>>,
        ipv4_header: Ipv4Header,
        transport_header: TransportHeader,
    ) -> io::Result<Rc<RefCell<Self>>> {
        cx_info!(target: TAG, id, "Open");
        let socket = Self::create_socket(&id)?;
        let packetizer = Packetizer::new(&ipv4_header, &transport_header);
        let interests = Ready::readable();
        let rc = Rc::new(RefCell::new(Self {
            id,
            client,
            socket,
            interests,
            token: Token(0), // default value, will be set afterwards
            client_to_network: DatagramBuffer::new(4 * MAX_PACKET_LENGTH),
            network_to_client: packetizer,
            closed: false,
            idle_since: Instant::now(),
        }));

        {
            let mut self_ref = rc.borrow_mut();

            let rc2 = rc.clone();
            // must anotate selector type: https://stackoverflow.com/a/44004103/1987178
            let handler =
                move |selector: &mut Selector, event| rc2.borrow_mut().on_ready(selector, event);
            let token =
                selector.register(&self_ref.socket, handler, interests, PollOpt::level())?;
            self_ref.token = token;
        }
        Ok(rc)
    }

    fn create_socket(id: &ConnectionId) -> io::Result<UdpSocket> {
        let autobind_addr = SocketAddr::new(Ipv4Addr::new(0, 0, 0, 0).into(), 0);
        let udp_socket = UdpSocket::bind(&autobind_addr)?;
        udp_socket.connect(id.rewritten_destination().into())?;
        Ok(udp_socket)
    }

    fn remove_from_router(&self) {
        // route is embedded in router which is embedded in client: the client necessarily exists
        let client_rc = self.client.upgrade().expect("Expected client not found");
        let mut client = client_rc.borrow_mut();
        client.router().remove(self);
    }

    fn on_ready(&mut self, selector: &mut Selector, event: Event) {
        #[allow(clippy::match_wild_err_arm)]
        match self.process(selector, event) {
            Ok(_) => (),
            Err(ref err) if err.kind() == io::ErrorKind::WouldBlock => {
                cx_debug!(target: TAG, self.id, "Spurious event, ignoring")
            }
            Err(_) => panic!("Unexpected unhandled error"),
        }
    }

    // return Err(err) with err.kind() == io::ErrorKind::WouldBlock on spurious event
    fn process(&mut self, selector: &mut Selector, event: Event) -> io::Result<()> {
        if !self.closed {
            self.touch();
            let ready = event.readiness();
            if ready.is_readable() || ready.is_writable() {
                if ready.is_writable() {
                    self.process_send(selector)?;
                }
                if !self.closed && ready.is_readable() {
                    self.process_receive(selector)?;
                }
                if !self.closed {
                    self.update_interests(selector);
                }
            } else {
                // error or hup
                self.close(selector);
            }
            if self.closed {
                // on_ready is not called from the router, so the connection must remove itself
                self.remove_from_router();
            }
        }
        Ok(())
    }

    // return Err(err) with err.kind() == io::ErrorKind::WouldBlock on spurious event
    fn process_send(&mut self, selector: &mut Selector) -> io::Result<()> {
        match self.write() {
            Ok(_) => (),
            Err(ref err) if err.kind() == io::ErrorKind::WouldBlock => {
                cx_debug!(target: TAG, self.id, "Spurious event, ignoring")
            }
            Err(err) => {
                if err.kind() == io::ErrorKind::WouldBlock {
                    // rethrow
                    return Err(err);
                }
                cx_error!(
                    target: TAG,
                    self.id,
                    "Cannot write: [{:?}] {}",
                    err.kind(),
                    err
                );
                self.close(selector);
            }
        }
        Ok(())
    }

    // return Err(err) with err.kind() == io::ErrorKind::WouldBlock on spurious event
    fn process_receive(&mut self, selector: &mut Selector) -> io::Result<()> {
        match self.read(selector) {
            Ok(_) => (),
            Err(err) => {
                if err.kind() == io::ErrorKind::WouldBlock {
                    // rethrow
                    return Err(err);
                }
                cx_error!(
                    target: TAG,
                    self.id,
                    "Cannot read: [{:?}] {}",
                    err.kind(),
                    err
                );
                self.close(selector);
            }
        }
        Ok(())
    }

    fn read(&mut self, selector: &mut Selector) -> io::Result<()> {
        let ipv4_packet = self.network_to_client.packetize(&mut self.socket)?;
        let client_rc = self.client.upgrade().expect("Expected client not found");
        match client_rc
            .borrow_mut()
            .send_to_client(selector, &ipv4_packet)
        {
            Ok(_) => {
                cx_debug!(
                    target: TAG,
                    self.id,
                    "Packet ({} bytes) sent to client",
                    ipv4_packet.length()
                );
                if log_enabled!(target: TAG, Level::Trace) {
                    cx_trace!(
                        target: TAG,
                        self.id,
                        "{}",
                        binary::to_string(ipv4_packet.raw())
                    );
                }
            }
            Err(_) => cx_warn!(target: TAG, self.id, "Cannot send to client, drop packet"),
        }
        Ok(())
    }

    fn write(&mut self) -> io::Result<()> {
        self.client_to_network.write_to(&mut self.socket)?;
        Ok(())
    }

    fn update_interests(&mut self, selector: &mut Selector) {
        let ready = if self.client_to_network.is_empty() {
            Ready::readable()
        } else {
            Ready::readable() | Ready::writable()
        };
        cx_debug!(target: TAG, self.id, "interests: {:?}", ready);
        if self.interests != ready {
            // interests must be changed
            self.interests = ready;
            selector
                .reregister(&self.socket, self.token, ready, PollOpt::level())
                .expect("Cannot register on poll");
        }
    }

    fn touch(&mut self) {
        self.idle_since = Instant::now();
    }
}

impl Connection for UdpConnection {
    fn id(&self) -> &ConnectionId {
        &self.id
    }

    fn send_to_network(
        &mut self,
        selector: &mut Selector,
        _: &mut ClientChannel,
        ipv4_packet: &Ipv4Packet,
    ) {
        match self
            .client_to_network
            .read_from(ipv4_packet.payload().expect("No payload"))
        {
            Ok(_) => {
                self.update_interests(selector);
            }
            Err(err) => cx_warn!(
                target: TAG,
                self.id,
                "Cannot send to network, drop packet: {}",
                err
            ),
        }
    }

    fn close(&mut self, selector: &mut Selector) {
        cx_info!(target: TAG, self.id, "Close");
        self.closed = true;
        selector.deregister(&self.socket, self.token).unwrap();
        // socket will be closed by RAII
    }

    fn is_expired(&self) -> bool {
        self.idle_since.elapsed().as_secs() > IDLE_TIMEOUT_SECONDS
    }

    fn is_closed(&self) -> bool {
        self.closed
    }
}
