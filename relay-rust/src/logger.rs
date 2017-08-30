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

use log::*;
use chrono::prelude::Local;

const THRESHOLD: LogLevelFilter = LogLevelFilter::Info;

pub struct SimpleLogger;

impl Log for SimpleLogger {
    fn enabled(&self, metadata: &LogMetadata) -> bool {
        metadata.level() <= THRESHOLD
    }

    fn log(&self, record: &LogRecord) {
        if self.enabled(record.metadata()) {
            let date = Local::now();
            let formatted_date = date.format("%Y-%m-%d %H:%M:%S%.3f");
            println!(
                "{} {} {}: {}",
                formatted_date,
                record.level(),
                record.target(),
                record.args()
            );
        }
    }
}

impl SimpleLogger {
    pub fn init() -> Result<(), SetLoggerError> {
        set_logger(|max_log_level| {
            max_log_level.set(THRESHOLD);
            Box::new(SimpleLogger)
        })
    }
}
