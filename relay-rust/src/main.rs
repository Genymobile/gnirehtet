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

extern crate chrono;
extern crate ctrlc;
#[macro_use]
extern crate log;
extern crate relaylib;
extern crate structopt;
#[macro_use]
extern crate structopt_derive;

mod execution_error;
mod logger;

use std::process::{self, exit};
use std::thread;
use std::time::Duration;
use execution_error::{Cmd, CommandExecutionError, ProcessStatusError, ProcessIoError};
use logger::SimpleLogger;
use structopt::StructOpt;

const TAG: &'static str = "Main";
const REQUIRED_APK_VERSION_CODE: &'static str = "4";

const GNIREHTET_NAME: &'static str = "gnirehtet";
const GNIREHTET_VERSION: &'static str = "2.1";
const GNIREHTET_AUTHOR: &'static str = "Romain Vimont <rvimont@genymobile.com>";
const GNIREHTET_ABOUT: &'static str = "A reverse tethering tool for Android";

const PARAM_SERIAL_HELP: &'static str = "the device serial number";
const PARAM_SERIAL_LONG_HELP: &'static str = "The device serial number as it appears in the \
    output of \"adb devices\". Optional if only one device is available.";

const PARAM_DNS_SERVERS_HELP: &'static str = "list of custom DNS server(s)";
const PARAM_DNS_SERVERS_LONG_HELP: &'static str = "A comma-separated list of DNS server(s) to use \
    instead of 8.8.8.8 (Google public DNS).\nTo use the host 'localhost' as DNS, use 10.0.2.2.";

#[derive(StructOpt, Debug)]
#[structopt(name_raw = "GNIREHTET_NAME", author_raw = "GNIREHTET_AUTHOR",
            about_raw = "GNIREHTET_ABOUT", version_raw = "GNIREHTET_VERSION")]
enum Gnirehtet {
    #[structopt(name = "install", about = "Install the client on the Android device and exit")]
    Install {
        #[structopt(help_raw = "PARAM_SERIAL_HELP", long_help_raw = "PARAM_SERIAL_LONG_HELP")]
        serial: Option<String>,
    },

    #[structopt(name = "uninstall",
                about = "Uninstall the client from the Android device and exit")]
    Uninstall {
        #[structopt(help_raw = "PARAM_SERIAL_HELP", long_help_raw = "PARAM_SERIAL_LONG_HELP")]
        serial: Option<String>,
    },

    #[structopt(name = "reinstall", about = "Uninstall then install")]
    Reinstall {
        #[structopt(help_raw = "PARAM_SERIAL_HELP", long_help_raw = "PARAM_SERIAL_LONG_HELP")]
        serial: Option<String>,
    },

    #[structopt(name = "run", alias = "rt",
                about = "Enable reverse tethering for exactly one device",
                long_about = "Enable reverse tethering for exactly one device\n\n\
                 - install the client if necessary;
                 - start the client;
                 - start the relay server;
                 - on Ctrl+C, stop both the relay server and the client.")]
    Run {
        #[structopt(help_raw = "PARAM_SERIAL_HELP", long_help_raw = "PARAM_SERIAL_LONG_HELP")]
        serial: Option<String>,
        #[structopt(short = "d", long = "dns", help_raw = "PARAM_DNS_SERVERS_HELP",
                    long_help_raw = "PARAM_DNS_SERVERS_LONG_HELP")]
        dns_servers: Option<String>,
    },

    #[structopt(name = "start", about = "Start the client on the Android device and exit",
                long_about = "Start the client on the Android device and exit\n\n\
                If the client is already started, then do nothing (and ignore the parameters).")]
    Start {
        #[structopt(help_raw = "PARAM_SERIAL_HELP", long_help_raw = "PARAM_SERIAL_LONG_HELP")]
        serial: Option<String>,
        #[structopt(short = "d", long = "dns", help_raw = "PARAM_DNS_SERVERS_HELP",
                    long_help_raw = "PARAM_DNS_SERVERS_LONG_HELP")]
        dns_servers: Option<String>,
    },

    #[structopt(name = "stop", about = "Stop the client on the Android device and exit")]
    Stop {
        #[structopt(help_raw = "PARAM_SERIAL_HELP", long_help_raw = "PARAM_SERIAL_LONG_HELP")]
        serial: Option<String>,
    },

    #[structopt(name = "restart", about = "Stop then start")]
    Restart {
        #[structopt(help_raw = "PARAM_SERIAL_HELP", long_help_raw = "PARAM_SERIAL_LONG_HELP")]
        serial: Option<String>,
        #[structopt(short = "d", long = "dns", help_raw = "PARAM_DNS_SERVERS_HELP",
                    long_help_raw = "PARAM_DNS_SERVERS_LONG_HELP")]
        dns_servers: Option<String>,
    },

    #[structopt(name = "tunnel", about = "Set up the 'adb reverse' tunnel",
                long_about = "Set up the 'adb reverse' tunnel\n\n\
                If a device is unplugged then plugged back while gnirehtet is active, \
                resetting the tunnel is sufficient to get the connection back.")]
    Tunnel {
        #[structopt(help_raw = "PARAM_SERIAL_HELP", long_help_raw = "PARAM_SERIAL_LONG_HELP")]
        serial: Option<String>,
    },

    #[structopt(name = "relay", about = "Start the relay server in the current terminal")]
    Relay,
}

fn cmd_install(serial: Option<&String>) -> Result<(), CommandExecutionError> {
    info!(target: TAG, "Installing gnirehtet client...");
    exec_adb(serial, vec!["install", "-r", "gnirehtet.apk"])
}

fn cmd_uninstall(serial: Option<&String>) -> Result<(), CommandExecutionError> {
    info!(target: TAG, "Uninstalling gnirehtet client...");
    exec_adb(serial, vec!["uninstall", "com.genymobile.gnirehtet"])
}

fn cmd_reinstall(serial: Option<&String>) -> Result<(), CommandExecutionError> {
    cmd_uninstall(serial)?;
    cmd_install(serial)?;
    Ok(())
}

fn cmd_run(
    serial: Option<&String>,
    dns_servers: Option<&String>,
) -> Result<(), CommandExecutionError> {
    if must_install_client(serial)? {
        cmd_install(serial)?;
        // wait a bit after the app is installed so that intent actions are correctly
        // registered
        thread::sleep(Duration::from_millis(500));
    }

    {
        // start in parallel so that the relay server is ready when the client connects
        let start_serial = serial.cloned();
        let start_dns_servers = dns_servers.cloned();
        thread::spawn(move || if let Err(err) = cmd_start(
            start_serial.as_ref(),
            start_dns_servers.as_ref(),
        )
        {
            error!(target: TAG, "Cannot start client: {}", err);
        });
    }

    let ctrlc_serial = serial.cloned();
    ctrlc::set_handler(move || {
        info!(target: TAG, "Interrupted");

        if let Err(err) = cmd_stop(ctrlc_serial.as_ref()) {
            error!(target: TAG, "Cannot stop client: {}", err);
        }

        exit(0);
    }).expect("Error setting Ctrl-C handler");

    match cmd_relay() {
        Err(ref err) => {
            panic!("Cannot relay: {}", err);
        }
        _ => Ok(()),
    }
}

fn cmd_start(
    serial: Option<&String>,
    dns_servers: Option<&String>,
) -> Result<(), CommandExecutionError> {
    info!(target: TAG, "Starting client...");
    cmd_tunnel(serial)?;

    let mut adb_args = vec![
        "shell",
        "am",
        "broadcast",
        "-a",
        "com.genymobile.gnirehtet.START",
        "-n",
        "com.genymobile.gnirehtet/.GnirehtetControlReceiver",
    ];
    if let Some(dns_servers) = dns_servers {
        adb_args.append(&mut vec!["--esa", "dnsServers", dns_servers]);
    }
    exec_adb(serial, adb_args)
}

fn cmd_stop(serial: Option<&String>) -> Result<(), CommandExecutionError> {
    info!(target: TAG, "Stopping client...");
    exec_adb(
        serial,
        vec![
            "shell",
            "am",
            "broadcast",
            "-a",
            "com.genymobile.gnirehtet.STOP",
            "-n",
            "com.genymobile.gnirehtet/.GnirehtetControlReceiver",
        ],
    )
}

fn cmd_restart(
    serial: Option<&String>,
    dns_servers: Option<&String>,
) -> Result<(), CommandExecutionError> {
    cmd_stop(serial)?;
    cmd_start(serial, dns_servers)?;
    Ok(())
}

fn cmd_tunnel(serial: Option<&String>) -> Result<(), CommandExecutionError> {
    exec_adb(
        serial,
        vec!["reverse", "localabstract:gnirehtet", "tcp:31416"],
    )
}

fn cmd_relay() -> Result<(), CommandExecutionError> {
    info!(target: TAG, "Starting relay server...");
    relaylib::relay()?;
    Ok(())
}

fn create_adb_args<S: Into<String>>(serial: Option<&String>, args: Vec<S>) -> Vec<String> {
    let mut command = Vec::<String>::new();
    if let Some(serial) = serial {
        command.push("-s".into());
        command.push(serial.clone());
    }
    for arg in args {
        command.push(arg.into());
    }
    command
}

fn exec_adb<S: Into<String>>(
    serial: Option<&String>,
    args: Vec<S>,
) -> Result<(), CommandExecutionError> {
    let adb_args = create_adb_args(serial, args);
    debug!(target: TAG, "Execute: adb {:?}", adb_args);
    match process::Command::new("adb").args(&adb_args[..]).status() {
        Ok(exit_status) => {
            if exit_status.success() {
                Ok(())
            } else {
                let cmd = Cmd::new("adb", adb_args);
                Err(ProcessStatusError::new(cmd, exit_status).into())
            }
        }
        Err(err) => {
            let cmd = Cmd::new("adb", adb_args);
            Err(ProcessIoError::new(cmd, err).into())
        }
    }
}

fn must_install_client(serial: Option<&String>) -> Result<bool, CommandExecutionError> {
    info!(target: TAG, "Checking gnirehtet client...");
    let args = create_adb_args(
        serial,
        vec!["shell", "dumpsys", "package", "com.genymobile.gnirehtet"],
    );
    debug!(target: TAG, "Execute: adb {:?}", args);
    match process::Command::new("adb").args(&args[..]).output() {
        Ok(output) => {
            if output.status.success() {
                // the "regex" crate makes the binary far bigger, so just parse the versionCode
                // manually
                let dumpsys = String::from_utf8_lossy(&output.stdout[..]);
                // read the versionCode of the installed package
                if let Some(index) = dumpsys.find("    versionCode=") {
                    let start = index + 16; // size of "    versionCode=\""
                    if let Some(end) = (&dumpsys[start..]).find(" ") {
                        let installed_version_code = &dumpsys[start..start + end];
                        Ok(installed_version_code != REQUIRED_APK_VERSION_CODE)
                    } else {
                        // end of versionCode value not found
                        Ok(true)
                    }
                } else {
                    // versionCode not found
                    Ok(true)
                }
            } else {
                let cmd = Cmd::new("adb", args);
                Err(ProcessStatusError::new(cmd, output.status).into())
            }
        }
        Err(err) => {
            let cmd = Cmd::new("adb", args);
            Err(ProcessIoError::new(cmd, err).into())
        }
    }
}

fn main() {
    SimpleLogger::init().unwrap();
    let opt = Gnirehtet::from_args();
    let result = match opt {
        Gnirehtet::Install { serial } => cmd_install(serial.as_ref()),
        Gnirehtet::Uninstall { serial } => cmd_uninstall(serial.as_ref()),
        Gnirehtet::Reinstall { serial } => cmd_reinstall(serial.as_ref()),
        Gnirehtet::Run {
            serial,
            dns_servers,
        } => cmd_run(serial.as_ref(), dns_servers.as_ref()),
        Gnirehtet::Start {
            serial,
            dns_servers,
        } => cmd_start(serial.as_ref(), dns_servers.as_ref()),
        Gnirehtet::Stop { serial } => cmd_stop(serial.as_ref()),
        Gnirehtet::Restart {
            serial,
            dns_servers,
        } => cmd_restart(serial.as_ref(), dns_servers.as_ref()),
        Gnirehtet::Tunnel { serial } => cmd_tunnel(serial.as_ref()),
        Gnirehtet::Relay => cmd_relay(),
    };
    if let Err(err) = result {
        error!(target: TAG, "Execution error: {}", err);
        exit(2);
    }
}
