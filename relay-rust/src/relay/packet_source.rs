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

use super::ipv4_packet::Ipv4Packet;
use super::selector::Selector;

/// Source that may produce packets.
///
/// When a `TcpConnection` sends a packet to the `Client` while its buffers are full, then it
/// fails. To recover, once some space becomes available, the `Client` must pull the available
/// packets.
///
/// This trait provides the abstraction of a packet source from which it can pull packets.
///
/// It is implemented by `TcpConnection`.
pub trait PacketSource {
    fn get(&mut self) -> Option<Ipv4Packet>;
    fn next(&mut self, selector: &mut Selector);
}
