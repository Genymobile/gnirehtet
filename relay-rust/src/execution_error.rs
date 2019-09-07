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

use std::error;
use std::fmt;
use std::io;
#[cfg(unix)]
use std::os::unix::process::ExitStatusExt;
use std::process::ExitStatus;

#[derive(Debug)]
pub enum CommandExecutionError {
    ProcessIo(ProcessIoError),
    ProcessStatus(ProcessStatusError),
    Io(io::Error),
}

#[derive(Debug)]
pub struct ProcessStatusError {
    cmd: Cmd,
    termination: Termination,
}

#[derive(Debug)]
pub struct ProcessIoError {
    cmd: Cmd,
    error: io::Error,
}

#[derive(Debug)]
pub struct Cmd {
    command: String,
    args: Vec<String>,
}

#[derive(Debug)]
pub enum Termination {
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

impl fmt::Display for Cmd {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{} {:?}", self.command, self.args)
    }
}

impl Cmd {
    pub fn new<S1, S2>(command: S1, args: Vec<S2>) -> Cmd
    where
        S1: Into<String>,
        S2: Into<String>,
    {
        Self {
            command: command.into(),
            args: args.into_iter().map(Into::into).collect::<Vec<_>>(),
        }
    }
}

impl ProcessStatusError {
    pub fn new(cmd: Cmd, status: ExitStatus) -> Self {
        Self {
            cmd,
            termination: Termination::from(status),
        }
    }
}

impl fmt::Display for ProcessStatusError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self.termination {
            Termination::Value(code) => {
                write!(f, "Command {} returned with value {}", self.cmd, code)
            }
            #[cfg(unix)]
            Termination::Signal(sig) => {
                write!(f, "Command {} terminated by signal {}", self.cmd, sig)
            }
        }
    }
}

impl error::Error for ProcessStatusError {
    fn description(&self) -> &str {
        "Execution terminated with failure"
    }

    fn cause(&self) -> Option<&dyn error::Error> {
        None
    }
}

impl ProcessIoError {
    pub fn new(cmd: Cmd, error: io::Error) -> Self {
        Self { cmd, error }
    }
}

impl fmt::Display for ProcessIoError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "Command {} failed: {}", self.cmd, self.error)
    }
}

impl error::Error for ProcessIoError {
    fn description(&self) -> &str {
        "Execution I/O failed"
    }

    fn cause(&self) -> Option<&dyn error::Error> {
        Some(&self.error)
    }
}

impl fmt::Display for CommandExecutionError {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match *self {
            CommandExecutionError::ProcessIo(ref err) => write!(f, "{}", err),
            CommandExecutionError::ProcessStatus(ref err) => write!(f, "{}", err),
            CommandExecutionError::Io(ref err) => write!(f, "IO error: {}", err),
        }
    }
}

impl error::Error for CommandExecutionError {
    fn description(&self) -> &str {
        match *self {
            CommandExecutionError::ProcessIo(ref err) => err.description(),
            CommandExecutionError::ProcessStatus(ref err) => err.description(),
            CommandExecutionError::Io(ref err) => err.description(),
        }
    }

    fn cause(&self) -> Option<&dyn error::Error> {
        match *self {
            CommandExecutionError::ProcessIo(ref err) => Some(err),
            CommandExecutionError::ProcessStatus(ref err) => Some(err),
            CommandExecutionError::Io(ref err) => Some(err),
        }
    }
}

impl From<ProcessIoError> for CommandExecutionError {
    fn from(error: ProcessIoError) -> Self {
        CommandExecutionError::ProcessIo(error)
    }
}

impl From<ProcessStatusError> for CommandExecutionError {
    fn from(error: ProcessStatusError) -> Self {
        CommandExecutionError::ProcessStatus(error)
    }
}

impl From<io::Error> for CommandExecutionError {
    fn from(error: io::Error) -> Self {
        CommandExecutionError::Io(error)
    }
}
