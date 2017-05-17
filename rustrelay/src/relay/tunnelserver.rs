use std::cell::RefCell;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::io;
use std::rc::Rc;
use mio::{Ready, PollOpt};
use mio::tcp::TcpListener;

use super::client::Client;
use super::selector::{EventHandler, Selector};

pub struct TunnelServer {
    clients: Vec<Client>,
    tcp_listener: TcpListener,
}

impl TunnelServer {
    pub fn new(port: u16, selector: &mut Selector) -> io::Result<Rc<RefCell<TunnelServer>>> {
        let tcp_listener = TunnelServer::start_socket(port)?;
        let rc = Rc::new(RefCell::new(TunnelServer {
            clients: Vec::new(),
            tcp_listener: tcp_listener,
        }));
/*        let rc_clone = rc.clone();
        let handler = Box::new(move |selector: &mut Selector, ready| {
            let mut self_ref = rc_clone.borrow_mut();
            println!("{:?}", ready);
            // TODO
        });*/
        selector.register(&rc.borrow().tcp_listener, rc.clone(), Ready::readable(), PollOpt::edge())?;
        Ok(rc)
    }

    fn start_socket(port: u16) -> io::Result<TcpListener> {
        let localhost = IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1));
        let addr = SocketAddr::new(localhost, port);
        let server = TcpListener::bind(&addr)?;
        Ok(server)
    }

    fn accept_client(&mut self, selector: &mut Selector) {
        match self.tcp_listener.accept() {
            Ok((stream, addr)) => {
                let client = Client::new(selector, stream);
            },
            Err(err) => println!("Cannot accept client: {}", err)
        }
    }
}

impl EventHandler for TunnelServer {
    fn on_ready(&mut self, selector: &mut Selector, _: Ready) {
        self.accept_client(selector);
    }
}
