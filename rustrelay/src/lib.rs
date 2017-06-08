extern crate byteorder;
#[macro_use]
extern crate log;
extern crate mio;
extern crate slab;

mod relay;
use relay::Relay;

pub fn relay() {
    const PORT: u16 = 31416;
    Relay::new(PORT).start();
}
