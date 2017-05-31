use mio::*;
use std::cell::RefCell;
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
        let mut self_raw = self.borrow_mut();
        self_raw.on_ready(selector, event);
    }
}

pub struct Selector {
    poll: Poll,
    handlers: Slab<Rc<EventHandler>, Token>,
}

impl Selector {
    pub fn new() -> io::Result<Self> {
        Ok(Self {
            poll: Poll::new()?,
            handlers: Slab::with_capacity(1024),
        })
    }

    pub fn register<E, H>(&mut self, handle: &E, handler: H,
                   interest: Ready, opts: PollOpt) -> io::Result<Token>
            where E: Evented + ?Sized,
                  H: EventHandler + 'static {
        let token = self.handlers.insert(Rc::new(handler))
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

    pub fn poll(&mut self, events: &mut Events, timeout: Option<Duration>) -> io::Result<usize> {
        self.poll.poll(events, None)
    }

    pub fn run_handler(&mut self, event: Event) {
        let mut handler = self.handlers.get_mut(event.token()).unwrap().clone();
        handler.on_ready(self, event);
    }
}
