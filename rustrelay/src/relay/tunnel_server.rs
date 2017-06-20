use std::cell::RefCell;
use std::net::{Ipv4Addr, SocketAddr};
use std::io;
use std::rc::Rc;
use mio::{Event, PollOpt, Ready};
use mio::tcp::TcpListener;

use super::client::Client;
use super::selector::Selector;

const TAG: &'static str = "TunnelServer";

pub struct TunnelServer {
    clients: Vec<Rc<RefCell<Client>>>,
    tcp_listener: TcpListener,
    next_client_id: u32,
}

impl TunnelServer {
    pub fn new(port: u16, selector: &mut Selector) -> io::Result<Rc<RefCell<Self>>> {
        let tcp_listener = TunnelServer::start_socket(port)?;
        let rc = Rc::new(RefCell::new(Self {
            clients: Vec::new(),
            tcp_listener: tcp_listener,
            next_client_id: 0,
        }));
        let rc_clone = rc.clone();
        let handler = move |selector: &mut Selector, ready| {
            let mut self_ref = rc_clone.borrow_mut();
            self_ref.on_ready(&rc_clone, selector, ready);
        };
        selector.register(&rc.borrow().tcp_listener, handler, Ready::readable(), PollOpt::edge())?;
        Ok(rc)
    }

    fn start_socket(port: u16) -> io::Result<TcpListener> {
        let localhost = Ipv4Addr::new(127, 0, 0, 1).into();
        let addr = SocketAddr::new(localhost, port);
        let server = TcpListener::bind(&addr)?;
        Ok(server)
    }

    fn accept_client(&mut self, self_rc: &Rc<RefCell<Self>>, selector: &mut Selector) -> io::Result<()> {
        let (stream, _) = self.tcp_listener.accept()?;
        let client_id = self.next_client_id;
        self.next_client_id += 1;
        let weak = Rc::downgrade(self_rc);
        let on_client_closed = Box::new(move |client: &Client| {
            if let Some(rc) = weak.upgrade() {
                let mut tunnel_server = rc.borrow_mut();
                tunnel_server.remove_client(client);
            } else {
                warn!(target: TAG, "on_client_closed called but no client available");
            }
        });
        let client = Client::new(client_id, selector, stream, on_client_closed)?;
        self.clients.push(client);
        info!(target: TAG, "Client #{} connected", client_id);
        Ok(())
    }

    fn remove_client(&mut self, client: &Client) {
        info!(target: TAG, "Client #{} disconnected", client.id());
        let index = self.clients.iter().position(|item| {
            // compare pointers to find the client to remove
            ptr_eq(client, &*item.borrow())
        }).expect("Trying to remove an unknown client");
        self.clients.swap_remove(index);
    }

    fn on_ready(&mut self, self_rc: &Rc<RefCell<Self>>, selector: &mut Selector, _: Event) {
        if let Err(err) = self.accept_client(self_rc, selector) {
            error!(target: TAG, "Cannot accept client: {}", err);
        }
    }

    pub fn clean_up(&mut self, selector: &mut Selector) {
        for client in &self.clients {
            client.borrow_mut().clean_expired_connections(selector);
        }
    }
}

// std::ptr::eq is too recent:
// <https://doc.rust-lang.org/std/ptr/fn.eq.html>
fn ptr_eq<T: ?Sized>(lhs: *const T, rhs: *const T) -> bool {
    lhs == rhs
}
