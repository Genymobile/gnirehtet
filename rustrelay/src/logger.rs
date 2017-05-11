use log::*;
use chrono::prelude::Local;

const THRESHOLD: LogLevelFilter = LogLevelFilter::Info;

pub struct SimpleLogger;

impl Log for SimpleLogger {
    fn enabled(&self, metadata: &LogMetadata) -> bool {
        metadata.level() <= LogLevel::Info
    }

    fn log(&self, record: &LogRecord) {
        if self.enabled(record.metadata()) {
            let date = Local::now();
            let formatted_date = date.format("%Y-%m-%d %H:%M:%S%.3f");
            println!("{} {} {}: {}", formatted_date, record.level(), record.target(), record.args());
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
