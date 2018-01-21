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

pub const PARAM_NONE: u8 = 0;
pub const PARAM_SERIAL: u8 = 1;
pub const PARAM_DNS_SERVERS: u8 = 1 << 1;
pub const PARAM_ROUTES: u8 = 1 << 2;

pub struct CommandLineArguments {
    serial: Option<String>,
    dns_servers: Option<String>,
    routes: Option<String>,
}

impl CommandLineArguments {
    // simple String as errors is sufficient, we never need to inspect them
    pub fn parse<S: Into<String>>(accepted_parameters: u8, args: Vec<S>) -> Result<Self, String> {
        let mut serial = None;
        let mut dns_servers = None;
        let mut routes = None;

        let mut iter = args.into_iter();
        while let Some(arg) = iter.next() {
            let arg = arg.into();
            if (accepted_parameters & PARAM_DNS_SERVERS) != 0 && "-d" == arg {
                if dns_servers.is_some() {
                    return Err(String::from("DNS servers already set"));
                }
                if let Some(value) = iter.next() {
                    dns_servers = Some(value.into());
                } else {
                    return Err(String::from("Missing -d parameter"));
                }
            } else if (accepted_parameters & PARAM_ROUTES) != 0 && "-r" == arg {
                if routes.is_some() {
                    return Err(String::from("Routes already set"));
                }
                if let Some(value) = iter.next() {
                    routes = Some(value.into());
                } else {
                    return Err(String::from("Missing -r parameter"));
                }
            } else if (accepted_parameters & PARAM_SERIAL) != 0 && serial.is_none() {
                serial = Some(arg);
            } else {
                return Err(format!("Unexpected argument: \"{}\"", arg));
            }
        }
        Ok(Self {
            serial: serial,
            dns_servers: dns_servers,
            routes: routes,
        })
    }

    pub fn serial(&self) -> Option<&String> {
        self.serial.as_ref()
    }

    pub fn dns_servers(&self) -> Option<&String> {
        self.dns_servers.as_ref()
    }

    pub fn routes(&self) -> Option<&String> {
        self.routes.as_ref()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const ACCEPT_ALL: u8 = PARAM_SERIAL | PARAM_DNS_SERVERS | PARAM_ROUTES;

    #[test]
    fn test_no_args() {
        let args = CommandLineArguments::parse(ACCEPT_ALL, Vec::<&str>::new()).unwrap();
        assert!(args.serial.is_none());
        assert!(args.dns_servers.is_none());
    }

    #[test]
    fn test_serial_only() {
        let raw_args = vec!["myserial"];
        let args = CommandLineArguments::parse(ACCEPT_ALL, raw_args).unwrap();
        assert_eq!("myserial", args.serial.unwrap());
    }

    #[test]
    fn test_invalid_paramater() {
        let raw_args = vec!["myserial", "other"];
        assert!(CommandLineArguments::parse(ACCEPT_ALL, raw_args).is_err());
    }

    #[test]
    fn test_dns_servers_only() {
        let raw_args = vec!["-d", "8.8.8.8"];
        let args = CommandLineArguments::parse(ACCEPT_ALL, raw_args).unwrap();
        assert!(args.serial.is_none());
        assert_eq!("8.8.8.8", args.dns_servers.unwrap());
    }

    #[test]
    fn test_serial_and_dns_servers() {
        let raw_args = vec!["myserial", "-d", "8.8.8.8"];
        let args = CommandLineArguments::parse(ACCEPT_ALL, raw_args).unwrap();
        assert_eq!("myserial", args.serial.unwrap());
        assert_eq!("8.8.8.8", args.dns_servers.unwrap());
    }

    #[test]
    fn test_dns_servers_and_serial() {
        let raw_args = vec!["-d", "8.8.8.8", "myserial"];
        let args = CommandLineArguments::parse(ACCEPT_ALL, raw_args).unwrap();
        assert_eq!("myserial", args.serial.unwrap());
        assert_eq!("8.8.8.8", args.dns_servers.unwrap());
    }

    #[test]
    fn test_serial_with_no_dns_servers_parameter() {
        let raw_args = vec!["myserial", "-d"];
        assert!(CommandLineArguments::parse(ACCEPT_ALL, raw_args).is_err());
    }

    #[test]
    fn test_no_dns_servers_parameter() {
        let raw_args = vec!["-d"];
        assert!(CommandLineArguments::parse(ACCEPT_ALL, raw_args).is_err());
    }

    #[test]
    fn test_routes_parameter() {
        let raw_args = vec!["-r", "1.2.3.0/24"];
        let args = CommandLineArguments::parse(ACCEPT_ALL, raw_args).unwrap();
        assert_eq!("1.2.3.0/24", args.routes.unwrap());
    }

    #[test]
    fn test_no_routes_parameter() {
        let raw_args = vec!["-r"];
        assert!(CommandLineArguments::parse(ACCEPT_ALL, raw_args).is_err());
    }
}
