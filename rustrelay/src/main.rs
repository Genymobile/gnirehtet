extern crate chrono;
extern crate ctrlc;
#[macro_use]
extern crate log;
extern crate relaylib;

mod cli_args;
mod logger;

use std::env;
use cli_args::CommandLineArguments;
use logger::SimpleLogger;
use std::error;
use std::io;
use std::fmt;
#[cfg(unix)]
use std::os::unix::process::ExitStatusExt;
use std::process::{self, ExitStatus};
use std::thread;
use std::time::Duration;

const TAG: &'static str = "Main";

const COMMANDS: &[&'static Command] = &[
    &InstallCommand,
    &UninstallCommand,
    &ReinstallCommand,
    &RtCommand,
    &StartCommand,
    &StopCommand,
    &RelayCommand,
];

trait Command {
    fn command(&self) -> &'static str;
    fn accepted_parameters(&self) -> u8;
    fn description(&self) -> &'static str;
    fn execute(&self, args: &CommandLineArguments) -> Result<(), CommandExecutionError>;
}

struct InstallCommand;
struct UninstallCommand;
struct ReinstallCommand;
struct RtCommand;
struct StartCommand;
struct StopCommand;
struct RelayCommand;

impl Command for InstallCommand {
    fn command(&self) -> &'static str {
        "install"
    }

    fn accepted_parameters(&self) -> u8 {
        cli_args::PARAM_SERIAL
    }

    fn description(&self) -> &'static str {
        "Install the client on the Android device and exit.\n\
        If several devices are connected via adb, then serial must be\n\
        specified."
    }

    fn execute(&self, args: &CommandLineArguments) -> Result<(), CommandExecutionError> {
        info!("Installing gnirehtet...");
        exec_adb(args.serial(), vec!["install", "-r", "gnirehtet.apk"])
    }
}

impl Command for UninstallCommand {
    fn command(&self) -> &'static str {
        "uninstall"
    }

    fn accepted_parameters(&self) -> u8 {
        cli_args::PARAM_SERIAL
    }

    fn description(&self) -> &'static str {
        "Uninstall the client from the Android device and exit.\n\
        If several devices are connected via adb, then serial must be\n\
        specified."
    }

    fn execute(&self, args: &CommandLineArguments) -> Result<(), CommandExecutionError> {
        info!("Uninstalling gnirehtet...");
        exec_adb(args.serial(), vec!["uninstall", "com.genymobile.gnirehtet"])
    }
}

impl Command for ReinstallCommand {
    fn command(&self) -> &'static str {
        "reinstall"
    }

    fn accepted_parameters(&self) -> u8 {
        cli_args::PARAM_SERIAL
    }

    fn description(&self) -> &'static str {
        "Uninstall then install."
    }

    fn execute(&self, args: &CommandLineArguments) -> Result<(), CommandExecutionError> {
        UninstallCommand.execute(args)?;
        InstallCommand.execute(args)?;
        Ok(())
    }
}

impl Command for RtCommand {
    fn command(&self) -> &'static str {
        "rt"
    }

    fn accepted_parameters(&self) -> u8 {
        cli_args::PARAM_SERIAL | cli_args::PARAM_DNS_SERVERS
    }

    fn description(&self) -> &'static str {
        "Enable reverse tethering for exactly one device:\n  \
          - install the client if necessary;\n  \
          - start the client;\n  \
          - start the relay server;\n  \
          - on Ctrl+C, stop both the relay server and the client."
    }

    fn execute(&self, args: &CommandLineArguments) -> Result<(), CommandExecutionError> {
        if !is_gnirehtet_installed(args.serial())? {
            InstallCommand.execute(args)?;
            // wait a bit after the app is installed so that intent actions are correctly
            // registered
            thread::sleep(Duration::from_millis(500));
        }

        {
            // start in parallel so that the relay server is ready when the client connects
            let serial = args.serial().cloned();
            let dns_servers = args.dns_servers().cloned();
            thread::spawn(move || if let Err(err) = start_gnirehtet(
                serial.as_ref(),
                dns_servers.as_ref(),
            )
            {
                eprintln!("Cannot start gnirehtet: {}", err);
            });
        }

        let serial = args.serial().cloned();
        ctrlc::set_handler(move || if let Err(err) = stop_gnirehtet(serial.as_ref()) {
            eprintln!("Cannot stop gnirehtet: {}", err);
        }).expect("Error setting Ctrl-C handler");

        match relay() {
            Err(CommandExecutionError::Io(ref err)) if err.kind() == io::ErrorKind::Interrupted => {
                warn!(target: TAG, "Relay server interrupted");
                // wait a bit so that the ctrlc handler is executed
                thread::sleep(Duration::from_secs(1));
                Ok(())
            }
            Err(ref err) => {
                panic!("Cannot relay: {}", err);
            }
            ok => ok,
        }
    }
}

impl Command for StartCommand {
    fn command(&self) -> &'static str {
        "start"
    }

    fn accepted_parameters(&self) -> u8 {
        cli_args::PARAM_SERIAL | cli_args::PARAM_DNS_SERVERS
    }

    fn description(&self) -> &'static str {
        "Start a client on the Android device and exit.\n\
        If several devices are connected via adb, then serial must be\n\
        specified.\n\
        If -d is given, then make the Android device use the specified\n\
        DNS server(s). Otherwise, use 8.8.8.8 (Google public DNS).\n\
        If the client is already started, then do nothing, and ignore\n\
        DNS servers parameter.\n\
        To use the host 'localhost' as DNS, use 10.0.2.2."
    }

    fn execute(&self, args: &CommandLineArguments) -> Result<(), CommandExecutionError> {
        start_gnirehtet(args.serial(), args.dns_servers())
    }
}

impl Command for StopCommand {
    fn command(&self) -> &'static str {
        "stop"
    }

    fn accepted_parameters(&self) -> u8 {
        cli_args::PARAM_SERIAL
    }

    fn description(&self) -> &'static str {
        "Stop the client on the Android device and exit.\n\
        If several devices are connected via adb, then serial must be\n\
        specified."
    }

    fn execute(&self, args: &CommandLineArguments) -> Result<(), CommandExecutionError> {
        stop_gnirehtet(args.serial())
    }
}

impl Command for RelayCommand {
    fn command(&self) -> &'static str {
        "relay"
    }

    fn accepted_parameters(&self) -> u8 {
        cli_args::PARAM_NONE
    }

    fn description(&self) -> &'static str {
        "Start the relay server in the current terminal."
    }

    fn execute(&self, _: &CommandLineArguments) -> Result<(), CommandExecutionError> {
        relay()
    }
}

#[derive(Debug)]
enum Termination {
    Value(i32),
    #[cfg(unix)]
    Signal(i32),
}

impl Termination {
    fn from(status: ExitStatus) -> Self {
        match status.code() {
            Some(code) => Termination::Value(code),
            #[cfg(unix)]
            None => Termination::Signal(status.signal().unwrap()),
            #[cfg(not(unix))]
            None => panic!("Unexpected signal termination on non-unix system"),
        }
    }
}

#[derive(Debug)]
struct CommandStatusError {
    command: Vec<String>,
    termination: Termination,
}

impl CommandStatusError {
    fn new(command: Vec<String>, status: ExitStatus) -> Self {
        Self {
            command: command,
            termination: Termination::from(status),
        }
    }
}

impl fmt::Display for CommandStatusError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self.termination {
            Termination::Value(code) => {
                write!(f, "Command {:?} returned with value {}", self.command, code)
            }
            #[cfg(unix)]
            Termination::Signal(sig) => {
                write!(f, "Command {:?} terminated by signal {}", self.command, sig)
            }
        }
    }
}

impl error::Error for CommandStatusError {
    fn description(&self) -> &str {
        "Execution terminated with failure"
    }

    fn cause(&self) -> Option<&error::Error> {
        None
    }
}

#[derive(Debug)]
enum CommandExecutionError {
    Io(io::Error),
    Status(CommandStatusError),
}

impl fmt::Display for CommandExecutionError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match *self {
            CommandExecutionError::Io(ref err) => write!(f, "IO error: {}", err),
            CommandExecutionError::Status(ref err) => write!(f, "Status in error: {}", err),
        }
    }
}

impl error::Error for CommandExecutionError {
    fn description(&self) -> &str {
        match *self {
            CommandExecutionError::Io(ref err) => err.description(),
            CommandExecutionError::Status(ref err) => err.description(),
        }
    }

    fn cause(&self) -> Option<&error::Error> {
        match *self {
            CommandExecutionError::Io(ref err) => Some(err),
            CommandExecutionError::Status(ref err) => Some(err),
        }
    }
}

impl From<io::Error> for CommandExecutionError {
    fn from(error: io::Error) -> Self {
        CommandExecutionError::Io(error)
    }
}

impl From<CommandStatusError> for CommandExecutionError {
    fn from(error: CommandStatusError) -> Self {
        CommandExecutionError::Status(error)
    }
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
    let mut adb_args = create_adb_args(serial, args);
    let exit_status = process::Command::new("adb").args(&adb_args[..]).status()?;
    if exit_status.success() {
        Ok(())
    } else {
        let mut cmd = vec!["adb".to_string()];
        cmd.append(&mut adb_args);
        Err(CommandStatusError::new(cmd, exit_status).into())
    }
}

fn is_gnirehtet_installed(serial: Option<&String>) -> Result<bool, CommandExecutionError> {
    let args = create_adb_args(
        serial,
        vec![
            "shell",
            "pm",
            "list",
            "packages",
            "com.genymobile.gnirehtet",
        ],
    );
    let output = process::Command::new("adb").args(&args[..]).output()?;
    // empty output when not found
    Ok(!output.stdout.is_empty())
}

fn start_gnirehtet(
    serial: Option<&String>,
    dns_servers: Option<&String>,
) -> Result<(), CommandExecutionError> {
    info!("Starting gnirehtet...");
    exec_adb(serial, vec!["reverse", "tcp:31416", "tcp:31416"])?;

    let mut adb_args = vec![
        "shell",
        "am",
        "startservice",
        "-a",
        "com.genymobile.gnirehtet.START",
    ];
    if let Some(dns_servers) = dns_servers {
        adb_args.append(&mut vec!["--esa", "dnsServers", dns_servers]);
    }
    exec_adb(serial, adb_args)
}

fn stop_gnirehtet(serial: Option<&String>) -> Result<(), CommandExecutionError> {
    info!("Stopping gnirehtet...");
    exec_adb(
        serial,
        vec![
            "shell",
            "am",
            "startservice",
            "-a",
            "com.genymobile.gnirehtet.STOP",
        ],
    )
}

fn relay() -> Result<(), CommandExecutionError> {
    relaylib::relay()?;
    Ok(())
}

fn print_usage() {
    let mut msg = "Syntax: gnirehtet (".to_string();
    msg.push_str(COMMANDS[0].command());
    for command in &COMMANDS[1..] {
        msg.push('|');
        msg.push_str(command.command());
    }
    msg.push_str(") ...\n");
    for &command in COMMANDS {
        msg.push('\n');
        append_command_usage(&mut msg, command);
    }
    eprint!("{}", msg);
}

fn append_command_usage(msg: &mut String, command: &Command) {
    msg.push_str("  gnirehtet ");
    msg.push_str(command.command());
    let accepted_parameters = command.accepted_parameters();
    if (accepted_parameters & cli_args::PARAM_SERIAL) != 0 {
        msg.push_str(" [serial]");
    }
    if (accepted_parameters & cli_args::PARAM_DNS_SERVERS) != 0 {
        msg.push_str(" [-d DNS[,DNS2,...]]");
    }
    msg.push('\n');
    for desc_line in command.description().split('\n') {
        msg.push_str("      ");
        msg.push_str(desc_line);
        msg.push('\n');
    }
}

fn print_command_usage(command: &Command) {
    let mut msg = String::new();
    append_command_usage(&mut msg, command);
    eprint!("{}", msg);
}

fn main() {
    SimpleLogger::init().unwrap();
    let mut args = env::args();
    // args.nth(1) will consume the two first arguments (the binary name and the command name)
    if let Some(command_name) = args.nth(1) {
        let command = COMMANDS.iter().find(
            |&&command| command.command() == command_name,
        );
        match command {
            Some(&command) => {
                // args now contains only the command parameters
                let arguments =
                    CommandLineArguments::parse(command.accepted_parameters(), args.collect());
                match arguments {
                    Ok(arguments) => {
                        if let Err(err) = command.execute(&arguments) {
                            eprintln!("[Error] Execution error: {}", err);
                        }
                    }
                    Err(err) => {
                        eprintln!("[Error] {}\n", err);
                        print_command_usage(command);
                    }
                }
            }
            None => {
                eprintln!("[Error] Unknown command: {}\n", command_name);
                print_usage();
            }
        }
    } else {
        print_usage();
    }
}
