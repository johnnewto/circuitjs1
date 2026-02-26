# SFCR Format Reference

Human-readable text format for Stock-Flow Consistent (SFC) models in CircuitJS1.

**Implementation**: [SFCRParser.java](../src/com/lushprojects/circuitjs1/client/SFCRParser.java), [SFCRExporter.java](../src/com/lushprojects/circuitjs1/client/SFCRExporter.java)

## Usage

| Action | How |
|--------|-----|
| Import | **File → Import from Text** or **File → Open** |
| Export | **File → Export as SFCR** |
| URL | `?ctz=` compressed parameter |

Auto-detected when content contains `@matrix`, `@equations`, `@parameters`, `@init`, `@hints`, `@scope`, `@circuit`, or `@info` blocks.

---

## Block Reference

### @init — Simulation Settings

```
@init
  timestep: 0.1
  voltageUnit: $
  timeUnit: yr
  showDots: false
  showVolts: false
@end
```

| Setting | Description |
|---------|-------------|
| `timestep` | Simulation timestep (seconds) |
| `voltageUnit` | Display unit symbol (default: `V`, use `$` for economics) |
| `timeUnit` | Time unit symbol (e.g., `yr`, `qtr`) |
| `voltageRange` | Voltage display range |
| `showToolbar` | Show/hide toolbar (`true`/`false`) |
| `showDots` | Show current flow dots |
| `showVolts` | Show voltage colors |
| `showValues` | Show component values |
| `showPower` | Show power dissipation |

---

### @info — Model Documentation

Markdown content displayed via **View → Model Info**.

```
@info
# Model Title

Description of the model...

## Key Variables
- Y: National income
- C: Consumption
@end
```

---

### @equations — Equations & Constants

All equations and constants. Creates an `EquationTableElm`.

```
@equations Model x=200 y=100
  # Constants
  α_1 ~ 0.6                       # Propensity to consume income
  α_2 ~ 0.4                       # Propensity to consume wealth
  θ ~ 0.2                         # Tax rate
  G_d ~ 20                        # Government demand
  W ~ 1                           # Wage rate
  
  # Equations
  YD ~ W * N_s - T_s              # Disposable income
  C_d ~ α_1 * YD + α_2 * H_h      # Consumption function
  H_h ~ integrate(YD - C_d)       # Stock (integration)
  ∆H_h ~ diff(H_h)                # Flow (differentiation)
@end
```

**Position:** Optional `x=N y=N` places the table at specific canvas coordinates.

- Use `~` (sfcr-style) or `=` for assignment
- Inline `# comment` becomes a hint (unless overridden by `@hints`)
- Self-accumulation `X ~ expr + X[-1]` auto-converts to `integrate(expr)`

> **Note:** `@parameters` is accepted as an alias for `@equations` (for sfcr compatibility).

---

### @matrix — Transaction Flow Matrix

SFC transaction matrix. Creates an `SFCTableElm`.

```
@matrix Transaction_Flow x=400 y=100
columns: Households, Firms, Govt

| Transaction     | Households  | Firms       | Govt        |
|-----------------|-------------|-------------|-------------|
| Consumption     | -C_d        | C_s         |             |
| Govt Spending   |             | G_s         | -G_d        |
| Wages           | W * N_s     | -W * N_s    |             |
| Taxes           | -T_s        |             | T_d         |
@end
```

**Position:** Optional `x=N y=N` places the table at specific canvas coordinates.

- `columns:` defines column headers (optional if table header row present)
- `codes:` short codes for R-style compatibility (optional)
- `type: transaction_flow | balance_sheet` (optional)
- Empty cells: leave blank or use `0`
- Σ column auto-added on import

---

### @hints — Variable Documentation

Tooltips for variables. Overrides inline comments.

```
@hints
  Y: National output
  C_d: Consumption demand
  H_h: Household money holdings
  α_1: Propensity to spend income
@end
```

---

### @scope — Scope Viewer (UID Traces)

Defines a docked scope with one source trace and optional additional traces.

```
@scope Main_Scope position=0
  speed: 64
  flags: x800060
  source: uid:Ab3_Xz value:0
  trace: uid:Q9m-L2 value:0
@end
```

| Field | Description |
|-------|-------------|
| `position` | Dock position/index used by the scope layout |
| `speed` | Scope sample speed |
| `flags` | Scope flags (decimal or `x...` hex) |
| `source` | Primary trace (`uid` + `value`) |
| `trace` | Additional trace (`uid` + `value`) |

Legacy single-line form is still accepted for import compatibility:

```
@scope Y
```

---

### @circuit — Raw Circuit Elements

Pass-through for native CircuitJS1 element dump format.

```
@circuit
  431 480 64 592 160 0 50 true false
  w 96 48 96 112 0
@end
```

---

## Element Mapping

| SFCR Block | CircuitJS1 Element |
|------------|-------------------|
| `@matrix` | `SFCTableElm` (265) |
| `@equations` | `EquationTableElm` (266) |
| `@parameters` | `EquationTableElm` (266) — import alias |
| `@hints` | `HintRegistry` |
| `@scope` | `Scope` entries (resolved by element UID) |
| `@circuit` | Native elements |

---

## Expression Syntax

| Feature | Syntax |
|---------|--------|
| Assignment | `~` or `=` |
| Integration | `integrate(expr)` |
| Differentiation | `diff(expr)` |
| Lagged value | `X[-1]` → converts to `integrate()` pattern |
| Greek letters | Unicode (`α`, `θ`, `∆`) or ASCII (`alpha`, `theta`) |

---

## R sfcr Compatibility

The parser also supports R-style `sfcr_matrix()` and `sfcr_set()` syntax:

```r
tfm <- sfcr_matrix(
  columns = c("Households", "Firms"),
  codes = c("h", "f"),
  c("Consumption", h = "-C_d", f = "C_s")
)

eqs <- sfcr_set(
  YD ~ W * N_s - T_s,
  C_d ~ alpha1 * YD
)
```

---

## Example Files

| File | Description |
|------|-------------|
| [sfcr-sim-model.txt](../tests/sfcr-sim-model.txt) | SIM model (G&L Ch.3) |
| [sfcr-pc-model.txt](../tests/sfcr-pc-model.txt) | Portfolio Choice model |
| [sfcr-lp-model.txt](../tests/sfcr-lp-model.txt) | Liquidity Preference model |
| [sfcr-r-style.txt](../tests/sfcr-r-style.txt) | R sfcr-style syntax |

---

## References

- [sfcr R package](https://github.com/joaomacalos/sfcr)
- Godley & Lavoie (2007), *Monetary Economics*
- [CircuitJS1 SFC Documentation](../docs-template/docs/money/)
