pub trait CloseListener<T> {
    fn on_closed(&self, target: &T);
}

impl<F, T> CloseListener<T> for F
where
    F: Fn(&T),
{
    fn on_closed(&self, target: &T) {
        self(target);
    }
}
