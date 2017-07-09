use mio::*;
use std::cell::RefCell;
use std::io;
use std::rc::Rc;
use std::time::Duration;
use slab::Slab;

pub trait EventHandler {
    fn on_ready(&mut self, selector: &mut Selector, event: Event);
}

impl<F> EventHandler for F where F: FnMut(&mut Selector, Event) {
    fn on_ready(&mut self, selector: &mut Selector, event: Event) {
        self(selector, event);
    }
}

// for convenience
impl<T: EventHandler> EventHandler for Rc<RefCell<T>> {
    fn on_ready(&mut self, selector: &mut Selector, event: Event) {
        self.borrow_mut().on_ready(selector, event);
    }
}

pub struct Selector {
    poll: Poll,
    handlers: Slab<Rc<RefCell<Box<EventHandler>>>, Token>,
}

struct RunningState {
    token: Option<Token>,
    removed: bool,
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
        let token = self.handlers.insert(Rc::new(RefCell::new(handler)))
                        .map_err(|_| io::Error::new(io::ErrorKind::Other, "Cannot allocate slab slot"))?;
        self.poll.register(handle, token, interest, opts)?;
        Ok(token)
    }

    pub fn reregister<E>(&mut self, handle: &E, token: Token,
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
        let handler = self.handlers.get_mut(event.token()).expect("Token not found").clone();
        handler.borrow_mut().on_ready(self, event);
    }
}
