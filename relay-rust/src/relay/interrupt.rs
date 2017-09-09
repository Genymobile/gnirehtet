/// Reexecute the expression if the it returns an `Interrupted` error
macro_rules! retry_on_intr {
    ($e:expr) => {{
        let result;
        loop {
            match $e {
                Err(ref err) if err.kind() == io::ErrorKind::Interrupted => {
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
