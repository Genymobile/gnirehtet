use std::cell::RefCell;
use std::io;
use std::net::{Ipv4Addr, SocketAddr};
use std::rc::{Rc, Weak};
use std::time::Instant;
use log::LogLevel;
use mio::{Event, PollOpt, Ready, Token};
use mio::net::UdpSocket;

use super::binary;
use super::client::{Client, ClientChannel};
use super::connection::{Connection, ConnectionId};
use super::datagram_buffer::DatagramBuffer;
use super::ipv4_header::Ipv4Header;
use super::ipv4_packet::{Ipv4Packet, MAX_PACKET_LENGTH};
use super::packetizer::Packetizer;
use super::selector::{EventHandler, Selector};
use super::transport_header::TransportHeader;

const TAG: &'static str = "UdpConnection";

pub const IDLE_TIMEOUT_SECONDS: u64 = 2 * 60;

pub struct UdpConnection {
    id: ConnectionId,
    client: Weak<RefCell<Client>>,
    socket: UdpSocket,
    token: Token,
    client_to_network: DatagramBuffer,
    network_to_client: Packetizer,
    closed: bool,
    idle_since: Instant,
}

impl UdpConnection {
    pub fn new(
        selector: &mut Selector,
        id: ConnectionId,
        client: Weak<RefCell<Client>>,
        ipv4_header: Ipv4Header,
        transport_header: TransportHeader,
    ) -> io::Result<Rc<RefCell<Self>>> {
        let socket = UdpConnection::create_socket(&id)?;
        let packetizer = Packetizer::new(&ipv4_header, &transport_header);
        let rc = Rc::new(RefCell::new(Self {
            id: id,
            client: client,
            socket: socket,
            token: Token(0), // default value, will be set afterwards
            client_to_network: DatagramBuffer::new(4 * MAX_PACKET_LENGTH),
            network_to_client: packetizer,
            closed: false,
            idle_since: Instant::now(),
        }));

        {
            let mut self_ref = rc.borrow_mut();

            // rc is an EventHandler, register() expects a Box<EventHandler>
            let handler = Box::new(rc.clone());
            let token = selector.register(
                &self_ref.socket,
                handler,
                Ready::readable(),
                PollOpt::level(),
            )?;
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

    fn process_send(&mut self, selector: &mut Selector) {
        if let Err(err) = self.write() {
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

    fn process_receive(&mut self, selector: &mut Selector) {
        if let Err(err) = self.read(selector) {
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

    fn read(&mut self, selector: &mut Selector) -> io::Result<()> {
        let ipv4_packet = self.network_to_client.packetize(&mut self.socket)?;
        let client_rc = self.client.upgrade().expect("Expected client not found");
        match client_rc.borrow_mut().send_to_client(
            selector,
            &ipv4_packet,
        ) {
            Ok(_) => {
                cx_debug!(
                    target: TAG,
                    self.id,
                    "Packet ({} bytes) sent to client",
                    ipv4_packet.length()
                );
                if log_enabled!(target: TAG, LogLevel::Trace) {
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
        selector
            .reregister(&self.socket, self.token, ready, PollOpt::level())
            .expect("Cannot register on poll");
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
        match self.client_to_network.read_from(
            ipv4_packet.payload().expect(
                "No payload",
            ),
        ) {
            Ok(_) => {
                self.update_interests(selector);
            }
            Err(err) => {
                cx_warn!(
                    target: TAG,
                    self.id,
                    "Cannot send to network, drop packet: {}",
                    err
                )
            }
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

impl EventHandler for UdpConnection {
    fn on_ready(&mut self, selector: &mut Selector, event: Event) {
        if !self.closed {
            self.touch();
            let ready = event.readiness();
            if ready.is_writable() {
                self.process_send(selector);
            }
            if !self.closed && ready.is_readable() {
                self.process_receive(selector);
            }
            if !self.closed {
                self.update_interests(selector);
            } else {
                // on_ready is not called from the router, so the connection must remove itself
                self.remove_from_router();
            }
        }
    }
}
