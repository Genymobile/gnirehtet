use std::cell::RefCell;
use std::io;
use std::net::Shutdown;
use std::rc::Rc;
use mio::Token;
use mio::net::TcpStream;
use mio::{Event, PollOpt, Ready};

use super::close_listener::CloseListener;
use super::ipv4_packet_buffer::IPv4PacketBuffer;
use super::selector::Selector;

const TAG: &'static str = "Client";

pub struct Client {
    id: u32,
    stream: TcpStream,
    client_to_network: IPv4PacketBuffer,
    closed: bool,
    close_listener: Box<CloseListener<Client>>,
    token: Token,
}

impl Client {
    pub fn new<C>(id: u32, selector: &mut Selector, stream: TcpStream, close_listener: C) -> io::Result<Rc<RefCell<Self>>>
            where C: CloseListener<Client> + 'static {
        let rc = Rc::new(RefCell::new(Self {
            id: id,
            stream: stream,
            client_to_network: IPv4PacketBuffer::new(),
            closed: false,
            close_listener: Box::new(close_listener),
            token: Token(0), // default value, will be set afterwards
        }));
        let rc_clone = rc.clone();
        let handler = move |selector: &mut Selector, ready| {
            let mut self_ref = rc_clone.borrow_mut();
            self_ref.on_ready(selector, ready);
        };
        {
            let mut self_ref = rc.borrow_mut();
            // on start, we are interested only in writing (we must first send the client id)
            let token = selector.register(&self_ref.stream, handler, Ready::writable(), PollOpt::level())?;
            self_ref.token = token;
        }
        Ok(rc)
    }

    pub fn get_id(&self) -> u32 {
        return self.id;
    }

    fn close(&mut self, selector: &mut Selector) {
        self.closed = true;
        selector.deregister(&self.stream, self.token);
        // shutdown only (there is no close), the socket will be closed on drop
        self.stream.shutdown(Shutdown::Both);
        // TODO router.clear();
        self.close_listener.on_closed(self);
    }

    fn process_send(&mut self, selector: &mut Selector) {
        
    }

    fn process_receive(&mut self, selector: &mut Selector) {
        match self.read() {
            Ok(_) => self.push_to_network(),
            Err(_) => {
                error!(target: TAG, "Cannot read");
                self.close(selector);
            }
        }
    }

    fn update_interests(&mut self, selector: &mut Selector) {

    }

    fn read(&mut self) -> io::Result<()> {
        self.client_to_network.read_from(&mut self.stream)
    }

    fn write(&mut self) -> io::Result<()> {
        // TODO
        Ok(())
    }

    fn push_to_network(&mut self) {
        while self.push_one_packet_to_network() {
            self.client_to_network.next();
        }
    }

    fn push_one_packet_to_network(&mut self) -> bool {
        match self.client_to_network.as_ipv4_packet() {
            Some(ref packet) => {
                // router.send_to_network(packet);
                true
            },
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
}
