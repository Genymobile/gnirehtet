use mio::*;
use std::io;
use std::time::Duration;
use slab::Slab;

pub trait EventHandler {
    fn on_ready(&mut self, ready: Ready);
}

impl<F> EventHandler for F where F: FnMut(Ready) {
    fn on_ready(&mut self, ready: Ready) {
        self(ready);
    }
}

pub struct Selector {
    poll: Poll,
    pub events: Events,
    pub handlers: Slab<Box<EventHandler>, Token>,
}

impl Selector {
    pub fn new() -> io::Result<Selector> {
        Ok(Selector {
            poll: Poll::new()?,
            events: Events::with_capacity(1024),
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

    pub fn reregister<E>(&mut self, handle: &E, token: Token,
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

    pub fn select(&mut self, timeout: Option<Duration>) -> io::Result<usize> {
        self.poll.poll(&mut self.events, timeout)
    }
}
