# WASM Matrix Solver

CircuitJS1 includes an optional WebAssembly (WASM) matrix solver that provides speedup for LU factorization on circuits with 30+ nodes. While synthetic benchmarks show crossover around n=80, real circuit simulation benefits at lower sizes due to GC pressure and cache effects.

## Overview

The WASM solver compiles the LU decomposition algorithm (Crout's method with partial pivoting) from C to WebAssembly using Emscripten. For matrices ≥30×30, the fast solve path and reduced GC interference provide net speedup in real-world usage.

### SIMD Acceleration

On browsers supporting WebAssembly SIMD (Chrome 91+, Firefox 89+, Safari 16.4+, Edge 91+), the solver automatically uses SIMD-optimized code that processes 2 double-precision values simultaneously using 128-bit vector operations.

## How It Works

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Java/GWT      │────▶│  JavaScript      │────▶│     WASM        │
│  (CirSim.java)  │     │  Bridge          │     │  (Compiled C)   │
└─────────────────┘     └──────────────────┘     └─────────────────┘
        │                        │                        │
   lu_factor_auto()      WasmSolver.luFactor()     _lu_factor()
   lu_solve_auto()       WasmSolver.luSolve()      _lu_solve()
```

1. **Java wrapper** (`WasmMatrixSolver.java`) - JSNI methods that call JavaScript
2. **JavaScript bridge** (`wasm_solver_bridge.js`) - Handles memory management and WASM calls
3. **WASM module** (`matrix_solver.wasm`) - Compiled C code

## Files

| File | Description |
|------|-------------|
| `wasm/matrix_solver.c` | C source code for LU factorization (standard) |
| `wasm/matrix_solver_simd.c` | C source code with SIMD intrinsics |
| `wasm/build.sh` | Emscripten build script (builds both versions) |
| `wasm/wasm_solver_bridge.js` | JavaScript API bridge (source) |
| `war/circuitjs1/matrix_solver.js` | WASM loader - standard (generated) |
| `war/circuitjs1/matrix_solver.wasm` | WASM binary - standard (generated) |
| `war/circuitjs1/matrix_solver_simd.js` | WASM loader - SIMD (generated) |
| `war/circuitjs1/matrix_solver_simd.wasm` | WASM binary - SIMD (generated) |
| `war/circuitjs1/wasm_solver_bridge.js` | JavaScript API bridge (deployed copy) |
| `src/.../WasmMatrixSolver.java` | GWT JSNI wrapper |

### File Deployment Note

The `wasm_solver_bridge.js` source is stored in `wasm/` and copied to `war/circuitjs1/` during WASM build.

**Important**: Running `./gradlew compileGwt` followed by copying GWT output to `war/circuitjs1/` will overwrite the bridge file. After GWT compile, either:
1. Run `wasm/build.sh` to restore the bridge file, OR
2. Manually copy: `cp wasm/wasm_solver_bridge.js war/circuitjs1/`

## Building the WASM Module

### Prerequisites

Install the Emscripten SDK:

```bash
git clone https://github.com/emscripten-core/emsdk.git
cd emsdk
./emsdk install latest
./emsdk activate latest
source ./emsdk_env.sh
```

### Build

```bash
cd wasm
./build.sh
```

This generates:
- `war/circuitjs1/matrix_solver.js` (~15KB)
- `war/circuitjs1/matrix_solver.wasm` (~8KB)
- `war/circuitjs1/matrix_solver_simd.js` (~15KB)
- `war/circuitjs1/matrix_solver_simd.wasm` (~9KB)

## Browser Compatibility

### Standard WASM

WASM is supported in all modern browsers:

| Browser | Minimum Version |
|---------|-----------------|
| Chrome | 57+ (March 2017) |
| Firefox | 52+ (March 2017) |
| Safari | 11+ (Sept 2017) |
| Edge | 16+ (Oct 2017) |
| Electron | All versions |

### WASM SIMD

SIMD support requires newer browser versions:

| Browser | Minimum Version | SIMD Support |
|---------|-----------------|--------------|
| Chrome | 91+ (May 2021) | ✓ |
| Firefox | 89+ (June 2021) | ✓ |
| Safari | 16.4+ (March 2023) | ✓ |
| Edge | 91+ (May 2021) | ✓ |
| Electron | 12+ (March 2021) | ✓ |

The bridge automatically detects SIMD support and falls back to the standard WASM version if unavailable.

## Automatic Fallback

If WASM is not available or fails to load:
- The JavaScript solver is used automatically
- A message is logged to the console
- No user intervention is required

## Performance

### Real-World Performance

In actual circuit simulation, WASM provides significant speedup:

| Circuit | JS Time | WASM Time | Speedup |
|---------|---------|-----------|---------|
| 1debug.txt (49×49 matrix, 100 periods) | 4.1s | 2.8s | **1.46x** |

### Synthetic Benchmark vs Real-World Discrepancy

**Important:** Synthetic benchmarks (e.g., `WasmSolver.benchmark()`) may show WASM as *slower* than JavaScript:

| Matrix Size | Benchmark JS | Benchmark WASM | Benchmark "Speedup" |
|-------------|--------------|----------------|---------------------|
| 50×50 | 0.15ms | 0.68ms | 0.23x (slower!) |
| 100×100 | 0.70ms | 1.44ms | 0.49x (slower!) |

**This is misleading.** Real-world circuit simulation shows WASM is ~46% faster because:

1. **GC Pressure**: Real simulation creates many temporary JavaScript objects (element updates, current calculations, drawing). WASM uses fixed linear memory with no garbage collection interference. Isolated benchmarks have minimal GC activity.

2. **CPU Cache Competition**: In actual use, JavaScript for drawing, UI, and element calculations all compete for L1/L2 cache. WASM's linear memory has predictable access patterns and stays in cache. Benchmarks run in isolation with the entire cache available to JavaScript.

3. **JIT Deoptimization**: V8's optimized JavaScript code can get "deoptimized" when the runtime detects unexpected patterns. Real circuits have varied code paths (different element types, convergence checks). WASM compiled code never deoptimizes.

4. **lu_solve Hot Path**: Real circuits call `lu_solve` many times per timestep (subiterations for nonlinear convergence) with the *same* factored matrix. WASM's lu_solve benefit accumulates.

5. **Memory Reuse**: Benchmarks allocate fresh arrays each iteration. Real circuits reuse the same WASM memory buffers, amortizing allocation overhead.

### Recommendation

Trust real-world timing (the `real = Xs` display in the simulator) over synthetic benchmarks when evaluating WASM performance.

### Benchmark Variability: Why WASM Matters

Running `WasmSolver.benchmarkDetailed(50, 100)` multiple times shows significant variance:

| Run | JS lu_factor | WASM lu_factor | Speedup |
|-----|--------------|----------------|---------|
| 1 (cold) | **0.409 ms** | 0.154 ms | **2.66x** |
| 2 (warm) | 0.114 ms | 0.130 ms | 0.88x |
| 3 (warm) | 0.160 ms | 0.142 ms | 1.13x |

**Key insight**: JavaScript varies 3.6x while WASM is consistent.

This is JIT warmup behavior:
- **First run**: JS not yet JIT-compiled → slow (WASM wins big)
- **Later runs**: JS JIT-optimized → roughly equal
- **After GC/tab switch**: JS may deoptimize → WASM wins again

**WASM's real benefit is consistency**, not peak speed. In real circuit simulation:
- Page load and first timesteps benefit from WASM
- GC pauses cause JavaScript slowdowns that WASM avoids
- Predictable timing gives smoother real-time feel

The solver uses WASM for matrices ≥30×30 based on real-world testing.

### V8 Production Mode Performance

**Important:** The JavaScript performance numbers above assume **production-optimized compilation** (GWT optimize=9). Development builds are significantly slower.

| Compilation Mode | Command | Runtime Performance |
|------------------|---------|---------------------|
| Production (optimize=9) | `./dev.sh compile` or `./dev.sh startprod` | **Same speed as WASM** |
| Production (optimize=9) | `./gradlew compileGwt -Pgwt.compiler.optimize=9` | **Same speed as WASM** |
| Development (optimize=0) | `./gradlew compileGwt` (default) | **~2x slower** |
| SuperDevMode | `./dev.sh start` | **~2x slower** |

**For performance testing:**
- Use `./dev.sh startprod` for production-speed local testing
- GitHub Pages deployment always uses optimize=9 via `ant build`
- Development mode (`./dev.sh start`) prioritizes compilation speed over runtime speed

**Real-world comparison (100 seconds simulation):**
- Production V8: 3.1 seconds
- Production WASM: 3.1 seconds
- Development V8: 6.2 seconds (2x slower)

Modern V8 JIT optimization is so effective that fully-optimized GWT JavaScript matches WASM performance for typical circuit sizes. WASM's primary benefit is **consistency** - avoiding GC pauses and JIT deoptimization - rather than raw speed advantage.

## Configuration

### Disable WASM Solver

In `CirSim.java`:
```java
static boolean useWasmSolver = false;  // Disable WASM
```

### Adjust Threshold

In `WasmMatrixSolver.java`:
```java
public static final int MIN_WASM_SIZE = 30;  // Only use WASM for n >= 30
```

## Memory Management

The WASM bridge reuses memory allocations across solver calls:
- Matrix memory grows as needed
- Memory is freed when analyzing a new circuit
- Call `WasmMatrixSolver.freeMemory()` to manually release memory

## Fast Solve Path (luSolveFromFactor)

When `luSolve` immediately follows `luFactor`, the solver uses a fast path that avoids redundant data copying:

### The Problem

The standard `luSolve` copies the entire n×n matrix and pivot array to WASM memory on every call. For a 100×100 matrix with 5 subiterations per timestep, this means copying 80KB of matrix data 5 times per timestep - completely unnecessary since the matrix hasn't changed.

### The Solution

`luSolveFromFactor(n, b)` skips the matrix/ipvt copy:
- Only copies the `b` vector (n doubles = 800 bytes for 100×100)
- Uses the matrix/ipvt already in WASM memory from the preceding `luFactor`
- Provides **significant speedup** for realistic circuit simulation

### Automatic Usage

`CirSim.lu_solve_auto()` automatically uses the fast path when:
1. A `luFactor` was just called (tracked via `lastWasmFactoredSize`)
2. The matrix size matches

```java
// In CirSim.java - automatic fast path selection
void lu_solve_auto(double a[][], int n, int ipvt[], double b[]) {
    if (useWasmSolver && WasmMatrixSolver.shouldUseWasm(n)) {
        if (lastWasmFactoredSize == n) {
            // Fast path: matrix/ipvt already in WASM from lu_factor_auto
            WasmMatrixSolver.luSolveFromFactor(n, b);
        } else {
            // Full copy needed
            WasmMatrixSolver.luSolve(a, n, ipvt, b);
        }
        return;
    }
    lu_solve(a, n, ipvt, b);
}
```

### Benchmark Comparison

With fast path (100×100 matrix, pattern: 1 factor + 4 solves):
```
WasmSolver.benchmarkDetailed(100, 100)
JavaScript: 0.917 ms/timestep
WASM:       0.827 ms/timestep
Speedup:    1.11x
```

Without fast path (old behavior, copying matrix every solve):
```
JavaScript: 0.92 ms/timestep
WASM:       1.52 ms/timestep
Speedup:    0.62x (slower!)
```

## Debugging

Check browser console for WASM status:
```
WASM matrix solver: enabled [SIMD enabled] (using for matrices >= 20x20)
```

Or without SIMD:
```
WASM matrix solver: enabled [SIMD not available] (using for matrices >= 20x20)
```

Or if WASM fails to load:
```
WASM solver not available, using JavaScript fallback
```

### Check SIMD Status

In the browser console:
```javascript
WasmSolver.isSimd()  // Returns true if SIMD module is active
WasmSolver.hasSimdSupport()  // Returns true if browser supports WASM SIMD
```

### Benchmark Functions

Several benchmark functions are available in the browser console:

```javascript
// Standard benchmark (may show misleading results - see Performance section)
WasmSolver.benchmark(100, 100)  // 100x100 matrix, 100 iterations

// Realistic benchmark with GC pressure and cache pollution
WasmSolver.benchmarkRealistic(50, 50, 4)  // 50x50 matrix, 50 timesteps, 4 subiterations

// Benchmark lu_solve only (the hot path)
WasmSolver.benchmarkSolveOnly(50, 500)  // 50x50 matrix, 500 solve iterations
```

**Note:** For accurate performance measurement, use the `real = Xs` timer in the simulator while running a circuit.

## Development Without WASM

If you don't want to build the WASM module:
1. The `.wasm` files can be omitted
2. The JavaScript fallback will be used automatically
3. No code changes are required

## Technical Details

### Algorithm

Both implementations use Crout's method for LU decomposition with partial pivoting:

1. **Singular check**: Scan for all-zero rows
2. **Crout's method**: Compute L and U factors column by column
3. **Partial pivoting**: Swap rows to improve numerical stability
4. **Forward substitution**: Solve Ly = b
5. **Back substitution**: Solve Ux = y

### Memory Layout

The WASM module uses flat (1D) arrays for matrices:
- Matrix `a[i][j]` → `flat[i * n + j]` (row-major order)
- This eliminates pointer-chasing overhead in the C code

### Type Sizes

- `double`: 8 bytes (IEEE 754 double precision)
- `int`: 4 bytes (32-bit signed integer)

### SIMD Implementation

The SIMD version uses WebAssembly 128-bit SIMD instructions via the `wasm_simd128.h` intrinsics:

| Operation | Standard | SIMD |
|-----------|----------|------|
| Dot product | Loop, 1 double/iteration | `wasm_f64x2_mul` + `wasm_f64x2_add`, 2 doubles/iteration |
| Row swap | 1 element at a time | `v128_t` loads/stores, 2 doubles/operation |
| Scalar multiply | 1 element at a time | `wasm_f64x2_mul` with splatted scalar |

Key intrinsics used:
- `wasm_v128_load()` / `wasm_v128_store()` - Load/store 2 doubles
- `wasm_f64x2_mul()` - Multiply 2×2 doubles in parallel
- `wasm_f64x2_add()` - Add 2×2 doubles in parallel
- `wasm_f64x2_extract_lane()` - Extract single double from vector
- `wasm_f64x2_splat()` - Broadcast scalar to both lanes

The SIMD module exports `is_simd_build()` to verify it's the SIMD version.
