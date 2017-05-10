use mio::*;
use mio::tcp::TcpListener;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};

use super::eventhandler::*;

pub struct Relay {
    port: u16,
}

impl Relay {
    pub fn new(port: u16) -> Relay {
        Relay {
            port: port
        }
    }

    pub fn start(&self) {
        println!("Starting on port {}", self.port);

        let mut selector = Selector::new().unwrap();

        let localhost = IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1));
        let addr = SocketAddr::new(localhost, self.port);

        // Setup the server socket
        let server = TcpListener::bind(&addr).expect("Cannot bind socket");

        // Start listening for incoming connections
        let handler = Box::new(|ready| {
            println!("Ready! {:?}", ready);
        });
        selector.register(&server, handler, Ready::readable(), PollOpt::edge()).unwrap();

        loop {
            selector.select(None).unwrap();

            for event in &selector.events {
                println!("event={:?}", event);
                let handler = selector.get_handler(event.token()).unwrap();
                handler.on_ready(event.readiness());
            }
        }
    }
}

