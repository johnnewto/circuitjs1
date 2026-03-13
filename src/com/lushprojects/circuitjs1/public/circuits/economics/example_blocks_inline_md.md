# CircuitJS1 SFCR Example
# Demonstrates block syntax + inline markdown that stays associated with blocks.

@init
  timestep: 0.1
  voltageUnit: $
  timeUnit: yr
  showDots: true
  showVolts: true
  showValues: true
  showPower: false
  autoAdjustTimestep: false
  equationTableMnaMode: true
  equationTableNewtonJacobianEnabled: false
  equationTableTolerance: 0.001
  convergenceCheckThreshold: 100
  infoViewerUpdateIntervalMs: 200
@end

# Inline Markdown + Plot Directives

This paragraph is plain inline markdown and should render in the Info Viewer.

```{circuit}
@plot x=680 y=280
vars: Y, C_d
title: Plot using vars/ylabel/points/xlabel
ylabel: Value $
xlabel: Time (years)
points: 200
xmin: 0
xmax: 120
ymin: 0
ymax: 120
@end
```

This is another inline markdown paragraph between blocks.

@plot
plot: Y, YD
title: Plot using plot/yaxis/window/xaxis
yaxis: Value
xaxis: Time
window: 150
xrange: 0, 150
yrange: 0, 120
legend: true
style: lines+points
@end

# Equation Blocks

## R-style in fenced block (sfcr_set)
Inline note associated with the next block.

```{r}
Eq_RStyle_1 <- sfcr_set(
  # [ x=176 y=240 ]
  e1 = YD ~ W * N_s - T_s,  # Disposable income [mode=voltage, slider=a, sliderValue=0.5 ]
  e2 = C_d ~ alpha_1 * YD + alpha_2 * H_h,  # Consumption [mode=voltage, slider=b, sliderValue=0.5 ]
  e3 = Y ~ C_s + G_s  # Output [mode=voltage, slider=c, sliderValue=0.5 ]
)
```

## R-style unfenced (optional fence)
This inline text should remain attached to this next equation block.

Eq_RStyle_2 <- sfcr_set(
  # [ x=432 y=240 ]
  e1 = H_h ~ integrate(YD - C_d),  # Household cash [mode=voltage, slider=a, sliderValue=0.5 ]
  e2 = N_d ~ Y / W  # Labour demand [mode=voltage, slider=b, sliderValue=0.5 ]
)

## Native @equations block
@equations Eq_Block_1 x=176 y=352
  alpha_1 ~ 0.6  # Propensity to spend out of income
  alpha_2 ~ 0.4  # Propensity to spend out of wealth
  theta ~ 0.2    # Tax rate
  G_d ~ 20       # Govt demand
  W ~ 1          # Wage rate
@end

# Matrix Blocks

## R-style matrix in fenced block
```{r}
Flow_Matrix_R <- sfcr_matrix(
  # [ x=176 y=88 type: transaction_flow ]
  columns = c("Households", "Production", "Govt"),
  codes = c("Households", "Production", "Govt"),
  c("Consumption", Households = "-C_d", Production = "C_s", Govt = ""),
  c("Govt Expenditures", Households = "", Production = "G_s", Govt = "-G_d"),
  c("Taxes", Households = "-T_s", Production = "", Govt = "T_d")
)
```

## Native @matrix block
@matrix Flow_Matrix_Block x=496 y=88
  type: transaction_flow

| Transaction       | Households | Production | Govt |
|-------------------|------------|------------|------|
| Consumption       | -C_d       | C_s        |      |
| Govt Expenditures |            | G_s        | -G_d |
| Taxes             | -T_s       |            | T_d  |
@end

# Sankey / Scope / Hints

@sankey x=864 y=120
  source: Flow_Matrix_Block
  layout: linear
  width: 520
  height: 300
  showScaleBar: false
  useHighWaterMark: true
  showFlowValues: true
@end

# Legacy one-line scope form (variable probe style)
@scope Y

# Block scope form
@scope DemoScope position=0
  title: Output Scope
  yaxis: Value
  window: 200
@end

@hints
  Y: National output
  C_d: Consumption demand
  H_h: Household money holdings
@end

# Optional raw circuit passthrough block
@circuit
x 160 -56 458 -53 4 18 Example\sinline\smarkdown\sand\sblocks 808080FF U:EXAMPLE01
@end

# Optional @info block (supported but not required)
@info
This file intentionally keeps most documentation inline.
@end
