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

// std::ptr::eq is too recent:
// <https://doc.rust-lang.org/std/ptr/fn.eq.html>
pub fn ptr_eq<T: ?Sized>(lhs: *const T, rhs: *const T) -> bool {
    lhs == rhs
}
