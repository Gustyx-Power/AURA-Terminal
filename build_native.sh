#!/bin/bash

# Source cargo env to ensure cargo is found
if [ -f "$HOME/.cargo/env" ]; then
    . "$HOME/.cargo/env"
fi

OS="$(uname -s)"
echo "Detected OS: $OS"

if [ -d "native/aura_core" ]; then
    cd native/aura_core
else
    echo "Error: Directory 'native/aura_core' not found!"
    exit 1
fi

echo "Building Rust project..."
if cargo build --release; then
    echo "Build successful."
else
    echo "Error: Cargo build failed!"
    exit 1
fi

cd ../..

# --- PERBAIKAN NAMA FILE DISINI ---
if [ "$OS" = "Linux" ]; then
    # Sesuaikan nama file dengan [package] name di Cargo.toml
    # Jika namanya "aura_terminal_core", maka outputnya "libaura_terminal_core.so"
    TARGET="native/aura_core/target/release/libaura_terminal_core.so" 
    
    if [ -f "$TARGET" ]; then
        cp "$TARGET" .
        echo "Build complete! Linux lib copied to project root."
    else
        # Fallback cek nama lama (jaga-jaga)
        TARGET_OLD="native/aura_core/target/release/libaura_core.so"
        if [ -f "$TARGET_OLD" ]; then
             cp "$TARGET_OLD" .
             echo "Build complete! (Using old name: libaura_core.so)"
        else
             echo "Error: Output file not found. Check your Cargo.toml package name!"
             exit 1
        fi
    fi
elif [ "$OS" = "Darwin" ]; then
    # Sesuaikan nama file dengan [package] name di Cargo.toml
    TARGET="native/aura_core/target/release/libaura_terminal_core.dylib"
    
    if [ -f "$TARGET" ]; then
        cp "$TARGET" .
        echo "Build complete! macOS lib copied to project root."
    else
        # Fallback cek nama lama
        TARGET_OLD="native/aura_core/target/release/libaura_core.dylib"
        if [ -f "$TARGET_OLD" ]; then
             cp "$TARGET_OLD" .
             echo "Build complete! (Using old name: libaura_core.dylib)"
        else
             echo "Error: Output file not found. Check your Cargo.toml package name!"
             exit 1
        fi
    fi
else
    echo "Warning: Unsupported OS '$OS'"
fi