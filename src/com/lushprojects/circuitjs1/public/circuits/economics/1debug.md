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
@action Action_Schedule x=-384 y=864
  pauseTime: 0
  enabled: true
  element: -384 864 -368 880 0

| time | target | value | text | enabled | stop |
|------|--------|-------|------|---------|------|
| 30 | \alpha0 | =10 | propensity to spend | true | false |
| 40 | \alpha0 | +10 | After | true | false |
@end
```

```{r}
BMW <- sfcr_set(
  # [ x=1024 y=216 invisible=false ]
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
  e11 = Is ~ Id,  # Investment supply  [mode=param ]
  e12 = Ns ~ Nd,  # Labour supply  [mode=voltage ]
  e13 = Ls ~ Ls - Ls[-1] = Ld - Ld[-1],  # - Ls[-1] Loan supply  [mode=param ]
  # Transactions of the firms
  e14 = Y ~ Y = Cs + Is,  # National income (output)  [mode=voltage ]
  e15 = WBd ~ Y - last(rl) * last(Ld) - AF,  # Wage bill demanded  [mode=voltage ]
  e16 = AF ~ \delta * last(K),  # Amortisation funds  [mode=param ]
  e17 = Ld ~ last(Ld) + Id - AF,  # Loan demand  [mode=param ]
  # Transactions of households
  e18 = YD ~ WBs + last(rm) * last(Mh),  # Household disposable income  [mode=voltage ]
  e19 = Mh ~ last(Mh) + YD - Cd,  # Household money holdings (deposits)  [mode=param ]
  # Transactions of the banks
  e20 = Ms ~ last(Ms) + Ls - last(Ls),  # Money supply  [mode=param ]
  e21 = rm ~ rl,  # Deposit interest rate  [mode=param ]
  # The wage bill
  e22 = WBs ~ W * Ns,  # Wage bill supplied  [mode=voltage ]
  e23 = Nd ~ Y / pr,  # Labour demand  [mode=voltage ]
  e24 = W ~ WBd / max(Nd, 0.01),  # Wage rate  [mode=voltage ]
  # Household behavior
  e25 = Cd ~ \alpha0 + \alpha1 * YD + \alpha2 * last(Mh),  # Consumption demand  [mode=voltage ]
  # The investment behavior
  e26 = K ~ last(K) + Id - DA,  # Capital stock  [mode=param ]
  e27 = DA ~ \delta * last(K),  # Depreciation (actual)  [mode=param ]
  e28 = KT ~ \kappa * last(Y),  # Target capital stock  [mode=param ]
  e29 = Id ~ \gamma * (KT - last(K)) + DA  # Investment demand  [mode=param ]
)
```

```{r}
Transaction_Flow_Matrix <- sfcr_matrix(
  # [ x=320 y=216 type: transaction_flow invisible=false ]
  columns = c("Households_C", "Firms_{Current}", "Firms_{Capital}", "Banks_{Current}", "Banks_{Capital}"),
  codes = c("Households_C", "Firms__Current_", "Firms__Capital_", "Banks__Current_", "Banks__Capital_"),
  c("Change in Loans", Households_C = "", Firms__Current_ = "", Firms__Capital_ = "Ld-last(Ld)", Banks__Current_ = "", Banks__Capital_ = "-(Ls-last(Ls))"),
  c("Investment", Households_C = "", Firms__Current_ = "Is", Firms__Capital_ = "-Id", Banks__Current_ = "", Banks__Capital_ = ""),
  c("Wages", Households_C = "WBs", Firms__Current_ = "-WBd", Firms__Capital_ = "", Banks__Current_ = "", Banks__Capital_ = ""),
  c("Consumption", Households_C = "-Cd", Firms__Current_ = "Cs", Firms__Capital_ = "", Banks__Current_ = "", Banks__Capital_ = ""),
  c("[Production]", Households_C = "", Firms__Current_ = "Y", Firms__Capital_ = "", Banks__Current_ = "", Banks__Capital_ = ""),
  c("Depreciation", Households_C = "", Firms__Current_ = "-AF", Firms__Capital_ = "AF", Banks__Current_ = "", Banks__Capital_ = ""),
  c("Interest on Loans", Households_C = "", Firms__Current_ = "-last(rl)*last(Ld)", Firms__Capital_ = "", Banks__Current_ = "last(rl)*last(Ls)", Banks__Capital_ = ""),
  c("Change in Deposits", Households_C = "-(Mh-last(Mh))", Firms__Current_ = "", Firms__Capital_ = "", Banks__Current_ = "", Banks__Capital_ = "Ms-last(Ms)"),
  c("Interest on Deposits", Households_C = "last(rm)*last(Mh)", Firms__Current_ = "", Firms__Capital_ = "", Banks__Current_ = "-last(rm)*last(Ms)", Banks__Capital_ = "")
)
```

```{r}
Balance_Sheet <- sfcr_matrix(
  # [ x=320 y=424 type: transaction_flow invisible=false ]
  columns = c("Households", "Firms", "Banks"),
  codes = c("Households", "Firms", "Banks"),
  c("Deposits", Households = "Mh", Firms = "", Banks = "-Ms"),
  c("Loans", Households = "", Firms = "-Ld", Banks = "Ls"),
  c("Fixed Capital", Households = "", Firms = "K", Banks = ""),
  c("Balance (net worth)", Households = "-Mh", Firms = "", Banks = "")
)
```

```{r}
@startuml x=-640 y=200 w=933 h=575 width=660 scale=1.1653061224489796
   source: Transaction Flow Matrix
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
x 491 159 1045 162 4 18 Godley\sand\sLavoie\s(2007)\sChapter\s7:\sModel\sBMW\s(flow/param,\slast()) 808080FF U:Ck8M0l
x 491 183 796 186 4 12 Bank-Money\sWorld\smodel\s(flow-only\svariant,\slast()\sfor\slag) 808080FF U:BRUOXo
x 491 207 776 210 4 12 Steady-state:\sY\q160,\sCs\q144,\sIs\q16,\sK\q160,\sMh\q160 808080FF U:C67zij
207 64 848 128 848 180 Y U:K79ipy
431 -80 816 -32 848 0 70 true false U:Ozs0Ey
207 64 880 128 880 164 Ms U:1ZqYCM
207 64 896 128 896 164 Mh U:USgC2r
207 64 864 128 864 164 \\alpha0 U:9LTbPZ
207 64 912 128 912 164 Ls U:jyeEI-
207 64 928 128 928 164 Ld U:H3CeRk
@end
```

