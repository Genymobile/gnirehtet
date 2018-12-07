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

use chrono::Local;
use log::*;
use mio::Events;
use std::cell::RefCell;
use std::cmp::max;
use std::io;
use std::rc::Rc;
use std::time::Duration;

use super::selector::Selector;
use super::tunnel_server::TunnelServer;
use super::udp_connection::IDLE_TIMEOUT_SECONDS;

const TAG: &'static str = "Relay";
const CLEANING_INTERVAL_SECONDS: i64 = 60;

pub struct Relay {
    port: u16,
}

impl Relay {
    pub fn new(port: u16) -> Self {
        Self { port: port }
    }

    pub fn run(&self) -> io::Result<()> {
        let mut selector = Selector::new().unwrap();
        let tunnel_server = TunnelServer::new(self.port, &mut selector)?;
        info!(target: TAG, "Relay server started");
        self.poll_loop(&mut selector, &tunnel_server)
    }

    fn poll_loop(
        &self,
        selector: &mut Selector,
        tunnel_server: &Rc<RefCell<TunnelServer>>,
    ) -> io::Result<()> {
        let mut events = Events::with_capacity(1024);
        // no connection may expire before the UDP idle timeout delay
        let mut next_cleaning_deadline = Local::now().timestamp() + IDLE_TIMEOUT_SECONDS as i64;
        loop {
            retry_on_intr!({
                let timeout_seconds = max(0, next_cleaning_deadline - Local::now().timestamp());
                let timeout = Some(Duration::new(timeout_seconds as u64, 0));
                selector.poll(&mut events, timeout)
            })?;

            let now = Local::now().timestamp();
            if now >= next_cleaning_deadline {
                tunnel_server.borrow_mut().clean_up(selector);
                next_cleaning_deadline = now + CLEANING_INTERVAL_SECONDS;
            } else if events.is_empty() {
                debug!(
                    target: TAG,
                    "Spurious wakeup: poll() returned without any event"
                );
                continue;
            }

            selector.run_handlers(&mut events);
        }
    }
}
