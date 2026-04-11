```{r}
growth_eqs <- sfcr_set(
  # [ x=-80 y=1240 uid=THSfHC invisible=false ]
  Yk ~ Ske + INke - last(INk),  # 11.1 : Real output  [mode=voltage, initial=12088400 ]
  Ske ~ beta*Sk + (1-beta)*last(Sk)*(1 + (GRpr + RA)),  # 11.2 : Expected real sales  [mode=voltage, initial=12028300 ]
  INke ~ last(INk) + gamma*(INkt - last(INk)),  # 11.3 : Long-run inventory target  [mode=voltage, initial=2405660 ]
  INkt ~ sigmat*Ske,  # 11.4 : Short-run inventory target  [mode=voltage, initial=2064890 ]
  INk ~ last(INk) + Yk - Sk - NPL/UC,  # 11.5 : Actual real inventories  [mode=param, initial=2064890 ]
  Kk ~ last(Kk)*(1 + GRk),  # 11.6 : Real capital stock  [mode=param, initial=17774838 ]
  GRk ~ gamma0 + gammau*last(U) - gammar*RRl,  # 11.7 : Growth of real capital stock  [mode=param, initial=0.03001 ]
  U ~ Yk/last(Kk),  # 11.8 : Capital utilization proxy  [mode=param, initial=0.70073 ]
  RRl ~ ((1 + Rl)/(1 + PI)) - 1,  # 11.9 : Real interest rate on loans  [mode=param, initial=0.06246 ]
  PI ~ (P - last(P))/last(P),  # 11.10 : Rate of price inflation  [mode=param, initial=0.0026 ]
  # Ik ~ (Kk - Kk[-1]) + delta*Kk[-1]
  Ik ~ (Kk - last(Kk)) + delta * last(Kk),  # [mode=param, initial=2357910 ]
  # Box 11.2 : Firms equations
  # ---------------------------
  Sk ~ Ck + Gk + Ik,  # 11.12 : Actual real sales  [mode=voltage, initial=12028300 ]
  S ~ Sk*P,  # 11.13 : Value of realized sales  [mode=param, initial=86270300 ]
  IN ~ INk*UC,  # 11.14 : Inventories valued at current cost  [mode=param, initial=11585400 ]
  INV ~ Ik*P,  # 11.15 : Nominal gross investment  [mode=param, initial=16911600 ]
  K ~ Kk*P,  # 11.16 : Nomincal value of fixed capital  [mode=param, initial=127486471 ]
  Y ~ Sk*P + (INk - last(INk))*UC,  # 11.17 : Nomincal GDP  [mode=param, initial=86607700 ]
  # Box 11.3 : Firms equations
  # ---------------------------
  # 11.18 : Real wage aspirations
  omegat ~ exp(omega0 + omega1*log(PR) + omega2*log(ER + z3*(1 - ER) - z4*BANDt + z5*BANDb)),  # [mode=param, initial=112852 ]
  ER ~ last(N)/last(Nfe),  # 11.19 : Employment rate  [mode=param, initial=1 ]
  # 11.20 : Switch variables
  z3a ~ (ER > (1-BANDb)) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  z3b ~ (ER <= (1+BANDt)) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  z3 ~ z3a * z3b,  # [mode=param ]
  z4 ~ (ER > (1+BANDt)) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  z5 ~ (ER < (1-BANDb)) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  W ~ last(W) + omega3*(omegat*last(P) - last(W)),  # 11.21 : Nominal wage  [mode=param, initial=777968 ]
  PR ~ last(PR)*(1 + GRpr),  # 11.22 : Labor productivity  [mode=param, initial=138659 ]
  Nt ~ Yk/PR,  # 11.23 : Desired employment  [mode=voltage, initial=87.181 ]
  N ~ last(N) + etan*(Nt - last(N)),  # 11.24 : Actual employment --> etan not in the book  [mode=voltage, initial=87.181 ]
  WB ~ N*W,  # 11.25 : Nominal wage bill  [mode=voltage, initial=67824000 ]
  UC ~ WB/Yk,  # 11.26 : Actual unit cost  [mode=param, initial=5.6106 ]
  NUC ~ W/PR,  # 11.27 : Normal unit cost  [mode=param, initial=5.6106 ]
  NHUC ~ (1 - sigman)*NUC + sigman*(1 + last(Rln))*last(NUC),  # 11.28 : Normal historic unit cost  [mode=param, initial=5.6735 ]
  # Box 11.4 : Firms equations
  # ---------------------------
  P ~ (1 + phi)*NHUC,  # 11.29 : Normal-cost pricing  [mode=param, initial=7.1723 ]
  phi ~ last(phi) + eps2*(last(phit) - last(phi)),  # 11.30 : Actual mark-up --> eps2 not in the book  [mode=param, initial=0.26417 ]
  # 11.31 : Ideal mark-up
  # phit ~ (FDf + FUft + Rl[-1]*(Lfd[-1] - IN[-1]))/((1 - sigmase)*Ske*UC + (1 + Rl[-1])*sigmase*Ske*UC[-1]),
  phit ~ (FUft + FDf + last(Rl)*(last(Lfd) - last(IN))) / ((1 - sigmase)*Ske*UC + (1 + last(Rl))*sigmase*Ske*last(UC)),  # [mode=param, initial=0.26417 ]
  HCe ~ (1 - sigmase)*Ske*UC + (1 + last(Rl))*sigmase*Ske*last(UC),  # 11.32 : Expected historical costs  [mode=param ]
  sigmase ~ last(INk)/Ske,  # 11.33 : Opening inventories to expected sales ratio  [mode=param, initial=0.16667 ]
  Fft ~ FUft + FDf + last(Rl)*(last(Lfd) - last(IN)),  # 11.34 : Planned entrepeneurial profits of firmss  [mode=param, initial=18013600 ]
  FUft ~ psiu*last(INV),  # 11.35 : Planned retained earnings of firms  [mode=param, initial=15066200 ]
  FDf ~ psid*last(Ff),  # 11.36 : Dividends of firms  [mode=param, initial=2670970 ]
  # Box 11.5 : Firms equations
  # ---------------------------
  Ff ~ S - WB + (IN - last(IN)) - last(Rl)*last(IN),  # 11.37 : Realized entrepeneurial profits  [mode=param, initial=18081100 ]
  FUf ~ Ff - FDf - last(Rl)*(last(Lfd) - last(IN)) + last(Rl)*NPL,  # 11.38 : Retained earnings of firms  [mode=param, initial=15153800 ]
  # 11.39 : Demand for loans by firms
  Lfd ~ last(Lfd) + INV + (IN - last(IN)) - FUf - (Eks - last(Eks))*Pe - NPL,  # [mode=param, initial=15962900 ]
  # NPL ~ NPLk*Lfs[-1]
  NPL ~ NPLk * last(Lfs),  # [mode=param, initial=309158 ]
  Eks ~ last(Eks) + ((1 - psiu)*last(INV))/Pe,  # 11.41 : Supply of equities issued by firms  [mode=voltage, initial=5112.6001 ]
  # Rk ~ FDf/(Pe[-1]*Ekd[-1])
  Rk ~ FDf/(last(Pe) * last(Ekd)),  # [mode=param, initial=0.03008 ]
  PE ~ Pe/(Ff/last(Eks)),  # 11.43 : Price earnings ratio  [mode=param, initial=5.07185 ]
  Q ~ (Eks*Pe + Lfd)/(K + IN),  # 11.44 : Tobins Q ratio  [mode=param, initial=0.77443 ]
  # Box 11.6 : Households equations
  # --------------------------------
  # YP ~ WB + FDf + FDb + Rm[-1]*Md[-1] + Rb[-1]*Bhd[-1] + BLs[-1]
  YP ~ WB + FDf + FDb + last(Rm)*last(Mh) + last(Rb)*last(Bhd) + last(BLs),  # [mode=voltage, initial=73158700 ]
  # YP ~ WB + FDf + FDb + Rm[-1]*Mh[-1] + Rb[-1]*Bhd[-1] + BLs[-1] + NL,
  TX ~ \theta*YP,  # 11.46 : Income taxes  [mode=voltage, initial=17024100 ]
  YDr ~ YP - TX - last(Rl)*last(Lhd),  # 11.47 : Regular disposable income  [mode=voltage, initial=56446400 ]
  YDhs ~ YDr + CG,  # 11.48 : Haig-Simons disposable income  [mode=param ]
  # !1.49 : Capital gains
  CG ~ (Pbl - last(Pbl))*last(BLd) + (Pe - last(Pe))*last(Ekd) + (OFb - last(OFb)),  # [mode=param ]
  # 11.50 : Wealth
  V ~ last(V) + YDr - CONS + (Pbl - last(Pbl))*last(BLd) + (Pe - last(Pe))*last(Ekd) + (OFb - last(OFb)),  # [mode=voltage, initial=165438779 ]
  Vk ~ V/P,  # 11.51 : Real staock of wealth  [mode=param, initial=23066350 ]
  CONS ~ Ck*P,  # 11.52 : Consumption  [mode=param, initial=52603100 ]
  Ck ~ \alpha_1*(YDkre + NLk) + \alpha_2*last(Vk),  # 11.53 : Real consumption  [mode=voltage, initial=7334240 ]
  YDkre ~ eps*YDkr + (1 - eps)*(last(YDkr)*(1 + GRpr)),  # 11.54 : Expected real regular disposable income  [mode=voltage, initial=7813290 ]
  # YDkr ~ YDr/P - (P - P[-1])*Vk[-1]/P
  YDkr ~ YDr/P - ((P - last(P)) * last(Vk))/P,  # [mode=voltage, initial=7813270 ]
  # Box 11.7 : Households equations
  # --------------------------------
  GL ~ eta*YDr,  # 11.56 : Gross amount of new personal loans ---> new eta here  [mode=voltage, initial=2775900 ]
  eta ~ eta0 - etar*RRl,  # 11.57 : New loans to personal income ratio  [mode=param, initial=0.04918 ]
  NL ~ GL - REP,  # 11.58 : Net amount of new personal loans  [mode=voltage, initial=683593 ]
  REP ~ deltarep*last(Lhd),  # 11.59 : Personal loans repayments  [mode=param, initial=2092310 ]
  Lhd ~ last(Lhd) + GL - REP,  # 11.60 : Demand for personal loans  [mode=param, initial=21606600 ]
  NLk ~ NL/P,  # 11.61 : Real amount of new personal loans  [mode=voltage, initial=95311 ]
  # BUR ~ (REP + Rl[-1]*Lhd[-1])/YDr[-1]
  BUR ~ (REP + last(Rl) * last(Lhd)) / last(YDr),  # [mode=param, initial=0.06324 ]
  # Box 11.8 : Households equations - portfolio decisions
  # -----------------------------------------------------
  # 11.64 : Demand for bills
  # YDr/V
  # Md ~ Vfma[-1] * (lambda10 + lambda11*Rm[-1] - lambda12 * Rb[-1] - lambda13 * Rbl[-1] - lambda14 * Rk[-1] + lambda25 * (YP/V)),
  Bhd ~ last(Vfma)*(lambda20 + lambda22*last(Rb) - lambda21*last(Rm) - lambda24*last(Rk) - lambda23*last(Rbl) - lambda25*(YDr/V)),  # [mode=param, initial=33439320 ]
  # 11.65 : Demand for bonds
  BLd ~ last(Vfma)*(lambda30 - lambda32*last(Rb) - lambda31*last(Rm) - lambda34*last(Rk) + lambda33*last(Rbl) - lambda35*(YDr/V))/Pbl,  # [mode=param, initial=840742 ]
  # 11.66 : Demand for equities - normalized to get the price of equitities
  Pe ~ last(Vfma)*(lambda40 - lambda42*last(Rb) - lambda41*last(Rm) + lambda44*last(Rk) - lambda43*last(Rbl) - lambda45*(YDr/V))/Ekd,  # [mode=voltage, initial=17937 ]
  Mh ~ Vfma - Bhd - Pe*Ekd - Pbl*BLd + Lhd,  # 11.67 : Money deposits - as a residual  [mode=param, initial=40510800 ]
  Vfma ~ V - Hhd - OFb,  # 11.68 : Investible wealth  [mode=param, initial=159334599 ]
  VfmaA ~ Mh + Bhd + Pbl * BLd + Pe * Ekd,  # [mode=param ]
  Hhd ~ lambdac*CONS,  # 11.69 : Households demand for cash  [mode=param, initial=2630150 ]
  Ekd ~ Eks,  # 11.70 : Stock market equilibrium  [mode=voltage, initial=5112.6001 ]
  # Box 11.9 : Governments equations
  # ---------------------------------
  G ~ Gk*P,  # 11.71 : Pure government expenditures  [mode=param, initial=16755600 ]
  Gk ~ last(Gk)*(1 + GRg),  # 11.72 : Real government expenditures  [mode=param, initial=2336160 ]
  PSBR ~ G + last(BLs) + last(Rb)*(last(Bbs) + last(Bhs)) - TX,  # 11.73 : Government deficit --> BLs[-1] missing in the book  [mode=param, initial=1894780 ]
  # 11.74 : New issues of bills
  Bs ~ last(Bs) + G - TX - (BLs - last(BLs))*Pbl + last(Rb)*(last(Bhs) + last(Bbs)) + last(BLs),  # [mode=param, initial=42484800 ]
  GD ~ Bbs + Bhs + BLs*Pbl + Hs,  # 11.75 : Government debt  [mode=param, initial=57728700 ]
  # Box 11.10 : The Central banks equations
  # ----------------------------------------
  Fcb ~ last(Rb)*last(Bcbd),  # 11.76 : Central bank profits  [mode=param ]
  BLs ~ BLd,  # 11.77 : Bonds are supplied on demand  [mode=param, initial=840742 ]
  Bhs ~ Bhd,  # 11.78 : Household bills supplied on demand  [mode=param, initial=33439320 ]
  Hhs ~ Hhd,  # 11.79 : Cash supplied on demand --> Mistake on the book  [mode=param, initial=2630150 ]
  Hbs ~ Hbd,  # 11.80 : Reserves supplied on demand  [mode=param, initial=2025540 ]
  Hs ~ Hbs + Hhs,  # 11.81 : Total supply of cash  [mode=param, initial=4655690 ]
  Bcbd ~ Hs,  # 11.82 : Central bankd  [mode=param, initial=4655690 ]
  Bcbs ~ Bcbd,  # 11.83 : Supply of bills to Central bank  [mode=param, initial=4655690 ]
  Rb ~ Rbbar,  # 11.84 : Interest rate on bills set exogenously  [mode=param, initial=0.035 ]
  Rbl ~ Rb + ADDbl,  # 11.85 : Long term interest rate  [mode=param, initial=0.055 ]
  Pbl ~ 1/Rbl,  # 11.86 : Price of long-term bonds  [mode=param, initial=18.182 ]
  # Box 11.11 : Commercial Banks equations
  # ---------------------------------------
  Ms ~ Mh,  # 11.87 : Bank deposits supplied on demand  [mode=param, initial=40510800 ]
  Lfs ~ Lfd,  # 11.88 : Loans to firms supplied on demand  [mode=param, initial=15962900 ]
  Lhs ~ Lhd,  # 11.89 : Personal loans supplied on demand  [mode=param, initial=21606600 ]
  Hbd ~ ro*Ms,  # 11.90 Reserve requirements of banks  [mode=param, initial=2025540 ]
  # 11.91 : Bills supplied to banks
  Bbs ~ last(Bbs) + (Bs - last(Bs)) - (Bhs - last(Bhs)) - (Bcbs - last(Bcbs)),  # [mode=param, initial=4389790 ]
  # 11.92 : Balance sheet constraint of banks
  Bbd ~ Ms + OFb - Lfs - Lhs - Hbd,  # [mode=param, initial=4389790 ]
  BLR ~ Bbd/Ms,  # 11.93 : Bank liquidity ratio  [mode=param, initial=0.1091 ]
  # 11.94 : Deposit interest rate
  Rm ~ last(Rm) + z1a*xim1 + z1b*xim2 - z2a*xim1 - z2b*xim2,  # [mode=param, initial=0.0193 ]
  # 11.95-97 : Mechanism for determining changes to the interest rate on deposits
  z2a ~ (last(BLR) > (top + 0.05)) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  z2b ~ (last(BLR) > top) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  z1a ~ (last(BLR) <= bot) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  z1b ~ (last(BLR) <= (bot - 0.05)) ? 1 : 0,  # Switch  [mode=param, initial=0 ]
  # Box 11.12 : Commercial banks equations
  # ---------------------------------------
  Rl ~ Rm + ADDl,  # 11.98 : Loan interest rate  [mode=param, initial=0.06522 ]
  OFbt ~ NCAR*(last(Lfs) + last(Lhs)),  # 11.99 : Long-run own funds target  [mode=param, initial=3638100 ]
  OFbe ~ last(OFb) + betab*(OFbt - last(OFb)),  # 11.100 : Short-run own funds target  [mode=param, initial=3474030 ]
  FUbt ~ OFbe - last(OFb) + NPLke*last(Lfs),  # 11.101 : Target retained earnings of banks  [mode=param ]
  NPLke ~ epsb*last(NPLke) + (1 - epsb)*last(NPLk),  # 11.102 : Expected proportion of non-performaing loans  [mode=param, initial=0.02 ]
  # FDb ~ Fb - FUb
  FDb ~ Fb - FUb,  # [mode=param, initial=1325090 ]
  Fbt ~ lambdab*last(Y) + (OFbe - last(OFb) + NPLke*last(Lfs)),  # 11.104 : Target profits of banks  [mode=param, initial=1744140 ]
  # 11.105 : Actual profits of banks
  Fb ~ last(Rl)*(last(Lfs) + last(Lhs) - NPL) + last(Rb)*last(Bbd) - last(Rm)*last(Ms),  # [mode=param, initial=1744130 ]
  # 11.106 : Lending mark-up over deposit rate
  ADDl ~ (Fbt - last(Rb)*last(Bbd) + last(Rm)*(last(Ms) - (1 - NPLke)*last(Lfs) - last(Lhs)))/((1 - NPLke)*last(Lfs) + last(Lhs)),  # --> I added the lag term to Rm  [mode=param, initial=0.04592 ]
  FUb ~ Fb - lambdab*last(Y),  # 11.107 : Actual retained earnings  [mode=param, initial=419039 ]
  OFb ~ last(OFb) + FUb - NPL,  # 11.108 : Own funds of banks  [mode=param, initial=3474030 ]
  CAR ~ OFb/(Lfs + Lhs),  # [mode=param, initial=0.09245 ]
  Vf ~ IN + K - Lfd - Ekd * Pe,  # Firm's wealth (memo for matrices)  [mode=param, initial=31361792 ]
  # Vg ~ -Bs - BLs * Pbl
  Ls ~ Lfs + Lhs  # Loans supply (memo for matrices)  [mode=param, initial=37569500 ]
)
```

```{r}
growth_parameters <- sfcr_set(
  # [ x=-80 y=1496 uid=oMialb invisible=false ]
  \alpha_1 ~ 0.75,  # [mode=param ]
  \alpha_2 ~ 0.064,  # [mode=param ]
  beta ~ 0.5,  # [mode=param ]
  betab ~ 0.4,  # [mode=param ]
  gamma ~ 0.15,  # [mode=param ]
  gamma0 ~ 0.00122,  # [mode=param ]
  gammar ~ 0.1,  # [mode=param ]
  gammau ~ 0.05,  # [mode=param ]
  delta ~ 0.10667,  # [mode=param ]
  deltarep ~ 0.1,  # [mode=param ]
  eps ~ 0.5,  # [mode=param ]
  eps2 ~ 0.8,  # [mode=param ]
  epsb ~ 0.25,  # [mode=param ]
  epsrb ~ 0.9,  # [mode=param ]
  eta0 ~ 0.07416,  # [mode=param ]
  etan ~ 0.6,  # [mode=param ]
  etar ~ 0.4,  # [mode=param ]
  \theta ~ 0.22844,  # [mode=param ]
  # lambda10 ~ -0.17071,
  # lambda11 ~ 0,
  # lambda12 ~ 0,
  # lambda13 ~ 0,
  # lambda14 ~ 0,
  # lambda15 ~ 0.18,
  lambda20 ~ 0.25,  # [mode=param ]
  lambda21 ~ 2.2,  # [mode=param ]
  lambda22 ~ 6.6,  # [mode=param ]
  lambda23 ~ 2.2,  # [mode=param ]
  lambda24 ~ 2.2,  # [mode=param ]
  lambda25 ~ 0.1,  # [mode=param ]
  lambda30 ~ -0.04341,  # [mode=param ]
  lambda31 ~ 2.2,  # [mode=param ]
  lambda32 ~ 2.2,  # [mode=param ]
  lambda33 ~ 6.6,  # [mode=param ]
  lambda34 ~ 2.2,  # [mode=param ]
  lambda35 ~ 0.1,  # [mode=param ]
  lambda40 ~ 0.67132,  # [mode=param ]
  lambda41 ~ 2.2,  # [mode=param ]
  lambda42 ~ 2.2,  # [mode=param ]
  lambda43 ~ 2.2,  # [mode=param ]
  lambda44 ~ 6.6,  # [mode=param ]
  lambda45 ~ 0.1,  # [mode=param ]
  lambdab ~ 0.0153,  # [mode=param ]
  lambdac ~ 0.05,  # [mode=param ]
  xim1 ~ 0.0008,  # [mode=param ]
  xim2 ~ 0.0007,  # [mode=param ]
  ro ~ 0.05,  # [mode=param ]
  sigman ~ 0.1666,  # [mode=param ]
  sigmat ~ 0.2,  # [mode=param ]
  psid ~ 0.15255,  # [mode=param ]
  psiu ~ 0.92,  # [mode=param ]
  omega0 ~ -0.20594,  # [mode=param ]
  omega1 ~ 1,  # [mode=param ]
  omega2 ~ 2,  # [mode=param ]
  omega3 ~ 0.45621,  # [mode=param ]
  # Exogenous
  ADDbl ~ 0.02,  # [mode=param, initial=0.02 ]
  BANDt ~ 0.01,  # [mode=param, initial=0.01 ]
  BANDb ~ 0.01,  # [mode=param, initial=0.01 ]
  bot ~ 0.05,  # [mode=param, initial=0.05 ]
  GRg ~ 0.03,  # [mode=param, initial=0.03 ]
  GRpr ~ 0.03,  # [mode=param, initial=0.03 ]
  Nfe ~ 87.181,  # [mode=param, initial=87.181 ]
  NCAR ~ 0.1,  # [mode=param, initial=0.1 ]
  NPLk ~ 0.02,  # [mode=param, initial=0.02 ]
  Rbbar ~ 0.035,  # [mode=param, initial=0.035 ]
  Rln ~ 0.07,  # [mode=param, initial=0.07 ]
  RA ~ 0,  # [mode=param, initial=0 ]
  # sigmase ~ 0.16667,
  # eta ~ 0.04918,
  # phi ~ 0.26417,
  # phit ~ 0.26417,
  top ~ 0.12  # [mode=param, initial=0.12 ]
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

