pub trait DeathListener<T> {
    fn on_death(&self, target: &T);
}

impl<F, T> DeathListener<T> for F where F: Fn(&T) {
    fn on_death(&self, target: &T) {
        self(target);
    }
}
