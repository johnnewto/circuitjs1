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
equations <- sfcr_set(
  # [ x=-272 y=264 uid=xbsLrJ invisible=false ]
  # ===== Firm's behavioral equations ====
  y ~ sE + (invE - last(inv)),  # [mode=voltage ]
  N ~ y / pr,  # [mode=voltage ]
  WB ~ N * W,  # [mode=voltage ]
  UC ~ WB / y,  # [mode=voltage, initial=1 ]
  sE ~ beta * last(s) + (1 - beta) * last(sE),  # [mode=voltage ]
  invT ~ sigmaT * sE,  # [mode=voltage ]
  sigmaT ~ sigma0 - sigma1 * rl,  # [mode=voltage ]
  # rrl ~ ((1 + rl) / (1 + pi)) - 1,
  invE ~ last(inv) + gamma * (invT - last(inv)),  # [mode=voltage ]
  p ~ (1 + tau) * (1 + phi) * NHUC,  # [mode=voltage, initial=1 ]
  NHUC ~ (1 - sigmaT) * UC + sigmaT * (1 + last(rl)) * last(UC),  # # rl[-1] instead of rl  [mode=voltage ]
  # FXfE ~ (phi / (1 + phi)) * (1 / (1 + tau)) * p * sE,
  # ===== Firm's realized outcomes =====
  s ~ c + g,  # [mode=voltage ]
  S ~ s * p,  # [mode=voltage ]
  inv ~ last(inv) + y - s,  # [mode=voltage ]
  sigmas ~ last(inv) / s,  # [mode=voltage ]
  INV ~ inv * UC,  # [mode=voltage ]
  Ld ~ INV,  # [mode=voltage ]
  FXf ~ S - TX - WB + (INV - last(INV)) - last(rl) * last(INV),  # rl[-1] instead of rl  [mode=voltage ]
  pi ~ (p / last(p)) - 1,  # [mode=voltage ]
  # ==== Households realized outcomes ====
  YDr ~ FX + WB + last(rm) * last(M2h) + last(rb) * last(Bhh) + last(BLh),  # # Here's a mistake in Zezza's code. It should be M2h[-1] as in G&L and NOT M2d.  [mode=voltage ]
  CG ~ (pbl - last(pbl)) * last(BLh),  # [mode=voltage ]
  YDhs ~ YDr + CG,  # [mode=voltage ]
  FX ~ FXf + FXb,  # [mode=voltage ]
  V ~ last(V) + YDhs - C,  # [mode=voltage ]
  Vnc ~ V - Hhh,  # # Zezza writes it as Hhd instead of Hhh. Here it is harmless, but theoretically it should be Hhh and not Hhd as what matters for wealth net of cash is the realized holdings of cash.  [mode=voltage ]
  ydr ~ YDr/p - pi * (last(V)/p),  # [mode=voltage ]
  ydhs ~ (YDr - pi * last(V) + CG) / p,  # [mode=voltage ]
  # ydhs ~ c + v - v[-1]
  v ~ V/p,  # [mode=voltage ]
  # ==== Households behavioral ====
  c ~ alpha0 + \alpha_1 * ydrE + \alpha_2 * last(v),  # [mode=voltage ]
  ydrE ~ epsilon * last(ydr) + (1 - epsilon) * last(ydrE),  # [mode=voltage ]
  C ~ p * c,  # [mode=voltage ]
  YDrE ~ p * ydrE + pi * (last(V)/p),  # [mode=voltage ]
  VE ~ last(V) + (YDrE - C),  # [mode=voltage ]
  Hhd ~ lambdac * C,  # [mode=voltage ]
  VncE ~ VE - Hhd,  # [mode=voltage ]
  ERrbl ~ rbl,  # # ERrbl is not on the list of equations. I kept it simple.  [mode=voltage ]
  # ==== Households' portfolio equations ====
  # There's no M1d equation in Zezza's code as they are not necessary
  M2d ~ VncE * (lambda20 + lambda22 * rm + lambda23 * rb + lambda24 * ERrbl + lambda25 * (YDrE / VncE)),  # [mode=voltage ]
  Bhd ~ VncE * (lambda30 + lambda32 * rm + lambda33 * rb + lambda34 * ERrbl + lambda35 * (YDrE / VncE)),  # [mode=voltage ]
  BLd ~ (VncE / pbl) * (lambda40 + lambda42 * rm + lambda43 * rb + lambda44 * ERrbl + lambda45 * (YDrE / VncE)),  # [mode=voltage ]
  # below must be equal.
  M1d ~ VncE * (lambda10 + lambda12 * rm + lambda13 * rb + lambda14 * ERrbl + lambda15 * (YDrE / VncE)),  # [mode=voltage ]
  M1d2 ~ VncE - M2d - Bhd - pbl * BLd,  # [mode=voltage ]
  # ==== Realized portfolio asset holdings ====
  # Bhs ~ Bhd
  Hhh ~ Hhd,  # [mode=voltage ]
  Bhh ~ Bhd,  # [mode=voltage ]
  BLh ~ BLd,  # [mode=voltage ]
  M1hN ~ Vnc - M2d - Bhd - pbl * BLd,  # [mode=voltage ]
  z1 ~ (M1hN > 0) ? 1 : 0,  # Switch  [mode=voltage, initial=0 ]
  z2 ~ 1 - z1,  # [mode=voltage ]
  M1h ~ M1hN * z1,  # [mode=voltage ]
  M2hN ~ M2d,  # [mode=voltage ]
  M2h ~ M2d * z1 + (Vnc - Bhh - pbl * BLd) * z2,  # [mode=voltage ]
  # Government's equations
  TX ~ S * (tau / (1 + tau)),  # [mode=voltage ]
  G ~ p * g,  # [mode=voltage ]
  PSBR ~ G + last(rb) * last(Bs) + last(BLs) - (TX + FXcb),  # [mode=voltage ]
  Bs ~ last(Bs) + PSBR - (BLs - last(BLs)) * pbl,  # [mode=voltage ]
  BLs ~ BLd,  # [mode=voltage ]
  pbl ~ 1 / rbl,  # [mode=voltage ]
  GD ~ last(GD) + PSBR,  # Not in the equations list, but I added to check BS consistency  [mode=voltage ]
  # ==== Central bank's equations ====
  Hs ~ Bcb + As,  # [mode=voltage ]
  Hbs ~ Hs - Hhs,  # [mode=voltage ]
  Bcb ~ Bs - Bhh - Bbd,  # [mode=voltage ]
  As ~ Ad,  # [mode=voltage ]
  ra ~ rb,  # [mode=voltage ]
  FXcb ~ last(rb) * last(Bcb) + last(ra) * last(As),  # [mode=voltage ]
  # ==== Bank's realized (supply) equations ====
  Hhs ~ Hhd,  # [mode=voltage ]
  M1s ~ M1h,  # # M1h instead of M1d as in Zezza. Bank's supply the realized portfolio holdings and NOT its notional demand.  [mode=voltage ]
  M2s ~ M2h,  # # M2h instead of M2d as in Zezza. Bank's supply the realized portfolio holdings and NOT its notional demand.  [mode=voltage ]
  # Otherwise
  # on REALIZED wealth. It is a residual variable. The bank's supply the deposits that are ACTUALLY needed.
  # You can check that M1d
  Ls ~ Ld,  # [mode=voltage ]
  Hbd ~ ro1 * M1s + ro2 * M2s,  # [mode=voltage ]
  # ====  Bank's balance sheet constraints ====
  BbdN ~ M1s + M2s - Ls - Hbd,  # [mode=voltage ]
  BLRN ~ BbdN / (M1s + M2s),  # [mode=voltage ]
  Ad ~ (bot * (M1s + M2s) - BbdN) * z3,  # # Z3 instead of Z4  [mode=voltage ]
  z3 ~ (BLRN < bot) ? 1 : 0,  # Switch  [mode=voltage, initial=0 ]
  Bbd ~ Ad + M1s + M2s - Ls - Hbd,  # [mode=voltage ]
  BLR ~ Bbd / (M1s + M2s),  # [mode=voltage ]
  # ==== Determination of interest rates by banks ====
  rm ~ last(rm) + zetam * (z4 - z5) + zetab * (rb - last(rb)),  # [mode=voltage ]
  z4 ~ (last(BLRN) < bot) ? 1 : 0,  # Switch  [mode=voltage, initial=0 ]
  z5 ~ (last(BLRN) > top) ? 1 : 0,  # Switch  [mode=voltage, initial=0 ]
  FXb ~ last(rl) * last(Ls) + last(rb) * last(Bbd) - last(rm) * last(M2s) - last(ra) * last(Ad),  # [mode=voltage ]
  rl ~ last(rl) + zetal * (z6 - z7) + (rb - last(rb)),  # [mode=voltage ]
  z6 ~ (BPM < botpm) ? 1 : 0,  # Switch  [mode=voltage, initial=0 ]
  z7 ~ (BPM > toppm) ? 1 : 0,  # Switch  [mode=voltage, initial=0 ]
  # Since the sfcr package does not accept more than 1 lag directly,
  # we need to create two auxiliary values that are lags of endogenous
  # variables and then take the lag of these variables
  lM1s ~ last(M1s),  # [mode=voltage ]
  lM2s ~ last(M2s),  # [mode=voltage ]
  BPM ~ (FXb + last(FXb)) / (lM1s + last(lM1s) + lM2s + last(lM2s)),  # [mode=voltage, initial=0.0035 ]
  # ==== Inflationary forces ====
  # omegaT ~ Omega0 + Omega1 * pr + Omega2 * (N / Nfe),
  omegaT ~ exp(Omega0 + Omega1 * log(pr) + Omega2 * log(N / Nfe)),  # # Zezza's equation  [mode=voltage ]
  # The model explodes if G&L's definition of omegaT is used.
  W ~ last(W) * (1 + Omega3 * (last(omegaT) - (last(W) / last(p)))),  # [mode=voltage, initial=1 ]
  # Nfe ~ s / pr
  # Nfe ~ s[-1] / pr
  Y ~ p * s + UC * (inv - last(inv))  # [mode=voltage ]
)
```

```{r}
insout_ext <- sfcr_set(
  # [ x=-224 y=552 uid=eC3Pno invisible=false ]
  # ==== EXOGENOUS ====
  rbl ~ 0.027,  # [mode=voltage ]
  rb ~ 0.023,  # [mode=voltage ]
  pr ~ 1.2,  # [mode=voltage ]
  g ~ 25,  # [mode=voltage ]
  Nfe ~ 133.28,  # # Zezza supplies this exogenous full employment value  [mode=voltage ]
  # Nfe ~ 142.75,
  # In model DISINF Nfe was defined as `s / pr`
  # I discuss this issue in the scenarios.
  # ====  PARAMETERS ====
  alpha0 ~ 0,  # [mode=param ]
  \alpha_1 ~ 0.95,  # [mode=param ]
  \alpha_2 ~ 0.046,  # [mode=param ]
  beta ~ 0.5,  # [mode=param ]
  bot ~ 0.02,  # [mode=param ]
  botpm ~ 0.003,  # [mode=param ]
  epsilon ~ 0.5,  # [mode=param ]
  gamma ~ 0.5,  # [mode=param ]
  lambdac ~ 0.1,  # [mode=param ]
  phi ~ 0.1,  # [mode=param ]
  ro1 ~ 0.1,  # [mode=param ]
  ro2 ~ 0.1,  # [mode=param ]
  sigma0 ~ 0.3612,  # [mode=param ]
  sigma1 ~ 3,  # [mode=param ]
  tau ~ 0.25,  # [mode=param ]
  zetab ~ 0.9,  # [mode=param ]
  zetal ~ 0.0002,  # [mode=param ]
  zetam ~ 0.0002,  # [mode=param ]
  Omega0 ~ -0.32549,  # [mode=param ]
  Omega1 ~ 1,  # [mode=param ]
  Omega2 ~ 1.5,  # [mode=param ]
  Omega3 ~ 0.1,  # [mode=param ]
  # top ~ 0.04
  top ~ 0.06,  # [mode=param ]
  toppm ~ 0.005,  # [mode=param ]
  # ==== PORTFOLIO PARAMETERS ====
  lambda10 ~ -0.17071,  # [mode=param ]
  lambda11 ~ 0,  # [mode=param ]
  lambda12 ~ 0,  # [mode=param ]
  lambda13 ~ 0,  # [mode=param ]
  lambda14 ~ 0,  # [mode=param ]
  lambda15 ~ 0.18,  # [mode=param ]
  lambda20 ~ 0.52245,  # [mode=param ]
  lambda21 ~ 0,  # [mode=param ]
  lambda22 ~ 30,  # [mode=param ]
  lambda23 ~ -15,  # [mode=param ]
  lambda24 ~ -15,  # [mode=param ]
  lambda25 ~ -0.06,  # [mode=param ]
  lambda30 ~ 0.47311,  # [mode=param ]
  lambda31 ~ 0,  # [mode=param ]
  lambda32 ~ -15,  # [mode=param ]
  lambda33 ~ 30,  # [mode=param ]
  lambda34 ~ -15,  # [mode=param ]
  lambda35 ~ -0.06,  # [mode=param ]
  lambda40 ~ 0.17515,  # [mode=param ]
  lambda41 ~ 0,  # [mode=param ]
  lambda42 ~ -15,  # [mode=param ]
  lambda43 ~ -15,  # [mode=param ]
  lambda44 ~ 30,  # [mode=param ]
  lambda45 ~ -0.06  # [mode=param ]
)
```

`

```{r}
@zorder
  uid:eC3Pno z:0
  uid:xbsLrJ z:1
@end
```

