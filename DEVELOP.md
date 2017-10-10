# Gnirehtet for developers


## Getting started

### Requirements

You need the [Android SDK] (_Android Studio_) and the JDK 8 (`openjdk-8-jdk`).

You also need the [Rust] environment (currently 1.20) to build the Rust version:

```bash
wget https://sh.rustup.rs -O rustup-init
sh rustup-init --default-toolchain 1.20.0
```

[Android SDK]: https://developer.android.com/studio/index.html
[Rust]: https://www.rust-lang.org/


### Build

#### Everything

If `gradle` is installed on your computer:

    gradle build

Otherwise, you can call the [gradle wrapper]:

    ./gradlew build

This will build the Android application, the Java and Rust relay servers, both
in debug and release versions.

[gradle wrapper]: https://docs.gradle.org/current/userguide/gradle_wrapper.html


#### Specific parts

Several _gradle_ tasks are exposed in the root project. For instance:

 - `debugJava` and `releaseJava` build the Android application and the Java
   relay server;
 - `debugRust` and `releaseRust` build the Android application and the Rust
   relay server.

Even if the Rust build tasks are exposed through `gradle` (which wraps calls to
`cargo`), it is often more convenient to use `cargo` directly.

For instance, to build a release version of the Rust relay server:

    cd relay-rust
    cargo build --release

It will generate the binary in `target/release/gnirehtet`.


#### Cross-compile the Rust relay server from Linux to Windows

To build `gnirehtet.exe` from Linux, install the cross-compile toolchain (on
Debian):

    sudo apt install gcc-mingw-w64-x86-64
    rustup target add x86_64-pc-windows-gnu

Add the following lines to `~/.cargo/config`:

    [target.x86_64-pc-windows-gnu]
    linker = "x86_64-w64-mingw32-gcc"
    ar = "x86_64-w64-mingw32-gcc-ar"

Then build:

    cargo build --release --target=x86_64-pc-windows-gnu

It will generate `target/x86_64-pc-windows-gnu/release/gnirehtet.exe`.


### Android Studio

To import the project in _Android Studio_: File → Import…

From there, you can develop on the Android application and the Java relay
server. You can also execute any _gradle_ tasks, and run the tests with visual
results.


## Overview

The client registers itself as a [VPN], in order to intercept the whole device
network traffic.

It exchanges raw [IPv4 packets] as `byte[]` with the device:
 - it receives packets from the Android applications or system;
 - it must forge response packets.

The client (executed on the Android device) just maintains a TCP connection to
the relay server, and sends the raw packets to it.

This TCP connection is established over _adb_, after we started a reverse
port redirection:

    adb reverse localabstract:gnirehtet tcp:31416

This means that every connection initiated to `localhost:31416` from the device
will be redirected to the port `31416` on the computer, on which the relay
server is listening.

The relay server does all the hard work. It receives the IP packets from every
connected client and opens [standard sockets][berkeley] (which, of course, don't
require _root_) accordingly, then relays data in both directions. This requires
to translate packets between level 3 (on the device side) and level 5 (on the
network side) in the [OSI model].

In a sense, the relay server behaves like a [NAT] (more precisely a
[port-restricted cone NAT][portNAT]), in that it opens connections on behalf of
private peers. However, it differs from a standard NAT in the way it
communicates with the clients (the private peers), by using a very specific
(though simple) protocol, over a TCP connection.

[VPN]: https://developer.android.com/reference/android/net/VpnService.html
[IPv4 packets]: https://en.wikipedia.org/wiki/IPv4#Packet_structure
[OSI model]: https://en.wikipedia.org/wiki/OSI_model
[berkeley]: https://en.wikipedia.org/wiki/Berkeley_sockets
[NAT]: https://en.wikipedia.org/wiki/Network_address_translation
[portNAT]: https://en.wikipedia.org/wiki/Network_address_translation#Methods_of_translation


## Client

The client is an _Android_ project located in [`app/`](app/).

The [`VpnService`] is implemented by [`GnirehtetService`].

We control the application through broadcasts received by
[`GnirehtetControlReceiver`] (we cannot send intents to `GnirehtetService`
directly, read comments in [`GnirehtetControlReceiver`]).

Some configuration options may be passed as extra parameters, converted to a
[`VpnConfiguration`] instance. Currently, the user can configure the DNS servers
to use.

The very first time, Android requests to the user the permission to enable the
VPN. In that case, the API requires to call
[`startActivityForResult`], so we need an [`Activity`]: this is the purpose
of [`AuthorizationActivity`].

[`RelayTunnel`] manages one connection to the relay server.
[`PersistentRelayTunnel`] manages [`RelayTunnel`] instances to handle
reconnections, so that we can stop and start the relay while the client keeps
running.

To send response packets to the system, we must write one packet at a time to
the VPN interface. Since we receive packets from the relay server over a TCP
connection, we have to split writes at packet boundaries: this is the purpose
of [`IPPacketOutputStream`].

[`VpnService`]: https://developer.android.com/reference/android/net/VpnService.html
[`GnirehtetService`]: app/src/main/java/com/genymobile/gnirehtet/GnirehtetService.java
[`GnirehtetControlReceiver`]: app/src/main/java/com/genymobile/gnirehtet/GnirehtetControlReceiver.java
[`VpnConfiguration`]: app/src/main/java/com/genymobile/gnirehtet/VpnConfiguration.java
[`startActivityForResult`]: https://developer.android.com/reference/android/app/Activity.html#startActivityForResult%28android.content.Intent,%20int%29
[`Activity`]: https://developer.android.com/reference/android/app/Activity.html
[`AuthorizationActivity`]: app/src/main/java/com/genymobile/gnirehtet/AuthorizationActivity.java
[`RelayTunnel`]: app/src/main/java/com/genymobile/gnirehtet/RelayTunnel.java
[`PersistentRelayTunnel`]: app/src/main/java/com/genymobile/gnirehtet/PersistentRelayTunnel.java
[`IPPacketOutputStream`]: app/src/main/java/com/genymobile/gnirehtet/IPPacketOutputStream.java


## Relay server

The relay server comes in two flavors:
 - the **Java** version is a _Java 8_ project located in
   [`relay-java/`](relay-java/);
 - the **Rust** version is a _Rust_ project located in
   [`relay-rust/`](relay-rust/).

It is implemented using [asynchronous I/O] (through [Java NIO] and [Rust mio]).
As a consequence, it is essentially monothreaded, so there is no need for
synchronization to handle packets.

[asynchronous I/O]: https://en.wikipedia.org/wiki/Asynchronous_I/O
[Java NIO]: https://en.wikipedia.org/wiki/New_I/O_%28Java%29
[Rust mio]: https://docs.rs/mio/0.6.10/mio/


### Selector

There are different _socket channels_ registered to a unique _selector_:
 - one for the server socket, listening on port 31416;
 - one for each _client_, accepted by the server socket;
 - one for each _TCP connection_ to the network;
 - one for each _UDP connection_ to the network.

Initially, only the server socket _channel_ is registered.

In **Java**, the _channels_ ([`SelectableChannel`][nio/SelectableChannel]) are
registered to the _selector_ ([`Selector`][nio/Selector]) defined in
[`Relay`][java/Relay], with their [`SelectionHandler`][java/SelectionHandler] as
[attachment][nio/attachment] (for better decoupling). A [`Client`][java/Client]
is created for every accepted _client_.

[nio/Selector]: https://docs.oracle.com/javase/8/docs/api/java/nio/channels/Selector.html
[nio/SelectableChannel]: https://docs.oracle.com/javase/8/docs/api/java/nio/channels/SelectableChannel.html
[java/Relay]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/Relay.java
[java/SelectionHandler]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/SelectionHandler.java
[nio/attachment]: https://docs.oracle.com/javase/8/docs/api/java/nio/channels/SelectionKey.html#attachment--
[java/Client]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/Client.java

In **Rust**, our own [`Selector`][rust/selector] class wraps the
[`Poll`][mio/Poll] from _mio_ to expose an API accepting event handlers instead
of low-level [tokens][mio/Token]. The _selector_ instance is created in
[`Relay`][rust/relay]. The _channels_ are called _"handles"_ in _mio_; they are
simply the socket instances themselves ([`TcpListener`][mio/TcpListener],
[`TcpStream`][mio/TcpStream] and [`UdpSocket`][mio/UdpSocket]). A
[`Client`][rust/client] is created for every accepted _client_.

[mio/Poll]: https://docs.rs/mio/0.6.10/mio/struct.Poll.html
[mio/Token]: https://docs.rs/mio/0.6.10/mio/struct.Token.html
[mio/TcpListener]: https://docs.rs/mio/0.6.10/mio/net/struct.TcpListener.html
[mio/TcpStream]: https://docs.rs/mio/0.6.10/mio/net/struct.TcpStream.html
[mio/UdpSocket]: https://docs.rs/mio/0.6.10/mio/net/struct.UdpSocket.html
[rust/selector]: relay-rust/src/relay/selector.rs
[rust/relay]: relay-rust/src/relay/relay.rs
[rust/client]: relay-rust/src/relay/client.rs

![archi](assets/archi.png)

### Client

Each _client_ manages a TCP socket, used to transmit raw IP packets from and to
the _Gnirehtet_ Android client. Thus, these IP packets are encapsulated into TCP
(they are transmitted as the TCP payload).

When a client connects, the relay server assigns an integer id to it, which it
writes to the TCP socket. The client considers itself connected to the relay
server only once it has received this number. This allows to detect any
end-to-end connection issue immediately. For instance, a TCP _connect_ initiated
by a client succeeds whenever a port redirection is enabled (typically through
`adb reverse`), even if the relay server is not listening. In that case, the
first _read_ will fail.


### Packets

A class representing an _IPv4 packet_
([`IPv4Packet`][java/IPv4Packet] | [`Ipv4Packet`][rust/ipv4-packet]) provides a
structured view to read and write packet data, which is physically stored in the
buffers (the little squares on the schema). Since we handle one packet at a time
with asynchronous I/O, there is no need to copy or synchronize access to the
packets data: the packets just point to the buffer where they are stored.

[java/IPv4Packet]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/IPv4Packet.java
[rust/ipv4-packet]: relay-rust/src/relay/ipv4\_packet.rs

Each packet contains an instance of _IPv4 headers_ and _transport headers_
(which might be _TCP_ or _UDP_ headers).

In **Java**, this is straightforward: [`IPv4Header`][java/IPv4Header],
[`TCPHeader`][java/TCPHeader] and [`UDPHeader`][java/UDPHeader] just share a
slice of the raw packet buffer.

[java/IPv4Header]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/IPv4Header.java
[java/TCPHeader]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/TCPHeader.java
[java/UDPHeader]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/UDPHeader.java

In **Rust**, the borrowing rules prevent to share a mutable reference.
Therefore, _header data_ classes (`*HeaderData`) are used to store the fields,
and lifetime-bound views (`*Header<'a>` and `*HeaderMut<'a>`) reference both
the raw array and the _header data_:

 - [`ipv4_header`][rust/ipv4-header]:
   - data: `Ipv4HeaderData`
   - view: `Ipv4Header<'a>`
   - mutable view: `Ipv4HeaderMut<'a>`
 - [`tcp_header`][rust/tcp-header]:
   - data: `TcpHeaderData`
   - view: `TcpHeader<'a>`
   - mutable view: `TcpHeaderMut<'a>`
 - [`udp_header`][rust/udp-header]:
   - data: `UdpHeaderData`
   - view: `UdpHeader<'a>`
   - mutable view: `UdpHeaderMut<'a>`

In addition, we use [enums][rust-enums] for _transport headers_ to statically
dispatch calls to _UDP_ and _TCP_ header classes:

 - [`transport_header`][rust/transport-header]:
   - data: `TransportHeaderData`
   - view: `TransportHeader<'a>`
   - mutable view: `TransportHeaderMut<'a>`

[rust/ipv4-header]: relay-rust/src/relay/ipv4\_header.rs
[rust/tcp-header]: relay-rust/src/relay/tcp\_header.rs
[rust/udp-header]: relay-rust/src/relay/udp\_header.rs
[rust/transport-header]: relay-rust/src/relay/transport\_header.rs
[rust-enums]: https://doc.rust-lang.org/book/first-edition/enums.html


### Router

Each _client_ holds a _router_
([`Router`][java/Router] | [`Router`][rust/router]), responsible for sending the
packets to the right _connection_, identified by these 5 properties available in
the IP and transport headers:

 - protocol
 - source address
 - source port
 - destination address
 - destination port

These identifiers are stored in a _connection id_
([`ConnectionId`][java/ConnectionId] | [`ConnectionId`][rust/connection]),
used as a key to find or create the associated _connection_.

[java/Router]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/Router.java
[java/ConnectionId]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/ConnectionId.java
[rust/Router]: relay-rust/src/relay/router.rs
[rust/connection]: relay-rust/src/relay/connection.rs


### Connections

A _connection_ ([`Connection`][java/Connection] |
[`Connection`][rust/connection]) is either a _TCP connection_
([`TCPConnection`][java/TCPConnection] | [`TcpConnection`][rust/tcp-connection])
or a _UDP connection_ ([`UDPConnection`][java/UDPConnection] |
[`UdpConnection`][rust/udp-connection]) to the requested destination. It
registers its own _channel_ to the _selector_.

[java/Connection]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/Connection.java
[java/TCPConnection]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/TCPConnection.java
[java/UDPConnection]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/UDPConnection.java
[rust/connection]: relay-rust/src/relay/connection.rs
[rust/tcp-connection]: relay-rust/src/relay/tcp\_connection.rs
[rust/udp-connection]: relay-rust/src/relay/udp\_connection.rs

The connection is responsible for converting data from level 3 to level 5 for
device-to-network packets, and from level 5 to level 3 for network-to-device
packets. For _UDP connections_, it consists essentially in removing or
adding IP and transport headers. For _TCP connections_, however, it
requires to respond to the client according to the TCP protocol ([RFC 793]),
in such a way as to ensure a correct end-to-end communication.

[RFC 793]: https://tools.ietf.org/html/rfc793

A _packetizer_ ([`Packetizer`][java/Packetizer] |
[`Packetizer`][rust/packetizer]) converts from level 5 to level 3 by appending
correct IP and transport headers.

[java/Packetizer]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/Packetizer.java
[rust/packetizer]: relay-rust/src/relay/packetizer.rs


#### UDP connection

When the first packet for a specific UDP connection is received from the device,
a new `UdpConnection` is created. It keeps a copy of the IP and UDP headers
of this first packet, swapping the source and the destination, in order to use
them as headers for all response packets.

The relaying is simple for UDP: each packet received from one side must be sent
to the other side, without any splitting or merging (datagram boundaries must be
preserved for UDP).

Since UDP is not a connected protocol, a UDP connection is never "closed".
Therefore, the _selector_ wakes up once per minute (using a timeout) to clean
expired (in practice, unused for more than 2 minutes) UDP connections.


#### TCP connection

`TcpConnection` also keeps, as a reference, a copy of the IP and TCP headers
of the first packet received.

However, contrary to UDP, TCP must provide reliable delivery. In particular,
lost packets have to be retransmitted. Nonetheless, we can take advantage of the
two TCP we are proxifying, so that we can provide reliability by delegating the
retransmission mechanism to them. In fact, it is sufficient to guarantee that
**we cannot lose packets from network to device**.

Indeed, any packet written to a TCP channel is safe, since it will be managed by
the TCP implementation from the system. Losing a raw IP packet received from the
device is also safe: the device TCP implementation will follow the TCP protocol
to retransmit it. Therefore, **dropping packets from device to network does not
break the connection**.

On the other hand, once we retrieved a packet from a TCP channel from the
network, we are responsible for it. Would it be dropped, there would be no way
to recover the connection.

As far as I know, there are only two possible causes of packet loss for which we
are responsible:

 1. When **our buffers are full**, we won't resize them indefinitely, so we have to
drop packets. Typically, this may happen if the data from the network is
received at a higher rate than that they can be sent to the device.

 2. When **a raw packet is considered invalid** by the device, it is rejected.
This may happen for example if the checksum is invalid or if the TCP sequence
number is [out-of-the-window][flow control].

[flow control]: https://en.wikipedia.org/wiki/Transmission_Control_Protocol#Flow_control

Therefore, by [contraposition], if we guarantee that we never retrieve a packet
that we won't be able to store, and that we provide a valid checksum and respect
the client TCP window, then **we won't lose any packet without implementing any
retransmission mechanism**.

[contraposition]: https://en.wikipedia.org/wiki/Contraposition

To prevent retrieving a packet while our buffers are full, we indicate that we
are not interested in reading ([`interestOps`][nio/interestOps] |
[`interest`][mio/reregister]) the TCP channel when some pending data remain to
be written to the client buffer. Once some space becomes available, the client
then _pulls_ the available packets from the `TcpConnection`s, which are _packet
sources_ ([`PacketSource`][java/PacketSource] |
[`PacketSource`][rust/packet-source]).

[nio/interestOps]: https://developer.android.com/reference/java/nio/channels/SelectionKey.html#interestOps%28int%29
[mio/reregister]: https://docs.rs/mio/0.6.10/mio/struct.Poll.html#method.reregister
[java/PacketSource]: relay-java/src/main/java/com/genymobile/gnirehtet/relay/PacketSource.java
[rust/packet-source]: relay-rust/src/relay/packet\_source.rs


## Hack

For more details, go read the code!

If you find a bug, or have an awesome idea to implement, please discuss and
contribute ;-)
