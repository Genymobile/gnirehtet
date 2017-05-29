/// Circular buffer to store datagrams (preserving their boundaries).
///
/// ```text
///     circularBufferLength
/// |<------------------------->| extra space for storing the last datagram in one block
/// +---------------------------+------+
/// |                           |      |
/// |[D4]     [  D1  ][ D2 ][  D3  ]   |
/// +---------------------------+------+
///     ^     ^
///  head     tail
/// ```
pub struct DatagramBuffer {
    buf: Box<[u8]>,
    head: usize,
    tail: usize,
    circular_buffer_length: usize,
}

const HEADER_LENGTH: usize = 2;
const MAX_DATAGRAM_LENGTH: usize = 1 << 16;
const MAX_BLOCK_LENGTH: usize = HEADER_LENGTH + MAX_DATAGRAM_LENGTH;
