# CircuitJS1 SFCR Export
# Generated from circuit simulation

```{r}
@init
  timestep: 1
  voltageUnit: $
  timeUnit: yr
  showDots: true
  showVolts: true
  showValues: true
  showPower: false
  autoAdjustTimestep: false
  equationTableMnaMode: true
  equationTableNewtonJacobianEnabled: true
  equationTableTolerance: 0.0001
  convergenceCheckThreshold: 100
  infoViewerUpdateIntervalMs: 200
@end
```

## Equation table
first table 

```{r}
equations_1A <- sfcr_set(
  # [ x=168 y=264 ]
  e1 = C_s ~ C_d,  # Consumption goods Supply/Demand  [mode=voltage, slider=a, sliderValue=0.5 ]
  e2 = G_s ~ G_d,  # Services supplied to / demand by Govt  [mode=voltage, slider=b, sliderValue=0.5 ]
  e3 = T_s ~ T_d,  # Tax  [mode=voltage, slider=c, sliderValue=0.5 ]
  e4 = N_s ~ N_d,  # Supply/Demand for labour  [mode=voltage, slider=d, sliderValue=0.5 ]
  e5 = YD ~ W * N_s - T_s,  # Disposable Income  [mode=voltage, slider=e, sliderValue=0.5 ]
  e6 = T_d ~ \theta * W * N_s,  # Tax  [mode=voltage, slider=f, sliderValue=0.5 ]
  e7 = C_d ~ \alpha_1*YD+ \alpha_2*H_h,  # Consumption goods Supply/Demand  [mode=voltage, slider=g, sliderValue=0.5 ]
  e8 = H_s ~ integrate(G_d - T_d)  # cash money  [mode=voltage, sliderValue=0 ]
)
```

## Equation table
second table 

```{r}
equations_2 <- sfcr_set(
  # [ x=440 y=264 ]
  e1 = H_h ~ integrate(YD - C_d),  # Cash money held by households  [mode=voltage, slider=a, sliderValue=0.5 ]
  e2 = Y ~ C_s + G_s,  # Output  [mode=voltage, slider=b, sliderValue=0.5 ]
  e3 = N_d ~ Y / W  # Supply/Demand for labour  [mode=voltage, sliderValue=0 ]
)
```

## Parameters
parameter table !!

```{r}
Parameters <- sfcr_set(
  # [ x=440 y=352 ]
  e1 = \alpha_1 ~ 0.6,  # Propensity to Spend Income  [mode=voltage, slider=a, sliderValue=0.5 ]
  e2 = \alpha_2 ~ 0.4,  # Propensity to Spend Wealth  [mode=voltage, slider=b, sliderValue=0.5 ]
  e3 = \theta ~ 0.2,  # Tax Rate  [mode=voltage, slider=c, sliderValue=0.5 ]
  e4 = G_d ~ 20,  # Government demand for services  [mode=voltage, slider=d, sliderValue=0.5 ]
  e5 = W ~ 1  # Nominal Wages Rate  [mode=voltage, sliderValue=0 ]
)
```

## Balance_Sheet
balance !! 

```{r}
Balance_Sheet <- sfcr_matrix(
  # [ x=176 y=24 type: transaction_flow ]
  columns = c("Households_b", "Production_b", "Government_b"),
  codes = c("Households_b", "Production_b", "Government_b"),
  c("Money stock", Households_b = "H_h", Production_b = "", Government_b = "-H_s")
)
```

## Transaction_Flow_Matrix
TFM !!

```{r}
Transaction_Flow_Matrix <- sfcr_matrix(
  # [ x=176 y=104 type: transaction_flow ]
  columns = c("Households", "Production", "Govt"),
  codes = c("Households", "Production", "Govt"),
  c("Consumption", Households = "-C_d", Production = "C_s", Govt = ""),
  c("Govt Expenditures", Households = "", Production = "G_s", Govt = "-G_d"),
  c("Wages", Households = "W * N_s", Production = "-W * N_s", Govt = ""),
  c("Taxes", Households = "-T_s", Production = "", Govt = "T_d"),
  c("Money stock", Households = "-\\DeltaH_h", Production = "", Govt = "\\DeltaH_s")
)
```

## Sankey Diagram
sankey !! 

```{r}
@sankey x=672 y=-8
  source: Transaction Flow Matrix
  layout: linear
  width: 600
  height: 400
  showScaleBar: false
  useHighWaterMark: true
  showFlowValues: true
@end
```

```{r}
@circuit
x 168 -76 337 -73 4 24 SIM\sSFC\smodel 808080FF U:_JS6um
207 680 296 743 296 36 Y U:-6Q_F4
x 183 -51 509 -48 4 12 This\scode\sreplicates\sresults\sin\sthe\sbook\sMonetary\sEconomics: 808080FF U:MpBpa6
x 180 -9 536 -6 4 12 \sby\sWynne\sGodley\sand\sMarc\sLavoie,\schapter\s3,\sfigures\s3.2\sand\s3.3. 808080FF U:rCjs3e
x 183 -31 577 -28 4 12 \sAn\sIntegrated\sApproach\sto\sCredit,\sMoney,\sIncome,\sProduction\sand\sWealth 808080FF U:cdBS8b
207 832 288 864 288 36 \\DeltaH_h U:MeqIB3
207 832 272 864 272 4 \\DeltaH_s U:5DBes4
431 672 -80 688 -64 0 50 true false U:0AZ1rw
r 824 -88 936 -88 0 1000 U:kSRwWr
207 936 -88 967 -88 36 Y2 U:g5KXPD
207 824 -88 783 -88 36 Y U:kU1zw1
@end
```

```{r}
@scope Embedded_Scope_1 position=-1
  x1: 672
  y1: 320
  x2: 928
  y2: 496
  elmUid: 85xm31
  speed: 2
  flags: x2001206
  source: uid:-6Q_F4 value:0
  trace: uid:-6Q_F4 value:3
@end
```

