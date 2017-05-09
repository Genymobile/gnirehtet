use mio::*;
use std::io;
use std::collections::HashMap;

pub struct HandlerTokenManager {
    token_provider: Box<Iterator<Item=Token>>,
    handlers: HashMap<Token, Box<EventHandler>>,
}

impl HandlerTokenManager {
    pub fn new() -> HandlerTokenManager {
        HandlerTokenManager {
            token_provider: Box::new((0..).map(|x| Token(x))),
            handlers: HashMap::new(),
        }
    }

    pub fn register(&mut self, handler: Box<EventHandler>) -> Token {
        let token = self.token_provider.next().unwrap();
        self.handlers.insert(token, handler);
        token
    }

    pub fn get(&self, token: &Token) -> Option<&Box<EventHandler>> {
        self.handlers.get(token)
    }

    pub fn unregister(&mut self, token: &Token) -> bool {
        self.handlers.remove(token).is_some()
    }
}

struct CallbackPoll {
    poll: Poll,
    events: Events,
    token_provider: Box<Iterator<Item=Token>>,
    handlers: HashMap<Token, Box<EventHandler>>
}

impl CallbackPoll {
    fn new() -> io::Result<CallbackPoll> {
        Ok(CallbackPoll {
            poll: try!(Poll::new()),
            events: Events::with_capacity(1024),
            token_provider: Box::new((0..).map(|x| Token(x))),
            handlers: HashMap::new(),
        })
    }

    fn register<E>(&self, handle: &E, handler: &EventHandler, interest: Ready, opts: PollOpt) -> io::Result<()>
            where E: Evented + ?Sized {
        //let token = self.token_provider.next();
        self.poll.register(handle, Token(0), interest, opts)
    }
}

pub trait EventHandler {
    fn on_ready(&self, ready: Ready);
}

impl<F> EventHandler for F where F: Fn(Ready) {
    fn on_ready(&self, ready: Ready) {
        self(ready);
    }
}
