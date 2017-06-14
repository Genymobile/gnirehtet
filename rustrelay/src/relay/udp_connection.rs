use std::cell::RefCell;
use std::io;
use std::rc::{Rc, Weak};
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use mio::{Event, PollOpt, Ready, Token};
use mio::net::UdpSocket;

use super::client::Client;
use super::connection::{self, Connection};
use super::datagram_buffer::DatagramBuffer;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::packetizer::Packetizer;
use super::route::RouteKey;
use super::selector::Selector;

const TAG: &'static str = "UDPConnection";

pub struct UDPConnection {
    client: Weak<RefCell<Client>>,
    route_key: RouteKey,
    socket: UdpSocket,
    token: Token,
    client_to_network: DatagramBuffer,
    network_to_client: Packetizer,
    closed: bool,
}

impl UDPConnection {
    pub fn new(selector: &mut Selector, client: Weak<RefCell<Client>>, route_key: RouteKey, reference_packet: &IPv4Packet) -> io::Result<Rc<RefCell<Self>>> {
        let socket = UDPConnection::create_socket(&route_key)?;
        let raw: &[u8] = reference_packet.raw();
        let ipv4_header = reference_packet.ipv4_header().clone();
        let transport_header = reference_packet.transport_header().as_ref().unwrap().clone();
        let rc = Rc::new(RefCell::new(Self {
            client: client,
            route_key: route_key,
            socket: socket,
            token: Token(0), // default value, will be set afterwards
            client_to_network: DatagramBuffer::new(4 * MAX_PACKET_LENGTH),
            network_to_client: Packetizer::new(raw, ipv4_header, transport_header),
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

    fn create_socket(route_key: &RouteKey) -> io::Result<UdpSocket> {
        let autobind_addr = SocketAddr::new(Ipv4Addr::new(0, 0, 0, 0).into(), 0);
        let udp_socket = UdpSocket::bind(&autobind_addr)?;
        let rewritten_destination = connection::rewritten_destination(route_key.destination_ip(), route_key.destination_port()).into();
        udp_socket.connect(rewritten_destination)?;
        Ok(udp_socket)
    }
    fn close(&mut self, selector: &mut Selector) {
        // TODO
    }

    fn process_send(&mut self, selector: &mut Selector) {
        if let Err(_) = self.write() {
            error!(target: TAG, "Cannot write");
            self.close(selector);
        }
    }

    fn process_receive(&mut self, selector: &mut Selector) {
        // TODO
    }

    fn update_interests(&mut self, selector: &mut Selector) -> io::Result<()> {
        let mut ready = Ready::empty();
        if self.may_read() {
            ready = Ready::readable();
        }
        if self.may_write() {
            ready = ready | Ready::writable();
        }
        selector.reregister(&self.socket, self.token, ready, PollOpt::level())
    }

    fn read(&mut self) -> io::Result<()> {
        // TODO
        Ok(())
    }

    fn write(&mut self) -> io::Result<()> {
        // TODO
        Ok(())
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
    fn send_to_network(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        // TODO
    }

    fn disconnect(&mut self) {
        // TODO
    }

    fn is_expired(&self) -> bool {
        // TODO
        false
    }
}
