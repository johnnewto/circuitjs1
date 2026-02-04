# SFCR-Compatible Text Format for CircuitJS1

## Overview

This document proposes a human-readable text format for Stock-Flow Consistent (SFC) models in CircuitJS1, inspired by the R package [sfcr](https://github.com/joaomacalos/sfcr).

## Comparison: sfcr vs CircuitJS1

### sfcr R Syntax
```r
# Transaction Flow Matrix
tfm_sim <- sfcr_matrix(
  columns = c("Households", "Firms", "Government"),
  codes = c("h", "f", "g"),
  c("Consumption", h = "-Cd", f = "+Cs"),
  c("Govt. Exp.", f = "+Gs", g = "-Gd"),
  c("Factor Income", h = "W * Ns", f = "-W * Ns"),
  c("Taxes", h = "-TXs", g = "+TXd"),
  c("Ch. Money", h = "-d(Hh)", g = "d(Hs)")
)

# Equations
sim_eqs <- sfcr_set(
  TXs ~ TXd,
  YD ~ W * Ns - TXs,
  Cd ~ alpha1 * YD + alpha2 * Hh[-1],
  Hh ~ YD - Cd + Hh[-1],
  ...
)

# Parameters (exogenous)
sim_ext <- sfcr_set(
  Gd ~ 20,
  W ~ 1,
  alpha1 ~ 0.6,
  alpha2 ~ 0.4,
  theta ~ 0.2
)
```

### Current CircuitJS1 Format (1debug.txt)
The current format uses dump codes with space-separated escaped strings - not human-readable:
```
265 176 104 656 252 0 5 4 6 16 0 false 2 1 false 5 0 false Transaction\sFlow\sMatrix \0 Households Production Govt Σ ...
266 168 264 404 434 0 equations\s1 8 C_s C_d \0 \0 0.5 ...
```

## Proposed SFCR-Compatible Format

### Design Goals
1. Human-readable and editable in any text editor
2. Compatible with sfcr notation where possible
3. Easy to parse in Java (GWT-compatible)
4. Supports CircuitJS1-specific features (integration, hints, scopes)

### Proposed Syntax

```
% SFCR Model Definition
% Based on: Godley & Lavoie, Monetary Economics, Chapter 3 (SIM model)

#===============================================================================
# TRANSACTION FLOW MATRIX
#===============================================================================

@matrix Transaction_Flow_Matrix
columns: Households, Production, Govt
codes: h, p, g

| Flow            | h           | p           | g           |
|-----------------|-------------|-------------|-------------|
| Consumption     | -C_d        | C_s         |             |
| Govt Exp.       |             | G_s         | -G_d        |
| Wages           | W * N_s     | -W * N_s    |             |
| Taxes           | -T_s        |             | T_d         |
| Money stock     | -∆H_h       |             | ∆H_s        |

@end_matrix


#===============================================================================
# BALANCE SHEET
#===============================================================================

@matrix Balance_Sheet
columns: Households_b, Production_b, Government_b
codes: h, p, g
type: balance_sheet

| Stock           | h           | p           | g           |
|-----------------|-------------|-------------|-------------|
| Money stock     | H_h         |             | -H_s        |

@end_matrix


#===============================================================================
# EQUATIONS
#===============================================================================

@equations equations_1
  C_s ~ C_d                       # Supply = Demand identity
  G_s ~ G_d                       # Govt supply = demand
  T_s ~ T_d                       # Tax identity
  N_s ~ N_d                       # Labor market identity
  YD ~ W * N_s - T_s              # Disposable income
  T_d ~ θ * W * N_s               # Tax collection
  C_d ~ α_1 * YD + α_2 * H_h      # Consumption function
  H_s ~ integrate(G_d - T_d)      # Govt money stock (integral)
@end


@equations equations_2
  H_h ~ integrate(YD - C_d)       # Household money holdings
  Y ~ C_s + G_s                   # Total output
  N_d ~ Y / W                     # Labor demand
  ∆H_h ~ diff(H_h)                # Change in household money
  ∆H_s ~ diff(H_s)                # Change in govt money
@end


#===============================================================================
# PARAMETERS (Exogenous Variables)
#===============================================================================

@parameters Parameters
  α_1 = 0.6                       # Propensity to consume income
  α_2 = 0.4                       # Propensity to consume wealth
  θ = 0.2                         # Tax rate
  G_d = 20                        # Government demand
  W = 1                           # Wage rate
@end


#===============================================================================
# HINTS (Optional - for tooltips/documentation)
#===============================================================================

@hints
  Y: Output
  C_d: Consumption goods Supply/Demand
  C_s: Consumption goods Supply/Demand
  G_s: Services supplied to / demand by Govt
  T_s: Tax
  N_s: Supply/Demand for labour
  YD: Disposable Income
  G_d: Government demand for services
  W: Nominal Wages Rate
  H_s: Cash money, supplied by the central bank
  H_h: Cash money held by households
  α_1: Propensity to Spend Income
  α_2: Propensity to Spend Wealth
  θ: Tax Rate
@end


#===============================================================================
# SCOPES (Optional - for visualization)
#===============================================================================

@scope C_s
@scope Y
@scope ∆H_h
@scope ∆H_s
```

### Syntax Rules

#### Comments
- Lines starting with `#` are comments
- Inline comments with `#` after content
- Lines starting with `%` are metadata comments (preserved on export)

#### Blocks
- `@matrix <name>` ... `@end_matrix` - Define a matrix/table
- `@equations <name>` ... `@end` - Define equation blocks
- `@parameters <name>` ... `@end` - Define parameters
- `@hints` ... `@end` - Variable documentation
- `@scope <varname>` - Add scope for variable

#### Matrix Format
- `columns: col1, col2, ...` - Column names
- `codes: c1, c2, ...` - Short codes for columns (optional)
- `type: transaction_flow | balance_sheet` - Matrix type (optional)
- Markdown-style table with `|` separators
- First column is flow/stock name
- Empty cells: leave blank or use `0`

#### Equations
- Use `~` for assignment (sfcr style) or `=` 
- Greek symbols: use Unicode (α, θ, ∆) or escape codes (\alpha_1, \theta, \Delta)
- Integration: `integrate(expr)` or `∫(expr)`
- Differentiation: `diff(expr)` or `d(expr)` or `∆(expr)`
- Lagged values: `H_h[-1]` means previous timestep's value (sfcr style)
  - Maps to: `H_h + diff(H_h)` in CircuitJS1 or use integrate pattern

#### Parameters
- Use `=` for constant assignment
- Optional slider range: `α_1 = 0.6 [0, 1]`

### Mapping to CircuitJS1 Elements

| SFCR Block | CircuitJS1 Element |
|------------|-------------------|
| `@matrix` (transaction_flow) | `SFCTableElm` (265) |
| `@matrix` (balance_sheet) | `SFCTableElm` (265) |
| `@equations` | `EquationTableElm` (266) |
| `@parameters` | `EquationTableElm` (266) |
| `@hints` | `% Hint` lines |
| `@scope` | `ProbeElm` (207) + `ScopeElm` (403) |

### Conversion Notes

#### Lagged Values (`[-1]` notation)
sfcr uses `Hh[-1]` to reference previous period values. In CircuitJS1:

**Option 1**: Use integration pattern
```
# sfcr: Hh ~ YD - Cd + Hh[-1]
# CircuitJS1: H_h ~ integrate(YD - C_d)
```

**Option 2**: Explicit lagged variable (requires implementation)
```
# Create internal lagged variable tracking
H_h_prev = lag(H_h)
H_h ~ YD - C_d + H_h_prev
```

#### Greek Symbols
The parser should map common patterns:
- `alpha1`, `alpha_1`, `α_1`, `\alpha_1` → `α_1`
- `theta`, `θ`, `\theta` → `θ`
- `Delta`, `d()`, `∆`, `\Delta` → `∆` (for diff)

## Implementation Plan

### Phase 1: Parser (SFCRParser.java)
1. Tokenizer for block detection
2. Matrix parser (markdown table → SFCTableElm)
3. Equation parser (reuse ExprParser)
4. Parameter parser
5. Hint/scope parser

### Phase 2: Integration with CirSim
1. Detect SFCR format (starts with `%` or `@` blocks)
2. Parse and create elements
3. Position elements on canvas (auto-layout)
4. Connect scopes

### Phase 3: Export (Optional)
1. Export SFC model to SFCR format
2. Round-trip capability

## Example: Full SIM Model

See `/tests/sfcr-sim-model.txt` for complete example.

## References

- [sfcr R package](https://github.com/joaomacalos/sfcr)
- Godley & Lavoie (2007), *Monetary Economics*
- [CircuitJS1 SFC Documentation](../docs-template/docs/money/)
