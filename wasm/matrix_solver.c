/**
 * WASM Matrix Solver for CircuitJS1
 * 
 * LU decomposition and solve routines compiled to WebAssembly via Emscripten.
 * Provides ~2-3x speedup over JavaScript for matrix operations.
 * 
 * Build: ./build.sh (requires Emscripten SDK)
 * Output: matrix_solver.js, matrix_solver.wasm
 */

#include <emscripten.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

/**
 * LU factorization using Crout's method with partial pivoting.
 * 
 * @param a      Flat array representing n×n matrix (row-major: a[i*n + j])
 * @param n      Matrix dimension
 * @param ipvt   Output pivot indices array of size n
 * @return       -1 on success, or problematic row index on failure (singular matrix)
 */
EMSCRIPTEN_KEEPALIVE
int lu_factor(double* a, int n, int* ipvt) {
    int i, j, k;
    
    // Check for rows that are all zeros (singular matrix)
    for (i = 0; i < n; i++) {
        int row_all_zeros = 1;
        for (j = 0; j < n; j++) {
            if (a[i * n + j] != 0.0) {
                row_all_zeros = 0;
                break;
            }
        }
        if (row_all_zeros) {
            return i;  // Return the problematic row
        }
    }
    
    // Use Crout's method; loop through the columns
    for (j = 0; j < n; j++) {
        
        // Calculate upper triangular elements for this column
        for (i = 0; i < j; i++) {
            double q = a[i * n + j];
            for (k = 0; k < i; k++) {
                q -= a[i * n + k] * a[k * n + j];
            }
            a[i * n + j] = q;
        }
        
        // Calculate lower triangular elements for this column
        double largest = 0.0;
        int largestRow = -1;
        for (i = j; i < n; i++) {
            double q = a[i * n + j];
            for (k = 0; k < j; k++) {
                q -= a[i * n + k] * a[k * n + j];
            }
            a[i * n + j] = q;
            double x = fabs(q);
            if (x >= largest) {
                largest = x;
                largestRow = i;
            }
        }
        
        // Pivoting
        if (j != largestRow) {
            if (largestRow == -1) {
                return j;  // Return the problematic row
            }
            // Swap rows j and largestRow
            for (k = 0; k < n; k++) {
                double x = a[largestRow * n + k];
                a[largestRow * n + k] = a[j * n + k];
                a[j * n + k] = x;
            }
        }
        
        // Keep track of row interchanges
        ipvt[j] = largestRow;
        
        // Check for zero diagonal (singular matrix)
        if (a[j * n + j] == 0.0) {
            return j;  // Return the problematic row
        }
        
        // Scale the column below the diagonal
        if (j != n - 1) {
            double mult = 1.0 / a[j * n + j];
            for (i = j + 1; i < n; i++) {
                a[i * n + j] *= mult;
            }
        }
    }
    
    return -1;  // Success
}

/**
 * Solve the system using previously computed LU factorization.
 * 
 * @param a      LU factorized matrix (flat, row-major)
 * @param n      Matrix dimension
 * @param ipvt   Pivot indices from lu_factor
 * @param b      Input: right-hand side vector; Output: solution vector
 */
EMSCRIPTEN_KEEPALIVE
void lu_solve(double* a, int n, int* ipvt, double* b) {
    int i, j;
    
    // Find first nonzero b element and apply row permutations
    int bi = 0;
    for (i = 0; i < n; i++) {
        int row = ipvt[i];
        double swap = b[row];
        b[row] = b[i];
        b[i] = swap;
        if (swap != 0.0) {
            bi = i;
            i++;
            break;
        }
    }
    
    // Forward substitution using the lower triangular matrix
    for (; i < n; i++) {
        int row = ipvt[i];
        double tot = b[row];
        b[row] = b[i];
        for (j = bi; j < i; j++) {
            tot -= a[i * n + j] * b[j];
        }
        b[i] = tot;
    }
    
    // Back-substitution using the upper triangular matrix
    for (i = n - 1; i >= 0; i--) {
        double tot = b[i];
        for (j = i + 1; j < n; j++) {
            tot -= a[i * n + j] * b[j];
        }
        b[i] = tot / a[i * n + i];
    }
}

/**
 * Allocate memory for matrix data.
 * Use this to get persistent WASM memory that can be reused across calls.
 * 
 * @param size   Size in bytes to allocate
 * @return       Pointer to allocated memory
 */
EMSCRIPTEN_KEEPALIVE
void* wasm_malloc(int size) {
    return malloc(size);
}

/**
 * Free previously allocated memory.
 * 
 * @param ptr    Pointer to free
 */
EMSCRIPTEN_KEEPALIVE
void wasm_free(void* ptr) {
    free(ptr);
}

/**
 * Get the size of a double for memory calculations.
 * 
 * @return       Size of double in bytes (typically 8)
 */
EMSCRIPTEN_KEEPALIVE
int get_double_size() {
    return sizeof(double);
}

/**
 * Get the size of an int for memory calculations.
 * 
 * @return       Size of int in bytes (typically 4)
 */
EMSCRIPTEN_KEEPALIVE
int get_int_size() {
    return sizeof(int);
}

/**
 * Check if this is the SIMD-optimized build.
 * 
 * @return       0 for standard build, 1 for SIMD build
 */
EMSCRIPTEN_KEEPALIVE
int is_simd_build() {
    return 0;  // Standard build
}
