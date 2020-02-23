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
extern crate clap;
extern crate relaylib;

mod adb_monitor;
mod cli_args;
mod execution_error;
mod logger;

use crate::adb_monitor::AdbMonitor;
use crate::cli_args::Args;
use crate::execution_error::{Cmd, CommandExecutionError, ProcessIoError, ProcessStatusError};
use std::process::{self, exit};
use std::thread;
use std::time::Duration;

const TAG: &str = "Main";
const REQUIRED_APK_VERSION_CODE: &str = "7";

#[inline]
fn get_adb_path() -> String {
    if let Some(env_adb) = std::env::var_os("ADB") {
        env_adb.into_string().expect("invalid ADB value")
    } else {
        "adb".to_string()
    }
}

#[inline]
fn get_apk_path() -> String {
    if let Some(env_adb) = std::env::var_os("GNIREHTET_APK") {
        env_adb.into_string().expect("invalid GNIREHTET_APK value")
    } else {
        "gnirehtet.apk".to_string()
    }
}

fn cmd_install(args: &Args) -> Result<(), CommandExecutionError> {
    info!(target: TAG, "Installing gnirehtet client...");
    exec_adb(
        args.serial(),
        vec!["install".into(), "-r".into(), get_apk_path()],
    )
}

fn cmd_uninstall(args: &Args) -> Result<(), CommandExecutionError> {
    info!(target: TAG, "Uninstalling gnirehtet client...");
    exec_adb(args.serial(), vec!["uninstall", "com.genymobile.gnirehtet"])
}

fn cmd_reinstall(args: &Args) -> Result<(), CommandExecutionError> {
    cmd_uninstall(args).and(cmd_install(args))
}

fn cmd_run(args: Args) -> Result<(), CommandExecutionError> {
    // start in parallel so that the relay server is ready when the client connects
    async_start(args.clone());

    let ctrlc_args = args.clone();
    ctrlc::set_handler(move || {
        info!(target: TAG, "Interrupted");

        if let Err(err) = cmd_stop(&ctrlc_args) {
            error!(target: TAG, "Cannot stop client: {}", err);
        }

        exit(0);
    })
    .expect("Error setting Ctrl-C handler");

    cmd_relay(&args)
}

fn cmd_autorun(args: &Args) -> Result<(), CommandExecutionError> {
    let thread_args = args.clone();
    thread::spawn(move || {
        if let Err(err) = cmd_autostart(thread_args) {
            error!(target: TAG, "Cannot auto start clients: {}", err);
        }
    });

    cmd_relay(&args)
}

fn cmd_start(args: &Args) -> Result<(), CommandExecutionError> {
    if must_install_client(args.serial())? {
        cmd_install(args)?;
        // wait a bit after the app is installed so that intent actions are correctly
        // registered
        thread::sleep(Duration::from_millis(500));
    }

    info!(target: TAG, "Starting client...");
    cmd_tunnel(args)?;

    let mut adb_args = vec![
        "shell",
        "am",
        "start",
        "-a",
        "com.genymobile.gnirehtet.START",
        "-n",
        "com.genymobile.gnirehtet/.GnirehtetActivity",
    ];
    if let Some(dns_servers) = args.dns_servers() {
        adb_args.append(&mut vec!["--esa", "dnsServers", dns_servers]);
    }
    if let Some(routes) = args.routes() {
        adb_args.append(&mut vec!["--esa", "routes", routes]);
    }
    exec_adb(args.serial(), adb_args)
}

fn cmd_autostart(args: Args) -> Result<(), CommandExecutionError> {
    let mut adb_monitor = AdbMonitor::new(Box::new(move |serial: &str| {
        async_start(Args {
            serial: Some(serial.into()),
            ..args.clone()
        })
    }));
    adb_monitor.monitor();
    Ok(())
}

fn cmd_stop(args: &Args) -> Result<(), CommandExecutionError> {
    info!(target: TAG, "Stopping client...");
    exec_adb(
        args.serial(),
        vec![
            "shell",
            "am",
            "start",
            "-a",
            "com.genymobile.gnirehtet.STOP",
            "-n",
            "com.genymobile.gnirehtet/.GnirehtetActivity",
        ],
    )
}

fn cmd_tunnel(args: &Args) -> Result<(), CommandExecutionError> {
    exec_adb(
        args.serial(),
        vec![
            "reverse",
            "localabstract:gnirehtet",
            format!("tcp:{}", args.port()).as_str(),
        ],
    )
}

fn cmd_relay(args: &Args) -> Result<(), CommandExecutionError> {
    info!(
        target: TAG,
        "Starting relay server on port {}...",
        args.port()
    );
    relaylib::relay(args.port()).map_err(Into::into)
}

fn async_start(args: Args) {
    thread::spawn(move || {
        if let Err(err) = cmd_start(&args) {
            error!(target: TAG, "Cannot start client: {}", err);
        }
    });
}

fn create_adb_args<S: Into<String>>(serial: Option<&str>, args: Vec<S>) -> Vec<String> {
    let mut command = Vec::<String>::new();
    if let Some(serial) = serial {
        command.push("-s".into());
        command.push(serial.to_string());
    }
    for arg in args {
        command.push(arg.into());
    }
    command
}

fn exec_adb<S: Into<String>>(
    serial: Option<&str>,
    args: Vec<S>,
) -> Result<(), CommandExecutionError> {
    let adb_args = create_adb_args(serial, args);
    let adb = get_adb_path();
    debug!(target: TAG, "Execute: {:?} {:?}", adb, adb_args);
    match process::Command::new(&adb).args(&adb_args[..]).status() {
        Ok(exit_status) => {
            if exit_status.success() {
                Ok(())
            } else {
                let cmd = Cmd::new(adb, adb_args);
                Err(ProcessStatusError::new(cmd, exit_status).into())
            }
        }
        Err(err) => {
            let cmd = Cmd::new(adb, adb_args);
            Err(ProcessIoError::new(cmd, err).into())
        }
    }
}

fn must_install_client(serial: Option<&str>) -> Result<bool, CommandExecutionError> {
    info!(target: TAG, "Checking gnirehtet client...");
    let args = create_adb_args(
        serial,
        vec!["shell", "dumpsys", "package", "com.genymobile.gnirehtet"],
    );
    let adb = get_adb_path();
    debug!(target: TAG, "Execute: {:?} {:?}", adb, args);
    match process::Command::new(&adb).args(&args[..]).output() {
        Ok(output) => {
            if output.status.success() {
                // the "regex" crate makes the binary far bigger, so just parse the versionCode
                // manually
                let dumpsys = String::from_utf8_lossy(&output.stdout[..]);
                // read the versionCode of the installed package
                if let Some(index) = dumpsys.find("    versionCode=") {
                    let start = index + 16; // size of "    versionCode=\""
                    if let Some(end) = (&dumpsys[start..]).find(' ') {
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
                let cmd = Cmd::new(adb, args);
                Err(ProcessStatusError::new(cmd, output.status).into())
            }
        }
        Err(err) => {
            let cmd = Cmd::new(adb, args);
            Err(ProcessIoError::new(cmd, err).into())
        }
    }
}

fn main() {
    logger::init().unwrap();
    let matches = cli_args::build().get_matches();
    let res = match matches.subcommand() {
        ("install", Some(sub_matches)) => cmd_install(&Args::from(sub_matches)),
        ("uninstall", Some(sub_matches)) => cmd_uninstall(&Args::from(sub_matches)),
        ("reinstall", Some(sub_matches)) => cmd_reinstall(&Args::from(sub_matches)),
        ("run", Some(sub_matches)) => cmd_run(Args::from(sub_matches)),
        ("autorun", Some(sub_matches)) => cmd_autorun(&Args::from(sub_matches)),
        ("start", Some(sub_matches)) => cmd_start(&Args::from(sub_matches)),
        ("autostart", Some(sub_matches)) => cmd_autostart(Args::from(sub_matches)),
        ("stop", Some(sub_matches)) => cmd_stop(&Args::from(sub_matches)),
        ("tunnel", Some(sub_matches)) => cmd_tunnel(&Args::from(sub_matches)),
        ("relay", Some(sub_matches)) => cmd_relay(&Args::from(sub_matches)),
        ("", None) => unreachable!(), // impossible due to clap::AppSettings::SubcommandRequiredElseHelp
        _ => unreachable!(),
    };

    if let Err(e) = res {
        eprintln!("error: {}", e);
        exit(1);
    }
}
