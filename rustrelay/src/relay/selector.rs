use mio::*;
use std::cell::RefCell;
use std::fmt::Debug;
use std::io;
use std::rc::Rc;
use std::time::Duration;
use slab::Slab;

pub trait EventHandler {
    fn on_ready(&self, selector: &mut Selector, event: Event);
}

impl<F> EventHandler for F where F: Fn(&mut Selector, Event) {
    fn on_ready(&self, selector: &mut Selector, event: Event) {
        self(selector, event);
    }
}

// for convenience
impl EventHandler for Rc<RefCell<EventHandler>> {
    fn on_ready(&self, selector: &mut Selector, event: Event) {
        self.borrow().on_ready(selector, event);
    }
}

pub struct Selector {
    poll: Poll,
    handlers: Slab<Box<EventHandler>, Token>,
}

impl Selector {
    pub fn new() -> io::Result<Self> {
        Ok(Self {
            poll: Poll::new()?,
            handlers: Slab::with_capacity(1024),
        })
    }

    pub fn register<E>(&mut self, handle: &E, handler: Box<EventHandler>,
                   interest: Ready, opts: PollOpt) -> io::Result<Token>
            where E: Evented + ?Sized {
        let token = self.handlers.insert(handler)
                        .map_err(|_| io::Error::new(io::ErrorKind::Other, "Cannot allocate slab slot"))?;
        self.poll.register(handle, token, interest, opts)?;
        Ok(token)
    }

    pub fn reregister<E>(&self, handle: &E, token: Token,
                   interest: Ready, opts: PollOpt) -> io::Result<()>
            where E: Evented + ?Sized {
        self.poll.reregister(handle, token, interest, opts)
    }

    pub fn deregister<E>(&mut self, handle: &E, token: Token) -> io::Result<()>
            where E: Evented + ?Sized {
        self.handlers.remove(token).expect("Unknown token removed");
        self.poll.deregister(handle)
    }

    pub fn poll(&mut self, events: &mut Events, timeout: Option<Duration>) -> io::Result<usize> {
        self.poll.poll(events, timeout)
    }

    pub fn run_handler(&mut self, event: Event) {
        let mut handler = self.handlers.remove(event.token()).expect("Token not found");
        handler.on_ready(self, event);
        if let Err(_) = self.handlers.insert(handler) {
            panic!("Cannot allocate slab slot");
        }
    }
}
