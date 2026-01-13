use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use std::collections::VecDeque;
use std::sync::Mutex;
use lazy_static::lazy_static;

lazy_static! {
    static ref TERMINAL_BUFFER: Mutex<VecDeque<String>> = Mutex::new(VecDeque::with_capacity(10000));
}

const MAX_CAPACITY: usize = 10000;

#[no_mangle]
pub extern "system" fn Java_com_aura_terminal_engine_RustBridge_pushLine(
    mut env: JNIEnv,
    _class: JClass,
    line: JString,
) {
    let input: String = env.get_string(&line).expect("Couldn't get java string!").into();
    let mut buffer = TERMINAL_BUFFER.lock().unwrap();
    
    if buffer.len() >= MAX_CAPACITY {
        buffer.pop_front();
    }
    buffer.push_back(input);
}

#[no_mangle]
pub extern "system" fn Java_com_aura_terminal_engine_RustBridge_getLine<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    index: jint,
) -> jstring {
    let buffer = TERMINAL_BUFFER.lock().unwrap();
    let result = if index >= 0 && (index as usize) < buffer.len() {
         buffer.get(index as usize).cloned().unwrap_or_default()
    } else {
        String::new()
    };
    
    env.new_string(result).expect("Couldn't create java string!").into_raw()
}

#[no_mangle]
pub extern "system" fn Java_com_aura_terminal_engine_RustBridge_getSize(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    let buffer = TERMINAL_BUFFER.lock().unwrap();
    buffer.len() as jint
}

#[no_mangle]
pub extern "system" fn Java_com_aura_terminal_engine_RustBridge_clear(
    _env: JNIEnv,
    _class: JClass,
) {
    let mut buffer = TERMINAL_BUFFER.lock().unwrap();
    buffer.clear();
}
