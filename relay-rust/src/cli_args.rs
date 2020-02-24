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

use std::net::Ipv4Addr;

use clap::{
    crate_authors, crate_version, value_t_or_exit, App, AppSettings, Arg, ArgMatches, SubCommand,
};

pub const DEFAULT_PORT: &str = "31416";

fn valid_port(s: String) -> Result<(), String> {
    s.parse::<u16>()
        .map_err(|_| format!("{} is not a valid number between 1-65,535", s))
        .and_then(|port| {
            if port == 0 {
                Err(String::from(
                    "0 is not a valid port number (must be between 1 and 65,535 inclusive)",
                ))
            } else {
                Ok(())
            }
        })
}

fn valid_ip(s: String) -> Result<(), String> {
    for ip in s.split(",") {
        match ip.parse::<Ipv4Addr>() {
            Ok(_) => (),
            Err(_) => return Err(format!("{} is not a valid IPv4 Addres", ip)),
        }
    }

    Ok(())
}

fn valid_route(s: String) -> Result<(), String> {
    for route in s.split(",") {
        let mut r_split = route.split("/");
        if let Some(ip) = r_split.next() {
            match ip.parse::<Ipv4Addr>() {
                Ok(_) => (),
                Err(_) => return Err(format!("{} is not a valid IPv4 Addres", ip)),
            }
        } else {
            return Err(String::from(
                "each route must be in IP/CIDR format, such as 24.24.24.24/8",
            ));
        }
        if let Some(cidr) = r_split.next() {
            match cidr.parse::<u8>() {
                Ok(c) => {
                    if c > 32 {
                        return Err(format!(
                            "{} is not a valid CIDR (must be between 0 and 32 inclusive)",
                            c
                        ));
                    }
                }
                Err(_) => {
                    return Err(format!(
                        "{} is not a valid CIDR (must be between 0 and 32 inclusive)",
                        cidr
                    ))
                }
            }
        } else {
            return Err(String::from(
                "each route must be in IP/CIDR format, such as 24.24.24.24/8",
            ));
        }
    }

    Ok(())
}

pub(crate) fn build() -> App<'static, 'static> {
    let serial = Arg::with_name("serial")
        .help("The serial of the device (from `adb devices`) to install on")
        .long_help(
            "The serial of the device (from `adb devices`) to install \
                    on. If multiple devices are connected, then this option \
                    must be used. If only one device is connectd, the default \
                    is to simply connect to it.",
        )
        .takes_value(true);
    // @TODO validator for IP/CIDR
    let route = Arg::with_name("routes")
        .help("Only reverse tether the specified routes")
        .long_help(
            "Only reverse tether the specified routes, \
            whereas the default is to reverse all traffic. \
            Multiple routes may be specified by delimiting \
            with a comma (',')",
        )
        .takes_value(true)
        .short("r")
        .long("routes")
        .alias("route")
        .use_delimiter(false)
        .validator(valid_route)
        .default_value("0.0.0.0/0");
    let dns = Arg::with_name("dns-servers")
        .help("Make the device use the specified DNS server(s)")
        .long_help(
            "Make the device use the specified DNS server(s). \
             The default uses Google public DNS. Multiple DNS \
             servers may be specified by delimiting with a comma \
             (',')",
        )
        .takes_value(true)
        .use_delimiter(false)
        .short("d")
        .long("dns-servers")
        .alias("dns-server")
        .alias("dns")
        .validator(valid_ip)
        .default_value("8.8.8.8");
    let port = Arg::with_name("port")
        .help("Make the relay server listen on the specified port")
        .takes_value(true)
        .short("p")
        .long("port")
        .default_value(DEFAULT_PORT)
        .validator(valid_port);

    App::new("gnirehtet")
        .author(crate_authors!())
        .version(crate_version!())
        .setting(AppSettings::VersionlessSubcommands)
        .setting(AppSettings::SubcommandRequiredElseHelp)
        .set_term_width(80)
        .subcommand(
            SubCommand::with_name("run")
                .alias("rt")
                .about("Enable reverse tethering for a specific device")
                .long_about(
                    "Enable reverse tethering for a specific device:{n}    \
                - install the client if necessary{n}    \
                - start the client{n}    \
                - start the relay server{n}    \
                - on Ctrl+C, stop both the relay server and the client.",
                )
                .arg(&serial)
                .arg(&dns)
                .arg(&port)
                .arg(&route),
        )
        .subcommand(
            SubCommand::with_name("install")
                .about("Install the client on the device and exit")
                .arg(&serial),
        )
        .subcommand(
            SubCommand::with_name("uninstall")
                .about("Uninstall the client from the Android device and exit")
                .arg(&serial),
        )
        .subcommand(
            SubCommand::with_name("reinstall")
                .about("Uninstall then reinstall client on the device")
                .arg(&serial),
        )
        .subcommand(
            SubCommand::with_name("autorun")
                .about("Enable reverse tethering for all devices")
                .long_about(
                    "Enable reverse tethering for all devices:{n}    \
                - monitor for connected devices and autostart clients{n}    \
                - start the relay server",
                )
                .arg(&dns)
                .arg(&port)
                .arg(&route),
        )
        .subcommand(
            SubCommand::with_name("start")
                .about(
                    "Start a client on the device and exit (10.0.2.2 is mapped to the host 'localhost')",
                )
                .arg(&serial)
                .arg(&dns)
                .arg(&port)
                .arg(&route),
        )
        .subcommand(
            SubCommand::with_name("autostart")
                .about("Listen for device connections and start a client on every detected device")
                .arg(&dns)
                .arg(&port)
                .arg(&route),
        )
        .subcommand(
            SubCommand::with_name("stop")
                .about("Stop the client on the device and exit")
                .arg(&serial),
        )
        .subcommand(
            SubCommand::with_name("restart")
                .about("Stop client (if running) and then restart on a specific device")
                .arg(&serial)
                .arg(&dns)
                .arg(&port)
                .arg(&route),
        )
        .subcommand(
            SubCommand::with_name("tunnel")
                .about("Set up the 'adb reverse' tunnel")
                .long_about("Set up the 'adb reverse' tunnel.{n}    \
                Note: If a device is unplugged then plugged back while gnirehtet is{n}\
                active, resetting the tunnel is sufficient to get the{n}\
                connection back.",
                )
                .arg(&serial)
                .arg(&port),
        )
        .subcommand(
            SubCommand::with_name("relay")
                .about("Start the relay server in the current terminal.")
                .arg(&port),
        )
}

#[derive(Clone, Default)]
pub(crate) struct Args {
    pub(crate) serial: Option<String>,
    pub(crate) dns_servers: Option<String>,
    pub(crate) routes: Option<String>,
    pub(crate) port: u16,
}

impl Args {
    pub(crate) fn serial(&self) -> Option<&str> {
        self.serial.as_deref()
    }
    pub(crate) fn routes(&self) -> Option<&str> {
        self.routes.as_deref()
    }
    pub(crate) fn dns_servers(&self) -> Option<&str> {
        self.dns_servers.as_deref()
    }
    pub(crate) fn port(&self) -> u16 {
        self.port
    }
}

impl<'a, 'b> From<&'a ArgMatches<'b>> for Args {
    fn from(m: &'a ArgMatches<'b>) -> Self {
        Args {
            serial: m.value_of("serial").map(ToOwned::to_owned),
            dns_servers: m.value_of("dns-servers").map(ToOwned::to_owned),
            routes: m.value_of("routes").map(ToOwned::to_owned),
            port: value_t_or_exit!(m.value_of("port"), u16),
        }
    }
}
