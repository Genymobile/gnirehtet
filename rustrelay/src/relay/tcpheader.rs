pub struct TCPHeader {
    source_port: u16,
    destination_port: u16,
    header_length: u8,
    sequence_number: u32,
    acknowledgment_number: u32,
    flags: u16,
    window: u32,
}
