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
  equationTableMnaMode: true
  EqnTable Newton Jacobian: true
  equationTableTolerance: 1e-15
  lookupMode: pwl
  lookupClamp: true
  convergenceCheckThreshold: 100
  infoViewerUpdateIntervalMs: 200
  Auto-Open Model Info on Load: true
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
Real_and_Firms_Core <- sfcr_set(
  # [ x=-2992 y=664 uid=9aijO8 invisible=false ]
  e1 = Yk ~ Ske + INke - last(INk),  # Real output  [mode=voltage, initial=12088400 ]
  e2 = Ske ~ \beta * Sk + (1 - \beta) * last(Sk) * (1 + (GRpr + RA)),  # Expected real sales  [mode=voltage, initial=12028300 ]
  e3 = INke ~ last(INk) + \gamma * (INkt - last(INk)),  # Expected inventories  [mode=voltage, initial=2405660 ]
  e4 = INkt ~ \sigmat * Ske,  # Inventory target  [mode=voltage, initial=2064890 ]
  e5 = INk ~ last(INk) + Yk - Sk - NPL / UC,  # Real inventories  [mode=param, initial=2064890 ]
  e6 = Kk ~ last(Kk) * (1 + GRk),  # Real capital stock  [mode=param, initial=17774838 ]
  e7 = GRk ~ \gamma0 + \gammau * last(U) - \gammar * RRl,  # Real capital growth rate  [mode=param, initial=0.03001 ]
  e8 = U ~ Yk / last(Kk),  # Capacity utilization proxy  [mode=param, initial=0.70073 ]
  e9 = RRl ~ ((1 + Rl) / (1 + PI)) - 1,  # Real loan rate  [mode=param, initial=0.06246 ]
  e10 = PI ~ (P - last(P)) / last(P),  # Inflation rate  [mode=param, initial=0.0026 ]
  e11 = Ik ~ (Kk - last(Kk)) + \delta * last(Kk),  # Real gross investment  [mode=param, initial=2357910 ]
  e12 = Sk ~ Ck + Gk + Ik,  # Real sales  [mode=voltage, initial=12028300 ]
  e13 = S ~ Sk * P,  # Nominal sales  [mode=param, initial=86270300 ]
  e14 = IN ~ INk * UC,  # Inventories valued at current cost  [mode=param, initial=11585400 ]
  e15 = INV ~ Ik * P,  # Nominal gross investment  [mode=param, initial=16911600 ]
  e16 = K ~ Kk * P,  # Nominal capital stock  [mode=param, initial=127486471 ]
  e17 = Y ~ Sk * P + (INk - last(INk)) * UC  # Nominal GDP  [mode=param, initial=86607700 ]
)
```

```{r}
Wages_Prices_Profits <- sfcr_set(
  # [ x=-3904 y=-104 uid=Aqg0Nh invisible=false ]
  e1 = \omegat ~ exp(\omega0 + \omega1 * log(PR) + \omega2 * log(ER + z3 * (1 - ER) - z4 * BANDt + z5 * BANDb)),  # Wage aspiration  [mode=param, initial=112852 ]
  e2 = ER ~ last(N) / last(Nfe),  # Employment rate  [mode=param, initial=1 ]
  e3 = z3a ~ (ER > (1 - BANDb)) ? 1 : 0,  # Switch  [mode=param ]
  e4 = z3b ~ (ER <= (1 + BANDt)) ? 1 : 0,  # Switch  [mode=param ]
  e5 = z3 ~ z3a * z3b,  # Switch  [mode=param ]
  e6 = z4 ~ (ER > (1 + BANDt)) ? 1 : 0,  # Switch  [mode=param ]
  e7 = z5 ~ (ER < (1 - BANDb)) ? 1 : 0,  # Switch  [mode=param ]
  e8 = W ~ last(W) + \omega3 * (\omegat * last(P) - last(W)),  # Nominal wage  [mode=param, initial=777968 ]
  e9 = PR ~ last(PR) * (1 + GRpr),  # Labor productivity  [mode=param, initial=138659 ]
  e10 = Nt ~ Yk / PR,  # Desired employment  [mode=voltage, initial=87.181 ]
  e11 = N ~ last(N) + \etan * (Nt - last(N)),  # Employment  [mode=voltage, initial=87.181 ]
  e12 = WB ~ N * W,  # Wage bill  [mode=voltage, initial=67824000 ]
  e13 = UC ~ WB / Yk,  # Unit cost  [mode=param, initial=5.6106 ]
  e14 = NUC ~ W / PR,  # Normal unit cost  [mode=param, initial=5.6106 ]
  e15 = NHUC ~ (1 - \sigman) * NUC + \sigman * (1 + last(Rln)) * last(NUC),  # Normal historic unit cost  [mode=param, initial=5.6735 ]
  e16 = P ~ (1 + \phi) * NHUC,  # Price level  [mode=param, initial=7.1723 ]
  e17 = \phi ~ last(\phi) + \epsilon2 * (last(\phit) - last(\phi)),  # Actual markup  [mode=param, initial=0.26417 ]
  e18 = \phit ~ (FUft + FDf + last(Rl) * (last(Lfd) - last(IN))) / ((1 - \sigmase) * Ske * UC + (1 + last(Rl)) * \sigmase * Ske * last(UC)),  # Target markup  [mode=param, initial=0.26417 ]
  e19 = HCe ~ (1 - \sigmase) * Ske * UC + (1 + last(Rl)) * \sigmase * Ske * last(UC),  # Expected historical costs  [mode=param ]
  e20 = \sigmase ~ last(INk) / Ske,  # Opening inventory ratio  [mode=param, initial=0.16667 ]
  e21 = Fft ~ FUft + FDf + last(Rl) * (last(Lfd) - last(IN)),  # Planned entrepreneurial profits  [mode=param, initial=18013600 ]
  e22 = FUft ~ \psiu * last(INV),  # Planned retained earnings  [mode=param, initial=15066200 ]
  e23 = FDf ~ \psid * last(Ff),  # Firm dividends  [mode=param, initial=2670970 ]
  e24 = Ff ~ S - WB + (IN - last(IN)) - last(Rl) * last(IN),  # Firm profits  [mode=param, initial=18081100 ]
  e25 = FUf ~ Ff - FDf - last(Rl) * (last(Lfd) - last(IN)) + last(Rl) * NPL,  # Retained earnings of firms  [mode=param, initial=15153800 ]
  e26 = Lfd ~ last(Lfd) + INV + (IN - last(IN)) - FUf - (Eks - last(Eks)) * Pe - NPL,  # Firm loan demand  [mode=param, initial=15962900 ]
  e27 = NPL ~ NPLk * last(Lfs),  # Non-performing loans  [mode=param, initial=309158 ]
  e28 = Eks ~ last(Eks) + ((1 - \psiu) * last(INV)) / Pe,  # Equity supply  [mode=voltage, initial=5112.6001 ]
  e29 = Rk ~ FDf / (last(Pe) * last(Ekd)),  # Dividend yield on equities  [mode=param, initial=0.03008 ]
  e30 = PE ~ Pe / (Ff / last(Eks)),  # Price-earnings ratio  [mode=param, initial=5.07185 ]
  e31 = Q ~ (Eks * Pe + Lfd) / (K + IN)  # Tobin's q  [mode=param, initial=0.77443 ]
)
```

```{r}
Households_Government_Banks <- sfcr_set(
  # [ x=-3904 y=440 uid=U8OCHi invisible=false ]
  e1 = YP ~ WB + FDf + FDb + last(Rm) * last(Mh) + last(Rb) * last(Bhd) + last(BLs),  # Personal income  [mode=voltage, initial=73158700 ]
  e2 = TX ~ \theta * YP,  # Taxes  [mode=voltage, initial=17024100 ]
  e3 = YDr ~ YP - TX - last(Rl) * last(Lhd),  # Regular disposable income  [mode=voltage, initial=56446400 ]
  e4 = YDhs ~ YDr + CG,  # Haig-Simons income  [mode=param ]
  e5 = CG ~ (Pbl - last(Pbl)) * last(BLd) + (Pe - last(Pe)) * last(Ekd) + (OFb - last(OFb)),  # Capital gains  [mode=param ]
  e6 = V ~ last(V) + YDr - CONS + (Pbl - last(Pbl)) * last(BLd) + (Pe - last(Pe)) * last(Ekd) + (OFb - last(OFb)),  # Household wealth  [mode=voltage, initial=165438779 ]
  e7 = Vk ~ V / P,  # Real wealth  [mode=param, initial=23066350 ]
  e8 = CONS ~ Ck * P,  # Consumption  [mode=param, initial=52603100 ]
  e9 = Ck ~ \alpha_1 * (YDkre + NLk) + \alpha_2 * last(Vk),  # Real consumption  [mode=voltage, initial=7334240 ]
  e10 = YDkre ~ \epsilon * YDkr + (1 - \epsilon) * (last(YDkr) * (1 + GRpr)),  # Expected real disposable income  [mode=voltage, initial=7813290 ]
  e11 = YDkr ~ YDr / P - ((P - last(P)) * last(Vk)) / P,  # Real regular disposable income  [mode=voltage, initial=7813270 ]
  e12 = GL ~ \eta * YDr,  # Gross new household loans  [mode=voltage, initial=2775900 ]
  e13 = \eta ~ \eta0 - \etar * RRl,  # Loan demand ratio  [mode=param, initial=0.04918 ]
  e14 = NL ~ GL - REP,  # Net new household loans  [mode=voltage, initial=683593 ]
  e15 = REP ~ \deltarep * last(Lhd),  # Household loan repayments  [mode=param, initial=2092310 ]
  e16 = Lhd ~ last(Lhd) + GL - REP,  # Household loan demand  [mode=param, initial=21606600 ]
  e17 = NLk ~ NL / P,  # Real net new household loans  [mode=voltage, initial=95311 ]
  e18 = BUR ~ (REP + last(Rl) * last(Lhd)) / last(YDr),  # Debt service burden  [mode=param, initial=0.06324 ]
  e19 = Bhd ~ last(Vfma) * (\lambda20 + \lambda22 * last(Rb) - \lambda21 * last(Rm) - \lambda24 * last(Rk) - \lambda23 * last(Rbl) - \lambda25 * (YDr / V)),  # Household bill holdings  [mode=param, initial=33439320 ]
  e20 = BLd ~ last(Vfma) * (\lambda30 - \lambda32 * last(Rb) - \lambda31 * last(Rm) - \lambda34 * last(Rk) + \lambda33 * last(Rbl) - \lambda35 * (YDr / V)) / Pbl,  # Household bond holdings  [mode=param, initial=840742 ]
  e21 = Pe ~ last(Vfma) * (\lambda40 - \lambda42 * last(Rb) - \lambda41 * last(Rm) + \lambda44 * last(Rk) - \lambda43 * last(Rbl) - \lambda45 * (YDr / V)) / Ekd,  # Equity price  [mode=voltage, initial=17937 ]
  e22 = Mh ~ Vfma - Bhd - Pe * Ekd - Pbl * BLd + Lhd,  # Household deposits  [mode=param, initial=40510800 ]
  e23 = Vfma ~ V - Hhd - OFb,  # Investible wealth  [mode=param, initial=159334599 ]
  e24 = VfmaA ~ Mh + Bhd + Pbl * BLd + Pe * Ekd,  # Portfolio memo  [mode=param ]
  e25 = Hhd ~ \lambdac * CONS,  # Household cash demand  [mode=param, initial=2630150 ]
  e26 = Ekd ~ Eks,  # Equity market clearing  [mode=voltage, initial=5112.6001 ]
  e27 = G ~ Gk * P,  # Government expenditure  [mode=param, initial=16755600 ]
  e28 = Gk ~ last(Gk) * (1 + GRg),  # Real government spending  [mode=param, initial=2336160 ]
  e29 = PSBR ~ G + last(BLs) + last(Rb) * (last(Bbs) + last(Bhs)) - TX,  # Public sector borrowing requirement  [mode=param, initial=1894780 ]
  e30 = Bs ~ last(Bs) + G - TX - (BLs - last(BLs)) * Pbl + last(Rb) * (last(Bhs) + last(Bbs)) + last(BLs),  # Government bills  [mode=param, initial=42484800 ]
  e31 = GD ~ Bbs + Bhs + BLs * Pbl + Hs,  # Government debt  [mode=param, initial=57728700 ]
  e32 = Fcb ~ last(Rb) * last(Bcbd),  # Central bank profits  [mode=param ]
  e33 = BLs ~ BLd,  # Bond supply  [mode=param, initial=840742 ]
  e34 = Bhs ~ Bhd,  # Bill supply to households  [mode=param, initial=33439320 ]
  e35 = Hhs ~ Hhd,  # Cash supply to households  [mode=param, initial=2630150 ]
  e36 = Hbs ~ Hbd,  # Reserve supply  [mode=param, initial=2025540 ]
  e37 = Hs ~ Hbs + Hhs,  # High-powered money  [mode=param, initial=4655690 ]
  e38 = Bcbd ~ Hs,  # Bills held by central bank  [mode=param, initial=4655690 ]
  e39 = Bcbs ~ Bcbd,  # Bills supplied to central bank  [mode=param, initial=4655690 ]
  e40 = Rb ~ Rbbar,  # Bill rate  [mode=param, initial=0.035 ]
  e41 = Rbl ~ Rb + ADDbl,  # Long bond rate  [mode=param, initial=0.055 ]
  e42 = Pbl ~ 1 / Rbl,  # Long bond price  [mode=param, initial=18.182 ]
  e43 = Ms ~ Mh,  # Deposit supply  [mode=param, initial=40510800 ]
  e44 = Lfs ~ Lfd,  # Firm loans supplied  [mode=param, initial=15962900 ]
  e45 = Lhs ~ Lhd,  # Household loans supplied  [mode=param, initial=21606600 ]
  e46 = Hbd ~ ro * Ms,  # Required reserves  [mode=param, initial=2025540 ]
  e47 = Bbs ~ last(Bbs) + (Bs - last(Bs)) - (Bhs - last(Bhs)) - (Bcbs - last(Bcbs)),  # Bills held by banks  [mode=param, initial=4389790 ]
  e48 = Bbd ~ Ms + OFb - Lfs - Lhs - Hbd,  # Bank bill demand constraint  [mode=param, initial=4389790 ]
  e49 = BLR ~ Bbd / Ms,  # Bank liquidity ratio  [mode=param, initial=0.1091 ]
  e50 = Rm ~ last(Rm) + z1a * \xim1 + z1b * \xim2 - z2a * \xim1 - z2b * \xim2,  # Deposit rate  [mode=param, initial=0.0193 ]
  e51 = z2a ~ (last(BLR) > (top + 0.05)) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  e52 = z2b ~ (last(BLR) > top) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  e53 = z1a ~ (last(BLR) <= bot) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  e54 = z1b ~ (last(BLR) <= (bot - 0.05)) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  e55 = Rl ~ Rm + ADDl,  # Loan rate  [mode=param, initial=0.06522 ]
  e56 = OFbt ~ NCAR * (last(Lfs) + last(Lhs)),  # Target bank own funds  [mode=param, initial=3638100 ]
  e57 = OFbe ~ last(OFb) + \betab * (OFbt - last(OFb)),  # Expected own funds  [mode=param, initial=3474030 ]
  e58 = FUbt ~ OFbe - last(OFb) + NPLke * last(Lfs),  # Target retained earnings of banks  [mode=param ]
  e59 = NPLke ~ \epsilonb * last(NPLke) + (1 - \epsilonb) * last(NPLk),  # Expected bad loan share  [mode=param, initial=0.02 ]
  e60 = FDb ~ Fb - FUb,  # Bank dividends  [mode=param, initial=1325090 ]
  e61 = Fbt ~ \lambdab * last(Y) + (OFbe - last(OFb) + NPLke * last(Lfs)),  # Target bank profits  [mode=param, initial=1744140 ]
  e62 = Fb ~ last(Rl) * (last(Lfs) + last(Lhs) - NPL) + last(Rb) * last(Bbd) - last(Rm) * last(Ms),  # Bank profits  [mode=param, initial=1744130 ]
  e63 = ADDl ~ (Fbt - last(Rb) * last(Bbd) + last(Rm) * (last(Ms) - (1 - NPLke) * last(Lfs) - last(Lhs))) / ((1 - NPLke) * last(Lfs) + last(Lhs)),  # Loan spread  [mode=param, initial=0.04592 ]
  e64 = FUb ~ Fb - \lambdab * last(Y),  # Retained earnings of banks  [mode=param, initial=419039 ]
  e65 = OFb ~ last(OFb) + FUb - NPL,  # Bank own funds  [mode=param, initial=3474030 ]
  e66 = CAR ~ OFb / (Lfs + Lhs),  # Capital adequacy ratio  [mode=param, initial=0.09245 ]
  e67 = Vf ~ IN + K - Lfd - Ekd * Pe,  # Firm net worth memo  [mode=param, initial=31361792 ]
  e68 = Ls ~ Lfs + Lhs  # Total loan supply  [mode=param, initial=37569500 ]
)
```

## Parameters

```{r}
Parameters <- sfcr_set(
  # [ x=-2560 y=600 uid=8WmQNI invisible=false ]
  e1 = \alpha_1 ~ 0.75,  # Consumption out of expected income  [mode=param ]
  e2 = \alpha_2 ~ 0.064,  # Consumption out of wealth  [mode=param ]
  e3 = \beta ~ 0.5,  # Sales expectation weight  [mode=param ]
  e4 = \betab ~ 0.4,  # Bank own-funds adjustment  [mode=param ]
  e5 = \gamma ~ 0.15,  # Inventory adjustment speed  [mode=param ]
  e6 = \gamma0 ~ 0.00122,  # Autonomous capital growth  [mode=param ]
  e7 = \gammar ~ 0.1,  # Interest sensitivity of growth  [mode=param ]
  e8 = \gammau ~ 0.05,  # Utilization sensitivity of growth  [mode=param ]
  e9 = \delta ~ 0.10667,  # Depreciation rate  [mode=param ]
  e10 = \deltarep ~ 0.1,  # Household repayment rate  [mode=param ]
  e11 = \epsilon ~ 0.5,  # Disposable-income expectation weight  [mode=param ]
  e12 = \epsilon2 ~ 0.83,  # Markup adjustment speed  [mode=param ]
  e13 = \epsilonb ~ 0.25,  # Bank expectations weight  [mode=param ]
  e14 = \eta0 ~ 0.07416,  # Base household loan ratio  [mode=param ]
  e15 = \etan ~ 0.6,  # Employment adjustment speed  [mode=param ]
  e16 = \etar ~ 0.4,  # Loan-rate sensitivity of household borrowing  [mode=param ]
  e17 = \theta ~ 0.22844,  # Tax rate  [mode=param ]
  e18 = \lambda20 ~ 0.25,  # Portfolio coefficient  [mode=param ]
  e19 = \lambda21 ~ 2.2,  # Portfolio coefficient  [mode=param ]
  e20 = \lambda22 ~ 6.6,  # Portfolio coefficient  [mode=param ]
  e21 = \lambda23 ~ 2.2,  # Portfolio coefficient  [mode=param ]
  e22 = \lambda24 ~ 2.2,  # Portfolio coefficient  [mode=param ]
  e23 = \lambda25 ~ 0.1,  # Portfolio coefficient  [mode=param ]
  e24 = \lambda30 ~ -0.04341,  # Portfolio coefficient  [mode=param ]
  e25 = \lambda31 ~ 2.2,  # Portfolio coefficient  [mode=param ]
  e26 = \lambda32 ~ 2.2,  # Portfolio coefficient  [mode=param ]
  e27 = \lambda33 ~ 6.6,  # Portfolio coefficient  [mode=param ]
  e28 = \lambda34 ~ 2.2,  # Portfolio coefficient  [mode=param ]
  e29 = \lambda35 ~ 0.1,  # Portfolio coefficient  [mode=param ]
  e30 = \lambda40 ~ 0.67132,  # Portfolio coefficient  [mode=param ]
  e31 = \lambda41 ~ 2.2,  # Portfolio coefficient  [mode=param ]
  e32 = \lambda42 ~ 2.2,  # Portfolio coefficient  [mode=param ]
  e33 = \lambda43 ~ 2.2,  # Portfolio coefficient  [mode=param ]
  e34 = \lambda44 ~ 6.6,  # Portfolio coefficient  [mode=param ]
  e35 = \lambda45 ~ 0.1,  # Portfolio coefficient  [mode=param ]
  e36 = \lambdab ~ 0.0153,  # Target bank profits to output  [mode=param ]
  e37 = \lambdac ~ 0.05,  # Cash demand ratio  [mode=param ]
  e38 = \xim1 ~ 0.0008,  # Deposit-rate adjustment step 1  [mode=param ]
  e39 = \xim2 ~ 0.0007,  # Deposit-rate adjustment step 2  [mode=param ]
  e40 = ro ~ 0.05,  # Reserve ratio  [mode=param ]
  e41 = \sigman ~ 0.1666,  # Historic cost weight  [mode=param ]
  e42 = \sigmat ~ 0.2,  # Inventory target ratio  [mode=param ]
  e43 = \psid ~ 0.15255,  # Dividend payout from firms  [mode=param ]
  e44 = \psiu ~ 0.92,  # Retained earnings share for firms  [mode=param ]
  e45 = \omega0 ~ -0.20594,  # Wage aspiration coefficient  [mode=param ]
  e46 = \omega1 ~ 1,  # Wage aspiration coefficient  [mode=param ]
  e47 = \omega2 ~ 2,  # Wage aspiration coefficient  [mode=param ]
  e48 = \omega3 ~ 0.45621,  # Wage adjustment speed  [mode=param ]
  e49 = ADDbl ~ 0.02,  # Bond spread over bills  [mode=param ]
  e50 = BANDt ~ 0.01,  # Employment band top  [mode=param ]
  e51 = BANDb ~ 0.01,  # Employment band bottom  [mode=param ]
  e52 = bot ~ 0.05,  # Lower bank liquidity trigger  [mode=param ]
  e53 = GRg ~ 0.03,  # Government real spending growth  [mode=param ]
  e54 = GRpr ~ 0.03,  # Productivity growth  [mode=param ]
  e55 = Nfe ~ 87.181,  # Full employment labor force  [mode=param ]
  e56 = NCAR ~ 0.1,  # Target capital adequacy ratio  [mode=param ]
  e57 = NPLk ~ 0.02,  # Default share on firm loans  [mode=param ]
  e58 = Rbbar ~ 0.035,  # Policy bill rate  [mode=param ]
  e59 = Rln ~ 0.07,  # Normal loan rate  [mode=param ]
  e60 = RA ~ 0,  # Real adjustment term  [mode=param ]
  e61 = top ~ 0.12  # Upper bank liquidity trigger  [mode=param ]
)
```

## Initial Values

Initial values are now encoded inline on the corresponding equation rows using `initial=...` metadata.

## Balance Sheet

```{r}
Balance_Sheet <- sfcr_matrix(
  # [ x=-2832 y=360 uid=7QbcjL type: transaction_flow invisible=false ]
  columns = c("Households", "Firms", "Govt", "Central_Bank", "Banks"),
  codes = c("H1", "F2", "G3", "C4", "B5"),
  type = c("", "", "", "", ""),
  c("Inventories", H1 = "", F2 = "+IN", G3 = "", C4 = "", B5 = ""),
  c("Fixed Capital", H1 = "", F2 = "+K", G3 = "", C4 = "", B5 = ""),
  c("HPM", H1 = "+Hhd", F2 = "", G3 = "", C4 = "-Hs", B5 = "+Hbd"),
  c("Money", H1 = "+Mh", F2 = "", G3 = "", C4 = "", B5 = "-Ms"),
  c("Bills", H1 = "+Bhd", F2 = "", G3 = "-Bs", C4 = "+Bcbd", B5 = "+Bbd"),
  c("Bonds", H1 = "+BLd * Pbl", F2 = "", G3 = "-BLs * Pbl", C4 = "", B5 = ""),
  c("Loans", H1 = "-Lhd", F2 = "-Lfd", G3 = "", C4 = "", B5 = "+Ls"),
  c("Equities", H1 = "+Ekd * Pe", F2 = "-Eks * Pe", G3 = "", C4 = "", B5 = ""),
  c("Bank capital", H1 = "+OFb", F2 = "", G3 = "", C4 = "", B5 = "-OFb"),
  c("Balance", H1 = "-V", F2 = "-Vf", G3 = "GD", C4 = "", B5 = "")
)
```

## Transaction View

```{r}
Transaction_Flow_Matrix <- sfcr_matrix(
  # [ x=-3104 y=-88 uid=FDUuHd type: transaction_flow invisible=false ]
  columns = c("Households", "Firms_Current", "Firms_Capital", "Govt", "CB_Current", "CB_Capital", "Banks_Current", "Banks_Capital"),
  codes = c("H1", "F2", "F3", "G4", "C5", "C6", "B7", "B8"),
  type = c("", "", "", "", "", "", "", ""),
  c("Consumption", H1 = "-CONS", F2 = "+CONS", F3 = "", G4 = "", C5 = "", C6 = "", B7 = "", B8 = ""),
  c("Govt Expenditure", H1 = "", F2 = "+G", F3 = "", G4 = "-G", C5 = "", C6 = "", B7 = "", B8 = ""),
  c("Investment", H1 = "", F2 = "+INV", F3 = "-INV", G4 = "", C5 = "", C6 = "", B7 = "", B8 = ""),
  c("Inventories", H1 = "", F2 = "+(IN - last(IN))", F3 = "-(IN - last(IN))", G4 = "", C5 = "", C6 = "", B7 = "", B8 = ""),
  c("Taxes", H1 = "-TX", F2 = "", F3 = "", G4 = "+TX", C5 = "", C6 = "", B7 = "", B8 = ""),
  c("Wages", H1 = "+WB", F2 = "-WB", F3 = "", G4 = "", C5 = "", C6 = "", B7 = "", B8 = ""),
  c("Inventory Financing Cost", H1 = "", F2 = "-last(Rl) * last(IN)", F3 = "", G4 = "", C5 = "", C6 = "", B7 = "+last(Rl) * last(IN)", B8 = ""),
  c("Entrepreneurial Profits", H1 = "+FDf", F2 = "-Ff", F3 = "+FUf", G4 = "", C5 = "", C6 = "", B7 = "+last(Rl) * (last(Lfs) - last(IN) - NPL)", B8 = ""),
  c("Bank Profits", H1 = "+FDb", F2 = "", F3 = "", G4 = "", C5 = "", C6 = "", B7 = "-Fb", B8 = "+FUb"),
  c("Interest on HH Loans", H1 = "-last(Rl) * last(Lhd)", F2 = "", F3 = "", G4 = "", C5 = "", C6 = "", B7 = "+last(Rl) * last(Lhs)", B8 = ""),
  c("Interest on Deposits", H1 = "+last(Rm) * last(Mh)", F2 = "", F3 = "", G4 = "", C5 = "", C6 = "", B7 = "-last(Rm) * last(Ms)", B8 = ""),
  c("Interest on Bills", H1 = "+last(Rb) * last(Bhd)", F2 = "", F3 = "", G4 = "-last(Rb) * last(Bs)", C5 = "+last(Rb) * last(Bcbd)", C6 = "", B7 = "+last(Rb) * last(Bbd)", B8 = ""),
  c("Interest on Bonds", H1 = "+last(BLd)", F2 = "", F3 = "", G4 = "-last(BLd)", C5 = "", C6 = "", B7 = "", B8 = ""),
  c("Change in Loans", H1 = "+(Lhd - last(Lhd))", F2 = "", F3 = "+(Lfd - last(Lfd))", G4 = "", C5 = "", C6 = "", B7 = "", B8 = "-(Ls - last(Ls))"),
  c("Change in Cash", H1 = "-(Hhd - last(Hhd))", F2 = "", F3 = "", G4 = "", C5 = "", C6 = "+(Hs - last(Hs))", B7 = "", B8 = "-(Hbd - last(Hbd))"),
  c("Change in Deposits", H1 = "-(Mh - last(Mh))", F2 = "", F3 = "", G4 = "", C5 = "", C6 = "", B7 = "", B8 = "+(Ms - last(Ms))"),
  c("Change in Bills", H1 = "-(Bhd - last(Bhd))", F2 = "", F3 = "", G4 = "+(Bs - last(Bs))", C5 = "", C6 = "-(Bcbd - last(Bcbd))", B7 = "", B8 = "-(Bbd - last(Bbd))"),
  c("Change in Bonds", H1 = "-(BLd - last(BLd)) * Pbl", F2 = "", F3 = "", G4 = "+(BLs - last(BLs)) * Pbl", C5 = "", C6 = "", B7 = "", B8 = ""),
  c("Change in Equities", H1 = "-(Ekd - last(Ekd)) * Pe", F2 = "", F3 = "+(Eks - last(Eks)) * Pe", G4 = "", C5 = "", C6 = "", B7 = "", B8 = ""),
  c("Change in Bank Capital", H1 = "-(OFb - last(OFb))", F2 = "", F3 = "", G4 = "", C5 = "", C6 = "", B7 = "", B8 = "+(OFb - last(OFb))")
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
@zorder
  uid:8WmQNI z:0
  uid:7QbcjL z:2
  uid:FDUuHd z:3
  uid:GLG11_T1 z:4
  uid:GLG11_T2 z:5
  uid:GLG11_T3 z:6
  uid:GLG11_01 z:7
  uid:GLG11_02 z:8
  uid:GLG11_03 z:9
  uid:GLG11_04 z:10
  uid:GLG11_05 z:11
  uid:GLG11_06 z:12
  uid:GLG11_Box z:13
  uid:rGhA1I z:14
  uid:Aqg0Nh z:15
  uid:U8OCHi z:16
  uid:9aijO8 z:17
@end
```

```{r}
@circuit
x -1983 773 -1570 776 4 18 Godley\sand\sLavoie\s(2007)\sChapter\s11:\sGROWTH 808080FF U:GLG11_T1 Z:4
x -1983 800 -1613 803 4 12 Baseline\sspecification\sbased\son\sthe\ssfcr\sreplication\sof\sthe\smodel 808080FF U:GLG11_T2 Z:5
x -1983 821 -1460 824 4 12 Includes\sfirms,\shouseholds,\sgovt,\scentral\sbank,\sbanks,\sgrowth,\spricing,\sand\sportfolio\schoice 808080FF U:GLG11_T3 Z:6
207 -2016 976 -1952 976 164 Y U:GLG11_01 Z:7
207 -2016 1008 -1952 1008 164 V U:GLG11_02 Z:8
207 -2016 1040 -1952 1040 164 Bs U:GLG11_03 Z:9
207 -2016 1072 -1952 1072 164 Ls U:GLG11_04 Z:10
207 -3872 320 -3808 320 164 P U:GLG11_05 Z:11
207 -3872 352 -3808 352 164 Rl U:GLG11_06 Z:12
431 -2224 752 -2192 784 0 100 true false U:GLG11_Box Z:13
@end
```

