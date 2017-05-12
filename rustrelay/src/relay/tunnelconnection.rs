use std::cell::RefCell;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::io;
use std::rc::Rc;
use mio::{Ready, PollOpt};
use mio::tcp::TcpListener;

use super::client::Client;
use super::selector::{EventHandler, Selector};

pub struct TunnelConnection {
    clients: Vec<Client>,
    _tcp_listener: TcpListener, // keep for RAII
}

impl TunnelConnection {
    pub fn new(port: u16, selector: &mut Selector) -> io::Result<Rc<RefCell<TunnelConnection>>> {
        let tcp_listener = TunnelConnection::start_socket(port)?;
        let rc = Rc::new(RefCell::new(TunnelConnection {
            clients: Vec::new(),
            _tcp_listener: tcp_listener,
        }));
        let rc_clone = rc.clone();
        let handler = Box::new(move |ready| {
            let mut self_cell = rc_clone.borrow_mut();
            println!("{:?}", ready);
            // TODO
        });
        selector.register(&rc.borrow()._tcp_listener, handler, Ready::readable(), PollOpt::edge())?;
        Ok(rc)
    }

    fn start_socket(port: u16) -> io::Result<TcpListener> {
        let localhost = IpAddr::V4(Ipv4Addr::new(127, 0, 0, 1));
        let addr = SocketAddr::new(localhost, port);
        let server = TcpListener::bind(&addr)?;
        Ok(server)
    }
}
