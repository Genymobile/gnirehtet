use mio::*;
use mio::tcp::TcpListener;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::io;

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
        let _server = self.start_socket(&mut selector).expect("Cannot start server");
        self.poll_loop(&mut selector);
    }

    fn start_socket(&self, selector: &mut Selector) -> io::Result<TcpListener> {
        let localhost = IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1));
        let addr = SocketAddr::new(localhost, self.port);
        let server = TcpListener::bind(&addr)?;
        let handler = Box::new(|ready| {
            println!("Ready! {:?}", ready);
        });
        selector.register(&server, handler, Ready::readable(), PollOpt::edge())?;
        Ok(server)
    }

    fn poll_loop(&self, selector: &mut Selector) {
        loop {
            selector.select(None).expect("Cannot poll");

            for event in &selector.events {
                println!("event={:?}", event);
                let handler = selector.get_handler(event.token()).unwrap();
                handler.on_ready(event.readiness());
            }
        }
    }
}

