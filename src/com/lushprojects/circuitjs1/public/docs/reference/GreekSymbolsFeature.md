# Greek Symbols and LaTeX Formatting Support in CircuitJS1

## Overview

CircuitJS1 now supports LaTeX-style notation for:
1. **Greek symbols** (`\alpha`, `\beta`, `\omega`, etc.) 
2. **Subscripts** (`Z_1`, `Z_{banks}`)
3. **Superscripts** (`x^2`, `E^{10}`)

This allows users to display professional mathematical notation throughout the canvas automatically.

## Implementation

The feature consists of multiple integrated components:

1. **`Locale.convertGreekSymbols(String input)`** - Converts LaTeX Greek symbols to Unicode
2. **`Graphics.drawString()` with script rendering** - Renders subscripts/superscripts with proper sizing and positioning
3. **`Graphics.measureWidth()` with script support** - Accurate text width calculations
4. **Expression parser support** - Recognizes LaTeX formatting in identifiers
5. **Autocomplete support** - Validates formatted text as identifiers

## Supported Features

### Lowercase Greek Letters

| Escape Sequence | Symbol | Unicode |
|----------------|--------|---------|
| `\alpha`       | α      | U+03B1  |
| `\beta`        | β      | U+03B2  |
| `\gamma`       | γ      | U+03B3  |
| `\delta`       | δ      | U+03B4  |
| `\epsilon`     | ε      | U+03B5  |
| `\zeta`        | ζ      | U+03B6  |
| `\eta`         | η      | U+03B7  |
| `\theta`       | θ      | U+03B8  |
| `\iota`        | ι      | U+03B9  |
| `\kappa`       | κ      | U+03BA  |
| `\lambda`      | λ      | U+03BB  |
| `\mu`          | μ      | U+03BC  |
| `\nu`          | ν      | U+03BD  |
| `\xi`          | ξ      | U+03BE  |
| `\omicron`     | ο      | U+03BF  |
| `\pi`          | π      | U+03C0  |
| `\rho`         | ρ      | U+03C1  |
| `\sigma`       | σ      | U+03C3  |
| `\tau`         | τ      | U+03C4  |
| `\upsilon`     | υ      | U+03C5  |
| `\phi`         | φ      | U+03C6  |
| `\chi`         | χ      | U+03C7  |
| `\psi`         | ψ      | U+03C8  |
| `\omega`       | ω      | U+03C9  |

### Uppercase Greek Letters

| Escape Sequence | Symbol | Unicode |
|----------------|--------|---------|
| `\Alpha`       | Α      | U+0391  |
| `\Beta`        | Β      | U+0392  |
| `\Gamma`       | Γ      | U+0393  |
| `\Delta`       | Δ      | U+0394  |
| `\Theta`       | Θ      | U+0398  |
| `\Lambda`      | Λ      | U+039B  |
| `\Pi`          | Π      | U+03A0  |
| `\Sigma`       | Σ      | U+03A3  |
| `\Phi`         | Φ      | U+03A6  |
| `\Psi`         | Ψ      | U+03A8  |
| `\Omega`       | Ω      | U+03A9  |

### Math Symbols

| Escape Sequence | Symbol | Unicode | Meaning              |
|----------------|--------|---------|----------------------|
| `\degree`      | °      | U+00B0  | Degree               |
| `\pm`          | ±      | U+00B1  | Plus-minus           |
| `\times`       | ×      | U+00D7  | Multiplication       |
| `\div`         | ÷      | U+00F7  | Division             |
| `\infty`       | ∞      | U+221E  | Infinity             |
| `\sqrt`        | √      | U+221A  | Square root          |
| `\approx`      | ≈      | U+2248  | Approximately equal  |
| `\neq`         | ≠      | U+2260  | Not equal            |
| `\leq`         | ≤      | U+2264  | Less than or equal   |
| `\geq`         | ≥      | U+2265  | Greater than or equal|

### 2. Subscripts and Superscripts (LaTeX-style Rendering)

CircuitJS1 now supports LaTeX-style subscripts and superscripts with proper rendering:

#### Subscript Syntax

| Syntax | Renders As | Description |
|--------|-----------|-------------|
| `Z_1` | Z₁ | Single character subscript |
| `Z_i` | Zᵢ | Single letter subscript |
| `Z_{banks}` | Z<sub>banks</sub> | Multi-character subscript (bracketed) |
| `V_{in}` | V<sub>in</sub> | Subscripted variable name |
| `\omega_0` | ω₀ | Greek symbol with subscript |

#### Superscript Syntax

| Syntax | Renders As | Description |
|--------|-----------|-------------|
| `x^2` | x² | Single character superscript |
| `E^n` | Eⁿ | Single letter superscript |
| `10^{-6}` | 10⁻⁶ | Multi-character superscript (bracketed) |
| `x^{max}` | x<sup>max</sup> | Superscripted text |

#### Combined Usage

| Syntax | Renders As | Description |
|--------|-----------|-------------|
| `Z_L^2` | Z<sub>L</sub>² | Variable with both subscript and superscript |
| `\beta_{max}` | β<sub>max</sub> | Greek symbol with subscript |
| `V_{out}^{2}` | V<sub>out</sub>² | Complex subscript with superscript |

**Rendering Details:**
- Subscripts: Rendered at 70% font size, offset downward by 30% of base font height
- Superscripts: Rendered at 70% font size, offset upward by 40% of base font height
- Width calculation accounts for script sizing for proper text alignment

## Usage Examples

### Global Support - Works Everywhere!

Greek symbols and LaTeX formatting now work in **any element** that displays text on the canvas:

#### **In EquationElm**
```
User enters equation: "a * sin(\omega*t + \phi)"
Displays as: "a * sin(ω*t + φ)"

User enters equation: "Z_{total} = R + j*X_L"
Displays as: "Zₜₒₜₐₗ = R + j*X_L" (with subscript rendering)
```

#### **In TextElm (Text Labels)**
```
Text: "Impedance: Z_L = \omega*L"
Displays: "Impedance: Z_L = ω*L" (with subscript)

Text: "Power: P = V^2 / R"
Displays: "Power: P = V² / R" (with superscript)
```

#### **In LabeledNodeElm**
```
Label: "V_{in}"
Creates node named "V_{in}" (internal storage)
Displays: "V_in" with subscript on canvas
```

#### **In Component Labels**
```
Relay label: "K_{relay}"
Displays: "K" with subscript "relay"
```

### Complex Examples

#### **Physics Formulas**
```
Text: "E = mc^2"
Displays: E = mc² (with superscript)

Text: "x(t) = x_0 * e^{-\beta*t}"
Displays: x(t) = x₀ * e^(-β*t) (with subscript and Greek)
```

#### **Electrical Engineering**
```
Label: "Z_{input}"
Equation: "V_{out} = V_{in} * R_2 / (R_1 + R_2)"
Displays with proper subscripts throughout
```

#### **System Dynamics**
```
Table header: "Stock_{banks}"
Equation: "\Delta S = R_{in} - R_{out}"
Displays: ΔS = R<sub>in</sub> - R<sub>out</sub>
```

### Equation Examples

```
User enters equation: "a * sin(\omega*t + b)"
Displays as: "a * sin(ω*t + b)"
```

### Equation Examples

#### Basic Greek Symbols

```
User enters equation: "a * sin(\omega*t + b)"
Displays as: "a * sin(ω*t + b)"
```

#### Exponential Decay
```
User enters equation: "\alpha * exp(-\beta*t)"
Displays as: "α * exp(-β*t)"
```

#### Voltage Difference
```
User enters equation: "\Delta V = R * I"
Displays as: "Δ V = R * I"
```

### Complex Example

```
Equation: "\omega = 2*\pi*f"
With parameter: a=60 (frequency)
Displays as: "ω = 2*π*60"
```

### Physics Formulas

```
Equation: "E = m*c*c"  (Energy)
Can be written as: "E = mc\times10^8"
Displays as: "E = mc×10^8"
```

```
Equation: "\theta = \omega*t + \phi"  (Angular motion)
Displays as: "θ = ω*t + φ"
```

## Technical Notes

### Implementation Details

**Global Conversion Points:**

1. **`Graphics.drawString(String str, int x, int y)`**
   - Converts Greek symbols immediately before rendering
   - Applies to ALL text drawn on the canvas
   - Zero performance impact for strings without backslashes

2. **`Graphics.measureWidth(String s)`**
   - Converts Greek symbols before measuring width
   - Ensures accurate text positioning and alignment
   - Caches converted strings for performance

3. **`EquationElm.draw()`**
   - Converts Greek symbols BEFORE parameter substitution
   - Prevents symbol sequences from being split during replacement
   - Ensures equation parameters work correctly with Greek letters

### Conversion Timing

The Greek symbol conversion happens **before** parameter substitution, ensuring that:
1. Greek symbols in the equation template are converted first
2. Parameter values (which may contain numbers/decimals) are substituted afterward
3. The final display shows both Greek symbols and parameter values correctly

### Performance

- **Zero overhead** for strings without backslashes (early exit in `convertGreekSymbols()`)
- **HashMap lookup** for symbol replacement (O(1) per symbol)
- **Cached in text rendering** system for repeated draws (5-second cache lifetime)
- **No double conversion** - each string converted only once before caching
- **Minimal memory impact** - ~2KB for symbol table, ~10-20KB for text cache

### Backward Compatibility

- Strings without escape sequences pass through unchanged
- Existing circuits continue to work identically
- No breaking changes to any APIs

## Extending the System

To add new symbols, edit `/src/com/lushprojects/circuitjs1/client/util/Locale.java`:

```java
static {
    greekSymbols = new HashMap<String, String>();
    // ... existing symbols ...
    
    // Add your new symbols:
    greekSymbols.put("\\yourSymbol", "\uXXXX");  // Replace XXXX with Unicode hex
}
```

## Future Enhancements

Potential extensions to this feature:

1. **Subscripts/Superscripts** - Support for `x_1`, `x^2` notation using Unicode subscript/superscript characters
2. **More math symbols** - Additional operators (∇, ∂, ∫, ∑, ∏, etc.)
3. **Auto-completion** - Suggest `\beta` when user types `\b` in text input fields
4. **Symbol palette** - UI widget to insert symbols without typing escape sequences
5. **Custom symbol definitions** - User-defined symbol mappings

## Example Circuits

### RC Time Constant

```
Equation: "V = V0 * exp(-t/(\tau))"
Where τ = RC
Displays: "V = V0 * exp(-t/(τ))"
```

### Angular Frequency

```
Equation: "\omega = 2*\pi*f"
Displays: "ω = 2*π*f"
```

### Damping Ratio

```
Equation: "x = exp(-\zeta*\omega*t)"
Displays: "x = exp(-ζ*ω*t)"
```

## Known Limitations

1. **Parser compatibility**: Greek letters cannot be used as variable names in expressions
   - Use ASCII names (`a`, `b`, `c`) in the actual equation
   - Display with Greek symbols via the escape sequences
   
2. **Edit-time display**: Greek symbols only render in the canvas view, not in edit dialogs
   - Users see `\beta` in the edit field
   - Canvas displays `β`

3. **Case sensitivity**: Escape sequences are case-sensitive
   - `\beta` → β (lowercase)
   - `\Beta` → Β (uppercase)

## Conclusion

This feature enables professional mathematical notation throughout CircuitJS1, making circuits more readable and scientifically accurate without requiring complex input methods or font changes.
