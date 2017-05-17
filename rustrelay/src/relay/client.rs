use std::cell::RefCell;
use std::rc::Rc;
use mio::net::TcpStream;
use mio::{Ready, PollOpt};

use super::selector::{EventHandler, Selector};

pub struct Client {
    stream: TcpStream,
}

impl Client {
    pub fn new(selector: &mut Selector, stream: TcpStream) -> Rc<RefCell<Client>> {
        Rc::new(RefCell::new(Client {
            stream: stream,
        }))
    }
}

impl EventHandler for Client {
    fn on_ready(&mut self, selector: &mut Selector, _: Ready) {

    }
}
