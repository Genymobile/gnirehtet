use std::cell::RefCell;
use std::io;
use std::rc::{Rc, Weak};
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use log::LogLevel;
use mio::{Event, PollOpt, Ready, Token};
use mio::net::UdpSocket;

use super::client::Client;
use super::connection::{self, Connection, ConnectionId};
use super::datagram_buffer::DatagramBuffer;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::packetizer::Packetizer;
use super::selector::Selector;

const TAG: &'static str = "UDPConnection";

pub struct UDPConnection {
    id: ConnectionId,
    client: Weak<RefCell<Client>>,
    socket: UdpSocket,
    token: Token,
    client_to_network: DatagramBuffer,
    network_to_client: Packetizer,
    pending_packet_for_client: Option<Box<[u8]>>, // used when the client buffer is full, should be rare
    closed: bool,
}

impl UDPConnection {
    pub fn new(selector: &mut Selector, id: ConnectionId, client: Weak<RefCell<Client>>, reference_packet: &IPv4Packet) -> io::Result<Rc<RefCell<Self>>> {
        let socket = UDPConnection::create_socket(&id)?;
        let raw: &[u8] = reference_packet.raw();
        let ipv4_header = reference_packet.ipv4_header().clone();
        let transport_header = reference_packet.transport_header().as_ref().unwrap().clone();
        let rc = Rc::new(RefCell::new(Self {
            id: id,
            client: client,
            socket: socket,
            token: Token(0), // default value, will be set afterwards
            client_to_network: DatagramBuffer::new(4 * MAX_PACKET_LENGTH),
            network_to_client: Packetizer::new(raw, ipv4_header, transport_header),
            pending_packet_for_client: None,
            closed: false,
        }));

        {
            let rc_clone = rc.clone();
            let handler = move |selector: &mut Selector, ready| {
                let mut self_ref = rc_clone.borrow_mut();
                self_ref.on_ready(selector, ready);
            };
            let mut self_ref = rc.borrow_mut();
            let token = selector.register(&self_ref.socket, handler, Ready::readable(), PollOpt::level())?;
            self_ref.token = token;
        }
        Ok(rc)
    }

    fn create_socket(id: &ConnectionId) -> io::Result<UdpSocket> {
        let autobind_addr = SocketAddr::new(Ipv4Addr::new(0, 0, 0, 0).into(), 0);
        let udp_socket = UdpSocket::bind(&autobind_addr)?;
        let rewritten_destination = connection::rewritten_destination(id.destination_ip(), id.destination_port()).into();
        udp_socket.connect(rewritten_destination)?;
        Ok(udp_socket)
    }

    fn close(&mut self, selector: &mut Selector) {
        self.disconnect();

        // route is embedded in router which is embedded in client: the client necessarily exists
        let client_rc = self.client.upgrade().expect("expected client not found");
        let mut client = client_rc.borrow_mut();
        client.router().remove(&self.id);
    }

    fn process_send(&mut self, selector: &mut Selector) {
        if let Err(err) = self.write() {
            error!(target: TAG, "Cannot write: {}", err);
            self.close(selector);
        }
    }

    fn process_receive(&mut self, selector: &mut Selector) {
        if let Err(err) = self.read(selector) {
            error!(target: TAG, "Cannot read: {}", err);
            self.close(selector);
        }
    }

    fn read(&mut self, selector: &mut Selector) -> io::Result<()> {
        let ipv4_packet = self.network_to_client.packetize(&mut self.socket)?;
        let client_rc = self.client.upgrade().expect("expected client not found");
        match client_rc.borrow_mut().send_to_client(selector, &ipv4_packet) {
            Ok(_) => {
                debug!(target: TAG, "Packet ({} bytes) sent to client", ipv4_packet.length());
                if log_enabled!(target: TAG, LogLevel::Trace) {
                    // TODO log binary
                }
            },
            Err(err) => {
                // TODO copy packet locally for further sending

            }
        }
        Ok(())
    }

    fn write(&mut self) -> io::Result<()> {
        self.client_to_network.write_to(&mut self.socket)?;
        Ok(())
    }

    fn update_interests(&mut self, selector: &mut Selector) {
        let mut ready = Ready::empty();
        if self.may_read() {
            ready = Ready::readable();
        }
        if self.may_write() {
            ready = ready | Ready::writable();
        }
        selector.reregister(&self.socket, self.token, ready, PollOpt::level()).expect("Cannot register on poll");
    }

    fn may_read(&self) -> bool {
        // TODO
        true
    }

    fn may_write(&self) -> bool {
        !self.client_to_network.is_empty()
    }

    fn on_ready(&mut self, selector: &mut Selector, event: Event) {
        assert!(!self.closed);
        let ready = event.readiness();
        if ready.is_writable() {
            self.process_send(selector);
        }
        if !self.closed && ready.is_readable() {
            self.process_receive(selector);
        }
        if !self.closed {
            self.update_interests(selector);
        }
    }
}

impl Connection for UDPConnection {
    fn id(&self) -> &ConnectionId {
        &self.id
    }

    fn send_to_network(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        match self.client_to_network.read_from(ipv4_packet.payload()) {
            Ok(_) => {
                self.update_interests(selector);
            },
            Err(err) => warn!(target: TAG, "Cannot send to network, drop packet: {}", err),
        }
    }

    fn disconnect(&mut self) {
        // TODO
    }

    fn is_expired(&self) -> bool {
        // TODO
        false
    }
}
