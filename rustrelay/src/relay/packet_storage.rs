use std::cell::{Ref, RefCell};
use std::rc::Rc;

#[derive(Clone)]
pub struct PacketStorage(Rc<RefCell<Option<Box<[u8]>>>>);

impl PacketStorage {
    pub fn new() -> Self {
        PacketStorage(Rc::new(RefCell::new(None)))
    }

    pub fn set(&mut self, raw: &[u8]) {
        let data = raw.to_vec().into_boxed_slice();
        *self.0.borrow_mut() = Some(data);
    }

    pub fn get(&self) -> Ref<Option<Box<[u8]>>> {
        self.0.borrow()
    }

    pub fn has(&self) -> bool {
        self.get().is_some()
    }

    pub fn clear(&self) {
        *self.0.borrow_mut() = None;
    }

    pub fn share(&self) -> PacketStorage {
        PacketStorage(self.0.clone())
    }
}
