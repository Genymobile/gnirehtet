use std::fmt::Write;
use byteorder::{BigEndian, ByteOrder};

pub fn to_byte_array(value: u32) -> [u8; 4] {
    let mut raw = [0u8; 4];
    BigEndian::write_u32(&mut raw, value);
    raw
}

pub fn to_string(data: &[u8]) -> String {
    let mut s = String::new();
    for (i, &byte) in data.iter().enumerate() {
        if i % 16 == 0 {
            write!(&mut s, "\n").unwrap();
        } else if i % 8 == 0 {
            write!(&mut s, " ").unwrap();
        }
        write!(&mut s, "{:02X} ", byte).unwrap();
    }
    s
}
