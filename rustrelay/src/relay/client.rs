use std::cell::RefCell;
use std::io;
use std::rc::Rc;
use mio::net::TcpStream;
use mio::{Ready, PollOpt};

use super::selector::{EventHandler, Selector};
use super::ipv4_packet_buffer::IPv4PacketBuffer;

pub struct Client {
    id: u32,
    stream: TcpStream,
    client_to_network: IPv4PacketBuffer,
}

impl Client {
    pub fn new(id: u32, selector: &mut Selector, stream: TcpStream) -> io::Result<Rc<RefCell<Client>>> {
        let rc = Rc::new(RefCell::new(Client {
            id: id,
            stream: stream,
            client_to_network: IPv4PacketBuffer::new(),
        }));
        // on start, we are interested only in writing (we must first send the client id)
        selector.register(&rc.borrow().stream, rc.clone(), Ready::writable(), PollOpt::level())?;
        Ok(rc)
    }
}

impl EventHandler for Client {
    fn on_ready(&mut self, selector: &mut Selector, _: Ready) {

    }
}
