```{r}
dis_eqs <- sfcr_set(
  # [ x=128 y=56 uid=yzYTYM invisible=false ]
  # The production decision
  y ~ s_E + inv_E - last(inv),  # Real output  [mode=param ]
  inv_T ~ sigma_T * s_E,  # Target real inventories  [mode=param ]
  inv_E ~ last(inv) + gamma * (inv_T - last(inv)),  # Expected real inventories  [mode=param ]
  inv ~ last(inv) + (y - s),  # Real inventories  [mode=param ]
  s_E ~ beta * last(s) + (1 - beta) * last(s_E),  # Expected real sales  [mode=param ]
  s ~ c,  # Real sales  [mode=param ]
  N ~ y / pr,  # Employment level  [mode=param ]
  WB ~ N * W,  # Wage bill  [mode=param ]
  UC ~ WB / y,  # Unit cost  [mode=param ]
  INV ~ inv * UC,  # Inventories at current cost  [mode=param ]
  # The pricing decision
  S ~ p * s,  # Sales at current prices  [mode=param ]
  p ~ (1 + phi) * NHUC,  # Price level  [mode=param ]
  NHUC ~ (1 - sigma_T) * UC + sigma_T * (1 + last(rl)) * last(UC),  # Normal historic unit cost  [mode=param ]
  EF ~ S - WB + (INV - last(INV)) - last(rl) * last(INV),  # Realized firm profits  [mode=param ]
  # The banking system
  Ld ~ INV,  # Demand for loans  [mode=param ]
  Ls ~ Ld,  # Supply of loans  [mode=param ]
  Ms ~ Ls,  # Supply of deposits  [mode=param ]
  rm ~ rl - add,  # Interest rate on deposits  [mode=param ]
  EFb ~ last(rl) * last(Ls) - last(rm) * last(Mh),  # Realized bank profits  [mode=param ]
  # The consumption decision
  YD ~ WB + EF + EFb + last(rm) * last(Mh),  # Disposable income  [mode=param ]
  Mh ~ last(Mh) + YD - C,  # Deposits held by households  [mode=param ]
  ydhs ~ c + (mh - last(mh)),  # Haig-Simons measure of real disposable income  [mode=param ]
  C ~ c * p,  # Consumption at current prices  [mode=param ]
  mh ~ Mh / p,  # Real value of household deposits  [mode=param ]
  c ~ alpha0 + \alpha_1 * ydhs_E + \alpha_2 * last(mh),  # Real consumption  [mode=param ]
  ydhs_E ~ epsilon * last(ydhs) + (1 - epsilon) * last(ydhs_E)  # Expected Haig-Simons real disposable income  [mode=param ]
)
```

```{r}
dis_ext <- sfcr_set(
  # [ x=752 y=88 uid=bSDfq8 invisible=false ]
  # Exogenous variables
  rl ~ 0.025,  # Interest rate on loans  [mode=param ]
  pr ~ 1,  # Labor productivity  [mode=param ]
  W ~ 0.75,  # Wage rate  [mode=param ]
  # Parameters
  add ~ 0.02,  # Spread of loan rate over deposit rate  [mode=param ]
  alpha0 ~ 15,  # Autonomous consumption  [mode=param ]
  \alpha_1 ~ 0.8,  # Propensity to consume out of income  [mode=param ]
  \alpha_2 ~ 0.1,  # Propensity to consume out of wealth  [mode=param ]
  beta ~ 0.75,  # Expectation parameter for real sales  [mode=param ]
  epsilon ~ 0.75,  # Expectation parameter for real disposable income  [mode=param ]
  gamma ~ 0.25,  # Speed of adjustment of inventories to target  [mode=param ]
  phi ~ 0.25,  # Mark-up on unit cost  [mode=param ]
  sigma_T ~ 0.15  # Target inventories-to-sales ratio  [mode=param ]
)
```

```{r}
bs_dis <- sfcr_matrix(
  # [ x=144 y=-72 uid=FmL6J4 type: transaction_flow invisible=false ]
  columns = c("Households", "Production firms", "Banks"),
  codes = c("H1", "P2", "B3"),
  type = c("", "", ""),
  c("Money", H1 = "+Mh", P2 = "", B3 = "-Ms"),
  c("Loans", H1 = "", P2 = "-Ld", B3 = "+Ls"),
  c("Inventories", H1 = "", P2 = "+INV", B3 = ""),
  c("Balance", H1 = "-Mh", P2 = "", B3 = "")
)
```

```{r}
tfm <- sfcr_matrix(
  # [ x=160 y=360 uid=fXxCPm type: transaction_flow invisible=false ]
  columns = c("Households", "Firms_current", "Firms_capital", "Banks_current", "Banks_capital"),
  codes = c("H1", "F2", "F3", "B4", "B5"),
  type = c("", "", "", "", ""),
  c("Consumption", H1 = "-C", F2 = "+C", F3 = "", B4 = "", B5 = ""),
  c("Ch. Inventories", H1 = "", F2 = "+d(INV)", F3 = "-d(INV)", B4 = "", B5 = ""),
  c("Wages", H1 = "+WB", F2 = "-WB", F3 = "", B4 = "", B5 = ""),
  c("Interest on loans", H1 = "", F2 = "-last(rl) * last(Ld)", F3 = "", B4 = "last(rl) * last(Ls)", B5 = ""),
  c("Entrepreneurial Profits", H1 = "+EF", F2 = "-EF", F3 = "", B4 = "", B5 = ""),
  c("Banks profits", H1 = "+EFb", F2 = "", F3 = "", B4 = "-EFb", B5 = ""),
  c("Interest on deposits", H1 = "+last(rm) * last(Mh)", F2 = "", F3 = "", B4 = "-last(rm) * last(Mh)", B5 = ""),
  c("Change loans", H1 = "", F2 = "", F3 = "+d(Ld)", B4 = "", B5 = "-d(Ls)"),
  c("Change deposits", H1 = "-d(Mh)", F2 = "", F3 = "", B4 = "", B5 = "+d(Ms)")
)
```

```{r}
@startuml x=-576 y=-72 uid=U0b7xP w=669 h=526 width=660 scale=0.981203007518797
   source: tfm
@end
```

```{r}
@init
  timestep: 1
  voltageUnit: $
  timeUnit: yr
  showDots: false
  showVolts: true
  showValues: true
  showPower: false
  autoAdjustTimestep: false
  equationTableMnaMode: true
  EqnTable Newton Jacobian: true
  EqnTable Broyden Jacobian: true
  equationTableTolerance: 0.00001
  lookupMode: pwl
  lookupClamp: true
  convergenceCheckThreshold: 100
  infoViewerUpdateIntervalMs: 200
  Auto-Open Model Info on Load: true
@end
```

```{r}
@zorder
  uid:FmL6J4 z:0
  uid:bSDfq8 z:1
  uid:yzYTYM z:2
  uid:punvQr z:3
  uid:U0b7xP z:4
  uid:fXxCPm z:5
@end
```

```{r}
@circuit
431 640 128 704 160 0 100 true false U:punvQr Z:3
@end
```

