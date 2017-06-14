use std::cell::RefCell;
use std::io;
use std::rc::{Rc, Weak};
use log::LogLevel;

use super::client::Client;
use super::ipv4_packet::IPv4Packet;
use super::route::{Route, RouteKey};
use super::selector::Selector;

const TAG: &'static str = "Router";

pub struct Router {
    client: Weak<RefCell<Client>>,
    routes: Vec<Route>,
}

impl Router {
    pub fn new() -> Self {
        Self {
            client: Weak::new(),
            routes: Vec::new(),
        }
    }

    // expose client initialization after construction to break cyclic initialization dependencies
    pub fn set_client(&mut self, client: Weak<RefCell<Client>>) {
        self.client = client;
    }

    pub fn send_to_network(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) {
        if !ipv4_packet.is_valid() {
            warn!(target: TAG, "Dropping invalid packet");
            if log_enabled!(target: TAG, LogLevel::Trace) {
                // TODO log binary
            }
        } else {
            if let Ok(mut route) = self.route(selector, ipv4_packet) {
                route.send_to_network(selector, ipv4_packet);
            } else {
                error!(target: TAG, "Cannot create route, dropping packet");
            }
        }
    }

    fn route(&mut self, selector: &mut Selector, ipv4_packet: &IPv4Packet) -> io::Result<&mut Route> {
        let key = RouteKey::from_packet(ipv4_packet);
        let index = match self.find_route_index(&key) {
            Some(index) => index,
            None => {
                let weak = self.client.clone();
                let route = Route::new(selector, self.client.clone(), key, ipv4_packet)?;
                let index = self.routes.len();
                self.routes.push(route);
                index
            }
        };
        Ok(self.routes.get_mut(index).unwrap())
    }

    fn find_route_index(&self, key: &RouteKey) -> Option<usize> {
        self.routes.iter().position(|route| route.key() == key)
    }

    pub fn remove_route(&mut self, key: &RouteKey) {
        let index = self.find_route_index(key).expect("Removing an unknown route");
        self.routes.swap_remove(index);
    }

    pub fn clear(&mut self) {
        for route in &mut self.routes {
            route.disconnect();
        }
        // optimization of route.close() for all routes
        self.routes.clear();
    }
}
