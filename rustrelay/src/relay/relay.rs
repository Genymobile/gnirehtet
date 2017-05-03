use mio::*;
use mio::tcp::TcpListener;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};

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

        // Setup some tokens to allow us to identify which event is
        // for which socket.
        const SERVER: Token = Token(0);

        let localhost = IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1));
        let addr = SocketAddr::new(localhost, self.port);

        // Setup the server socket
        let server = TcpListener::bind(&addr).unwrap();

        // Create a poll instance
        let poll = Poll::new().unwrap();

        // Start listening for incoming connections
        poll.register(&server, SERVER, Ready::readable(),
                      PollOpt::edge()).unwrap();

        // Create storage for events
        let mut events = Events::with_capacity(1024);

        loop {
            poll.poll(&mut events, None).unwrap();

            for event in events.iter() {
                match event.token() {
                    SERVER => {
                        // Accept and drop the socket immediately, this will close
                        // the socket and notify the client of the EOF.
                        let _ = server.accept();
                        println!("client accepted");
                    }
                    _ => unreachable!(),
                }
            }
        }
    }
}

