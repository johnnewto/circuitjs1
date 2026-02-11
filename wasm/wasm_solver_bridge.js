/**
 * WASM Matrix Solver Bridge for CircuitJS1
 * 
 * This file provides the JavaScript interface between GWT-compiled Java
 * and the WASM matrix solver. It handles:
 * - Loading and initializing the WASM module (with SIMD detection)
 * - Memory management (allocation, copying between JS and WASM heaps)
 * - Providing callable functions for lu_factor and lu_solve
 * 
 * Usage from GWT JSNI:
 *   $wnd.WasmSolver.isReady()           - Check if WASM is loaded
 *   $wnd.WasmSolver.isSimd()            - Check if using SIMD version
 *   $wnd.WasmSolver.luFactor(a, n, ipvt) - LU factorization
 *   $wnd.WasmSolver.luSolve(a, n, ipvt, b) - LU solve
 */

(function() {
    'use strict';
    
    console.log('WasmSolver: Bridge script loading...');
    
    // WASM module instance
    var Module = null;
    var usingSimd = false;
    
    // Cached function wrappers
    var _lu_factor = null;
    var _lu_solve = null;
    var _wasm_malloc = null;
    var _wasm_free = null;
    
    // Persistent memory allocations (reused across calls)
    var matrixPtr = 0;
    var matrixAllocatedSize = 0;
    var ipvtPtr = 0;
    var ipvtAllocatedSize = 0;
    var bPtr = 0;
    var bAllocatedSize = 0;
    
    // Track if matrix/ipvt are already in WASM from a recent luFactor call
    // This allows luSolveFromFactor to skip redundant copying
    var lastFactoredSize = 0;
    
    // Size constants (set after WASM loads)
    var DOUBLE_SIZE = 8;
    var INT_SIZE = 4;
    
    /**
     * Detect if the browser supports WASM SIMD instructions.
     */
    function hasWasmSimd() {
        try {
            return WebAssembly.validate(new Uint8Array([
                0x00, 0x61, 0x73, 0x6d,
                0x01, 0x00, 0x00, 0x00,
                0x01, 0x05, 0x01, 0x60, 0x00, 0x01, 0x7b,
                0x03, 0x02, 0x01, 0x00,
                0x0a, 0x0a, 0x01, 0x08, 0x00, 0x41, 0x00, 0xfd, 0x0f, 0xfd, 0x62, 0x0b
            ]));
        } catch (e) {
            return false;
        }
    }
    
    /**
     * Initialize the WASM module.
     */
    function initWasm() {
        console.log('WasmSolver: Initializing WASM module...');
        
        var simdSupported = hasWasmSimd();
        console.log('WasmSolver: SIMD support detected:', simdSupported);
        
        if (simdSupported && typeof MatrixSolverModuleSIMD !== 'undefined') {
            console.log('WasmSolver: Loading SIMD version...');
            MatrixSolverModuleSIMD().then(function(mod) {
                Module = mod;
                usingSimd = true;
                setupFunctions();
                console.log('WasmSolver: SIMD WASM matrix solver loaded successfully');
            }).catch(function(err) {
                console.log('WasmSolver: SIMD load failed, trying standard:', err);
                loadStandardModule();
            });
        } else if (typeof MatrixSolverModule !== 'undefined') {
            loadStandardModule();
        } else {
            console.log('WasmSolver: No WASM modules found, WASM disabled');
        }
    }
    
    function loadStandardModule() {
        console.log('WasmSolver: Loading standard (non-SIMD) version...');
        MatrixSolverModule().then(function(mod) {
            Module = mod;
            usingSimd = false;
            setupFunctions();
            console.log('WasmSolver: Standard WASM matrix solver loaded successfully');
        }).catch(function(err) {
            console.log('WasmSolver: Failed to load WASM module:', err);
        });
    }
    
    function setupFunctions() {
        _lu_factor = Module.cwrap('lu_factor', 'number', ['number', 'number', 'number']);
        _lu_solve = Module.cwrap('lu_solve', null, ['number', 'number', 'number', 'number']);
        _wasm_malloc = Module._wasm_malloc;
        _wasm_free = Module._wasm_free;
        
        DOUBLE_SIZE = Module._get_double_size();
        INT_SIZE = Module._get_int_size();
    }
    
    function ensureMatrixMemory(n) {
        var neededSize = n * n * DOUBLE_SIZE;
        if (matrixPtr === 0 || matrixAllocatedSize < neededSize) {
            if (matrixPtr !== 0) _wasm_free(matrixPtr);
            matrixPtr = _wasm_malloc(neededSize);
            matrixAllocatedSize = neededSize;
        }
    }
    
    function ensureIpvtMemory(n) {
        var neededSize = n * INT_SIZE;
        if (ipvtPtr === 0 || ipvtAllocatedSize < neededSize) {
            if (ipvtPtr !== 0) _wasm_free(ipvtPtr);
            ipvtPtr = _wasm_malloc(neededSize);
            ipvtAllocatedSize = neededSize;
        }
    }
    
    function ensureBMemory(n) {
        var neededSize = n * DOUBLE_SIZE;
        if (bPtr === 0 || bAllocatedSize < neededSize) {
            if (bPtr !== 0) _wasm_free(bPtr);
            bPtr = _wasm_malloc(neededSize);
            bAllocatedSize = neededSize;
        }
    }
    
    function copyMatrixToWasm(a, n) {
        var heap = Module.HEAPF64;
        var offset = matrixPtr / DOUBLE_SIZE;
        for (var i = 0; i < n; i++) {
            for (var j = 0; j < n; j++) {
                heap[offset + i * n + j] = a[i][j];
            }
        }
    }
    
    function copyMatrixFromWasm(a, n) {
        var heap = Module.HEAPF64;
        var offset = matrixPtr / DOUBLE_SIZE;
        for (var i = 0; i < n; i++) {
            for (var j = 0; j < n; j++) {
                a[i][j] = heap[offset + i * n + j];
            }
        }
    }
    
    function copyIpvtToWasm(ipvt, n) {
        var heap = Module.HEAP32;
        var offset = ipvtPtr / INT_SIZE;
        for (var i = 0; i < n; i++) {
            heap[offset + i] = ipvt[i];
        }
    }
    
    function copyIpvtFromWasm(ipvt, n) {
        var heap = Module.HEAP32;
        var offset = ipvtPtr / INT_SIZE;
        for (var i = 0; i < n; i++) {
            ipvt[i] = heap[offset + i];
        }
    }
    
    function copyBToWasm(b, n) {
        var heap = Module.HEAPF64;
        var offset = bPtr / DOUBLE_SIZE;
        for (var i = 0; i < n; i++) {
            heap[offset + i] = b[i];
        }
    }
    
    function copyBFromWasm(b, n) {
        var heap = Module.HEAPF64;
        var offset = bPtr / DOUBLE_SIZE;
        for (var i = 0; i < n; i++) {
            b[i] = heap[offset + i];
        }
    }
    
    // Public API
    window.WasmSolver = {
        isReady: function() {
            return Module !== null;
        },
        
        isSimd: function() {
            return usingSimd;
        },
        
        hasSimdSupport: function() {
            return hasWasmSimd();
        },
        
        luFactor: function(a, n, ipvt) {
            if (!Module) throw new Error('WASM module not loaded');
            
            ensureMatrixMemory(n);
            ensureIpvtMemory(n);
            
            copyMatrixToWasm(a, n);
            
            var result = _lu_factor(matrixPtr, n, ipvtPtr);
            
            copyMatrixFromWasm(a, n);
            copyIpvtFromWasm(ipvt, n);
            
            lastFactoredSize = n;
            
            return result;
        },
        
        luSolve: function(a, n, ipvt, b) {
            if (!Module) throw new Error('WASM module not loaded');
            
            ensureMatrixMemory(n);
            ensureIpvtMemory(n);
            ensureBMemory(n);
            
            copyMatrixToWasm(a, n);
            copyIpvtToWasm(ipvt, n);
            copyBToWasm(b, n);
            
            _lu_solve(matrixPtr, n, ipvtPtr, bPtr);
            
            copyBFromWasm(b, n);
            
            lastFactoredSize = 0;
        },
        
        /**
         * Fast solve when matrix/ipvt already in WASM from luFactor.
         * Only copies b vector, skipping O(n²) matrix copy.
         */
        luSolveFromFactor: function(n, b) {
            if (!Module) throw new Error('WASM module not loaded');
            if (lastFactoredSize !== n) {
                throw new Error('luSolveFromFactor: matrix size mismatch or luFactor not called');
            }
            
            ensureBMemory(n);
            copyBToWasm(b, n);
            
            _lu_solve(matrixPtr, n, ipvtPtr, bPtr);
            
            copyBFromWasm(b, n);
        },
        
        freeMemory: function() {
            if (matrixPtr !== 0) { _wasm_free(matrixPtr); matrixPtr = 0; matrixAllocatedSize = 0; }
            if (ipvtPtr !== 0) { _wasm_free(ipvtPtr); ipvtPtr = 0; ipvtAllocatedSize = 0; }
            if (bPtr !== 0) { _wasm_free(bPtr); bPtr = 0; bAllocatedSize = 0; }
            lastFactoredSize = 0;
        },
        
        getMemoryStats: function() {
            return {
                matrixBytes: matrixAllocatedSize,
                ipvtBytes: ipvtAllocatedSize,
                bBytes: bAllocatedSize,
                totalBytes: matrixAllocatedSize + ipvtAllocatedSize + bAllocatedSize
            };
        },
        
        /**
         * Benchmark: detailed breakdown of factor vs solve performance
         */
        benchmarkDetailed: function(n, iterations) {
            n = n || 50;
            iterations = iterations || 100;
            
            if (!Module) {
                console.log('WASM not loaded');
                return null;
            }
            
            // Create test matrix
            var a = [];
            var ipvt = new Array(n);
            var b = new Array(n);
            
            for (var i = 0; i < n; i++) {
                a[i] = new Array(n);
                for (var j = 0; j < n; j++) {
                    a[i][j] = (i === j) ? 10 + Math.random() : Math.random() - 0.5;
                }
                b[i] = Math.random();
            }
            
            // WASM factor
            var startFactor = performance.now();
            for (var iter = 0; iter < iterations; iter++) {
                // Reset matrix each iteration
                for (var i = 0; i < n; i++) {
                    for (var j = 0; j < n; j++) {
                        a[i][j] = (i === j) ? 10 + Math.random() : Math.random() - 0.5;
                    }
                }
                window.WasmSolver.luFactor(a, n, ipvt);
            }
            var wasmFactorTime = performance.now() - startFactor;
            
            // WASM solve (using fast path)
            var startSolve = performance.now();
            for (var iter = 0; iter < iterations; iter++) {
                for (var i = 0; i < n; i++) b[i] = Math.random();
                window.WasmSolver.luSolveFromFactor(n, b);
            }
            var wasmSolveTime = performance.now() - startSolve;
            
            console.log('=== WASM Detailed Benchmark (' + n + 'x' + n + ', ' + iterations + ' iterations) ===');
            console.log('WASM lu_factor: ' + (wasmFactorTime / iterations).toFixed(3) + ' ms/call');
            console.log('WASM lu_solve (fast): ' + (wasmSolveTime / iterations).toFixed(3) + ' ms/call');
            console.log('WASM total: ' + ((wasmFactorTime + wasmSolveTime) / iterations).toFixed(3) + ' ms/call');
            console.log('Using SIMD:', usingSimd);
            
            return {
                n: n,
                iterations: iterations,
                factorMs: wasmFactorTime / iterations,
                solveMs: wasmSolveTime / iterations,
                totalMs: (wasmFactorTime + wasmSolveTime) / iterations,
                simd: usingSimd
            };
        },
        
        /**
         * Simple benchmark
         */
        benchmark: function(n, iterations) {
            return this.benchmarkDetailed(n, iterations);
        }
    };
    
    // Initialize when script loads
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function() {
            setTimeout(initWasm, 100);
        });
    } else {
        setTimeout(initWasm, 100);
    }
    
})();
