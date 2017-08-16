extern crate byteorder;
extern crate chrono;
#[macro_use]
extern crate log;
extern crate mio;
extern crate rand;
extern crate slab;

mod relay;

use std::io;
use relay::Relay;

pub fn relay() -> io::Result<()> {
    const PORT: u16 = 31416;
    Relay::new(PORT).run()
}
