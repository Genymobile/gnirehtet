extern crate mio;

mod relay;
use relay::*;

pub fn relay() {
    const PORT: u16 = 31416;
    Relay::new(PORT).start();
}
