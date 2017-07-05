use mio::*;
use std::cell::RefCell;
use std::fmt::Debug;
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
    handlers: Slab<Box<EventHandler>, Token>,
    running_state: RunningState,
}

struct RunningState {
    token: Option<Token>,
    removed: bool,
}

impl RunningState {
    fn new() -> Self {
        Self {
            token: None,
            removed: false,
        }
    }

    fn running(&mut self, token: Token) {
        self.token = Some(token);
    }

    fn stopping(&mut self) {
        self.token = None;
    }

    fn is_running(&self, token: Token) -> bool {
        self.token.map_or(false, |current_token| current_token == token)
    }

    fn set_removed(&mut self, removed: bool) {
        self.removed = removed;
    }

    fn is_removed(&self) -> bool {
        self.removed
    }
}

impl Selector {
    pub fn new() -> io::Result<Self> {
        Ok(Self {
            poll: Poll::new()?,
            handlers: Slab::with_capacity(1024),
            running_state: RunningState::new(),
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
        if self.running_state.is_running(token) {
            self.running_state.set_removed(false);
        }
        self.poll.reregister(handle, token, interest, opts)
    }

    pub fn deregister<E>(&mut self, handle: &E, token: Token) -> io::Result<()>
            where E: Evented + ?Sized {
        if self.running_state.is_running(token) {
            // only mark as removed to not reinsert it after its execution
            self.running_state.set_removed(true);
        } else {
            self.handlers.remove(token).expect("Unknown token removed");
        }
        self.poll.deregister(handle)
    }

    pub fn poll(&mut self, events: &mut Events, timeout: Option<Duration>) -> io::Result<usize> {
        self.poll.poll(events, timeout)
    }

    pub fn run_handler(&mut self, event: Event) {
        let mut handler = self.handlers.remove(event.token()).expect("Token not found");

        self.running_state.running(event.token());

        handler.on_ready(self, event);

        if !self.running_state.is_removed() {
            if let Err(_) = self.handlers.insert(handler) {
                panic!("Cannot allocate slab slot");
            }
        }

        self.running_state.stopping();
    }
}
