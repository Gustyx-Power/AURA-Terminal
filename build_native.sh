#!/bin/bash

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

if [ "$OS" = "Linux" ]; then
    TARGET="native/aura_core/target/release/libaura_core.so"
    if [ -f "$TARGET" ]; then
        cp "$TARGET" .
        echo "Build complete! Native lib (libaura_core.so) placed in project root."
    else
        echo "Error: Output file not found at $TARGET"
        exit 1
    fi
elif [ "$OS" = "Darwin" ]; then
    TARGET="native/aura_core/target/release/libaura_core.dylib"
    if [ -f "$TARGET" ]; then
        cp "$TARGET" .
        echo "Build complete! Native lib (libaura_core.dylib) placed in project root."
    else
        echo "Error: Output file not found at $TARGET"
        exit 1
    fi
else
    echo "Warning: Unsupported or unknown OS '$OS'. Please copy the library manually."
fi
