use std::cell::RefCell;
use std::rc::{Rc, Weak};
use log::LogLevel;

use super::client::Client;
use super::ipv4_packet::IPv4Packet;
use super::route::{Route, RouteKey};

const TAG: &'static str = "Router";

pub struct Router {
    client: Weak<RefCell<Client>>,
    routes: Vec<Route>,
}

impl Router {
    pub fn new() -> Self {
        Self {
            client: Weak::new(), // initialized by set_client() to break cyclic initialization dependencies
            routes: Vec::new(),
        }
    }

    pub fn set_client(&mut self, client: Weak<RefCell<Client>>) {
        self.client = client;
    }

    pub fn send_to_network(&mut self, ipv4_packet: &IPv4Packet) {
        if !ipv4_packet.is_valid() {
            warn!(target: TAG, "Dropping invalid packet");
            if log_enabled!(target: TAG, LogLevel::Trace) {
                // TODO log binary
            }
        } else {
            let route = self.get_route(ipv4_packet);
            // route.send_to_network(ipv4_packet);
        }
    }

    fn get_route(&mut self, ipv4_packet: &IPv4Packet) -> &Route {
        let key = RouteKey::from_packet(ipv4_packet);
        let index = match self.find_route_index(&key) {
            Some(index) => index,
            None => {
                let route = Route::new(self.client.clone(), key, ipv4_packet);
                let index = self.routes.len();
                self.routes.push(route);
                index
            }
        };
        self.routes.get(index).unwrap()
    }

    fn find_route_index(&self, key: &RouteKey) -> Option<usize> {
        self.routes.iter().position(|route| route.get_key() == key)
    }

    pub fn clear(&mut self) {
        for route in &mut self.routes {
            route.disconnect();
        }
        // optimization of route.close() for all routes
        self.routes.clear();
    }
}
