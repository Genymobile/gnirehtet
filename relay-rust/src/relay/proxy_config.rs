extern crate iprange;
extern crate ipnet;

use serde::Deserialize;

use iprange::IpRange;
use ipnet::Ipv4Net;

use std::fs;
use std::net::{SocketAddrV4, Ipv4Addr};

use lazy_static::lazy_static;
use once_cell::sync::OnceCell;

pub static CONF_PATH: OnceCell<String> = OnceCell::new();

#[derive(Debug, Deserialize, Clone)]
pub struct ProxyConfig {
    pub proxy_addr: SocketAddrV4,
    pub proxy_type: String,
    pub username: String,
    pub password: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct ProxyHostConfig {
    proxy: ProxyConfig,
    hosts: Vec<Ipv4Addr>
}

#[derive(Debug, Deserialize, Clone)]
pub struct GnirehtetProxyConfig {
    tcp_read_time_out: u32,
    tcp_connect_time_out: u32,
    #[serde(skip_deserializing)]
    lan_host_iprange: IpRange<Ipv4Net>,
    lan_host: Vec<String>,
    special_proxy_config: ProxyHostConfig,
    default_proxy_config: ProxyHostConfig
}

lazy_static! {
    pub static ref GNIREHTET_PROXY_CONFIG: GnirehtetProxyConfig = {
        let path: &str = CONF_PATH.get().unwrap();
        let toml_str = fs::read_to_string(path);
        match toml_str {
            Ok(val) => {
                let mut conf: GnirehtetProxyConfig = toml::from_str(&val).unwrap();
                let lan_host_range: IpRange<Ipv4Net> = conf.lan_host.iter().map(|s| s.parse().unwrap()).collect();
                conf.lan_host_iprange = lan_host_range;
                println!("Gnirehtet Proxy Config: \n{:#?}", conf);
                conf
            },
            Err(_) => {
                if !path.is_empty() {
                    panic!("invalid conf file, please check {}", path);
                } else {
                    panic!("invalid conf file, -c args is none");
                }
            }
        }
    };
}

pub fn get_proxy_for_addr(addr: SocketAddrV4) -> Option<ProxyConfig> {
    // in lan_host, don't use proxy
    if GNIREHTET_PROXY_CONFIG.lan_host_iprange.contains(addr.ip()) {
        return None
    }

    // in special proxy.hosts, return the specify proxy
    for (_, host) in GNIREHTET_PROXY_CONFIG.special_proxy_config.hosts.iter().enumerate() {
        if host == addr.ip() {
            return Some(ProxyConfig {
                proxy_addr: GNIREHTET_PROXY_CONFIG.special_proxy_config.proxy.proxy_addr,
                proxy_type: GNIREHTET_PROXY_CONFIG.special_proxy_config.proxy.proxy_type.clone(),
                username: GNIREHTET_PROXY_CONFIG.special_proxy_config.proxy.username.clone(),
                password: GNIREHTET_PROXY_CONFIG.special_proxy_config.proxy.password.clone()
            })
        }
    }

    // not in lan_host neither proxies.hosts, use default proxy
    Some(ProxyConfig {
        proxy_addr: GNIREHTET_PROXY_CONFIG.default_proxy_config.proxy.proxy_addr,
        proxy_type: GNIREHTET_PROXY_CONFIG.default_proxy_config.proxy.proxy_type.clone(),
        username: GNIREHTET_PROXY_CONFIG.default_proxy_config.proxy.username.clone(),
        password: GNIREHTET_PROXY_CONFIG.default_proxy_config.proxy.password.clone()
    })
}