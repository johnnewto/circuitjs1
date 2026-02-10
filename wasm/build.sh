#!/bin/bash
#
# Build script for WASM Matrix Solver (Standard and SIMD versions)
# Requires: Emscripten SDK (https://emscripten.org/docs/getting_started/downloads.html)
#
# Installation (one-time):
#   git clone https://github.com/emscripten-core/emsdk.git
#   cd emsdk
#   ./emsdk install latest
#   ./emsdk activate latest
#   source ./emsdk_env.sh
#
# Usage: ./build.sh
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../war/circuitjs1"

echo "Building WASM matrix solver..."

# Check if emcc is available
if ! command -v emcc &> /dev/null; then
    echo "Error: emcc (Emscripten compiler) not found."
    echo ""
    echo "Please install Emscripten SDK:"
    echo "  git clone https://github.com/emscripten-core/emsdk.git"
    echo "  cd emsdk"
    echo "  ./emsdk install latest"
    echo "  ./emsdk activate latest"
    echo "  source ./emsdk_env.sh"
    echo ""
    echo "Or if already installed, run: source /path/to/emsdk/emsdk_env.sh"
    exit 1
fi

# Common flags
COMMON_FLAGS="-O3 -s WASM=1 -s MODULARIZE=1 -s ALLOW_MEMORY_GROWTH=1 -s INITIAL_MEMORY=1048576 --no-entry"
EXPORTS='-s EXPORTED_FUNCTIONS=["_lu_factor","_lu_solve","_wasm_malloc","_wasm_free","_get_double_size","_get_int_size"]'
EXPORTS_SIMD='-s EXPORTED_FUNCTIONS=["_lu_factor","_lu_solve","_wasm_malloc","_wasm_free","_get_double_size","_get_int_size","_is_simd_build"]'
RUNTIME='-s EXPORTED_RUNTIME_METHODS=["ccall","cwrap","HEAPF64","HEAP32","setValue","getValue"]'

echo ""
echo "=== Building standard (non-SIMD) version ==="
# Compile standard version (no SIMD, maximum compatibility)

echo ""
echo "=== Building standard (non-SIMD) version ==="
# Compile standard version (no SIMD, maximum compatibility)

emcc "$SCRIPT_DIR/matrix_solver.c" \
    $COMMON_FLAGS \
    -s EXPORT_NAME="MatrixSolverModule" \
    $EXPORTS \
    $RUNTIME \
    -o "$OUTPUT_DIR/matrix_solver.js"

echo "Standard build complete!"

echo ""
echo "=== Building SIMD version ==="
# Compile SIMD version (requires browser SIMD support)
# -msimd128: Enable WASM SIMD instructions
# -mrelaxed-simd: Enable relaxed SIMD (optional, even faster)

emcc "$SCRIPT_DIR/matrix_solver_simd.c" \
    $COMMON_FLAGS \
    -msimd128 \
    -s EXPORT_NAME="MatrixSolverModuleSIMD" \
    $EXPORTS_SIMD \
    $RUNTIME \
    -o "$OUTPUT_DIR/matrix_solver_simd.js"

echo "SIMD build complete!"

echo ""
echo "Build complete!"
echo ""
echo "Output files:"
echo "  Standard (all browsers):"
ls -lh "$OUTPUT_DIR/matrix_solver.js" "$OUTPUT_DIR/matrix_solver.wasm"
echo ""
echo "  SIMD (modern browsers, ~1.5-2x faster):"
ls -lh "$OUTPUT_DIR/matrix_solver_simd.js" "$OUTPUT_DIR/matrix_solver_simd.wasm"
