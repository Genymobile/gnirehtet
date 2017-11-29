/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use mio::{Event, Evented, Events, Poll, PollOpt, Ready, Token};
use std::io;
use std::rc::Rc;
use std::time::Duration;
use slab::Slab;

const TAG: &'static str = "Selector";

pub trait EventHandler {
    fn on_ready(&self, selector: &mut Selector, event: Event);
}

impl<F> EventHandler for F
where
    F: Fn(&mut Selector, Event),
{
    fn on_ready(&self, selector: &mut Selector, event: Event) {
        self(selector, event);
    }
}

pub struct Selector {
    poll: Poll,
    handlers: Slab<Rc<EventHandler>>,
    // tokens to be removed after all the current poll events are executed
    tokens_to_remove: Vec<Token>,
}

impl Selector {
    pub fn new() -> io::Result<Self> {
        Ok(Self {
            poll: Poll::new()?,
            handlers: Slab::with_capacity(1024),
            tokens_to_remove: Vec::new(),
        })
    }

    pub fn register<E, H>(
        &mut self,
        handle: &E,
        handler: H,
        interest: Ready,
        opts: PollOpt,
    ) -> io::Result<Token>
    where
        E: Evented + ?Sized,
        H: EventHandler + 'static,
    {
        let token = Token(self.handlers.insert(Rc::new(handler)));
        if let Err(err) = self.poll.register(handle, token, interest, opts) {
            // remove the token we just added
            self.handlers.remove(token.0);
            Err(err)
        } else {
            Ok(token)
        }
    }

    pub fn reregister<E>(
        &mut self,
        handle: &E,
        token: Token,
        interest: Ready,
        opts: PollOpt,
    ) -> io::Result<()>
    where
        E: Evented + ?Sized,
    {
        self.poll.reregister(handle, token, interest, opts)
    }

    pub fn deregister<E>(&mut self, handle: &E, token: Token) -> io::Result<()>
    where
        E: Evented + ?Sized,
    {
        self.poll.deregister(handle)?;
        // remove them before next poll()
        self.tokens_to_remove.push(token);
        Ok(())
    }

    fn clean_removed_tokens(&mut self) {
        for &token in &self.tokens_to_remove {
            self.handlers.remove(token.0);
        }
        self.tokens_to_remove.clear();
    }

    pub fn poll(&mut self, events: &mut Events, timeout: Option<Duration>) -> io::Result<usize> {
        self.poll.poll(events, timeout)
    }

    pub fn run_handlers(&mut self, events: &Events) {
        for event in events {
            debug!(target: TAG, "event={:?}", event);
            let handler = self.handlers
                .get_mut(event.token().0)
                .expect("Token not found")
                .clone();
            handler.on_ready(self, event);
        }

        // remove the tokens marked as removed
        self.clean_removed_tokens();
    }
}
