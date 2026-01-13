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
    static ref CURSOR_COL: Mutex<usize> = Mutex::new(0);
}

const MAX_CAPACITY: usize = 10000;

struct LogInterpreter<'a> {
    buffer: std::sync::MutexGuard<'a, VecDeque<String>>,
}

impl<'a> LogInterpreter<'a> {
    fn new(buffer: std::sync::MutexGuard<'a, VecDeque<String>>) -> Self {
        Self { buffer }
    }

    fn print_char_with_cursor(&mut self, c: char, cursor: &mut usize) {
        if self.buffer.is_empty() {
            self.buffer.push_back(String::new());
        }

        if let Some(line) = self.buffer.back_mut() {
            let current_len = line.chars().count();
            let cur_val = *cursor;

            if cur_val < current_len {
                 let mut chars: Vec<char> = line.chars().collect();
                 if cur_val < chars.len() {
                    chars[cur_val] = c;
                    *line = chars.into_iter().collect();
                 } else {
                     line.push(c);
                 }
            } else {
                 while line.chars().count() < cur_val {
                     line.push(' ');
                 }
                 line.push(c);
            }
            *cursor += 1;
        }
    }
}

impl<'a> Perform for LogInterpreter<'a> {
    fn print(&mut self, c: char) {
        let mut cursor_guard = CURSOR_COL.lock().unwrap();
        self.print_char_with_cursor(c, &mut cursor_guard);
    }

    fn execute(&mut self, byte: u8) {
        let mut cursor_guard = CURSOR_COL.lock().unwrap();
        
        match byte {
            b'\n' => {
                 if self.buffer.len() >= MAX_CAPACITY {
                      self.buffer.pop_front();
                 }
                 self.buffer.push_back(String::new());
                 *cursor_guard = 0;
            },
            b'\r' => *cursor_guard = 0, 
            b'\t' => {
                let spaces = 8 - (*cursor_guard % 8);
                for _ in 0..spaces {
                    self.print_char_with_cursor(' ', &mut cursor_guard);
                }
            },
            0x08 => {
                if *cursor_guard > 0 {
                    *cursor_guard -= 1;
                }
            },
            _ => {} 
        }
    }
    
    fn hook(&mut self, _params: &vte::Params, _intermediates: &[u8], _ignore: bool, _action: char) {}
    fn put(&mut self, _byte: u8) {}
    fn unhook(&mut self) {}
    fn osc_dispatch(&mut self, _params: &[&[u8]], _bell_terminated: bool) {}
    
    fn csi_dispatch(&mut self, params: &vte::Params, _intermediates: &[u8], _ignore: bool, action: char) {
        let mut cursor_guard = CURSOR_COL.lock().unwrap();

        let param = params.iter().next()
            .and_then(|slice| slice.iter().next())
            .map(|&v| v)
            .unwrap_or(1) as usize;

        match action {
            'G' => *cursor_guard = if param > 0 { param - 1 } else { 0 },
            'K' => {
                let p_val = params.iter().next()
                    .and_then(|slice| slice.iter().next())
                    .map(|&v| v)
                    .unwrap_or(0);
                    
                if p_val == 0 {
                    if let Some(line) = self.buffer.back_mut() {
                        let cur_val = *cursor_guard;
                        let current_len = line.chars().count();
                        if cur_val < current_len {
                             let mut chars: Vec<char> = line.chars().collect();
                             chars.truncate(cur_val);
                             *line = chars.into_iter().collect();
                        }
                    }
                }
            },
            'C' => *cursor_guard += param,
            'D' => {
                let cur_val = *cursor_guard;
                *cursor_guard = if cur_val >= param { cur_val - param } else { 0 };
            },
            _ => {}
        }
    }
    
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
    let buffer = TERMINAL_BUFFER.lock().unwrap();
    
    let mut interpreter = LogInterpreter::new(buffer);
    
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
    let mut cursor = CURSOR_COL.lock().unwrap();
    *cursor = 0;
}
