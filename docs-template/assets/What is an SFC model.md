# What is an SFC Table?

An **SFC table** (Stock-Flow Consistent table), also known as a **transaction flow matrix** or **flow of funds matrix**, is a core accounting tool in stock-flow consistent (SFC) macroeconomic models. These models, popularized by economists like Wynne Godley and Marc Lavoie, ensure that every monetary flow in the economy is fully accounted for, with no "black holes" or unexplained creation/destruction of money. The table tracks all transactions between economic sectors in a given period, enforcing double-entry bookkeeping on a macroeconomic scale.

The key idea is **quadruple-entry accounting**: every transaction involves two sectors (payer and receiver), so it appears as an outflow (-) for one and an inflow (+) for the other. This guarantees consistency between the real economy (production, consumption) and the financial economy (money, debt).

## Structure of the Table

- **Rows**: Represent specific types of transactions or flows (e.g., consumption, wages, taxes, changes in financial assets).
- **Columns**: Represent economic sectors (e.g., Households, Firms, Government, Banks, Rest of the World).
- **Entries**: 
  - **+** sign: Receipt/inflow for that sector.
  - **-** sign: Payment/outflow for that sector.
- **Final column (Σ)**: Sum across sectors for each row — must be **0** (every flow comes from somewhere and goes somewhere).
- **Bottom row (Σ)**: Sum down each sector column — must be **0** (each sector's inflows equal outflows, enforcing budget constraints).

This structure ensures:
- **Horizontal consistency**: No net creation of money from a single transaction type.
- **Vertical consistency**: Each sector balances its current account (saving/investment or deficit/surplus leads to changes in stocks like money holdings).

## How It Works: The Example from Godley and Lavoie

The table you referenced is a simple closed-economy example (no foreign sector or explicit banks) from Godley and Lavoie's *Monetary Economics*. It shows flows between Households, Firms, and Government.

Here's a clearer rendering of the table:

| Transactions                  | Households | Firms (Current) | Firms (Capital) | Government | Σ   |
|--------------------------------|------------|-----------------|-----------------|------------|-----|
| Consumption                    | -C        | +C             |                 |            | 0   |
| Government expenditures        |           | +G             |                 | -G         | 0   |
| [Output / GDP]                 |           | [Y]            |                 |            |     |
| Wages                          | +WB       | -WB            |                 |            | 0   |
| Taxes                          | -T        |                |                 | +T         | 0   |
| Change in money holdings       | -ΔH_h     |                |                 | +ΔH_g      | 0   |
| **Σ**                          | 0         | 0              | 0               | 0          |     |

> **Note:** Slight variations exist in presentations; some split Firms into current and capital accounts for clarity. WB = wage bill, equivalent to W in your excerpt. ΔH_h = change in household money holdings; ΔH_g for government.

### Row-by-Row Explanation

- **Consumption**: Households spend -C on goods/services → outflows from households, inflows +C to firms (sales revenue).
- **Government expenditures**: Government spends -G on goods/services → outflows from government, inflows +G to firms.
- **Output [Y]**: Marks total production/income (Y = C + G in this simple model).
- **Wages**: Firms pay -WB in wages → outflows from firms, inflows +WB to households (their income).
- **Taxes**: Households pay -T → outflows from households, inflows +T to government.
- **Change in money holdings**: Residual row for financial balancing. If households save (income > spending), they accumulate money (-ΔH_h is negative, meaning + holdings). Government deficit might require issuing money (+ΔH_g).

The vertical sums being zero mean each sector's net saving (or deficit) translates into changes in financial stocks (here, just money).

## Why This Ensures Consistency

- All real flows (like output Y) link to financial flows (payments).
- Adding sectors/assets (e.g., banks for loans/deposits, or Rest of the World for exports/imports) complicates the table but follows the same rules.
- Flows accumulate into **stocks** (e.g., repeated deficits increase debt stocks on balance sheets).
- Models use these identities as equations, then add behavioral rules (e.g., consumption functions) to simulate dynamics.

---

# What is an SFC Model?

**Stock-Flow Consistent (SFC) models** are a class of **macroeconomic models** that enforce rigorous accounting rules to ensure every monetary flow (e.g., income, expenditure, interest payments) corresponds exactly to changes in stocks (e.g., assets, liabilities, wealth, debt). This approach prevents inconsistencies like unexplained "black holes" in money creation or destruction, providing a coherent integration of the real economy (production, consumption, investment) and the financial sector (credit, debt, balance sheets).

## Key Features

- **Accounting Foundation**: Models build on two core matrices:
  - **Balance Sheet Matrix**: Shows stocks of assets and liabilities across sectors (households, firms, banks, government, etc.), where rows sum to zero (every asset has a counterparty liability) and columns reflect net worth.
  - **Transactions Flow Matrix**: Tracks flows (e.g., wages, profits, taxes, loans) over a period, ensuring rows and columns sum to zero via quadruple-entry bookkeeping.
- **Behavioral Equations**: Added on top of accounting identities to model decisions (e.g., consumption as a function of income and wealth, investment based on capacity utilization or animal spirits).
- **Endogenous Money**: Banks create money through lending, rejecting the idea of a fixed money supply.
- **Sectoral Balances**: Automatically satisfy identities like (private surplus + public deficit + current account balance = 0).

## History and Origins

Pioneered by **Wynne Godley** (a post-Keynesian economist) and collaborators like Marc Lavoie, building on earlier work by James Tobin, Morris Copeland (flow-of-funds), and Michał Kalecki.

Godley famously used early SFC thinking to warn of unsustainable US private debt buildup in the 2000s, helping predict aspects of the 2008 crisis—something many mainstream models missed.

The seminal book is *Monetary Economics* (2007) by Godley and Lavoie.

## Why SFC Models Matter

They highlight how prolonged sectoral deficits (e.g., government borrowing) lead to unsustainable debt stocks, or how financial fragility builds over time. Often used in post-Keynesian, heterodox, and ecological macroeconomics to analyze crises, inequality, fiscal policy, and green transitions.

The document you provided is exactly an example of a simple SFC model: a three-sector (households, firms, banks) closed-economy simulation calibrated to US data, complete with matrices, equations, and R code for replication.

In short, SFC models offer a transparent, logically coherent alternative to mainstream approaches (like DSGE), emphasizing that "every flow must go somewhere, and every stock must come from somewhere."

---

These images show typical SFC transaction flow matrices from various models, illustrating the +/− structure and zero sums.

## Summary

The SFC table is a rigorous accounting ledger for the entire macroeconomy, preventing inconsistencies common in other models and allowing analysis of sustainability (e.g., rising debt ratios). More complex models include revaluation matrices for capital gains and full balance sheets for stocks.