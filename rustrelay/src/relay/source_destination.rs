pub trait SourceDestination<T> {

    fn get_source(&self, &[u8]) -> T;
    fn get_destination(&self, &[u8]) -> T;
    fn set_source(&mut self, &mut [u8], source: T);
    fn set_destination(&mut self, &mut [u8], destination: T);

    fn switch_source_and_destination(&mut self, raw: &mut [u8]) {
        let source = self.get_source(raw);
        let destination = self.get_destination(raw);
        self.set_source(raw, destination);
        self.set_destination(raw, source);
    }
}
