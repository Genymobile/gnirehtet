use byteorder::{BigEndian, ByteOrder};

pub fn to_byte_array(value: u32) -> [u8; 4] {
    let mut raw = [0u8; 4];
    BigEndian::write_u32(&mut raw, value);
    raw
}
