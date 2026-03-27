# CircuitJS1 SFCR Export
# Godley and Lavoie (2007) Chapter 11: GROWTH

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
  equationTableTolerance: 0.000000000000001
  infoViewerUpdateIntervalMs: 200
@end
```

# GROWTH Model

## Purpose

This circuit file encodes the baseline **GROWTH** model from **Godley and Lavoie (2007), chapter 11** using the same equation set and baseline initial values used in the `sfcr` replication.

It is a large stock-flow consistent model with:

- firms, households, government, central bank, and commercial banks
- inventories, fixed capital, equities, bills, bonds, deposits, cash, and loans
- endogenous growth, pricing, portfolio choice, and bank behavior

## Source Note

The equations below follow the `sfcr` article **"Chapter 11: Model GROWTH"**. That implementation notes that a few equations and initial values match Zezza's working code where it differs from the printed book, to obtain a stable baseline simulation.

## Equations

```{r}
GROWTH_Real_and_Firms_Core <- sfcr_set(
  # [ x=-2992 y=664 ]
  e1 = Yk ~ Ske + INke - last(INk),  # Real output  [mode=voltage, sliderValue=0 ]
  e2 = Ske ~ beta * Sk + (1 - beta) * last(Sk) * (1 + (GRpr + RA)),  # Expected real sales  [mode=voltage, sliderValue=0 ]
  e3 = INke ~ last(INk) + gamma * (INkt - last(INk)),  # Expected inventories  [mode=voltage, sliderValue=0 ]
  e4 = INkt ~ sigmat * Ske,  # Inventory target  [mode=voltage, sliderValue=0 ]
  e5 = INk ~ last(INk) + Yk - Sk - NPL / UC,  # Real inventories  [mode=voltage, sliderValue=0 ]
  e6 = Kk ~ last(Kk) * (1 + GRk),  # Real capital stock  [mode=voltage, sliderValue=0 ]
  e7 = GRk ~ gamma0 + gammau * last(U) - gammar * RRl,  # Real capital growth rate  [mode=voltage, sliderValue=0 ]
  e8 = U ~ Yk / last(Kk),  # Capacity utilization proxy  [mode=voltage, sliderValue=0 ]
  e9 = RRl ~ ((1 + Rl) / (1 + PI)) - 1,  # Real loan rate  [mode=voltage, sliderValue=0 ]
  e10 = PI ~ (P - last(P)) / last(P),  # Inflation rate  [mode=voltage, sliderValue=0 ]
  e11 = Ik ~ (Kk - last(Kk)) + delta * last(Kk),  # Real gross investment  [mode=voltage, sliderValue=0 ]
  e12 = Sk ~ Ck + Gk + Ik,  # Real sales  [mode=voltage, sliderValue=0 ]
  e13 = S ~ Sk * P,  # Nominal sales  [mode=voltage, sliderValue=0 ]
  e14 = IN ~ INk * UC,  # Inventories valued at current cost  [mode=voltage, sliderValue=0 ]
  e15 = INV ~ Ik * P,  # Nominal gross investment  [mode=voltage, sliderValue=0 ]
  e16 = K ~ Kk * P,  # Nominal capital stock  [mode=voltage, sliderValue=0 ]
  e17 = Y ~ Sk * P + (INk - last(INk)) * UC  # Nominal GDP  [mode=voltage, sliderValue=0 ]
)
```

```{r}
GROWTH_Wages_Prices_Profits <- sfcr_set(
  # [ x=-3904 y=-104 ]
  e1 = omegat ~ exp(omega0 + omega1 * log(PR) + omega2 * log(ER + z3 * (1 - ER) - z4 * BANDt + z5 * BANDb)),  # Wage aspiration  [mode=voltage, sliderValue=0 ]
  e2 = ER ~ last(N) / last(Nfe),  # Employment rate  [mode=voltage, sliderValue=0 ]
  e3 = z3a ~ if(ER > (1 - BANDb)) {1} else {0},  # Switch  [mode=voltage, sliderValue=0 ]
  e4 = z3b ~ if(ER <= (1 + BANDt)) {1} else {0},  # Switch  [mode=voltage, sliderValue=0 ]
  e5 = z3 ~ z3a * z3b,  # Switch  [mode=voltage, sliderValue=0 ]
  e6 = z4 ~ if(ER > (1 + BANDt)) {1} else {0},  # Switch  [mode=voltage, sliderValue=0 ]
  e7 = z5 ~ if(ER < (1 - BANDb)) {1} else {0},  # Switch  [mode=voltage, sliderValue=0 ]
  e8 = W ~ last(W) + omega3 * (omegat * last(P) - last(W)),  # Nominal wage  [mode=voltage, sliderValue=0 ]
  e9 = PR ~ last(PR) * (1 + GRpr),  # Labor productivity  [mode=voltage, sliderValue=0 ]
  e10 = Nt ~ Yk / PR,  # Desired employment  [mode=voltage, sliderValue=0 ]
  e11 = N ~ last(N) + etan * (Nt - last(N)),  # Employment  [mode=voltage, sliderValue=0 ]
  e12 = WB ~ N * W,  # Wage bill  [mode=voltage, sliderValue=0 ]
  e13 = UC ~ WB / Yk,  # Unit cost  [mode=voltage, sliderValue=0 ]
  e14 = NUC ~ W / PR,  # Normal unit cost  [mode=voltage, sliderValue=0 ]
  e15 = NHUC ~ (1 - sigman) * NUC + sigman * (1 + last(Rln)) * last(NUC),  # Normal historic unit cost  [mode=voltage, sliderValue=0 ]
  e16 = P ~ (1 + phi) * NHUC,  # Price level  [mode=voltage, sliderValue=0 ]
  e17 = phi ~ last(phi) + eps2 * (last(phit) - last(phi)),  # Actual markup  [mode=voltage, sliderValue=0 ]
  e18 = phit ~ (FUft + FDf + last(Rl) * (last(Lfd) - last(IN))) / ((1 - sigmase) * Ske * UC + (1 + last(Rl)) * sigmase * Ske * last(UC)),  # Target markup  [mode=voltage, sliderValue=0 ]
  e19 = HCe ~ (1 - sigmase) * Ske * UC + (1 + last(Rl)) * sigmase * Ske * last(UC),  # Expected historical costs  [mode=voltage, sliderValue=0 ]
  e20 = sigmase ~ last(INk) / Ske,  # Opening inventory ratio  [mode=voltage, sliderValue=0 ]
  e21 = Fft ~ FUft + FDf + last(Rl) * (last(Lfd) - last(IN)),  # Planned entrepreneurial profits  [mode=voltage, sliderValue=0 ]
  e22 = FUft ~ psiu * last(INV),  # Planned retained earnings  [mode=voltage, sliderValue=0 ]
  e23 = FDf ~ psid * last(Ff),  # Firm dividends  [mode=voltage, sliderValue=0 ]
  e24 = Ff ~ S - WB + (IN - last(IN)) - last(Rl) * last(IN),  # Firm profits  [mode=voltage, sliderValue=0 ]
  e25 = FUf ~ Ff - FDf - last(Rl) * (last(Lfd) - last(IN)) + last(Rl) * NPL,  # Retained earnings of firms  [mode=voltage, sliderValue=0 ]
  e26 = Lfd ~ last(Lfd) + INV + (IN - last(IN)) - FUf - (Eks - last(Eks)) * Pe - NPL,  # Firm loan demand  [mode=voltage, sliderValue=0 ]
  e27 = NPL ~ NPLk * last(Lfs),  # Non-performing loans  [mode=voltage, sliderValue=0 ]
  e28 = Eks ~ last(Eks) + ((1 - psiu) * last(INV)) / Pe,  # Equity supply  [mode=voltage, sliderValue=0 ]
  e29 = Rk ~ FDf / (last(Pe) * last(Ekd)),  # Dividend yield on equities  [mode=voltage, sliderValue=0 ]
  e30 = PE ~ Pe / (Ff / last(Eks)),  # Price-earnings ratio  [mode=voltage, sliderValue=0 ]
  e31 = Q ~ (Eks * Pe + Lfd) / (K + IN)  # Tobin's q  [mode=voltage, sliderValue=0 ]
)
```

```{r}
GROWTH_Households_Government_Banks <- sfcr_set(
  # [ x=-3904 y=440 ]
  e1 = YP ~ WB + FDf + FDb + last(Rm) * last(Mh) + last(Rb) * last(Bhd) + last(BLs),  # Personal income  [mode=voltage, sliderValue=0 ]
  e2 = TX ~ \theta * YP,  # Taxes  [mode=voltage, sliderValue=0 ]
  e3 = YDr ~ YP - TX - last(Rl) * last(Lhd),  # Regular disposable income  [mode=voltage, sliderValue=0 ]
  e4 = YDhs ~ YDr + CG,  # Haig-Simons income  [mode=voltage, sliderValue=0 ]
  e5 = CG ~ (Pbl - last(Pbl)) * last(BLd) + (Pe - last(Pe)) * last(Ekd) + (OFb - last(OFb)),  # Capital gains  [mode=voltage, sliderValue=0 ]
  e6 = V ~ last(V) + YDr - CONS + (Pbl - last(Pbl)) * last(BLd) + (Pe - last(Pe)) * last(Ekd) + (OFb - last(OFb)),  # Household wealth  [mode=voltage, sliderValue=0 ]
  e7 = Vk ~ V / P,  # Real wealth  [mode=voltage, sliderValue=0 ]
  e8 = CONS ~ Ck * P,  # Consumption  [mode=voltage, sliderValue=0 ]
  e9 = Ck ~ \alpha_1 * (YDkre + NLk) + \alpha_2 * last(Vk),  # Real consumption  [mode=voltage, sliderValue=0 ]
  e10 = YDkre ~ eps * YDkr + (1 - eps) * (last(YDkr) * (1 + GRpr)),  # Expected real disposable income  [mode=voltage, sliderValue=0 ]
  e11 = YDkr ~ YDr / P - ((P - last(P)) * last(Vk)) / P,  # Real regular disposable income  [mode=voltage, sliderValue=0 ]
  e12 = GL ~ eta * YDr,  # Gross new household loans  [mode=voltage, sliderValue=0 ]
  e13 = eta ~ eta0 - etar * RRl,  # Loan demand ratio  [mode=voltage, sliderValue=0 ]
  e14 = NL ~ GL - REP,  # Net new household loans  [mode=voltage, sliderValue=0 ]
  e15 = REP ~ deltarep * last(Lhd),  # Household loan repayments  [mode=voltage, sliderValue=0 ]
  e16 = Lhd ~ last(Lhd) + GL - REP,  # Household loan demand  [mode=voltage, sliderValue=0 ]
  e17 = NLk ~ NL / P,  # Real net new household loans  [mode=voltage, sliderValue=0 ]
  e18 = BUR ~ (REP + last(Rl) * last(Lhd)) / last(YDr),  # Debt service burden  [mode=voltage, sliderValue=0 ]
  e19 = Bhd ~ last(Vfma) * (lambda20 + lambda22 * last(Rb) - lambda21 * last(Rm) - lambda24 * last(Rk) - lambda23 * last(Rbl) - lambda25 * (YDr / V)),  # Household bill holdings  [mode=voltage, sliderValue=0 ]
  e20 = BLd ~ last(Vfma) * (lambda30 - lambda32 * last(Rb) - lambda31 * last(Rm) - lambda34 * last(Rk) + lambda33 * last(Rbl) - lambda35 * (YDr / V)) / Pbl,  # Household bond holdings  [mode=voltage, sliderValue=0 ]
  e21 = Pe ~ last(Vfma) * (lambda40 - lambda42 * last(Rb) - lambda41 * last(Rm) + lambda44 * last(Rk) - lambda43 * last(Rbl) - lambda45 * (YDr / V)) / Ekd,  # Equity price  [mode=voltage, sliderValue=0 ]
  e22 = Mh ~ Vfma - Bhd - Pe * Ekd - Pbl * BLd + Lhd,  # Household deposits  [mode=voltage, sliderValue=0 ]
  e23 = Vfma ~ V - Hhd - OFb,  # Investible wealth  [mode=voltage, sliderValue=0 ]
  e24 = VfmaA ~ Mh + Bhd + Pbl * BLd + Pe * Ekd,  # Portfolio memo  [mode=voltage, sliderValue=0 ]
  e25 = Hhd ~ lambdac * CONS,  # Household cash demand  [mode=voltage, sliderValue=0 ]
  e26 = Ekd ~ Eks,  # Equity market clearing  [mode=voltage, sliderValue=0 ]
  e27 = G ~ Gk * P,  # Government expenditure  [mode=voltage, sliderValue=0 ]
  e28 = Gk ~ last(Gk) * (1 + GRg),  # Real government spending  [mode=voltage, sliderValue=0 ]
  e29 = PSBR ~ G + last(BLs) + last(Rb) * (last(Bbs) + last(Bhs)) - TX,  # Public sector borrowing requirement  [mode=voltage, sliderValue=0 ]
  e30 = Bs ~ last(Bs) + G - TX - (BLs - last(BLs)) * Pbl + last(Rb) * (last(Bhs) + last(Bbs)) + last(BLs),  # Government bills  [mode=voltage, sliderValue=0 ]
  e31 = GD ~ Bbs + Bhs + BLs * Pbl + Hs,  # Government debt  [mode=voltage, sliderValue=0 ]
  e32 = Fcb ~ last(Rb) * last(Bcbd),  # Central bank profits  [mode=voltage, sliderValue=0 ]
  e33 = BLs ~ BLd,  # Bond supply  [mode=voltage, sliderValue=0 ]
  e34 = Bhs ~ Bhd,  # Bill supply to households  [mode=voltage, sliderValue=0 ]
  e35 = Hhs ~ Hhd,  # Cash supply to households  [mode=voltage, sliderValue=0 ]
  e36 = Hbs ~ Hbd,  # Reserve supply  [mode=voltage, sliderValue=0 ]
  e37 = Hs ~ Hbs + Hhs,  # High-powered money  [mode=voltage, sliderValue=0 ]
  e38 = Bcbd ~ Hs,  # Bills held by central bank  [mode=voltage, sliderValue=0 ]
  e39 = Bcbs ~ Bcbd,  # Bills supplied to central bank  [mode=voltage, sliderValue=0 ]
  e40 = Rb ~ Rbbar,  # Bill rate  [mode=voltage, sliderValue=0 ]
  e41 = Rbl ~ Rb + ADDbl,  # Long bond rate  [mode=voltage, sliderValue=0 ]
  e42 = Pbl ~ 1 / Rbl,  # Long bond price  [mode=voltage, sliderValue=0 ]
  e43 = Ms ~ Mh,  # Deposit supply  [mode=voltage, sliderValue=0 ]
  e44 = Lfs ~ Lfd,  # Firm loans supplied  [mode=voltage, sliderValue=0 ]
  e45 = Lhs ~ Lhd,  # Household loans supplied  [mode=voltage, sliderValue=0 ]
  e46 = Hbd ~ ro * Ms,  # Required reserves  [mode=voltage, sliderValue=0 ]
  e47 = Bbs ~ last(Bbs) + (Bs - last(Bs)) - (Bhs - last(Bhs)) - (Bcbs - last(Bcbs)),  # Bills held by banks  [mode=voltage, sliderValue=0 ]
  e48 = Bbd ~ Ms + OFb - Lfs - Lhs - Hbd,  # Bank bill demand constraint  [mode=voltage, sliderValue=0 ]
  e49 = BLR ~ Bbd / Ms,  # Bank liquidity ratio  [mode=voltage, sliderValue=0 ]
  e50 = Rm ~ last(Rm) + z1a * xim1 + z1b * xim2 - z2a * xim1 - z2b * xim2,  # Deposit rate  [mode=voltage, sliderValue=0 ]
  e51 = z2a ~ if(last(BLR) > (top + 0.05)) {1} else {0},  # Switch  [mode=voltage, sliderValue=0 ]
  e52 = z2b ~ if(last(BLR) > top) {1} else {0},  # Switch  [mode=voltage, sliderValue=0 ]
  e53 = z1a ~ if(last(BLR) <= bot) {1} else {0},  # Switch  [mode=voltage, sliderValue=0 ]
  e54 = z1b ~ if(last(BLR) <= (bot - 0.05)) {1} else {0},  # Switch  [mode=voltage, sliderValue=0 ]
  e55 = Rl ~ Rm + ADDl,  # Loan rate  [mode=voltage, sliderValue=0 ]
  e56 = OFbt ~ NCAR * (last(Lfs) + last(Lhs)),  # Target bank own funds  [mode=voltage, sliderValue=0 ]
  e57 = OFbe ~ last(OFb) + betab * (OFbt - last(OFb)),  # Expected own funds  [mode=voltage, sliderValue=0 ]
  e58 = FUbt ~ OFbe - last(OFb) + NPLke * last(Lfs),  # Target retained earnings of banks  [mode=voltage, sliderValue=0 ]
  e59 = NPLke ~ epsb * last(NPLke) + (1 - epsb) * last(NPLk),  # Expected bad loan share  [mode=voltage, sliderValue=0 ]
  e60 = FDb ~ Fb - FUb,  # Bank dividends  [mode=voltage, sliderValue=0 ]
  e61 = Fbt ~ lambdab * last(Y) + (OFbe - last(OFb) + NPLke * last(Lfs)),  # Target bank profits  [mode=voltage, sliderValue=0 ]
  e62 = Fb ~ last(Rl) * (last(Lfs) + last(Lhs) - NPL) + last(Rb) * last(Bbd) - last(Rm) * last(Ms),  # Bank profits  [mode=voltage, sliderValue=0 ]
  e63 = ADDl ~ (Fbt - last(Rb) * last(Bbd) + last(Rm) * (last(Ms) - (1 - NPLke) * last(Lfs) - last(Lhs))) / ((1 - NPLke) * last(Lfs) + last(Lhs)),  # Loan spread  [mode=voltage, sliderValue=0 ]
  e64 = FUb ~ Fb - lambdab * last(Y),  # Retained earnings of banks  [mode=voltage, sliderValue=0 ]
  e65 = OFb ~ last(OFb) + FUb - NPL,  # Bank own funds  [mode=voltage, sliderValue=0 ]
  e66 = CAR ~ OFb / (Lfs + Lhs),  # Capital adequacy ratio  [mode=voltage, sliderValue=0 ]
  e67 = Vf ~ IN + K - Lfd - Ekd * Pe,  # Firm net worth memo  [mode=voltage, sliderValue=0 ]
  e68 = Ls ~ Lfs + Lhs  # Total loan supply  [mode=voltage, sliderValue=0 ]
)
```

## Parameters

```{r}
GROWTH_Parameters <- sfcr_set(
  # [ x=-2560 y=600 ]
  e1 = \alpha_1 ~ 0.75,  # Consumption out of expected income  [mode=param, sliderValue=0 ]
  e2 = \alpha_2 ~ 0.064,  # Consumption out of wealth  [mode=param, sliderValue=0 ]
  e3 = beta ~ 0.5,  # Sales expectation weight  [mode=param, sliderValue=0 ]
  e4 = betab ~ 0.4,  # Bank own-funds adjustment  [mode=param, sliderValue=0 ]
  e5 = gamma ~ 0.15,  # Inventory adjustment speed  [mode=param, sliderValue=0 ]
  e6 = gamma0 ~ 0.00122,  # Autonomous capital growth  [mode=param, sliderValue=0 ]
  e7 = gammar ~ 0.1,  # Interest sensitivity of growth  [mode=param, sliderValue=0 ]
  e8 = gammau ~ 0.05,  # Utilization sensitivity of growth  [mode=param, sliderValue=0 ]
  e9 = delta ~ 0.10667,  # Depreciation rate  [mode=param, sliderValue=0 ]
  e10 = deltarep ~ 0.1,  # Household repayment rate  [mode=param, sliderValue=0 ]
  e11 = eps ~ 0.5,  # Disposable-income expectation weight  [mode=param, sliderValue=0 ]
  e12 = eps2 ~ 0.83,  # Markup adjustment speed  [mode=param, sliderValue=0 ]
  e13 = epsb ~ 0.25,  # Bank expectations weight  [mode=param, sliderValue=0 ]
  e14 = eta0 ~ 0.07416,  # Base household loan ratio  [mode=param, sliderValue=0 ]
  e15 = etan ~ 0.6,  # Employment adjustment speed  [mode=param, sliderValue=0 ]
  e16 = etar ~ 0.4,  # Loan-rate sensitivity of household borrowing  [mode=param, sliderValue=0 ]
  e17 = \theta ~ 0.22844,  # Tax rate  [mode=param, sliderValue=0 ]
  e18 = lambda20 ~ 0.25,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e19 = lambda21 ~ 2.2,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e20 = lambda22 ~ 6.6,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e21 = lambda23 ~ 2.2,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e22 = lambda24 ~ 2.2,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e23 = lambda25 ~ 0.1,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e24 = lambda30 ~ -0.04341,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e25 = lambda31 ~ 2.2,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e26 = lambda32 ~ 2.2,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e27 = lambda33 ~ 6.6,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e28 = lambda34 ~ 2.2,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e29 = lambda35 ~ 0.1,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e30 = lambda40 ~ 0.67132,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e31 = lambda41 ~ 2.2,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e32 = lambda42 ~ 2.2,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e33 = lambda43 ~ 2.2,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e34 = lambda44 ~ 6.6,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e35 = lambda45 ~ 0.1,  # Portfolio coefficient  [mode=param, sliderValue=0 ]
  e36 = lambdab ~ 0.0153,  # Target bank profits to output  [mode=param, sliderValue=0 ]
  e37 = lambdac ~ 0.05,  # Cash demand ratio  [mode=param, sliderValue=0 ]
  e38 = xim1 ~ 0.0008,  # Deposit-rate adjustment step 1  [mode=param, sliderValue=0 ]
  e39 = xim2 ~ 0.0007,  # Deposit-rate adjustment step 2  [mode=param, sliderValue=0 ]
  e40 = ro ~ 0.05,  # Reserve ratio  [mode=param, sliderValue=0 ]
  e41 = sigman ~ 0.1666,  # Historic cost weight  [mode=param, sliderValue=0 ]
  e42 = sigmat ~ 0.2,  # Inventory target ratio  [mode=param, sliderValue=0 ]
  e43 = psid ~ 0.15255,  # Dividend payout from firms  [mode=param, sliderValue=0 ]
  e44 = psiu ~ 0.92,  # Retained earnings share for firms  [mode=param, sliderValue=0 ]
  e45 = omega0 ~ -0.20594,  # Wage aspiration coefficient  [mode=param, sliderValue=0 ]
  e46 = omega1 ~ 1,  # Wage aspiration coefficient  [mode=param, sliderValue=0 ]
  e47 = omega2 ~ 2,  # Wage aspiration coefficient  [mode=param, sliderValue=0 ]
  e48 = omega3 ~ 0.45621,  # Wage adjustment speed  [mode=param, sliderValue=0 ]
  e49 = ADDbl ~ 0.02,  # Bond spread over bills  [mode=param, sliderValue=0 ]
  e50 = BANDt ~ 0.01,  # Employment band top  [mode=param, sliderValue=0 ]
  e51 = BANDb ~ 0.01,  # Employment band bottom  [mode=param, sliderValue=0 ]
  e52 = bot ~ 0.05,  # Lower bank liquidity trigger  [mode=param, sliderValue=0 ]
  e53 = GRg ~ 0.03,  # Government real spending growth  [mode=param, sliderValue=0 ]
  e54 = GRpr ~ 0.03,  # Productivity growth  [mode=param, sliderValue=0 ]
  e55 = Nfe ~ 87.181,  # Full employment labor force  [mode=param, sliderValue=0 ]
  e56 = NCAR ~ 0.1,  # Target capital adequacy ratio  [mode=param, sliderValue=0 ]
  e57 = NPLk ~ 0.02,  # Default share on firm loans  [mode=param, sliderValue=0 ]
  e58 = Rbbar ~ 0.035,  # Policy bill rate  [mode=param, sliderValue=0 ]
  e59 = Rln ~ 0.07,  # Normal loan rate  [mode=param, sliderValue=0 ]
  e60 = RA ~ 0,  # Real adjustment term  [mode=param, sliderValue=0 ]
  e61 = top ~ 0.12  # Upper bank liquidity trigger  [mode=param, sliderValue=0 ]
)
```

## Initial Values

```{r}
GROWTH_Initial_Values <- sfcr_set(
  # [ x=-4224 y=-104 ]
  e1 = sigmase ~ 0.16667,  # Opening inventory ratio  [mode=param, sliderValue=0 ]
  e2 = eta ~ 0.04918,  # Loan demand ratio  [mode=param, sliderValue=0 ]
  e3 = phi ~ 0.26417,  # Actual markup  [mode=param, sliderValue=0 ]
  e4 = phit ~ 0.26417,  # Target markup  [mode=param, sliderValue=0 ]
  e5 = ADDl ~ 0.04592,  # Loan spread  [mode=param, sliderValue=0 ]
  e6 = BLR ~ 0.1091,  # Bank liquidity ratio  [mode=param, sliderValue=0 ]
  e7 = BUR ~ 0.06324,  # Debt service burden  [mode=param, sliderValue=0 ]
  e8 = Ck ~ 7334240,  # Real consumption  [mode=param, sliderValue=0 ]
  e9 = CAR ~ 0.09245,  # Capital adequacy ratio  [mode=param, sliderValue=0 ]
  e10 = CONS ~ 52603100,  # Consumption  [mode=param, sliderValue=0 ]
  e11 = ER ~ 1,  # Employment rate  [mode=param, sliderValue=0 ]
  e12 = Fb ~ 1744130,  # Bank profits  [mode=param, sliderValue=0 ]
  e13 = Fbt ~ 1744140,  # Target bank profits  [mode=param, sliderValue=0 ]
  e14 = Ff ~ 18081100,  # Firm profits  [mode=param, sliderValue=0 ]
  e15 = Fft ~ 18013600,  # Planned entrepreneurial profits  [mode=param, sliderValue=0 ]
  e16 = FDb ~ 1325090,  # Bank dividends  [mode=param, sliderValue=0 ]
  e17 = FDf ~ 2670970,  # Firm dividends  [mode=param, sliderValue=0 ]
  e18 = FUb ~ 419039,  # Retained earnings of banks  [mode=param, sliderValue=0 ]
  e19 = FUf ~ 15153800,  # Retained earnings of firms  [mode=param, sliderValue=0 ]
  e20 = FUft ~ 15066200,  # Planned retained earnings  [mode=param, sliderValue=0 ]
  e21 = G ~ 16755600,  # Government expenditure  [mode=param, sliderValue=0 ]
  e22 = Gk ~ 2336160,  # Real government spending  [mode=param, sliderValue=0 ]
  e23 = GL ~ 2775900,  # Gross new household loans  [mode=param, sliderValue=0 ]
  e24 = GRk ~ 0.03001,  # Real capital growth rate  [mode=param, sliderValue=0 ]
  e25 = INV ~ 16911600,  # Nominal gross investment  [mode=param, sliderValue=0 ]
  e26 = Ik ~ 2357910,  # Real gross investment  [mode=param, sliderValue=0 ]
  e27 = N ~ 87.181,  # Employment  [mode=param, sliderValue=0 ]
  e28 = Nt ~ 87.181,  # Desired employment  [mode=param, sliderValue=0 ]
  e29 = NHUC ~ 5.6735,  # Normal historic unit cost  [mode=param, sliderValue=0 ]
  e30 = NL ~ 683593,  # Net new household loans  [mode=param, sliderValue=0 ]
  e31 = NLk ~ 95311,  # Real net new household loans  [mode=param, sliderValue=0 ]
  e32 = NPL ~ 309158,  # Non-performing loans  [mode=param, sliderValue=0 ]
  e33 = NPLke ~ 0.02,  # Expected bad loan share  [mode=param, sliderValue=0 ]
  e34 = NUC ~ 5.6106,  # Normal unit cost  [mode=param, sliderValue=0 ]
  e35 = omegat ~ 112852,  # Wage aspiration  [mode=param, sliderValue=0 ]
  e36 = P ~ 7.1723,  # Price level  [mode=param, sliderValue=0 ]
  e37 = Pbl ~ 18.182,  # Long bond price  [mode=param, sliderValue=0 ]
  e38 = Pe ~ 17937,  # Equity price  [mode=param, sliderValue=0 ]
  e39 = PE ~ 5.07185,  # Price-earnings ratio  [mode=param, sliderValue=0 ]
  e40 = PI ~ 0.0026,  # Inflation rate  [mode=param, sliderValue=0 ]
  e41 = PR ~ 138659,  # Labor productivity  [mode=param, sliderValue=0 ]
  e42 = PSBR ~ 1894780,  # Public sector borrowing requirement  [mode=param, sliderValue=0 ]
  e43 = Q ~ 0.77443,  # Tobin's q  [mode=param, sliderValue=0 ]
  e44 = Rb ~ 0.035,  # Bill rate  [mode=param, sliderValue=0 ]
  e45 = Rbl ~ 0.055,  # Long bond rate  [mode=param, sliderValue=0 ]
  e46 = Rk ~ 0.03008,  # Dividend yield on equities  [mode=param, sliderValue=0 ]
  e47 = Rl ~ 0.06522,  # Loan rate  [mode=param, sliderValue=0 ]
  e48 = Rm ~ 0.0193,  # Deposit rate  [mode=param, sliderValue=0 ]
  e49 = REP ~ 2092310,  # Household loan repayments  [mode=param, sliderValue=0 ]
  e50 = RRl ~ 0.06246,  # Real loan rate  [mode=param, sliderValue=0 ]
  e51 = S ~ 86270300,  # Nominal sales  [mode=param, sliderValue=0 ]
  e52 = Sk ~ 12028300,  # Real sales  [mode=param, sliderValue=0 ]
  e53 = Ske ~ 12028300,  # Expected real sales  [mode=param, sliderValue=0 ]
  e54 = TX ~ 17024100,  # Taxes  [mode=param, sliderValue=0 ]
  e55 = U ~ 0.70073,  # Capacity utilization proxy  [mode=param, sliderValue=0 ]
  e56 = UC ~ 5.6106,  # Unit cost  [mode=param, sliderValue=0 ]
  e57 = W ~ 777968,  # Nominal wage  [mode=param, sliderValue=0 ]
  e58 = WB ~ 67824000,  # Wage bill  [mode=param, sliderValue=0 ]
  e59 = Y ~ 86607700,  # Nominal GDP  [mode=param, sliderValue=0 ]
  e60 = Yk ~ 12088400,  # Real output  [mode=param, sliderValue=0 ]
  e61 = YDr ~ 56446400,  # Regular disposable income  [mode=param, sliderValue=0 ]
  e62 = YDkr ~ 7813270,  # Real regular disposable income  [mode=param, sliderValue=0 ]
  e63 = YDkre ~ 7813290,  # Expected real disposable income  [mode=param, sliderValue=0 ]
  e64 = YP ~ 73158700,  # Personal income  [mode=param, sliderValue=0 ]
  e65 = z1a ~ 0,  # Switch  [mode=param, sliderValue=0 ]
  e66 = z1b ~ 0,  # Switch  [mode=param, sliderValue=0 ]
  e67 = z2a ~ 0,  # Switch  [mode=param, sliderValue=0 ]
  e68 = z2b ~ 0,  # Switch  [mode=param, sliderValue=0 ]
  e69 = Bbd ~ 4389790,  # Bank bill demand constraint  [mode=param, sliderValue=0 ]
  e70 = Bbs ~ 4389790,  # Bills held by banks  [mode=param, sliderValue=0 ]
  e71 = Bcbd ~ 4655690,  # Bills held by central bank  [mode=param, sliderValue=0 ]
  e72 = Bcbs ~ 4655690,  # Bills supplied to central bank  [mode=param, sliderValue=0 ]
  e73 = Bhd ~ 33439320,  # Household bill holdings  [mode=param, sliderValue=0 ]
  e74 = Bhs ~ 33439320,  # Bill supply to households  [mode=param, sliderValue=0 ]
  e75 = Bs ~ 42484800,  # Government bills  [mode=param, sliderValue=0 ]
  e76 = BLd ~ 840742,  # Household bond holdings  [mode=param, sliderValue=0 ]
  e77 = BLs ~ 840742,  # Bond supply  [mode=param, sliderValue=0 ]
  e78 = GD ~ 57728700,  # Government debt  [mode=param, sliderValue=0 ]
  e79 = Ekd ~ 5112.6001,  # Equity market clearing  [mode=param, sliderValue=0 ]
  e80 = Eks ~ 5112.6001,  # Equity supply  [mode=param, sliderValue=0 ]
  e81 = Hbd ~ 2025540,  # Required reserves  [mode=param, sliderValue=0 ]
  e82 = Hbs ~ 2025540,  # Reserve supply  [mode=param, sliderValue=0 ]
  e83 = Hhd ~ 2630150,  # Household cash demand  [mode=param, sliderValue=0 ]
  e84 = Hhs ~ 2630150,  # Cash supply to households  [mode=param, sliderValue=0 ]
  e85 = Hs ~ 4655690,  # High-powered money  [mode=param, sliderValue=0 ]
  e86 = IN ~ 11585400,  # Inventories valued at current cost  [mode=param, sliderValue=0 ]
  e87 = INk ~ 2064890,  # Real inventories  [mode=param, sliderValue=0 ]
  e88 = INke ~ 2405660,  # Expected inventories  [mode=param, sliderValue=0 ]
  e89 = INkt ~ 2064890,  # Inventory target  [mode=param, sliderValue=0 ]
  e90 = K ~ 127486471,  # Nominal capital stock  [mode=param, sliderValue=0 ]
  e91 = Kk ~ 17774838,  # Real capital stock  [mode=param, sliderValue=0 ]
  e92 = Lfd ~ 15962900,  # Firm loan demand  [mode=param, sliderValue=0 ]
  e93 = Lfs ~ 15962900,  # Firm loans supplied  [mode=param, sliderValue=0 ]
  e94 = Lhd ~ 21606600,  # Household loan demand  [mode=param, sliderValue=0 ]
  e95 = Lhs ~ 21606600,  # Household loans supplied  [mode=param, sliderValue=0 ]
  e96 = Ls ~ 37569500,  # Total loan supply  [mode=param, sliderValue=0 ]
  e97 = Mh ~ 40510800,  # Household deposits  [mode=param, sliderValue=0 ]
  e98 = Ms ~ 40510800,  # Deposit supply  [mode=param, sliderValue=0 ]
  e99 = OFb ~ 3474030,  # Bank own funds  [mode=param, sliderValue=0 ]
  e100 = OFbe ~ 3474030,  # Expected own funds  [mode=param, sliderValue=0 ]
  e101 = OFbt ~ 3638100,  # Target bank own funds  [mode=param, sliderValue=0 ]
  e102 = V ~ 165438779,  # Household wealth  [mode=param, sliderValue=0 ]
  e103 = Vfma ~ 159334599,  # Investible wealth  [mode=param, sliderValue=0 ]
  e104 = Vk ~ 23066350,  # Real wealth  [mode=param, sliderValue=0 ]
  e105 = Vf ~ 31361792  # Firm net worth memo  [mode=param, sliderValue=0 ]
)
```

## Balance Sheet

```{r}
GROWTH_Balance_Sheet <- sfcr_matrix(
  # [ x=-2832 y=360 type: transaction_flow ]
  columns = c("Households", "Firms", "Govt", "Central_Bank", "Banks"),
  codes = c("Households", "Firms", "Govt", "Central_Bank", "Banks"),
  c("Inventories", Households = "", Firms = "+IN", Govt = "", Central_Bank = "", Banks = ""),
  c("Fixed Capital", Households = "", Firms = "+K", Govt = "", Central_Bank = "", Banks = ""),
  c("HPM", Households = "+Hhd", Firms = "", Govt = "", Central_Bank = "-Hs", Banks = "+Hbd"),
  c("Money", Households = "+Mh", Firms = "", Govt = "", Central_Bank = "", Banks = "-Ms"),
  c("Bills", Households = "+Bhd", Firms = "", Govt = "-Bs", Central_Bank = "+Bcbd", Banks = "+Bbd"),
  c("Bonds", Households = "+BLd * Pbl", Firms = "", Govt = "-BLs * Pbl", Central_Bank = "", Banks = ""),
  c("Loans", Households = "-Lhd", Firms = "-Lfd", Govt = "", Central_Bank = "", Banks = "+Ls"),
  c("Equities", Households = "+Ekd * Pe", Firms = "-Eks * Pe", Govt = "", Central_Bank = "", Banks = ""),
  c("Bank capital", Households = "+OFb", Firms = "", Govt = "", Central_Bank = "", Banks = "-OFb"),
  c("Balance", Households = "-V", Firms = "-Vf", Govt = "GD", Central_Bank = "", Banks = "")
)
```

## Transaction View

```{r}
GROWTH_Transaction_Flow_Matrix <- sfcr_matrix(
  # [ x=-3104 y=-88 type: transaction_flow ]
  columns = c("Households", "Firms_Current", "Firms_Capital", "Govt", "CB_Current", "CB_Capital", "Banks_Current", "Banks_Capital"),
  codes = c("Households", "Firms_Current", "Firms_Capital", "Govt", "CB_Current", "CB_Capital", "Banks_Current", "Banks_Capital"),
  c("Consumption", Households = "-CONS", Firms_Current = "+CONS", Firms_Capital = "", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = ""),
  c("Govt Expenditure", Households = "", Firms_Current = "+G", Firms_Capital = "", Govt = "-G", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = ""),
  c("Investment", Households = "", Firms_Current = "+INV", Firms_Capital = "-INV", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = ""),
  c("Inventories", Households = "", Firms_Current = "+(IN - last(IN))", Firms_Capital = "-(IN - last(IN))", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = ""),
  c("Taxes", Households = "-TX", Firms_Current = "", Firms_Capital = "", Govt = "+TX", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = ""),
  c("Wages", Households = "+WB", Firms_Current = "-WB", Firms_Capital = "", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = ""),
  c("Inventory Financing Cost", Households = "", Firms_Current = "-last(Rl) * last(IN)", Firms_Capital = "", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "+last(Rl) * last(IN)", Banks_Capital = ""),
  c("Entrepreneurial Profits", Households = "+FDf", Firms_Current = "-Ff", Firms_Capital = "+FUf", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "+last(Rl) * (last(Lfs) - last(IN) - NPL)", Banks_Capital = ""),
  c("Bank Profits", Households = "+FDb", Firms_Current = "", Firms_Capital = "", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "-Fb", Banks_Capital = "+FUb"),
  c("Interest on HH Loans", Households = "-last(Rl) * last(Lhd)", Firms_Current = "", Firms_Capital = "", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "+last(Rl) * last(Lhs)", Banks_Capital = ""),
  c("Interest on Deposits", Households = "+last(Rm) * last(Mh)", Firms_Current = "", Firms_Capital = "", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "-last(Rm) * last(Ms)", Banks_Capital = ""),
  c("Interest on Bills", Households = "+last(Rb) * last(Bhd)", Firms_Current = "", Firms_Capital = "", Govt = "-last(Rb) * last(Bs)", CB_Current = "+last(Rb) * last(Bcbd)", CB_Capital = "", Banks_Current = "+last(Rb) * last(Bbd)", Banks_Capital = ""),
  c("Interest on Bonds", Households = "+last(BLd)", Firms_Current = "", Firms_Capital = "", Govt = "-last(BLd)", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = ""),
  c("Change in Loans", Households = "+(Lhd - last(Lhd))", Firms_Current = "", Firms_Capital = "+(Lfd - last(Lfd))", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = "-(Ls - last(Ls))"),
  c("Change in Cash", Households = "-(Hhd - last(Hhd))", Firms_Current = "", Firms_Capital = "", Govt = "", CB_Current = "", CB_Capital = "+(Hs - last(Hs))", Banks_Current = "", Banks_Capital = "-(Hbd - last(Hbd))"),
  c("Change in Deposits", Households = "-(Mh - last(Mh))", Firms_Current = "", Firms_Capital = "", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = "+(Ms - last(Ms))"),
  c("Change in Bills", Households = "-(Bhd - last(Bhd))", Firms_Current = "", Firms_Capital = "", Govt = "+(Bs - last(Bs))", CB_Current = "", CB_Capital = "-(Bcbd - last(Bcbd))", Banks_Current = "", Banks_Capital = "-(Bbd - last(Bbd))"),
  c("Change in Bonds", Households = "-(BLd - last(BLd)) * Pbl", Firms_Current = "", Firms_Capital = "", Govt = "+(BLs - last(BLs)) * Pbl", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = ""),
  c("Change in Equities", Households = "-(Ekd - last(Ekd)) * Pe", Firms_Current = "", Firms_Capital = "+(Eks - last(Eks)) * Pe", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = ""),
  c("Change in Bank Capital", Households = "-(OFb - last(OFb))", Firms_Current = "", Firms_Capital = "", Govt = "", CB_Current = "", CB_Capital = "", Banks_Current = "", Banks_Capital = "+(OFb - last(OFb))")
)
```

## Hints

```{r}
@hints
  Yk: Real output
  Sk: Real sales
  INk: Real inventories
  Kk: Real capital stock
  GRk: Real capital growth rate
  U: Capacity utilization proxy
  PI: Inflation rate
  W: Nominal wage
  PR: Labor productivity
  N: Employment
  WB: Wage bill
  UC: Unit cost
  P: Price level
  Ff: Firm profits
  Lfd: Firm loan demand
  YP: Personal income
  YDr: Regular disposable income
  V: Household wealth
  Ck: Real consumption
  Lhd: Household loan demand
  Bhd: Household bill holdings
  BLd: Household bond holdings
  Mh: Household deposits
  G: Government expenditure
  Bs: Government bills
  GD: Government debt
  Rb: Bill rate
  Rbl: Long bond rate
  Rm: Deposit rate
  Rl: Loan rate
  Ms: Deposit supply
  Hs: High-powered money
  OFb: Bank own funds
  BLR: Bank liquidity ratio
  CAR: Capital adequacy ratio
@end
```

## Info

```{r}
@info
# GROWTH model

This file encodes the baseline **GROWTH** model from **Godley and Lavoie (2007), chapter 11**.

Practical notes:

- The equation set follows the `sfcr` chapter 11 replication.
- That replication explicitly notes that some operational equations and initial values follow Zezza's working code where it differs from the printed chapter, to obtain a stable baseline path.
- This is a large nonlinear SFC model, so convergence depends heavily on the supplied initial values.

Model structure:

- Firms determine output, inventories, investment, pricing, profits, and equity issuance.
- Households earn wages and distributed profits, consume, borrow, and allocate wealth across deposits, bills, bonds, equities, and cash.
- Government grows real expenditure and issues bills and bonds.
- The central bank supplies high-powered money and holds bills.
- Commercial banks set deposit and loan rates endogenously, satisfy reserve requirements, and manage own funds.

Use this file when you want:

- a single documented artifact for the chapter 11 baseline
- equations and matrices stored together
- a starting point for later shocks, experiments, or simplifications
@end
```

## Minimal Visual Layout

```{r}
@scope Embedded_Scope_1 position=-1
  x1: -2144
  y1: 16
  x2: -1440
  y2: 368
  elmUid: rGhA1I
  speed: 2
  flags: x6001206
  source: uid:GLG11_01 value:0
  trace: uid:GLG11_02 value:0
  trace: uid:GLG11_03 value:0
  trace: uid:GLG11_04 value:0
@end
```

```{r}
@circuit
x -1983 773 -1592 776 4 18 Godley\sand\sLavoie\s(2007)\sChapter\s11:\sGROWTH 808080FF U:GLG11_T1
x -1983 800 -1645 803 4 12 Baseline\sspecification\sbased\son\sthe\ssfcr\sreplication\sof\sthe\smodel 808080FF U:GLG11_T2
x -1983 821 -1506 824 4 12 Includes\sfirms,\shouseholds,\sgovt,\scentral\sbank,\sbanks,\sgrowth,\spricing,\sand\sportfolio\schoice 808080FF U:GLG11_T3
207 -2016 976 -1952 976 164 Y U:GLG11_01
207 -2016 1008 -1952 1008 164 V U:GLG11_02
207 -2016 1040 -1952 1040 164 Bs U:GLG11_03
207 -2016 1072 -1952 1072 164 Ls U:GLG11_04
207 -3872 320 -3808 320 164 P U:GLG11_05
207 -3872 352 -3808 352 164 Rl U:GLG11_06
431 -2224 752 -2192 784 0 100 true false U:GLG11_Box
@end
```

