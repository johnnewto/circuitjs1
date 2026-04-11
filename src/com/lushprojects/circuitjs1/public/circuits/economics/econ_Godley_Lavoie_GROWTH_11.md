```{r}
growth_eqs <- sfcr_set(
  # [ x=-80 y=1240 uid=THSfHC invisible=false ]
  # --- A.1: Central Bank Policy (exogenous anchor) ---
  Rb ~ Rbbar,  # Interest rate on government bills  [mode=param, initial=0.035 ]
  Rbl ~ Rb + ADDbl,  # Interest rate on bonds  [mode=param, initial=0.055 ]
  Pbl ~ 1/Rbl,  # Price of government bonds  [mode=param, initial=18.182 ]
  Fcb ~ last(Rb)*last(Bcbd),  # Central bank "profits"  [mode=param ]
  # --- A.2: Bank Interest Rate Mechanism (from lagged BLR) ---
  z1a ~ (last(BLR) <= bot) ? 1 : 0,  # Is one if bank liquidity ratio is below bottom range  [mode=param, initial=0 ]
  z1b ~ (last(BLR) <= (bot - 0.05)) ? 1 : 0,  # Is one if bank liquidity ratio is below bottom range  [mode=param, initial=0 ]
  z2a ~ (last(BLR) > (top + 0.05)) ? 1 : 0,  # Is one if bank liquidity ratio is above top range  [mode=param, initial=0 ]
  z2b ~ (last(BLR) > top) ? 1 : 0,  # Is one if bank liquidity ratio is above top range  [mode=param, initial=0 ]
  Rm ~ last(Rm) + z1a*xim1 + z1b*xim2 - z2a*xim1 - z2b*xim2,  # Interest rate on deposits  [mode=param, initial=0.0193 ]
  # --- A.3: Bank Targets
  NPLke ~ epsb*last(NPLke) + (1 - epsb)*last(NPLk),  # Expected proportion of Non-Performing Loans  [mode=param, initial=0.02 ]
  OFbt ~ NCAR*(last(Lfs) + last(Lhs)),  # Long-run target for banks own funds  [mode=param, initial=3638100 ]
  OFbe ~ last(OFb) + betab*(OFbt - last(OFb)),  # Short-run target for banks own funds  [mode=param, initial=3474030 ]
  FUbt ~ OFbe - last(OFb) + NPLke*last(Lfs),  # Targt retained earnings of banks  [mode=param ]
  Fbt ~ lambdab*last(Y) + (OFbe - last(OFb) + NPLke*last(Lfs)),  # Target profits of banks  [mode=param, initial=1744140 ]
  ADDl ~ (Fbt - last(Rb)*last(Bbd) + last(Rm)*(last(Ms) - (1 - NPLke)*last(Lfs) - last(Lhs)))/((1 - NPLke)*last(Lfs) + last(Lhs)),  # Spread between interest rate on loans and rate on deposits  [mode=param, initial=0.04592 ]
  Rl ~ Rm + ADDl,  # Interest rate on loans  [mode=param, initial=0.06522 ]
  # --- A.4: Labor Market — Wage-Setting (from lagged employment) ---
  PR ~ last(PR)*(1 + GRpr),  # Lobor productivity  [mode=param, initial=138659 ]
  ER ~ last(N)/last(Nfe),  # Employment rate  [mode=param, initial=1 ]
  z3a ~ (ER > (1-BANDb)) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  z3b ~ (ER <= (1+BANDt)) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  z3 ~ z3a * z3b,  # Parameter in wage aspiration equation  [mode=param ]
  z4 ~ (ER > (1+BANDt)) ? 1 : 0,  # Parameter in wage aspiration equation  [mode=param, initial=0 ]
  z5 ~ (ER < (1-BANDb)) ? 1 : 0,  # Parameter in wage aspiration equation  [mode=param, initial=0 ]
  omegat ~ exp(omega0 + omega1*log(PR) + omega2*log(ER + z3*(1 - ER) - z4*BANDt + z5*BANDb)),  # Target real wage for workers  [mode=param, initial=112852 ]
  W ~ last(W) + omega3*(omegat*last(P) - last(W)),  # Wage rate  [mode=param, initial=777968 ]
  # --- A.5: Other Predetermined Variables ---
  FDf ~ psid*last(Ff),  # Dividends of firms  [mode=param, initial=2670970 ]
  FUft ~ psiu*last(INV),  # Planned retained earnings of firms  [mode=param, initial=15066200 ]
  NPL ~ NPLk * last(Lfs),  # Non-Performing loans  [mode=param, initial=309158 ]
  phi ~ last(phi) + eps2*(last(phit) - last(phi)),  # Mark-up on unit costs  [mode=param, initial=0.26417 ]
  REP ~ deltarep*last(Lhd),  # Personal loans repayments  [mode=param, initial=2092310 ]
  Gk ~ last(Gk)*(1 + GRg),  # Real government expenditures  [mode=param, initial=2336160 ]
  Rk ~ FDf/(last(Pe) * last(Ekd)),  # Dividend yield of firms  [mode=param, initial=0.03008 ]
  Fb ~ last(Rl)*(last(Lfs) + last(Lhs) - NPL) + last(Rb)*last(Bbd) - last(Rm)*last(Ms),  # Realized banks profits  [mode=param, initial=1744130 ]
  FUb ~ Fb - lambdab*last(Y),  # Retained earnings of banks  [mode=param, initial=419039 ]
  OFb ~ last(OFb) + FUb - NPL,  # Own funds of banks  [mode=param, initial=3474030 ]
  FDb ~ Fb - FUb,  # Dividends of banks  [mode=param, initial=1325090 ]
  BUR ~ (REP + last(Rl) * last(Lhd)) / last(YDr),  # Burden of personal debt  [mode=param, initial=0.06324 ]
  # --- B.1: Pricing (from predetermined wages & mark-up) ---
  NUC ~ W/PR,  # Normal unit cost  [mode=param, initial=5.6106 ]
  NHUC ~ (1 - sigman)*NUC + sigman*(1 + last(Rln))*last(NUC),  # Normal historic unit cost  [mode=param, initial=5.6735 ]
  P ~ (1 + phi)*NHUC,  # Price level  [mode=param, initial=7.1723 ]
  PI ~ (P - last(P))/last(P),  # Price inflation  [mode=param, initial=0.0026 ]
  # --- B.2: Investment Decision ---
  RRl ~ ((1 + Rl)/(1 + PI)) - 1,  # Real interest rate on loans  [mode=param, initial=0.06246 ]
  GRk ~ gamma0 + gammau*last(U) - gammar*RRl,  # growth_mod of real capital stock  [mode=param, initial=0.03001 ]
  Kk ~ last(Kk)*(1 + GRk),  # Real capital stock  [mode=param, initial=17774838 ]
  Ik ~ (Kk - last(Kk)) + delta * last(Kk),  # Gross investment in real terms  [mode=param, initial=2357910 ]
  # --- B.3: Production (expected sales)
  Ske ~ beta*Sk + (1-beta)*last(Sk)*(1 + (GRpr + RA)),  # Expected real sales  [mode=voltage, initial=12028300 ]
  INkt ~ sigmat*Ske,  # Target level of real inventories  [mode=voltage, initial=2064890 ]
  INke ~ last(INk) + gamma*(INkt - last(INk)),  # Expected real inventories  [mode=voltage, initial=2405660 ]
  Yk ~ Ske + INke - last(INk),  # Real output  [mode=voltage, initial=12088400 ]
  U ~ Yk/last(Kk),  # Capital utilization proxy  [mode=param, initial=0.70073 ]
  Sk ~ Ck + Gk + Ik,  # Real sales  [mode=voltage, initial=12028300 ]
  INk ~ last(INk) + Yk - Sk - NPL/UC,  # Real inventories  [mode=param, initial=2064890 ]
  # --- B.4: Employment & Unit Costs ---
  Nt ~ Yk/PR,  # Desired employment level  [mode=voltage, initial=87.181 ]
  N ~ last(N) + etan*(Nt - last(N)),  # Employment level  [mode=voltage, initial=87.181 ]
  WB ~ N*W,  # The wage bill  [mode=voltage, initial=67824000 ]
  UC ~ WB/Yk,  # Unit costs  [mode=param, initial=5.6106 ]
  # --- B.5: Mark-up Target & Firm Planned Profits ---
  sigmase ~ last(INk)/Ske,  # Opening inventories to expected sales ratio  [mode=param, initial=0.16667 ]
  HCe ~ (1 - sigmase)*Ske*UC + (1 + last(Rl))*sigmase*Ske*last(UC),  # Expected historical costs  [mode=param ]
  Fft ~ FUft + FDf + last(Rl)*(last(Lfd) - last(IN)),  # Planned entrepreneurial profits  [mode=param, initial=18013600 ]
  phit ~ (FUft + FDf + last(Rl)*(last(Lfd) - last(IN))) / ((1 - sigmase)*Ske*UC + (1 + last(Rl))*sigmase*Ske*last(UC)),  # Ideal mark-up on unit costs  [mode=param, initial=0.26417 ]
  # --- B.6: Nominal Aggregates ---
  S ~ Sk*P,  # Sales at current prices  [mode=param, initial=86270300 ]
  IN ~ INk*UC,  # Stock of inventories at current costs  [mode=param, initial=11585400 ]
  INV ~ Ik*P,  # Gross investment  [mode=param, initial=16911600 ]
  K ~ Kk*P,  # Capital stock  [mode=param, initial=127486471 ]
  Y ~ Sk*P + (INk - last(INk))*UC,  # Output at current prices (nominal GDP)  [mode=param, initial=86607700 ]
  G ~ Gk*P,  # Government expenditures  [mode=param, initial=16755600 ]
  CONS ~ Ck*P,  # Consumption at current prices  [mode=param, initial=52603100 ]
  # --- B.7: Firm Financing ---
  Ff ~ S - WB + (IN - last(IN)) - last(Rl)*last(IN),  # Realized entrepreneurial profits  [mode=param, initial=18081100 ]
  FUf ~ Ff - FDf - last(Rl)*(last(Lfd) - last(IN)) + last(Rl)*NPL,  # Retained earnings of firms  [mode=param, initial=15153800 ]
  Eks ~ last(Eks) + ((1 - psiu)*last(INV))/Pe,  # Number of equities supplied by firms  [mode=voltage, initial=5112.6001 ]
  Lfd ~ last(Lfd) + INV + (IN - last(IN)) - FUf - (Eks - last(Eks))*Pe - NPL,  # Demand for loans by firms  [mode=param, initial=15962900 ]
  # --- B.8: Household Income
  YP ~ WB + FDf + FDb + last(Rm)*last(Mh) + last(Rb)*last(Bhd) + last(BLs),  # Personal income  [mode=voltage, initial=73158700 ]
  TX ~ \theta*YP,  # 11.46 : Income taxes  [mode=voltage, initial=17024100 ]
  YDr ~ YP - TX - last(Rl)*last(Lhd),  # Regular disposable income  [mode=voltage, initial=56446400 ]
  YDkr ~ YDr/P - ((P - last(P)) * last(Vk))/P,  # Regular real disposable income  [mode=voltage, initial=7813270 ]
  YDkre ~ eps*YDkr + (1 - eps)*(last(YDkr)*(1 + GRpr)),  # Expected regular real disposable income  [mode=voltage, initial=7813290 ]
  eta ~ eta0 - etar*RRl,  # Ratio of new loans to personal income  [mode=param, initial=0.04918 ]
  GL ~ eta*YDr,  # Gross amount of new personal loans  [mode=voltage, initial=2775900 ]
  NL ~ GL - REP,  # Net flow of new loans to the household sector  [mode=voltage, initial=683593 ]
  Lhd ~ last(Lhd) + GL - REP,  # Demand for loans by households  [mode=param, initial=21606600 ]
  NLk ~ NL/P,  # Real flow of new loans to the household sector  [mode=voltage, initial=95311 ]
  Ck ~ \alpha_1*(YDkre + NLk) + \alpha_2*last(Vk),  # Real consumption  [mode=voltage, initial=7334240 ]
  # --- B.9: Household Wealth & Portfolio Allocation ---
  CG ~ (Pbl - last(Pbl))*last(BLd) + (Pe - last(Pe))*last(Ekd) + (OFb - last(OFb)),  # Capital gains on government bonds  [mode=param ]
  V ~ last(V) + YDr - CONS + (Pbl - last(Pbl))*last(BLd) + (Pe - last(Pe))*last(Ekd) + (OFb - last(OFb)),  # Wealth of households  [mode=voltage, initial=165438779 ]
  Vk ~ V/P,  # Real wealth of households  [mode=param, initial=23066350 ]
  YDhs ~ YDr + CG,  # Haig-Simons measure of disposable income  [mode=param ]
  Hhd ~ lambdac*CONS,  # Households demand for cash  [mode=param, initial=2630150 ]
  Vfma ~ V - Hhd - OFb,  # Investible wealth of households  [mode=param, initial=159334599 ]
  Ekd ~ Eks,  # Number of equities demanded  [mode=voltage, initial=5112.6001 ]
  Bhd ~ last(Vfma)*(lambda20 + lambda22*last(Rb) - lambda21*last(Rm) - lambda24*last(Rk) - lambda23*last(Rbl) - lambda25*(YDr/V)),  # Demand for government bills from households  [mode=param, initial=33439320 ]
  BLd ~ last(Vfma)*(lambda30 - lambda32*last(Rb) - lambda31*last(Rm) - lambda34*last(Rk) + lambda33*last(Rbl) - lambda35*(YDr/V))/Pbl,  # Demand for government bonds  [mode=param, initial=840742 ]
  Pe ~ last(Vfma)*(lambda40 - lambda42*last(Rb) - lambda41*last(Rm) + lambda44*last(Rk) - lambda43*last(Rbl) - lambda45*(YDr/V))/Ekd,  # Price of equities  [mode=voltage, initial=17937 ]
  Mh ~ Vfma - Bhd - Pe*Ekd - Pbl*BLd + Lhd,  # 11.67 : Money deposits - as a residual  [mode=param, initial=40510800 ]
  # --- C.1: Government Accounts ---
  PSBR ~ G + last(BLs) + last(Rb)*(last(Bbs) + last(Bhs)) - TX,  # Government deficit  [mode=param, initial=1894780 ]
  Bs ~ last(Bs) + G - TX - (BLs - last(BLs))*Pbl + last(Rb)*(last(Bhs) + last(Bbs)) + last(BLs),  # Supply of government bills  [mode=param, initial=42484800 ]
  # --- C.2: Central Bank & Supply-on-Demand Identities ---
  BLs ~ BLd,  # Supply of government bonds  [mode=param, initial=840742 ]
  Bhs ~ Bhd,  # Government bills supplied to households  [mode=param, initial=33439320 ]
  Hhs ~ Hhd,  # Cash supplied to households  [mode=param, initial=2630150 ]
  Ms ~ Mh,  # Deposits supplied by banks  [mode=param, initial=40510800 ]
  Lfs ~ Lfd,  # Supply of loans to firms  [mode=param, initial=15962900 ]
  Lhs ~ Lhd,  # Loans supplied to households  [mode=param, initial=21606600 ]
  Hbd ~ ro*Ms,  # Cash required by banks  [mode=param, initial=2025540 ]
  Hbs ~ Hbd,  # Cash supplied to banks  [mode=param, initial=2025540 ]
  Hs ~ Hbs + Hhs,  # Total supply of cash  [mode=param, initial=4655690 ]
  Bcbd ~ Hs,  # Government bills demanded by Central bank  [mode=param, initial=4655690 ]
  Bcbs ~ Bcbd,  # Government bills supplied by Central bank  [mode=param, initial=4655690 ]
  # --- C.3: Commercial Bank Balance Sheet (hidden equation: Bbs ≈ Bbd) ---
  Bbs ~ last(Bbs) + (Bs - last(Bs)) - (Bhs - last(Bhs)) - (Bcbs - last(Bcbs)),  # Government bills supplied to commercial banks  [mode=param, initial=4389790 ]
  Bbd ~ Ms + OFb - Lfs - Lhs - Hbd,  # Government bills demanded by commercial banks  [mode=param, initial=4389790 ]
  BLR ~ Bbd/Ms,  # Gross bank liquidity ratio  [mode=param, initial=0.1091 ]
  CAR ~ OFb/(Lfs + Lhs),  # Capital adequacy ratio of banks  [mode=param, initial=0.09245 ]
  # --- C.4: Memo & Diagnostic Items ---
  GD ~ Bbs + Bhs + BLs*Pbl + Hs,  # Government debt  [mode=param, initial=57728700 ]
  PE ~ Pe/(Ff/last(Eks)),  # Price earnings ratio  [mode=param, initial=5.07185 ]
  Q ~ (Eks*Pe + Lfd)/(K + IN),  # Tobins Q  [mode=param, initial=0.77443 ]
  VfmaA ~ Mh + Bhd + Pbl * BLd + Pe * Ekd,  # [mode=param ]
  Vf ~ IN + K - Lfd - Ekd * Pe,  # Firm's wealth (memo for matrices)  [mode=param, initial=31361792 ]
  Ls ~ Lfs + Lhs  # Loans supply (memo for matrices)  [mode=param, initial=37569500 ]
)
```

```{r}
growth_parameters <- sfcr_set(
  # [ x=-80 y=1496 uid=oMialb invisible=false ]
  \alpha_1 ~ 0.75,  # Propensity to consume out of income  [mode=param ]
  \alpha_2 ~ 0.064,  # Propensity to consume out of wealth  [mode=param ]
  beta ~ 0.5,  # Parameter in expectation formations on real sales  [mode=param ]
  betab ~ 0.4,  # Spped of adjustment of banks own funds  [mode=param ]
  gamma ~ 0.15,  # Speed of adjustment of inventories to the target level  [mode=param ]
  gamma0 ~ 0.00122,  # Exogenous growth_mod in the real stock of capital  [mode=param ]
  gammar ~ 0.1,  # Relation between the real interest rate and growth_mod in the stock of capital  [mode=param ]
  gammau ~ 0.05,  # Relation between the utilization rate and growth_mod in the stock of capital  [mode=param ]
  delta ~ 0.10667,  # Rate of depreciation of fixed capital  [mode=param ]
  deltarep ~ 0.1,  # Ratio of personal loans repayments to stock of loans  [mode=param ]
  eps ~ 0.5,  # Parameter in expectation formations on real disposable income  [mode=param ]
  eps2 ~ 0.8,  # Speed of adjustment of mark-up  [mode=param ]
  epsb ~ 0.25,  # Speed of adjustment in expected proportion of non-performing loans  [mode=param ]
  epsrb ~ 0.9,  # Speed of adjustment in the real interest rate on bills  [mode=param ]
  eta0 ~ 0.07416,  # Ratio of new loans to personal income - exogenous component  [mode=param ]
  etan ~ 0.6,  # Speed of adjustment of actual employment to desired employment  [mode=param ]
  etar ~ 0.4,  # Relation between the ratio of new loans to personal income and the interest rate on loans  [mode=param ]
  \theta ~ 0.22844,  # Income tax rate  [mode=param ]
  # lambda10 ~ -0.17071,
  # lambda11 ~ 0,
  # lambda12 ~ 0,
  # lambda13 ~ 0,
  # lambda14 ~ 0,
  # lambda15 ~ 0.18,
  lambda20 ~ 0.25,  # Parameter in households demand for bills  [mode=param ]
  lambda21 ~ 2.2,  # Parameter in households demand for bills  [mode=param ]
  lambda22 ~ 6.6,  # Parameter in households demand for bills  [mode=param ]
  lambda23 ~ 2.2,  # Parameter in households demand for bills  [mode=param ]
  lambda24 ~ 2.2,  # Parameter in households demand for bills  [mode=param ]
  lambda25 ~ 0.1,  # Parameter in households demand for bills  [mode=param ]
  lambda30 ~ -0.04341,  # Parameter in households demand for bonds  [mode=param ]
  lambda31 ~ 2.2,  # Parameter in households demand for bonds  [mode=param ]
  lambda32 ~ 2.2,  # Parameter in households demand for bonds  [mode=param ]
  lambda33 ~ 6.6,  # Parameter in households demand for bonds  [mode=param ]
  lambda34 ~ 2.2,  # Parameter in households demand for bonds  [mode=param ]
  lambda35 ~ 0.1,  # Parameter in households demand for bonds  [mode=param ]
  lambda40 ~ 0.67132,  # Parameter in households demand for equities  [mode=param ]
  lambda41 ~ 2.2,  # Parameter in households demand for equities  [mode=param ]
  lambda42 ~ 2.2,  # Parameter in households demand for equities  [mode=param ]
  lambda43 ~ 2.2,  # Parameter in households demand for equities  [mode=param ]
  lambda44 ~ 6.6,  # Parameter in households demand for equities  [mode=param ]
  lambda45 ~ 0.1,  # Parameter in households demand for equities  [mode=param ]
  lambdab ~ 0.0153,  # Parameter determining dividends of banks  [mode=param ]
  lambdac ~ 0.05,  # Parameter in households demand for cash  [mode=param ]
  xim1 ~ 0.0008,  # Parameter in the equation for setting interest rate on deposits  [mode=param ]
  xim2 ~ 0.0007,  # Parameter in the equation for setting interest rate on deposits  [mode=param ]
  ro ~ 0.05,  # Reserve requirement parameter  [mode=param ]
  sigman ~ 0.1666,  # Parameter of influencing normal historic unit costs  [mode=param ]
  sigmat ~ 0.2,  # Target inventories to sales ratio  [mode=param ]
  psid ~ 0.15255,  # Ratio of dividends to gross profits  [mode=param ]
  psiu ~ 0.92,  # Ratio of retained earnings to investments  [mode=param ]
  omega0 ~ -0.20594,  # Parameter influencing the target real wage for workers  [mode=param ]
  omega1 ~ 1,  # Parameter influencing the target real wage for workers  [mode=param ]
  omega2 ~ 2,  # Parameter influencing the target real wage for workers  [mode=param ]
  omega3 ~ 0.45621,  # Speed of adjustment of wages to target value  [mode=param ]
  # Exogenous
  ADDbl ~ 0.02,  # Spread between long-term interest rate and rate on bills  [mode=param, initial=0.02 ]
  BANDt ~ 0.01,  # Upper range of the flat Phillips curve  [mode=param, initial=0.01 ]
  BANDb ~ 0.01,  # Lower range of the flat Phillips curve  [mode=param, initial=0.01 ]
  bot ~ 0.05,  # Bottom value for bank net liquidity ratio  [mode=param, initial=0.05 ]
  GRg ~ 0.03,  # growth_mod of real government expenditures  [mode=param, initial=0.03 ]
  GRpr ~ 0.03,  # growth_mod rate of productivity  [mode=param, initial=0.03 ]
  Nfe ~ 87.181,  # Full employment level  [mode=param, initial=87.181 ]
  NCAR ~ 0.1,  # Normal capital adequacy ratio of banks  [mode=param, initial=0.1 ]
  NPLk ~ 0.02,  # Proportion of Non-Performing loans  [mode=param, initial=0.02 ]
  Rbbar ~ 0.035,  # Interest rate on bills, set exogenously  [mode=param, initial=0.035 ]
  Rln ~ 0.07,  # Normal interest rate on loans  [mode=param, initial=0.07 ]
  RA ~ 0,  # Random shock to expectations on real sales  [mode=param, initial=0 ]
  # sigmase ~ 0.16667,
  # eta ~ 0.04918,
  # phi ~ 0.26417,
  # phit ~ 0.26417,
  top ~ 0.12  # Top value for bank net liquidity ratio  [mode=param, initial=0.12 ]
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
  equationTableTolerance: 0.001
  lookupMode: pwl
  lookupClamp: true
  convergenceCheckThreshold: 100
  infoViewerUpdateIntervalMs: 200
  Auto-Open Model Info on Load: true
@end
```


```{r}
@scope Embedded_Scope_1 position=-1
  x1: 160
  y1: 1568
  x2: 736
  y2: 1904
  elmUid: GrNukH
  speed: 1
  flags: x2001206
  source: uid:rVIsJ- value:0
@end
```

```{r}
@zorder
  uid:oMialb z:0
  uid:THSfHC z:1
  uid:GrNukH z:2
  uid:rVIsJ- z:3
  uid:Dfrh1z z:4
@end
```

```{r}
@circuit
207 208 1520 272 1520 164 Yk U:rVIsJ- Z:3
207 448 1520 528 1520 164 K U:Dfrh1z Z:4
@end
```

