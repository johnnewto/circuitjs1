# CircuitJS1 SFCR Export
# Generated from circuit simulation

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
  equationTableTolerance: 0.000001
  lookupMode: pwl
  lookupClamp: true
  convergenceCheckThreshold: 100
  infoViewerUpdateIntervalMs: 200
  Auto-Open Model Info on Load: true
@end
```

The **SFC model BMW** refers to one of the foundational models in the book *Monetary Economics: An Integrated Approach to Credit, Money, Income, Production and Wealth* (2007) by **Wynne Godley** and **Marc Lavoie**. This is a key text in **Post-Keynesian economics** that develops **Stock-Flow Consistent (SFC)** macroeconomic modeling.

**SFC models** ensure every financial flow (e.g., income, spending, borrowing) corresponds to changes in stocks (e.g., wealth, debt, capital), with all sectoral balance sheets and transactions matrices adding up correctly — nothing is created or destroyed without accounting for it. This enforces strict accounting coherence between real (production, investment) and financial (money, credit, debt) sides of the economy.

**BMW** stands for **"Bank-Money World"** (or sometimes interpreted as a "bank-money" economy). It is presented in Chapter 7 of the book and represents one of the simplest yet insightful SFC models that explicitly includes **commercial banks** and **endogenous credit money**.

### Core structure and sectors in Model BMW
It is a **closed economy** (no foreign sector) with **no government** and **no central bank / base money / cash**. Money exists purely as bank deposits created through lending.

Three main sectors / agents:

- **Households**
- **Firms** (non-financial corporations)
- **Banks** (commercial banks)

### Balance sheet matrix (simplified, from Godley & Lavoie 2007, around p. 219)

| Item              | Households | Firms          | Banks     | Total |
|-------------------|------------|----------------|-----------|-------|
| **Assets**        |            |                |           |       |
| Deposits (M)      | +M         |                | -M        | 0     |
| Loans (L)         |            | -L             | +L        | 0     |
| Fixed capital (K) |            | +K             |           | +K    |
| **Liabilities & Equity** |       |                |           |       |
| Loans             |            | +L (liability) |           |       |
| Deposits          |            |                | +M (liability) |       |
| Equity / Net worth| Vₕ         | V_f (often implicit or zero profits) |       |       |

- Households hold all the **money** (bank deposits M = household wealth).
- Firms hold physical **capital** (K) but finance part of investment via **bank loans** (L).
- Banks intermediate: loans to firms = deposits from households.
- No cash, no government bonds, no bills — pure "bank money" world.

Key: **Money is endogenous** — created when banks grant loans to firms, destroyed when loans are repaid.

### Main assumptions and behavioural features
- Fixed prices (no inflation dynamics in basic version).
- Zero net profits for firms in steady state (or very low).
- Firms aim for a **target capital-to-output ratio** (normal utilization).
- Investment is partly financed by **internal funds** (amortization / depreciation funds) and partly by **new bank loans**.
- Households consume out of disposable income (wages + interest income minus taxes if any, but often simplified).
- No government sector → no taxes, no public spending, no public debt (this is deliberately simple to focus on private credit dynamics).

### How the model works (key mechanisms)
1. **Production and income**  
   Output (Y) = consumption (C) + investment (I).  
   Wages are the main income source for households.

2. **Investment and financing**  
   Gross investment I = replacement investment (depreciation DA) + net investment.  
   Firms target K / Y ≈ constant → desired capital stock depends on expected/output.  
   If internal funds (retained earnings + depreciation) < required investment → firms borrow ΔL from banks.

3. **Money creation**  
   Banks create new deposits (ΔM) when they make new loans (ΔL).  
   Households receive these deposits as income or wealth.

4. **Consumption**  
   Households consume a fraction of income + wealth effects (they hold wealth only as deposits).

5. **Dynamics**  
   The model shows how private debt (loans L) builds up when investment > saving from internal sources.  
   In steady state: growth requires consistent saving/investment rates, but without government the model often needs parameter tuning for stable growth.  
   It illustrates endogenous money, credit-driven investment, and potential instability from debt accumulation.

### Why BMW is important
- It bridges **real** (production, capital accumulation) and **financial** (credit, money creation) spheres in a coherent way.
- Demonstrates **endogenous money** (loans create deposits, not vice versa).
- Shows limits of pure private economies: without government deficits, achieving stable full-employment growth is difficult (related to Godley's later work on unsustainable processes).
- Serves as a stepping stone to more complex models (e.g., BMWK adds capital, others add government, open economy, inflation).

```{r}
@action Action_Schedule x=496 y=-144
  pauseTime: 0
  enabled: true
  element: 496 -144 512 -128 0

| time | target | value | text | enabled | stop |
|------|--------|-------|------|---------|------|
| 30 | \alpha0 | =10 | propensity to spend | true | false |
| 40 | \alpha0 | +10 | After | true | false |
@end
```

```{r}
BMW <- sfcr_set(
  # [ x=368 y=-152 invisible=false ]
  # Parameters
  e1 = rl ~ 0.025,  # Interest rate on loans  [mode=param ]
  e2 = Y3 ~ Cs + Cd,  # [mode=param ]
  e3 = \alpha0 ~ 20,  # Autonomous consumption  [mode=param ]
  e4 = \alpha1 ~ 0.75,  # Propensity to consume from income  [mode=param ]
  e5 = \alpha2 ~ 0.10,  # Propensity to consume from wealth  [mode=param ]
  e6 = \delta ~ 0.1,  # Depreciation rate  [mode=param ]
  e7 = \gamma ~ 0.15,  # Adjustment speed of capital to target  [mode=param ]
  e8 = \kappa ~ 1,  # Capital-output ratio target  [mode=param ]
  e9 = pr ~ 1,  # Labor productivity  [mode=param ]
  # Basic behavioral equations
  e10 = Cs ~ Cd,  # Consumption supply  [mode=voltage ]
  e11 = Y13 ~ last(rm)*last(Mh),  # hint13  [mode=voltage ]
  e12 = Y14 ~ -last(rm)*last(Ms),  # hint14  [mode=voltage ]
  e13 = Y15 ~ Ld,  # hint15  [mode=voltage ]
  e14 = Y16 ~ last(Ld),  # hint16  [mode=voltage ]
  e15 = Is ~ Id,  # Investment supply  [mode=param ]
  e16 = Ns ~ Nd,  # Labour supply  [mode=voltage ]
  e17 = Ls ~ Ls - Ls[-1] = Ld - Ld[-1],  # - Ls[-1] Loan supply  [mode=param ]
  # Transactions of the firms
  e18 = Y ~ Y = Cs + Is,  # National income (output)  [mode=voltage ]
  e19 = WBd ~ Y - last(rl) * last(Ld) - AF,  # Wage bill demanded  [mode=voltage ]
  e20 = AF ~ \delta * last(K),  # Amortisation funds  [mode=param ]
  e21 = Ld ~ last(Ld) + Id - AF,  # Loan demand  [mode=param ]
  # Transactions of households
  e22 = YD ~ WBs + last(rm) * last(Mh),  # Household disposable income  [mode=voltage ]
  e23 = Mh ~ last(Mh) + YD - Cd,  # Household money holdings (deposits)  [mode=param ]
  # Transactions of the banks
  e24 = Ms ~ last(Ms) + Ls - last(Ls),  # Money supply  [mode=param ]
  e25 = rm ~ rl,  # Deposit interest rate  [mode=param ]
  # The wage bill
  e26 = WBs ~ W * Ns,  # Wage bill supplied  [mode=voltage ]
  e27 = Nd ~ Y / pr,  # Labour demand  [mode=voltage ]
  e28 = W ~ WBd / max(Nd, 0.01),  # Wage rate  [mode=voltage ]
  # Household behavior
  e29 = Cd ~ \alpha0 + \alpha1 * YD + \alpha2 * last(Mh),  # Consumption demand  [mode=voltage ]
  # The investment behavior
  e30 = K ~ last(K) + Id - DA,  # Capital stock  [mode=param ]
  e31 = DA ~ \delta * last(K),  # Depreciation (actual)  [mode=param ]
  e32 = KT ~ \kappa * last(Y),  # Target capital stock  [mode=param ]
  e33 = Id ~ \gamma * (KT - last(K)) + DA  # Investment demand  [mode=param ]
)
```

```{r}
Transaction_Flow_Matrix <- sfcr_matrix(
  # [ x=-496 y=-136 type: transaction_flow invisible=false ]
  columns = c("Households_C", "Households_C", "Firms_{Current}", "Firms_{Capital}", "Banks_{Current}", "Banks_{Capital}"),
  codes = c("H1", "H2", "F3", "F4", "B5", "B6"),
  type = c("", "", "", "", "", ""),
  c("Change in Loans", H1 = "", H2 = "", F3 = "", F4 = "Ld-last(Ld)", B5 = "", B6 = "-(Ls-last(Ls))"),
  c("Investment", H1 = "", H2 = "", F3 = "Is", F4 = "-Id", B5 = "", B6 = ""),
  c("Wages", H1 = "WBs", H2 = "", F3 = "-WBd", F4 = "", B5 = "", B6 = ""),
  c("Consumption", H1 = "-Cd", H2 = "", F3 = "Cs", F4 = "", B5 = "", B6 = ""),
  c("[Production]", H1 = "", H2 = "", F3 = "Y", F4 = "", B5 = "", B6 = ""),
  c("Depreciation", H1 = "", H2 = "", F3 = "-AF", F4 = "AF", B5 = "", B6 = ""),
  c("Interest on Loans", H1 = "", H2 = "", F3 = "-last(rl)*last(Ld)", F4 = "", B5 = "last(rl)*last(Ls)", B6 = ""),
  c("Change in Deposits", H1 = "-(Mh-last(Mh))", H2 = "", F3 = "", F4 = "", B5 = "", B6 = "Ms-last(Ms)"),
  c("Interest on Deposits", H1 = "last(rm)*last(Mh)", H2 = "", F3 = "", F4 = "", B5 = "-last(rm)*last(Ms)", B6 = "")
)
```

```{r}
Balance_Sheet <- sfcr_matrix(
  # [ x=-496 y=56 type: transaction_flow invisible=false ]
  columns = c("Households", "Households", "Firms", "Firms", "Banks", "Banks"),
  codes = c("H1", "H2", "F3", "F4", "B5", "B6"),
  type = c("Asset", "Liability", "Asset", "Liability", "Asset", "Liability"),
  c("Deposits", H1 = "Mh", H2 = "", F3 = "", F4 = "", B5 = "", B6 = "-Ms"),
  c("Loans", H1 = "", H2 = "", F3 = "", F4 = "-Ld", B5 = "Ls", B6 = ""),
  c("Fixed Capital", H1 = "", H2 = "", F3 = "K", F4 = "", B5 = "", B6 = ""),
  c("Balance (net worth)", H1 = "-Mh", H2 = "", F3 = "", F4 = "", B5 = "", B6 = "")
)
```

```{r}
@startuml x=-512 y=184 w=773 h=526 width=660 scale=1.0653061224489795
   source: Transaction Flow Matrix
@end
```

```{r}
@scope Embedded_Scope_1 position=-1
  x1: 256
  y1: 432
  x2: 656
  y2: 656
  elmUid: KaRSfa
  speed: 1
  flags: x6001206
  source: uid:K79ipy value:0
  trace: uid:USgC2r value:0
  trace: uid:1ZqYCM value:0
@end
```

```{r}
@scope Embedded_Scope_1 position=-1
  x1: 320
  y1: 560
  x2: 928
  y2: 976
  elmUid: KaRSfa
  speed: 1
  flags: x6001206
  source: uid:K79ipy value:0
  trace: uid:USgC2r value:0
  trace: uid:1ZqYCM value:0
@end
```

```{r}
@circuit
x -460 -204 127 -201 4 18 Godley\sand\sLavoie\s(2007)\sChapter\s7:\sModel\sBMW\s(flow/param,\slast()) 808080FF U:Ck8M0l
x -460 -180 -126 -177 4 12 Bank-Money\sWorld\smodel\s(flow-only\svariant,\slast()\sfor\slag) 808080FF U:BRUOXo
x -460 -156 -150 -153 4 12 Steady-state:\sY\q160,\sCs\q144,\sIs\q16,\sK\q160,\sMh\q160 808080FF U:C67zij
207 400 544 464 544 180 Y U:K79ipy
431 288 272 336 304 0 70 true false U:Ozs0Ey
207 400 576 464 576 164 Ms U:1ZqYCM
207 400 592 464 592 164 Mh U:USgC2r
207 400 560 464 560 164 \\alpha0 U:9LTbPZ
207 400 608 464 608 164 Ls U:jyeEI-
207 400 624 464 624 164 Ld U:H3CeRk
@end
```

