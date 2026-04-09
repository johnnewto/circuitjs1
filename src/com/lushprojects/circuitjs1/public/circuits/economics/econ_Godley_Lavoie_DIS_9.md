```{r}
dis_eqs <- sfcr_set(
  # [ x=128 y=56 uid=yzYTYM invisible=false ]
  # The production decision
  y ~ s_E + inv_E - last(inv),  # [mode=voltage ]
  inv_T ~ sigma_T * s_E,  # [mode=voltage ]
  inv_E ~ last(inv) + gamma * (inv_T - last(inv)),  # [mode=voltage ]
  inv ~ last(inv) + (y - s),  # [mode=voltage ]
  s_E ~ beta * last(s) + (1 - beta) * last(s_E),  # [mode=voltage ]
  s ~ c,  # [mode=voltage ]
  N ~ y / pr,  # [mode=voltage ]
  WB ~ N * W,  # [mode=voltage ]
  UC ~ WB / y,  # [mode=voltage ]
  INV ~ inv * UC,  # [mode=voltage ]
  # The pricing decision
  S ~ p * s,  # [mode=voltage ]
  p ~ (1 + phi) * NHUC,  # [mode=voltage ]
  NHUC ~ (1 - sigma_T) * UC + sigma_T * (1 + last(rl)) * last(UC),  # [mode=voltage ]
  EF ~ S - WB + (INV - last(INV)) - last(rl) * last(INV),  # [mode=voltage ]
  # The banking system
  Ld ~ INV,  # [mode=voltage ]
  Ls ~ Ld,  # [mode=voltage ]
  Ms ~ Ls,  # [mode=voltage ]
  rm ~ rl - add,  # [mode=voltage ]
  EFb ~ last(rl) * last(Ls) - last(rm) * last(Mh),  # [mode=voltage ]
  # The consumption decision
  YD ~ WB + EF + EFb + last(rm) * last(Mh),  # [mode=voltage ]
  Mh ~ last(Mh) + YD - C,  # [mode=voltage ]
  ydhs ~ c + (mh - last(mh)),  # [mode=voltage ]
  C ~ c * p,  # [mode=voltage ]
  mh ~ Mh / p,  # [mode=voltage ]
  c ~ alpha0 + \alpha_1 * ydhs_E + \alpha_2 * last(mh),  # [mode=voltage ]
  ydhs_E ~ epsilon * last(ydhs) + (1 - epsilon) * last(ydhs_E)  # [mode=voltage ]
)
```

```{r}
dis_ext <- sfcr_set(
  # [ x=48 y=504 uid=bSDfq8 invisible=false ]
  # Exogenous variables
  rl ~ 0.025,  # [mode=voltage ]
  pr ~ 1,  # [mode=voltage ]
  W ~ 0.75,  # [mode=voltage ]
  # Parameters
  add ~ 0.02,  # [mode=param ]
  alpha0 ~ 15,  # [mode=param ]
  \alpha_1 ~ 0.8,  # [mode=param ]
  \alpha_2 ~ 0.1,  # [mode=param ]
  beta ~ 0.75,  # [mode=param ]
  epsilon ~ 0.75,  # [mode=param ]
  gamma ~ 0.25,  # [mode=param ]
  phi ~ 0.25,  # [mode=param ]
  sigma_T ~ 0.15  # [mode=param ]
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
tfm_dis <- sfcr_matrix(
  # [ x=208 y=312 uid=fXxCPm type: transaction_flow invisible=false ]
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
  uid:yzYTYM z:0
  uid:bSDfq8 z:1
  uid:FmL6J4 z:2
  uid:fXxCPm z:2
@end
```

