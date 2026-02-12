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
pub const PARAM_PORT: u8 = 1 << 3;
pub const PARAM_PROXY_HOST: u8 = 1 << 4;
pub const PARAM_PROXY_PORT: u8 = 1 << 5;
pub const PARAM_PROXY_EXCLUSION_LIST: u8 = 1 << 6;

pub const DEFAULT_PORT: u16 = 31416;

pub struct CommandLineArguments {
    serial: Option<String>,
    dns_servers: Option<String>,
    routes: Option<String>,
    proxy_host: Option<String>,
    proxy_port: u16,
    proxy_exclusion_list: Option<String>,
    port: u16,
}

impl CommandLineArguments {
    // simple String as errors is sufficient, we never need to inspect them
    pub fn parse<S: Into<String>>(accepted_parameters: u8, args: Vec<S>) -> Result<Self, String> {
        let mut serial = None;
        let mut dns_servers = None;
        let mut routes = None;
        let mut proxy_host = None;
        let mut proxy_port = 0;
        let mut proxy_exclusion_list = None;
        let mut port = 0;

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
            } else if (accepted_parameters & PARAM_PORT) != 0 && "-p" == arg {
                if port != 0 {
                    return Err(String::from("Port already set"));
                }
                if let Some(value) = iter.next() {
                    port = parse_port(&value.into(), "-p")?;
                } else {
                    return Err(String::from("Missing -p parameter"));
                }
            } else if (accepted_parameters & PARAM_PROXY_HOST) != 0 && "--proxy-host" == arg {
                if proxy_host.is_some() {
                    return Err(String::from("Proxy host already set"));
                }
                if let Some(value) = iter.next() {
                    let value = value.into();
                    if value.trim().is_empty() {
                        return Err(String::from("Proxy host must not be empty"));
                    }
                    proxy_host = Some(value);
                } else {
                    return Err(String::from("Missing --proxy-host parameter"));
                }
            } else if (accepted_parameters & PARAM_PROXY_PORT) != 0 && "--proxy-port" == arg {
                if proxy_port != 0 {
                    return Err(String::from("Proxy port already set"));
                }
                if let Some(value) = iter.next() {
                    proxy_port = parse_port(&value.into(), "--proxy-port")?;
                } else {
                    return Err(String::from("Missing --proxy-port parameter"));
                }
            } else if (accepted_parameters & PARAM_PROXY_EXCLUSION_LIST) != 0
                && "--proxy-exclusion-list" == arg
            {
                if proxy_exclusion_list.is_some() {
                    return Err(String::from("Proxy exclusion list already set"));
                }
                if let Some(value) = iter.next() {
                    proxy_exclusion_list = Some(value.into());
                } else {
                    return Err(String::from("Missing --proxy-exclusion-list parameter"));
                }
            } else if (accepted_parameters & PARAM_SERIAL) != 0 && serial.is_none() {
                serial = Some(arg);
            } else {
                return Err(format!("Unexpected argument: \"{}\"", arg));
            }
        }

        if proxy_host.is_some() != (proxy_port != 0) {
            return Err(String::from(
                "Both --proxy-host and --proxy-port must be provided together",
            ));
        }
        if proxy_exclusion_list.is_some() && proxy_host.is_none() {
            return Err(String::from(
                "Proxy exclusion list requires --proxy-host and --proxy-port",
            ));
        }

        if port == 0 {
            port = DEFAULT_PORT;
        }
        Ok(Self {
            serial,
            dns_servers,
            routes,
            proxy_host,
            proxy_port,
            proxy_exclusion_list,
            port,
        })
    }

    pub fn serial(&self) -> Option<&str> {
        self.serial.as_deref()
    }

    pub fn dns_servers(&self) -> Option<&str> {
        self.dns_servers.as_deref()
    }

    pub fn routes(&self) -> Option<&str> {
        self.routes.as_deref()
    }

    pub fn proxy_host(&self) -> Option<&str> {
        self.proxy_host.as_deref()
    }

    pub fn proxy_port(&self) -> u16 {
        self.proxy_port
    }

    pub fn proxy_exclusion_list(&self) -> Option<&str> {
        self.proxy_exclusion_list.as_deref()
    }

    pub fn port(&self) -> u16 {
        self.port
    }
}

fn parse_port(value: &str, parameter_name: &str) -> Result<u16, String> {
    match value.parse::<u16>() {
        Ok(port) if port > 0 => Ok(port),
        _ => Err(format!("Invalid {} value: {}", parameter_name, value)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const ACCEPT_ALL: u8 = PARAM_SERIAL
        | PARAM_DNS_SERVERS
        | PARAM_ROUTES
        | PARAM_PROXY_HOST
        | PARAM_PROXY_PORT
        | PARAM_PROXY_EXCLUSION_LIST;

    #[test]
    fn test_no_args() {
        let args = CommandLineArguments::parse(ACCEPT_ALL, Vec::<&str>::new()).unwrap();
        assert!(args.serial.is_none());
        assert!(args.dns_servers.is_none());
        assert!(args.proxy_host.is_none());
        assert_eq!(0, args.proxy_port);
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

    #[test]
    fn test_proxy_host_and_port() {
        let raw_args = vec!["--proxy-host", "127.0.0.1", "--proxy-port", "8080"];
        let args = CommandLineArguments::parse(ACCEPT_ALL, raw_args).unwrap();
        assert_eq!("127.0.0.1", args.proxy_host.unwrap());
        assert_eq!(8080, args.proxy_port);
        assert!(args.proxy_exclusion_list.is_none());
    }

    #[test]
    fn test_proxy_exclusion_list() {
        let raw_args = vec![
            "--proxy-host",
            "127.0.0.1",
            "--proxy-port",
            "8080",
            "--proxy-exclusion-list",
            "localhost,*.example.org",
        ];
        let args = CommandLineArguments::parse(ACCEPT_ALL, raw_args).unwrap();
        assert_eq!(
            "localhost,*.example.org",
            args.proxy_exclusion_list.unwrap()
        );
    }

    #[test]
    fn test_proxy_host_without_proxy_port() {
        let raw_args = vec!["--proxy-host", "127.0.0.1"];
        assert!(CommandLineArguments::parse(ACCEPT_ALL, raw_args).is_err());
    }

    #[test]
    fn test_proxy_port_without_proxy_host() {
        let raw_args = vec!["--proxy-port", "8080"];
        assert!(CommandLineArguments::parse(ACCEPT_ALL, raw_args).is_err());
    }

    #[test]
    fn test_empty_proxy_host() {
        let raw_args = vec!["--proxy-host", "  ", "--proxy-port", "8080"];
        assert!(CommandLineArguments::parse(ACCEPT_ALL, raw_args).is_err());
    }

    #[test]
    fn test_proxy_exclusion_list_without_proxy() {
        let raw_args = vec!["--proxy-exclusion-list", "localhost"];
        assert!(CommandLineArguments::parse(ACCEPT_ALL, raw_args).is_err());
    }
}
