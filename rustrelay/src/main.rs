extern crate chrono;
extern crate log;
extern crate relaylib;

mod logger;
use logger::SimpleLogger;

fn main() {
    SimpleLogger::init().unwrap();
    relaylib::relay();
}
