use mio::*;

struct LambdaPoll {
    poll: Poll,
    events: Events,
    token_provider: Box<Iterator<Item=Token>>,
}

impl LambdaPoll {
    fn new() -> LambdaPoll {
        LambdaPoll {
            poll: Poll::new().unwrap(),
            events: Events::with_capacity(1024),
            token_provider: Box::new((0..).map(|x| Token(x))),
        }
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
