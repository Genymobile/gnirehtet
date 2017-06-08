use std::cell::RefCell;
use std::rc::Rc;

use super::client::Client;
use super::ipv4_packet::IPv4Packet;
use super::route::{Route, RouteKey};

const TAG: &'static str = "Router";

pub struct Router {
    client: Rc<RefCell<Client>>,
    routes: Vec<Route>,
}

impl Router {
    pub fn send_to_network(&mut self, ipv4_packet: &IPv4Packet) {
        if !ipv4_packet.is_valid() {
            warn!(target: TAG, "Dropping invalid packet");
        }
        // TODO
    }

    fn get_route_for(&mut self, ipv4_packet: &IPv4Packet) -> usize {
        let key = RouteKey::from_packet(ipv4_packet);
        match self.find_route_index(&key) {
            Some(index) => index,
            None => {
                let route = Route::new(&self.client, key, ipv4_packet);
                let index = self.routes.len();
                self.routes.push(route);
                index
            }
        }
    }

    fn find_route_index(&self, key: &RouteKey) -> Option<usize> {
        None
    }
}
