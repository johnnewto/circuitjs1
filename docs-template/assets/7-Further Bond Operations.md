# 7. Further Bond Operations

The model of Endogenous Money developed here is constrained by the Loanable Funds model from which it was derived. We can now extend it to include two more standard features of the current system: bond purchases by the Central Bank, and bond sales to non-banks by the private banks. This adds three more rows to the Private Banks table and creates an Asset for the Central Bank—see Figure 17.

## Figure 17: Including Private Bank bond sales and Central Bank Open Market Operations

### Private Banks

| Flows / Stocks → | Assets: Reserves | Assets: Debts from Households | Assets: Debts from Firms | Liabilities: Households | Liabilities: Firms | Liabilities: Banks | Equity | A-L-E |
|------------------|------------------|-------------------------------|--------------------------|--------------------------|--------------------|--------------------|--------|-------|
| Initial Conditions → | 100 | 0 | 0 | 40 | 50 | 10 | 0 | 0 |
| Pay Wages | | | | Wages | -Wages | | | |
| Buy Goods | | | | -Consume | Consume | | | |
| Borrow money | | Credit | | | | | | |
| Pay Interest | | | | | Interest | Interest | | |
| Government Spending | | | | | | | | |
| Taxation | | | | -Tax | | | | |
| Government Bond Sales | | | | | | -Bonds | | |
| Bond Sales by Banks | | | | | -Bonds^{NB} | Bonds^{NB} | | |
| Central Bank purchases from Banks | Bonds^{CB} | | | | | -Bonds^{CB} | | |
| Central Bank purchases from Non-Banks | Bonds^{CB} | | | | | | | |
| Bond Interest to Non-Banks | | | | | | | | Interest^{NB} |

### Central Bank

| Flows / Stocks → | Assets: Debts from Government | Assets: Bonds | Liabilities: Reserves | Liabilities: Government | Equity | A-L-E |
|------------------|-------------------------------|---------------|-----------------------|-------------------------|--------|-------|
| Initial Conditions | 0 | -Bonds | 0 | Bonds | 100 | 0 |
| Government Bond Sales | | Bonds | | -Bonds | | |
| Government Payments | | | | | | |
| Government Spending | | | | Spend_{Gov} | | |
| Taxation | | | | -Tax | | |
| Central Bank purchases from Banks | Bonds^{CB} | -Bonds^{CB} | | | | |
| Central Bank purchases from Non-Banks | Bonds^{CB} | -Bonds^{NB} | | | | |
| Bond Interest to Non-Banks | | | | | | Interest^{NB} |

This adds two more means by which fiat money can be created and destroyed. Bond sales by private banks to non-banks are financed by non-banks reducing their deposits, which destroys money; and Central Bank purchases of Bonds from Non-Banks create money.

The sale of bonds by banks to non-banks reverses the money creation effect of a government deficit, while it also adds another method of government money creation—paying interest on bonds owned by the non-bank private sector. Central Bank bond purchases from non-banks also create money. The general formula for money creation is shown in Equation (9):

$$
\frac{d}{dt} \text{Money} = \text{Credit} + (\text{Spend}_{\text{Gov}} - \text{Tax} + \text{Int}_{\text{Bonds}}^B) - \text{Bonds}_{\text{B}}^NB + \text{Bonds}_{\text{CB}}^NB + \text{Int}_{\text{Bonds}}^NB \quad (9)
$$

Finally, the capacity of the Central Bank to buy bonds off the private sector is effectively unlimited. Bernanke, despite being an advocate of the “Loanable Funds” model, once said that “the U.S. government has a technology, called a printing press (or, today, its electronic equivalent), that allows it to produce as many U.S. dollars as it wishes” (Bernanke 2002). Since the Central Bank can buy bonds by marking up both sides of its balance sheet, it would be feasible for the Central Bank to buy all outstanding government bonds, and thus reduce the interest payments on government debt to zero (since Treasuries either pay no interest on bonds owned by their Central Banks, or the interest payments are remitted back to the Treasury since it is the effective owner of the Central Bank).

There is, therefore, no fiscal crisis of the State from the mere fact that its spending exceeds taxation. There can be other problems, including inflation and trade deficits, which I explore later, but government spending in excess of taxation is a feature, not a bug, of a mixed fiat-credit monetary system.

But the State has helped trigger credit crises in the past, by attempting to eliminate its debt. Arguably, a major factor in such crises—in addition to the destruction of part of the money supply—is that, in the absence of government debt and deficits, the private non-bank sectors of the economy must be in negative financial equity.