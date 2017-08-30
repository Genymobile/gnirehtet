use std::sync::atomic::{AtomicBool, ATOMIC_BOOL_INIT, Ordering};

static INTERRUPTED_BY_USER: AtomicBool = ATOMIC_BOOL_INIT;

pub fn interrupted_by_user() -> bool {
    INTERRUPTED_BY_USER.load(Ordering::Acquire)
}

pub fn set_interrupted_by_user() {
    INTERRUPTED_BY_USER.store(true, Ordering::Release);
}

/// Reexecute the expression if the it returns an `Interrupted` error while no interruption was
/// requested by the user.
macro_rules! retry_on_intr {
    ($e:expr) => {{
        let result;
        loop {
            match $e {
                Err(ref err) if err.kind() == io::ErrorKind::Interrupted &&
                            !::relay::interrupt::interrupted_by_user() => {
                    continue;
                }
                x => {
                    result = x;
                    break;
                }
            }
        }
        result
    }}
}
