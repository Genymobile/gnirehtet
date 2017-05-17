use mio::*;
use std::cell::RefCell;
use std::io;
use std::rc::Rc;
use slab::Slab;

pub trait EventHandler {
    fn on_ready(&mut self, selector: &mut Selector, ready: Ready);
}

impl<F> EventHandler for F where F: FnMut(&mut Selector, Ready) {
    fn on_ready(&mut self, selector: &mut Selector, ready: Ready) {
        self(selector, ready);
    }
}

// for convenience
impl EventHandler for Rc<RefCell<EventHandler>> {
    fn on_ready(&mut self, selector: &mut Selector, ready: Ready) {
        let mut self_raw = self.borrow_mut();
        self_raw.on_ready(selector, ready);
    }
}

pub struct Selector {
    pub poll: Poll,
    pub handlers: Slab<Rc<RefCell<EventHandler>>, Token>,
}

impl Selector {
    pub fn new() -> io::Result<Selector> {
        Ok(Selector {
            poll: Poll::new()?,
            handlers: Slab::with_capacity(1024),
        })
    }

    pub fn register<E>(&mut self, handle: &E, handler: Rc<RefCell<EventHandler>>,
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
        if self.handlers.remove(token).is_some() {
            panic!("Unknown token removed");
        }
        self.poll.deregister(handle)
    }
}
