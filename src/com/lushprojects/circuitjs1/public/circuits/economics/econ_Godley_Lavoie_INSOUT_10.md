# CircuitJS1 SFCR Export
# Godley and Lavoie (2007) Chapter 10: INSOUT

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
  equationTableTolerance: 0.0001
  infoViewerUpdateIntervalMs: 200
@end
```

# INSOUT Model

## Purpose

This file packages the **INSOUT** model from **Godley and Lavoie (2007), chapter 10** as a CircuitJS1 economics circuit.

It follows the corrected **sfcr** replication for the equation system and parameterization, while also recording steady-state reference values recovered from the Python replication notebook.

The model includes:

- firms with inventories financed by bank credit
- households with checking deposits, time deposits, bills, bonds, and cash
- government bills and bonds
- a central bank supplying high-powered money and advances
- banks adjusting deposit and loan rates in response to liquidity and profitability targets
- inflation dynamics through wage adjustment and cost-plus pricing

## Source Notes

- Equations and parameters follow the `sfcr` article **Chapter 10: Model INSOUT**.
- The corrected `sfcr` version fixes several issues relative to Zezza's program, including:
  - `YDr` using `M2h[-1]` rather than `M2d[-1]`
  - `Vnc ~ V - Hhh`
  - realized deposit supply using `M1s ~ M1h` and `M2s ~ M2h`
  - `Ad` using `z3`
  - `omegaT` using the exponential specification
- The notebook-derived reference values below are included as steady-state seeds/reference values where the naming maps cleanly.
- The baseline uses `top ~ 0.06`, which is the corrected value used to avoid the liquidity-ratio ping-pong issue.

## Equations

```{r}
INSOUT_Firms <- sfcr_set(
  # [ x=-3328 y=64 ]
  e1 = y ~ sE + (invE - inv[-1]),
  e2 = N ~ y / pr,
  e3 = WB ~ N * W,
  e4 = UC ~ WB / y,
  e5 = sE ~ beta * s[-1] + (1 - beta) * sE[-1],
  e6 = invT ~ sigmaT * sE,
  e7 = sigmaT ~ sigma0 - sigma1 * rl,
  e8 = invE ~ inv[-1] + gamma * (invT - inv[-1]),
  e9 = p ~ (1 + tau) * (1 + phi) * NHUC,
  e10 = NHUC ~ (1 - sigmaT) * UC + sigmaT * (1 + rl[-1]) * UC[-1],
  e11 = s ~ c + g,
  e12 = S ~ s * p,
  e13 = inv ~ inv[-1] + y - s,
  e14 = sigmas ~ inv[-1] / s,
  e15 = INV ~ inv * UC,
  e16 = Ld ~ INV,
  e17 = FXf ~ S - TX - WB + (INV - INV[-1]) - rl[-1] * INV[-1],
  e18 = pi ~ (p / p[-1]) - 1
)
```

```{r}
INSOUT_Households <- sfcr_set(
  # [ x=-3328 y=448 ]
  e1 = YDr ~ FX + WB + rm[-1] * M2h[-1] + rb[-1] * Bhh[-1] + BLh[-1],
  e2 = CG ~ (pbl - pbl[-1]) * BLh[-1],
  e3 = YDhs ~ YDr + CG,
  e4 = FX ~ FXf + FXb,
  e5 = V ~ V[-1] + YDhs - C,
  e6 = Vnc ~ V - Hhh,
  e7 = ydr ~ YDr / p - pi * (V[-1] / p),
  e8 = ydhs ~ (YDr - pi * V[-1] + CG) / p,
  e9 = v ~ V / p,
  e10 = c ~ alpha0 + alpha1 * ydrE + alpha2 * v[-1],
  e11 = ydrE ~ epsilon * ydr[-1] + (1 - epsilon) * ydrE[-1],
  e12 = C ~ p * c,
  e13 = YDrE ~ p * ydrE + pi * (V[-1] / p),
  e14 = VE ~ V[-1] + (YDrE - C),
  e15 = Hhd ~ lambdac * C,
  e16 = VncE ~ VE - Hhd,
  e17 = ERrbl ~ rbl
)
```

```{r}
INSOUT_Household_Portfolio <- sfcr_set(
  # [ x=-3328 y=832 ]
  e1 = M2d ~ VncE * (lambda20 + lambda22 * rm + lambda23 * rb + lambda24 * ERrbl + lambda25 * (YDrE / VncE)),
  e2 = Bhd ~ VncE * (lambda30 + lambda32 * rm + lambda33 * rb + lambda34 * ERrbl + lambda35 * (YDrE / VncE)),
  e3 = BLd ~ (VncE / pbl) * (lambda40 + lambda42 * rm + lambda43 * rb + lambda44 * ERrbl + lambda45 * (YDrE / VncE)),
  e4 = M1d ~ VncE * (lambda10 + lambda12 * rm + lambda13 * rb + lambda14 * ERrbl + lambda15 * (YDrE / VncE)),
  e5 = M1d2 ~ VncE - M2d - Bhd - pbl * BLd,
  e6 = Hhh ~ Hhd,
  e7 = Bhh ~ Bhd,
  e8 = BLh ~ BLd,
  e9 = M1hN ~ Vnc - M2d - Bhd - pbl * BLd,
  e10 = z1 ~ if(M1hN > 0) {1} else {0},
  e11 = z2 ~ 1 - z1,
  e12 = M1h ~ M1hN * z1,
  e13 = M2hN ~ M2d,
  e14 = M2h ~ M2d * z1 + (Vnc - Bhh - pbl * BLd) * z2
)
```

```{r}
INSOUT_Public_CentralBank_Banks <- sfcr_set(
  # [ x=-3328 y=1232 ]
  e1 = TX ~ S * (tau / (1 + tau)),
  e2 = G ~ p * g,
  e3 = PSBR ~ G + rb[-1] * Bs[-1] + BLs[-1] - (TX + FXcb),
  e4 = Bs ~ Bs[-1] + PSBR - (BLs - BLs[-1]) * pbl,
  e5 = BLs ~ BLd,
  e6 = pbl ~ 1 / rbl,
  e7 = GD ~ GD[-1] + PSBR,
  e8 = Hs ~ Bcb + As,
  e9 = Hbs ~ Hs - Hhs,
  e10 = Bcb ~ Bs - Bhh - Bbd,
  e11 = As ~ Ad,
  e12 = ra ~ rb,
  e13 = FXcb ~ rb[-1] * Bcb[-1] + ra[-1] * As[-1],
  e14 = Hhs ~ Hhd,
  e15 = M1s ~ M1h,
  e16 = M2s ~ M2h,
  e17 = Ls ~ Ld,
  e18 = Hbd ~ ro1 * M1s + ro2 * M2s,
  e19 = BbdN ~ M1s + M2s - Ls - Hbd,
  e20 = BLRN ~ BbdN / (M1s + M2s),
  e21 = Ad ~ (bot * (M1s + M2s) - BbdN) * z3,
  e22 = z3 ~ if(BLRN < bot) {1} else {0},
  e23 = Bbd ~ Ad + M1s + M2s - Ls - Hbd,
  e24 = BLR ~ Bbd / (M1s + M2s),
  e25 = rm ~ rm[-1] + zetam * (z4 - z5) + zetab * (rb - rb[-1]),
  e26 = z4 ~ if(BLRN[-1] < bot) {1} else {0},
  e27 = z5 ~ if(BLRN[-1] > top) {1} else {0},
  e28 = FXb ~ rl[-1] * Ls[-1] + rb[-1] * Bbd[-1] - rm[-1] * M2s[-1] - ra[-1] * Ad[-1],
  e29 = rl ~ rl[-1] + zetal * (z6 - z7) + (rb - rb[-1]),
  e30 = z6 ~ if(BPM < botpm) {1} else {0},
  e31 = z7 ~ if(BPM > toppm) {1} else {0},
  e32 = lM1s ~ M1s[-1],
  e33 = lM2s ~ M2s[-1],
  e34 = BPM ~ (FXb + FXb[-1]) / (lM1s + lM1s[-1] + lM2s + lM2s[-1])
)
```

```{r}
INSOUT_Inflation_and_Closure <- sfcr_set(
  # [ x=-3328 y=1648 ]
  e1 = omegaT ~ exp(Omega0 + Omega1 * log(pr) + Omega2 * log(N / Nfe)),
  e2 = W ~ W[-1] * (1 + Omega3 * (omegaT[-1] - (W[-1] / p[-1]))),
  e3 = Y ~ p * s + UC * (inv - inv[-1])
)
```

```{r}
INSOUT_Hidden_Closure <- sfcr_set(
  # [ x=-3328 y=1856 ]
  e1 = Hbs ~ Hbd  # Hidden-equation closure used in the sfcr baseline
)
```

## Parameters

```{r}
INSOUT_Parameters <- sfcr_set(
  # [ x=-2288 y=64 ]
  e1 = rbl ~ 0.027,
  e2 = rb ~ 0.023,
  e3 = pr ~ 1,
  e4 = g ~ 25,
  e5 = Nfe ~ 133.28,
  e6 = alpha0 ~ 0,
  e7 = alpha1 ~ 0.95,
  e8 = alpha2 ~ 0.05,
  e9 = beta ~ 0.5,
  e10 = bot ~ 0.02,
  e11 = botpm ~ 0.003,
  e12 = epsilon ~ 0.5,
  e13 = gamma ~ 0.5,
  e14 = lambdac ~ 0.1,
  e15 = phi ~ 0.1,
  e16 = ro1 ~ 0.1,
  e17 = ro2 ~ 0.1,
  e18 = sigma0 ~ 0.3612,
  e19 = sigma1 ~ 3,
  e20 = tau ~ 0.25,
  e21 = zetab ~ 0.9,
  e22 = zetal ~ 0.0002,
  e23 = zetam ~ 0.0002,
  e24 = Omega0 ~ -0.32549,
  e25 = Omega1 ~ 1,
  e26 = Omega2 ~ 1.5,
  e27 = Omega3 ~ 0.1,
  e28 = top ~ 0.06,
  e29 = toppm ~ 0.005,
  e30 = lambda10 ~ -0.17071,
  e31 = lambda11 ~ 0,
  e32 = lambda12 ~ 0,
  e33 = lambda13 ~ 0,
  e34 = lambda14 ~ 0,
  e35 = lambda15 ~ 0.18,
  e36 = lambda20 ~ 0.52245,
  e37 = lambda21 ~ 0,
  e38 = lambda22 ~ 30,
  e39 = lambda23 ~ -15,
  e40 = lambda24 ~ -15,
  e41 = lambda25 ~ -0.06,
  e42 = lambda30 ~ 0.47311,
  e43 = lambda31 ~ 0,
  e44 = lambda32 ~ -15,
  e45 = lambda33 ~ 30,
  e46 = lambda34 ~ -15,
  e47 = lambda35 ~ -0.06,
  e48 = lambda40 ~ 0.17515,
  e49 = lambda41 ~ 0,
  e50 = lambda42 ~ -15,
  e51 = lambda43 ~ -15,
  e52 = lambda44 ~ 30,
  e53 = lambda45 ~ -0.06
)
```

## Initial / Reference Values

```{r}
INSOUT_Initial_Reference <- sfcr_set(
  # [ x=-2288 y=768 ]
  e1 = p ~ 1.38469,
  e2 = W ~ 1,
  e3 = UC ~ 1,
  e4 = BPM ~ 0.0035,
  e5 = y ~ 133.277,
  e6 = s ~ 133.277,
  e7 = inv ~ 38.07,
  e8 = INV ~ 38.0676,
  e9 = Ld ~ 38.0676,
  e10 = Ls ~ 38.0676,
  e11 = Bcb ~ 19.355,
  e12 = Bbd ~ 1.19481,
  e13 = Bhd ~ 49.69136,
  e14 = Bhh ~ 49.69136,
  e15 = Bs ~ 70.25162,
  e16 = BLd ~ 1.12309,
  e17 = BLh ~ 1.12309,
  e18 = Hhd ~ 14.992,
  e19 = Hhh ~ 14.992,
  e20 = Hbd ~ 4.36249,
  e21 = Hbs ~ 4.36249,
  e22 = M1d ~ 3.9482,
  e23 = M1h ~ 3.9482,
  e24 = M1s ~ 3.9482,
  e25 = M2d ~ 39.667,
  e26 = M2h ~ 39.667,
  e27 = M2s ~ 39.667,
  e28 = rm ~ 0.02095,
  e29 = rb ~ 0.02301,
  e30 = rl ~ 0.02515,
  e31 = ra ~ 0.02301,
  e32 = pbl ~ 37.06,
  e33 = v ~ 108.285,
  e34 = V ~ 149.987,
  e35 = Vnc ~ 134.995,
  e36 = FXb ~ 0.1535,
  e37 = ydr ~ 108.28,
  e38 = ydrE ~ 108.28,
  e39 = omegaT ~ 0.72215,
  e40 = BLR ~ 0.02737,
  e41 = BLRN ~ 0.02737
)
```

## Balance Sheet Matrix

```{r}
bs_insout <- sfcr_matrix(
  # [ x=160 y=96 type: balance_sheet ]
  columns = c("Households", "Firms", "Government", "Central bank", "Banks", "Sum"),
  codes = c("h", "f", "g", "cb", "b", "s"),
  c("Inventories", h = "", f = "+INV", g = "", cb = "", b = "", s = "+INV"),
  c("HPM", h = "+Hhd", f = "", g = "", cb = "-Hs", b = "+Hbd", s = ""),
  c("Advances", h = "", f = "", g = "", cb = "+As", b = "-Ad", s = ""),
  c("Checking deposits", h = "+M1h", f = "", g = "", cb = "", b = "-M1s", s = ""),
  c("Time deposits", h = "+M2h", f = "", g = "", cb = "", b = "-M2s", s = ""),
  c("Bills", h = "+Bhh", f = "", g = "-Bs", cb = "+Bcb", b = "+Bbd", s = ""),
  c("Bonds", h = "+BLh * pbl", f = "", g = "-BLs * pbl", cb = "", b = "", s = ""),
  c("Loans", h = "", f = "-Ld", g = "", cb = "", b = "+Ls", s = ""),
  c("Balance", h = "-V", f = "0", g = "+GD", cb = "0", b = "0", s = "-INV")
)
```

## Transaction-Flow Matrix

```{r}
tfm_insout <- sfcr_matrix(
  # [ x=160 y=432 type: transaction_flow ]
  columns = c("Households", "Firms current", "Firms capital", "Govt.", "CB current", "CB capital", "Banks current", "Banks capital"),
  codes = c("h", "fc", "fk", "g", "cbc", "cbk", "bc", "bk"),
  c("Consumption", h = "-C", fc = "+C"),
  c("Govt. Expenditures", fc = "+G", g = "-G"),
  c("Ch. Inv", fc = "+(INV - INV[-1])", fk = "-(INV - INV[-1])"),
  c("Taxes", fc = "-TX", g = "+TX"),
  c("Wages", h = "+WB", fc = "-WB"),
  c("Entrepreneurial profits", h = "+FXf", fc = "-FXf"),
  c("Bank profits", h = "+FXb", bc = "-FXb"),
  c("CB profits", g = "+FXcb", cbc = "-FXcb"),
  c("int. advances", cbc = "+ra[-1] * As[-1]", bc = "-ra[-1] * Ad[-1]"),
  c("int. loans", fc = "-rl[-1] * Ld[-1]", bc = "+rl[-1] * Ld[-1]"),
  c("int. deposits", h = "+rm[-1] * M2h[-1]", bc = "-rm[-1] * M2h[-1]"),
  c("int. bills", h = "+rb[-1] * Bhh[-1]", g = "-rb[-1] * Bs[-1]", cbc = "+rb[-1] * Bcb[-1]", bc = "+rb[-1] * Bbd[-1]"),
  c("int. bonds", h = "+BLh[-1]", g = "-BLs[-1]"),
  c("Ch. advances", cbk = "-(As - As[-1])", bk = "+(Ad - Ad[-1])"),
  c("Ch. loans", fk = "+(Ld - Ld[-1])", bk = "-(Ls - Ls[-1])"),
  c("Ch. cash", h = "-(Hhh - Hhh[-1])", cbk = "+(Hs - Hs[-1])", bk = "-(Hbd - Hbd[-1])"),
  c("Ch. M1", h = "-(M1h - M1h[-1])", bk = "+(M1s - M1s[-1])"),
  c("Ch. M2", h = "-(M2h - M2h[-1])", bk = "+(M2s - M2s[-1])"),
  c("Ch. bills", h = "-(Bhh - Bhh[-1])", g = "+(Bs - Bs[-1])", cbk = "-(Bcb - Bcb[-1])", bk = "-(Bbd - Bbd[-1])"),
  c("Ch. bonds", h = "-(BLh - BLh[-1]) * pbl", g = "+(BLs - BLs[-1]) * pbl")
)
```

## Hints

```{r}
@hints
  y: Real output
  s: Real sales
  inv: Real inventories
  INV: Inventories at current cost
  V: Household nominal wealth
  c: Real household consumption
  ydr: Real regular disposable income
  M1s: Checking deposits supplied by banks
  M2s: Time deposits supplied by banks
  Bhh: Household bill holdings
  BLh: Household bond holdings
  Ls: Bank loan supply
  As: Central-bank advances
  Hbs: Reserve supply
  Bcb: Bills held by the central bank
  BLRN: Notional bank liquidity ratio
  BLR: Realized bank liquidity ratio
  BPM: Bank profitability margin
  rb: Bill rate
  rm: Deposit rate
  rl: Loan rate
  rbl: Bond rate
  pi: Inflation rate
  PSBR: Public sector borrowing requirement
@end
```

## Info

```{r}
@info
# INSOUT model

This file encodes the **INSOUT** model from **Godley and Lavoie (2007), chapter 10**.

Practical notes:

- The equation set follows the corrected `sfcr` replication.
- The reserve-closure condition is included explicitly with `Hbs ~ Hbd`, mirroring the hidden-equation closure used in the article.
- The parameter `top ~ 0.06` is used here because it is the corrected value that avoids the liquidity-ratio ping-pong problem.
- The reference-value block includes steady-state values mapped from the Python replication notebook where the name mapping is straightforward.

Use this file when you want:

- a documented baseline INSOUT specification inside CircuitJS1
- the chapter 10 matrices stored with the equations
- a starting point for shocks to `sigma0`, `g`, reserve ratios, or bank-liquidity targets
@end
```

## Minimal Visual Layout

```{r}
@scope Embedded_Scope_1 position=-1
  x1: -2080
  y1: 48
  x2: -1376
  y2: 416
  elmUid: GLI10_SCOPE
  speed: 2
  flags: x6001206
  source: uid:GLI10_Y value:0
  trace: uid:GLI10_V value:0
  trace: uid:GLI10_BS value:0
  trace: uid:GLI10_LS value:0
@end
```

```{r}
@circuit
x -1984 768 -1522 771 4 18 Godley\sand\Lavoie\s(2007)\sChapter\s10:\sINSOUT 808080FF U:GLI10_T1
x -1984 792 -1408 795 4 12 Corrected\ssfcr\sbaseline\swith\sbanks,\scentral\sbank,\sgovt,\sinventories,\sand\sportfolio\schoice 808080FF U:GLI10_T2
x -1984 816 -1388 819 4 12 Parameters\sfollow\sthe\ssfcr\sarticle;\sreference\ssteady-state\svalues\sare\smapped\sfrom\sthe\sPython\snotebook 808080FF U:GLI10_T3
207 -2016 960 -1952 960 164 Y U:GLI10_Y
207 -2016 992 -1952 992 164 V U:GLI10_V
207 -2016 1024 -1952 1024 164 Bs U:GLI10_BS
207 -2016 1056 -1952 1056 164 Ls U:GLI10_LS
207 -2016 1088 -1952 1088 164 BLRN U:GLI10_BLRN
207 -2016 1120 -1952 1120 164 BPM U:GLI10_BPM
431 -2224 752 -2192 784 0 100 true false U:GLI10_BOX
@end
```
