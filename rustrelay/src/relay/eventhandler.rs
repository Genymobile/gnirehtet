use mio::*;
use std::io;
use std::collections::HashMap;
use std::time::Duration;

// EventHandler

pub trait EventHandler {
    fn on_ready(&self, ready: Ready);
}

impl<F> EventHandler for F where F: Fn(Ready) {
    fn on_ready(&self, ready: Ready) {
        self(ready);
    }
}


// Selector

pub struct Selector {
    poll: Poll,
    pub events: Events,
    handler_token_manager: HandlerTokenManager,
}

impl Selector {
    pub fn new() -> io::Result<Selector> {
        Ok(Selector {
            poll: Poll::new()?,
            events: Events::with_capacity(1024),
            handler_token_manager: HandlerTokenManager::new(),
        })
    }

    pub fn register<E>(&mut self, handle: &E, handler: Box<EventHandler>,
                   interest: Ready, opts: PollOpt) -> io::Result<Token>
            where E: Evented + ?Sized {
        let token = self.handler_token_manager.register(handler);
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
        if !self.handler_token_manager.remove(token) {
            panic!("Unknown token removed");
        }
        self.poll.deregister(handle)
    }

    pub fn select(&mut self, timeout: Option<Duration>) -> io::Result<usize> {
        self.poll.poll(&mut self.events, timeout)
    }

    pub fn get_handler(&self, token: Token) -> Option<&Box<EventHandler>> {
        self.handler_token_manager.get(token)
    }
}

struct HandlerTokenManager {
    token_provider: Box<Iterator<Item=Token>>,
    handlers: HashMap<Token, Box<EventHandler>>,
}

impl HandlerTokenManager {
    fn new() -> HandlerTokenManager {
        HandlerTokenManager {
            token_provider: Box::new((0..).map(|x| Token(x))),
            handlers: HashMap::new(),
        }
    }

    fn register(&mut self, handler: Box<EventHandler>) -> Token {
        let token = self.token_provider.next().unwrap();
        self.handlers.insert(token, handler);
        token
    }

    fn get(&self, token: Token) -> Option<&Box<EventHandler>> {
        self.handlers.get(&token)
    }

    fn remove(&mut self, token: Token) -> bool {
        self.handlers.remove(&token).is_some()
    }
}
