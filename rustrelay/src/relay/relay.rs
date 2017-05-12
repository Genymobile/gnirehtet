use mio::*;
use mio::tcp::TcpListener;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::io;

use super::client::Client;
use super::selector::Selector;
use super::tunnelconnection::TunnelConnection;

pub struct Relay {
    port: u16,
}

impl Relay {
    pub fn new(port: u16) -> Relay {
        Relay {
            port: port,
        }
    }

    pub fn start(&self) {
        let mut selector = Selector::new().unwrap();
        let _tunnel_connection = TunnelConnection::new(self.port, &mut selector);
        self.poll_loop(&mut selector);
    }

    fn poll_loop(&self, selector: &mut Selector) {
        loop {
            selector.select(None).expect("Cannot poll");

            for event in &selector.events {
                println!("event={:?}", event);
                let mut handler = selector.handlers.get_mut(event.token()).unwrap();
                handler.on_ready(event.readiness());
            }
        }
    }
}

