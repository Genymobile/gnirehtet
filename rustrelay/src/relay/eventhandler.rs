use mio::*;
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

pub trait EventHandler {
    fn on_ready(&self, ready: Ready);
}

impl<F> EventHandler for F where F: Fn(Ready) {
    fn on_ready(&self, ready: Ready) {
        self(ready);
    }
}
