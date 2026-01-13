use jni::objects::{JClass, JByteArray};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use std::collections::VecDeque;
use std::sync::Mutex;
use lazy_static::lazy_static;
use vte::{Parser, Perform};

lazy_static! {
    static ref TERMINAL_BUFFER: Mutex<VecDeque<String>> = Mutex::new(VecDeque::with_capacity(10000));
    static ref PARSER: Mutex<Parser> = Mutex::new(Parser::new());
}

const MAX_CAPACITY: usize = 10000;

struct LogInterpreter {
    buffer: std::sync::MutexGuard<'static, VecDeque<String>>,
    cursor_col: usize,
}

impl LogInterpreter {
    fn new(buffer: std::sync::MutexGuard<'static, VecDeque<String>>) -> Self {
        Self {
            buffer,
            cursor_col: 0,
        }
    }
}

impl Perform for LogInterpreter {
    fn print(&mut self, c: char) {
        // Ensure there is a line to append to
        if self.buffer.is_empty() {
            self.buffer.push_back(String::new());
        }
        
        if let Some(line) = self.buffer.back_mut() {
            let current_len = line.chars().count();

            if self.cursor_col < current_len {
                // Determine byte start index of the character at cursor_col
                let mut char_indices = line.char_indices();
                if let Some((byte_start, _)) = char_indices.nth(self.cursor_col) {
                     // We need to replace the char at this position.
                     // Since replacement might change byte length (e.g. 'a' -> '€'), 
                     // easiest SAFE way is to reconstruct via Vec<char> as requested.
                     let mut chars: Vec<char> = line.chars().collect();
                     chars[self.cursor_col] = c;
                     *line = chars.into_iter().collect();
                }
            } else {
                // If cursor is beyond current len (padding needed) or at end
                while self.cursor_col > current_len {
                     line.push(' ');
                     // Check len again if we want to be strict, but for now assuming monotonic fill
                     // actually, let's just push ' ' until we reach cursor
                     if line.chars().count() == self.cursor_col { break; } 
                }
                // Now we are at end or cursor_col was just at end
                line.push(c);
            }
            self.cursor_col += 1;
        }
    }

    fn execute(&mut self, byte: u8) {
        match byte {
            b'\n' => {
                 if self.buffer.len() >= MAX_CAPACITY {
                      self.buffer.pop_front();
                 }
                 self.buffer.push_back(String::new());
                 self.cursor_col = 0;
            },
            b'\r' => {
                self.cursor_col = 0;
            }, 
            b'\x08' => { // Backspace
                if self.cursor_col > 0 {
                    self.cursor_col -= 1;
                }
            },
            _ => {} 
        }
    }
    
    fn hook(&mut self, _params: &vte::Params, _intermediates: &[u8], _ignore: bool, _action: char) {}
    fn put(&mut self, _byte: u8) {}
    fn unhook(&mut self) {}
    fn osc_dispatch(&mut self, _params: &[&[u8]], _bell_terminated: bool) {}
    fn csi_dispatch(&mut self, _params: &vte::Params, _intermediates: &[u8], _ignore: bool, _action: char) {}
    fn esc_dispatch(&mut self, _intermediates: &[u8], _ignore: bool, _byte: u8) {}
}

#[no_mangle]
pub extern "system" fn Java_com_aura_terminal_engine_RustBridge_pushData(
    env: JNIEnv,
    _class: JClass,
    data: JByteArray,
) {
    let bytes: Vec<u8> = env.convert_byte_array(data).expect("Couldn't get byte array!");
    
    let mut parser = PARSER.lock().unwrap();
    let mut buffer = TERMINAL_BUFFER.lock().unwrap();
    
    let mut interpreter = LogInterpreter::new(buffer);
    
    // Ensure we have at least one line to start with if empty
    if interpreter.buffer.is_empty() {
        interpreter.buffer.push_back(String::new());
    }

    for byte in bytes {
        parser.advance(&mut interpreter, byte);
    }
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
