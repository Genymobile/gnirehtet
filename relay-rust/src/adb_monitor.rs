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

use std::net::{SocketAddr, TcpStream};
use std::io::{self, Write};
use std::process;
use std::str;
use std::thread;
use std::time::Duration;
use relaylib::byte_buffer::ByteBuffer;

const TAG: &'static str = "AdbMonitor";

pub trait AdbMonitorCallback {
    fn on_new_device_connected(&self, serial: &String);
}

impl<F> AdbMonitorCallback for F
where
    F: Fn(&String),
{
    fn on_new_device_connected(&self, serial: &String) {
        self(serial);
    }
}
pub struct AdbMonitor {
    callback: Box<AdbMonitorCallback>,
    buf: ByteBuffer,
}

impl AdbMonitor {
    const TRACK_DEVICES_REQUEST: &'static [u8] = b"0012host:track-devices";
    const BUFFER_SIZE: usize = 1024;
    const RETRY_DELAY_ADB_DAEMON_OK: u64 = 1000;
    const RETRY_DELAY_ADB_DAEMON_KO: u64 = 5000;

    pub fn new(callback: Box<AdbMonitorCallback>) -> Self {
        Self {
            callback: callback,
            buf: ByteBuffer::new(Self::BUFFER_SIZE),
        }
    }

    pub fn monitor(&mut self) -> io::Result<()> {
        loop {
            let adbd_addr = SocketAddr::from(([127, 0, 0, 1], 5037));
            let mut stream = TcpStream::connect(adbd_addr)?;
            if let Err(err) = self.track_devices(&mut stream) {
                error!(target: TAG, "Failed to monitor adb devices: {}", err);
                Self::repair_adb_daemon();
            }
        }
    }

    fn track_devices(&mut self, stream: &mut TcpStream) -> io::Result<()> {
        stream.write_all(Self::TRACK_DEVICES_REQUEST)?;
        if self.consume_okay(stream)? {
            loop {
                let packet = self.next_packet(stream)?;
                self.handle_packet(&packet);
            }
        }
        Ok(())
    }

    fn consume_okay(&mut self, stream: &mut TcpStream) -> io::Result<bool> {
        while self.buf.peek().len() < 4 {
            self.buf.read_from(stream)?;
        }
        let ok = b"OKAY" == &self.buf.peek()[0..4];
        self.buf.consume(4);
        Ok(ok)
    }

    fn next_packet(&mut self, stream: &mut TcpStream) -> io::Result<String> {
        loop {
            let packet_length = Self::available_packet_length(self.buf.peek())?;
            match packet_length {
                Some(len) => {
                    // retrieve the content and consume the packet
                    let data = Self::to_string(&self.buf.peek()[4..len])?;
                    self.buf.consume(len);
                    return Ok(data);
                }
                // need more data
                None => self.fill_buffer_from(stream)?,
            };
        }
    }

    fn fill_buffer_from(&mut self, stream: &mut TcpStream) -> io::Result<()> {
        match self.buf.read_from(stream) {
            Ok(false) => Err(io::Error::new(
                io::ErrorKind::UnexpectedEof,
                "ADB daemon closed the track-devices connexion",
            )),
            Ok(_) => Ok(()),
            Err(err) => Err(err),
        }
    }

    fn available_packet_length(input: &[u8]) -> io::Result<Option<usize>> {
        if input.len() < 4 {
            Ok(None)
        } else {
            // each packet contains 4 bytes representing the String length in hexa, followed by the
            // device serial, `\t', the state, '\n'
            // for example: "00180123456789abcdef\tdevice\n": 0018 indicates that the data is 0x18
            // (24) bytes length
            let len = Self::parse_length(&input[0..4])?;
            if len > Self::BUFFER_SIZE as u32 {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    format!("Packet size should not be that big: {}", len),
                ));
            }
            if input.len() - 4usize >= len as usize {
                Ok(Some(4usize + len as usize))
            } else {
                // not enough data
                Ok(None)
            }
        }
    }

    fn handle_packet(&self, serial: &String) {
        let mut split = serial.split_whitespace();
        if let Some(serial) = split.next() {
            if let Some(state) = split.next() {
                if "device" == state {
                    self.callback.on_new_device_connected(&serial.to_string());
                }
            }
        }
    }

    fn parse_length(data: &[u8]) -> io::Result<u32> {
        assert!(data.len() == 4, "Invalid length field value");
        let hexa = str::from_utf8(data).map_err(|err| {
            io::Error::new(
                io::ErrorKind::InvalidInput,
                format!("Cannot read hexa length as UTF-8 ({})", err),
            )
        })?;
        u32::from_str_radix(hexa, 0x10).map_err(|err| {
            io::Error::new(
                io::ErrorKind::InvalidInput,
                format!("Cannot parse hexa length ({})", err),
            )
        })
    }

    fn repair_adb_daemon() {
        if Self::start_adb_daemon() {
            thread::sleep(Duration::from_millis(Self::RETRY_DELAY_ADB_DAEMON_OK));
        } else {
            thread::sleep(Duration::from_millis(Self::RETRY_DELAY_ADB_DAEMON_KO));
        }
    }

    fn start_adb_daemon() -> bool {
        match process::Command::new("adb")
            .args(&["start-server"])
            .status() {
            Ok(exit_status) => {
                if exit_status.success() {
                    true
                } else {
                    error!(
                        target: TAG,
                        "Could not restart adb daemon (exited on error)"
                    );
                    false
                }
            }
            Err(err) => {
                error!(target: TAG, "Could not restart adb daemon: {}", err);
                false
            }
        }
    }

    fn to_string(data: &[u8]) -> io::Result<String> {
        let raw_content = data.to_vec();
        let content = String::from_utf8(raw_content);
        if let Ok(content) = content {
            Ok(content)
        } else {
            Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "Track-devices string is not valid UTF-8",
            ))
        }
    }
}
