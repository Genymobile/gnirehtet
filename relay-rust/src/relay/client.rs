/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use std::cell::RefCell;
use std::io::{self, Write};
use std::mem;
use std::net::Shutdown;
use std::rc::Rc;
use log::*;
use mio::net::TcpStream;
use mio::{Event, PollOpt, Ready, Token};

use super::binary;
use super::close_listener::CloseListener;
use super::ipv4_packet::{Ipv4Packet, MAX_PACKET_LENGTH};
use super::ipv4_packet_buffer::Ipv4PacketBuffer;
use super::packet_source::PacketSource;
use super::router::Router;
use super::selector::Selector;
use super::stream_buffer::StreamBuffer;

const TAG: &'static str = "Client";

pub struct Client {
    id: u32,
    stream: TcpStream,
    interests: Ready,
    token: Token,
    client_to_network: Ipv4PacketBuffer,
    network_to_client: StreamBuffer,
    router: Router,
    close_listener: Box<CloseListener<Client>>,
    closed: bool,
    pending_packet_sources: Vec<Rc<RefCell<PacketSource>>>,
    // number of remaining bytes of "id" to send to the client before relaying any data
    pending_id_bytes: usize,
}

/// Channel for connections to send back data immediately to the client
pub struct ClientChannel<'a> {
    network_to_client: &'a mut StreamBuffer,
    stream: &'a TcpStream,
    token: Token,
    interests: &'a mut Ready,
}

impl<'a> ClientChannel<'a> {
    fn new(
        network_to_client: &'a mut StreamBuffer,
        stream: &'a TcpStream,
        token: Token,
        interests: &'a mut Ready,
    ) -> Self {
        Self {
            network_to_client: network_to_client,
            stream: stream,
            token: token,
            interests: interests,
        }
    }

    // Functionally equivalent to Client::send_to_client(), except that it does not require to
    // mutably borrow the whole client.
    pub fn send_to_client(
        &mut self,
        selector: &mut Selector,
        ipv4_packet: &Ipv4Packet,
    ) -> io::Result<()> {
        if ipv4_packet.length() as usize <= self.network_to_client.remaining() {
            self.network_to_client.read_from(ipv4_packet.raw());
            self.update_interests(selector);
            Ok(())
        } else {
            warn!(target: TAG, "Client buffer full");
            Err(io::Error::new(
                io::ErrorKind::WouldBlock,
                "Client buffer full",
            ))
        }
    }

    fn update_interests(&mut self, selector: &mut Selector) {
        let ready = if self.network_to_client.is_empty() {
            Ready::readable()
        } else {
            Ready::readable() | Ready::writable()
        };
        if *self.interests != ready {
            // interests must be changed
            *self.interests = ready;
            selector
                .reregister(self.stream, self.token, ready, PollOpt::level())
                .expect("Cannot register on poll");
        }
    }
}

impl Client {
    pub fn new(
        id: u32,
        selector: &mut Selector,
        stream: TcpStream,
        close_listener: Box<CloseListener<Client>>,
    ) -> io::Result<Rc<RefCell<Self>>> {
        // on start, we are interested only in writing (we must first send the client id)
        let interests = Ready::writable();
        let rc = Rc::new(RefCell::new(Self {
            id: id,
            stream: stream,
            interests: interests,
            token: Token(0), // default value, will be set afterwards
            client_to_network: Ipv4PacketBuffer::new(),
            network_to_client: StreamBuffer::new(16 * MAX_PACKET_LENGTH),
            router: Router::new(),
            closed: false,
            close_listener: close_listener,
            pending_packet_sources: Vec::new(),
            pending_id_bytes: 4,
        }));

        {
            let mut self_ref = rc.borrow_mut();
            // set client as router owner
            self_ref.router.set_client(Rc::downgrade(&rc));

            let rc2 = rc.clone();
            // must anotate selector type: https://stackoverflow.com/a/44004103/1987178
            let handler =
                move |selector: &mut Selector, event| rc2.borrow_mut().on_ready(selector, event);
            let token = selector.register(
                &self_ref.stream,
                handler,
                interests,
                PollOpt::level(),
            )?;
            self_ref.token = token;
        }
        Ok(rc)
    }

    pub fn id(&self) -> u32 {
        return self.id;
    }

    pub fn router(&mut self) -> &mut Router {
        &mut self.router
    }

    pub fn channel(&mut self) -> ClientChannel {
        ClientChannel::new(
            &mut self.network_to_client,
            &self.stream,
            self.token,
            &mut self.interests,
        )
    }

    fn close(&mut self, selector: &mut Selector) {
        self.closed = true;
        selector.deregister(&self.stream, self.token).unwrap();
        // shutdown only (there is no close), the socket will be closed on drop
        if let Err(_) = self.stream.shutdown(Shutdown::Both) {
            warn!(target: TAG, "Cannot shutdown client socket");
        }
        self.router.clear(selector);
        self.close_listener.on_closed(self);
    }

    fn on_ready(&mut self, selector: &mut Selector, event: Event) {
        match self.process(selector, event) {
            Ok(_) => (),
            Err(ref err) if err.kind() == io::ErrorKind::WouldBlock => {
                debug!(target: TAG, "Spurious event, ignoring")
            }
            Err(_) => panic!("Unexpected unhandled error"),
        }
    }

    // return Err(err) with err.kind() == io::ErrorKind::WouldBlock on spurious event
    fn process(&mut self, selector: &mut Selector, event: Event) -> io::Result<()> {
        if !self.closed {
            let ready = event.readiness();
            if ready.is_writable() {
                self.process_send(selector)?;
            }
            if !self.closed && ready.is_readable() {
                self.process_receive(selector)?;
            }
            if !self.closed {
                self.update_interests(selector);
            }
        }
        Ok(())
    }

    // return Err(err) with err.kind() == io::ErrorKind::WouldBlock on spurious event
    fn process_send(&mut self, selector: &mut Selector) -> io::Result<()> {
        if self.must_send_id() {
            match self.send_id() {
                Ok(_) => {
                    if self.pending_id_bytes == 0 {
                        debug!(target: TAG, "Client id #{} sent to client", self.id);
                    }
                }
                Err(err) => {
                    if err.kind() == io::ErrorKind::WouldBlock {
                        // rethrow
                        return Err(err);
                    }
                    error!(target: TAG, "Cannot write client id #{}", self.id);
                    self.close(selector);
                }
            }
        } else {
            match self.write() {
                Ok(_) => self.process_pending(selector),
                Err(err) => {
                    error!(target: TAG, "Cannot write: [{:?}] {}", err.kind(), err);
                    self.close(selector);
                }
            }
        }
        Ok(())
    }

    // return Err(err) with err.kind() == io::ErrorKind::WouldBlock on spurious event
    fn process_receive(&mut self, selector: &mut Selector) -> io::Result<()> {
        match self.read() {
            Ok(true) => self.push_to_network(selector),
            Ok(false) => {
                debug!(target: TAG, "EOF reached");
                self.close(selector);
            }
            Err(err) => {
                if err.kind() == io::ErrorKind::WouldBlock {
                    // rethrow
                    return Err(err);
                }
                error!(target: TAG, "Cannot read: [{:?}] {}", err.kind(), err);
                self.close(selector);
            }
        }
        Ok(())
    }

    pub fn send_to_client(
        &mut self,
        selector: &mut Selector,
        ipv4_packet: &Ipv4Packet,
    ) -> io::Result<()> {
        if ipv4_packet.length() as usize <= self.network_to_client.remaining() {
            self.network_to_client.read_from(ipv4_packet.raw());
            self.update_interests(selector);
            Ok(())
        } else {
            warn!(target: TAG, "Client buffer full");
            Err(io::Error::new(
                io::ErrorKind::WouldBlock,
                "Client buffer full",
            ))
        }
    }

    pub fn register_pending_packet_source(&mut self, source: Rc<RefCell<PacketSource>>) {
        self.pending_packet_sources.push(source);
    }

    fn send_id(&mut self) -> io::Result<()> {
        assert!(self.must_send_id());
        let raw_id = binary::to_byte_array(self.id);
        let w = self.stream.write(&raw_id[4 - self.pending_id_bytes..])?;
        self.pending_id_bytes -= w;
        Ok(())
    }

    fn update_interests(&mut self, selector: &mut Selector) {
        self.channel().update_interests(selector);
    }

    fn read(&mut self) -> io::Result<(bool)> {
        self.client_to_network.read_from(&mut self.stream)
    }

    fn write(&mut self) -> io::Result<()> {
        self.network_to_client.write_to(&mut self.stream)?;
        Ok(())
    }

    fn push_to_network(&mut self, selector: &mut Selector) {
        while self.push_one_packet_to_network(selector) {
            self.client_to_network.next();
        }
    }

    fn push_one_packet_to_network(&mut self, selector: &mut Selector) -> bool {
        match self.client_to_network.as_ipv4_packet() {
            Some(ref packet) => {
                let mut client_channel = ClientChannel::new(
                    &mut self.network_to_client,
                    &self.stream,
                    self.token,
                    &mut self.interests,
                );
                self.router.send_to_network(
                    selector,
                    &mut client_channel,
                    packet,
                );
                true
            }
            None => false,
        }
    }

    fn process_pending(&mut self, selector: &mut Selector) {
        let mut vec = Vec::new();
        mem::swap(&mut self.pending_packet_sources, &mut vec);
        for pending in vec.into_iter() {
            let consumed = {
                let mut source = pending.borrow_mut();
                let result = {
                    let ipv4_packet = source.get().expect(
                        "Unexpected pending source with no packet",
                    );
                    self.send_to_client(selector, &ipv4_packet)
                };
                match result {
                    Ok(_) => {
                        source.next(selector);
                        true
                    }
                    Err(ref err) if err.kind() == io::ErrorKind::WouldBlock => false,
                    Err(_) => {
                        panic!("Cannot send packet to client for unknown reason");
                    }
                }
            };
            if !consumed {
                // keep it pending
                self.pending_packet_sources.push(pending);
            }
        }
    }

    pub fn clean_expired_connections(&mut self, selector: &mut Selector) {
        self.router.clean_expired_connections(selector);
    }

    fn must_send_id(&self) -> bool {
        self.pending_id_bytes > 0
    }
}
