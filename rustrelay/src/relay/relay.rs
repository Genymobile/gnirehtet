use std::cell::RefCell;
use std::rc::Rc;
use std::time::Duration;
use chrono::Local;
use mio::Events;

use super::udp_connection::IDLE_TIMEOUT_SECONDS;
use super::selector::Selector;
use super::tunnel_server::TunnelServer;

const TAG: &'static str = "Relay";
const CLEANING_INTERVAL_SECONDS: i64 = 60;

pub struct Relay {
    port: u16,
}

impl Relay {
    pub fn new(port: u16) -> Self {
        Self { port: port }
    }

    pub fn start(&self) {
        info!(target: TAG, "Starting server...");
        let mut selector = Selector::new().unwrap();
        let tunnel_server =
            TunnelServer::new(self.port, &mut selector).expect("Cannot start tunnel server");
        self.poll_loop(&mut selector, &tunnel_server);
    }

    fn poll_loop(&self, selector: &mut Selector, tunnel_server: &Rc<RefCell<TunnelServer>>) {
        let mut events = Events::with_capacity(1024);
        // no connection may expire before the UDP idle timeout delay
        let mut next_cleaning_deadline = Local::now().timestamp() + IDLE_TIMEOUT_SECONDS as i64;
        loop {
            let timeout_seconds = next_cleaning_deadline - Local::now().timestamp();
            let timeout = if timeout_seconds > 0 {
                Some(Duration::new(timeout_seconds as u64, 0))
            } else {
                None
            };
            selector.poll(&mut events, timeout).expect("Cannot poll");

            let now = Local::now().timestamp();
            if now >= next_cleaning_deadline {
                tunnel_server.borrow_mut().clean_up(selector);
                next_cleaning_deadline = now + CLEANING_INTERVAL_SECONDS;
            } else if events.is_empty() {
                debug!(target: TAG, "Spurious wakeup: poll() returned without any event");
                continue;
            }

            selector.run_handlers(&mut events);
        }
    }
}
