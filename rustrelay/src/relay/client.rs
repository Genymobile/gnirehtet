use std::cell::RefCell;
use std::io::{self, Write};
use std::net::Shutdown;
use std::rc::Rc;
use mio::net::TcpStream;
use mio::{Event, PollOpt, Ready, Token};

use super::binary;
use super::close_listener::CloseListener;
use super::ipv4_packet::{IPv4Packet, MAX_PACKET_LENGTH};
use super::ipv4_packet_buffer::IPv4PacketBuffer;
use super::router::Router;
use super::selector::Selector;
use super::stream_buffer::StreamBuffer;

const TAG: &'static str = "Client";

pub struct Client {
    id: u32,
    stream: TcpStream,
    token: Token,
    client_to_network: IPv4PacketBuffer,
    network_to_client: StreamBuffer,
    router: Router,
    close_listener: Box<CloseListener<Client>>,
    closed: bool,
    // number of remaining bytes of "id" to send to the client before relaying any data
    pending_id_bytes: usize,
}

impl Client {
    pub fn new(id: u32, selector: &mut Selector, stream: TcpStream, close_listener: Box<CloseListener<Client>>) -> io::Result<Rc<RefCell<Self>>> {
        let rc = Rc::new(RefCell::new(Self {
            id: id,
            stream: stream,
            token: Token(0), // default value, will be set afterwards
            client_to_network: IPv4PacketBuffer::new(),
            network_to_client: StreamBuffer::new(16 * MAX_PACKET_LENGTH),
            router: Router::new(),
            closed: false,
            close_listener: close_listener,
            pending_id_bytes: 4,
        }));
        // set client as router owner
        rc.borrow_mut().router.set_client(Rc::downgrade(&rc));

        {
            let rc_clone = rc.clone();
            let handler = move |selector: &mut Selector, ready| {
                let mut self_ref = rc_clone.borrow_mut();
                self_ref.on_ready(selector, ready);
            };
            let mut self_ref = rc.borrow_mut();
            // on start, we are interested only in writing (we must first send the client id)
            let token = selector.register(&self_ref.stream, handler, Ready::writable(), PollOpt::level())?;
            self_ref.token = token;
        }
        Ok(rc)
    }

    pub fn id(&self) -> u32 {
        return self.id;
    }

    pub fn router(&mut self) -> &mut Router {
        &mut self.router
    }

    fn close(&mut self, selector: &mut Selector) {
        self.closed = true;
        selector.deregister(&self.stream, self.token);
        // shutdown only (there is no close), the socket will be closed on drop
        self.stream.shutdown(Shutdown::Both);
        self.router.clear();
        self.close_listener.on_closed(self);
    }

    fn process_send(&mut self, selector: &mut Selector) {
        if self.must_send_id() {
            match self.send_id() {
                Ok(_) => {
                    if self.pending_id_bytes == 0 {
                        debug!(target:TAG, "Client id #{} sent to client", self.id);
                    }
                }
                Err(_) => {
                    error!(target: TAG, "Cannot write client id #{}", self.id);
                    self.close(selector);
                }
            }
        } else {
            match self.write() {
                Ok(_) => self.process_pending(),
                Err(_) => {
                    error!(target: TAG, "Cannot write");
                    self.close(selector);
                }
            }
        }
    }

    fn process_receive(&mut self, selector: &mut Selector) {
        match self.read() {
            Ok(_) => self.push_to_network(selector),
            Err(_) => {
                error!(target: TAG, "Cannot read");
                self.close(selector);
            }
        }
    }

    pub fn send_to_client(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) -> io::Result<()> {
        if ipv4_packet.length() as usize <= self.network_to_client.remaining() {
            self.network_to_client.read_from(ipv4_packet.raw());
            self.update_interests(selector);
            Ok(())
        } else {
            warn!(target: TAG, "Client buffer full, delaying packet processing");
            Err(io::Error::new(io::ErrorKind::WouldBlock, "Client buffer full"))
        }
    }

    fn send_id(&mut self) -> io::Result<()> {
        assert!(self.must_send_id());
        let raw_id = binary::to_byte_array(self.id);
        let w = self.stream.write(&raw_id[4 - self.pending_id_bytes..])?;
        self.pending_id_bytes -= w;
        Ok(())
    }

    fn update_interests(&mut self, selector: &mut Selector) {
        let ready = if self.network_to_client.is_empty() {
            Ready::readable()
        } else {
            Ready::readable() | Ready::writable()
        };
        selector.reregister(&self.stream, self.token, ready, PollOpt::level()).expect("Cannot register on poll");
    }

    fn read(&mut self) -> io::Result<()> {
        self.client_to_network.read_from(&mut self.stream)
    }

    fn write(&mut self) -> io::Result<()> {
        self.network_to_client.write_to(&mut self.stream)?;
        Ok(())
    }

    fn push_to_network(&mut self, selector: &mut Selector) {
        while self.push_one_packet_to_network(selector) {
            self.client_to_network.next();
        }
    }

    fn push_one_packet_to_network(&mut self, selector: &mut Selector) -> bool {
        match self.client_to_network.as_ipv4_packet() {
            Some(ref packet) => {
                self.router.send_to_network(selector, packet);
                true
            }
            None => false
        }
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

    fn process_pending(&mut self) {
        // TODO
    }

    fn clean_expired_connections() {
        // TODO
    }

    fn must_send_id(&self) -> bool{
        self.pending_id_bytes > 0
    }
}
