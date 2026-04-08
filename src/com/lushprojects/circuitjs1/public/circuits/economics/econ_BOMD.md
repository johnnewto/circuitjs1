# CircuitJS1 SFCR Export
# Endogenous Money Model (BOMD)

@init
  timestep: 0.05
  voltageUnit: $
  timeUnit: yr
  showDots: false
  showVolts: true
  showValues: true
  showPower: false
  autoAdjustTimestep: false
  equationTableMnaMode: true
  equationTableNewtonJacobianEnabled: false
  equationTableTolerance: 0.001
  convergenceCheckThreshold: 100
  infoViewerUpdateIntervalMs: 200
@end

# Endogenous Money Model (BOMD)

This SFCR version restates the raw circuit in [src/com/lushprojects/circuitjs1/public/circuits/economics/econ_BOMD.txt](src/com/lushprojects/circuitjs1/public/circuits/economics/econ_BOMD.txt).

**BOMD** means **Bank-Originated Money and Debt**:

- banks create money by creating loans
- the new loan simultaneously creates a new deposit
- borrowers pay interest to banks
- banks recycle part of that income back into the private sector via spending

The documentation page at the endogenous-money example shows the government-extended BOMD variant. This file captures the simpler private version already encoded in the raw circuit, while presenting it in SFCR form with an equation table, a transaction-flow table, and a balance-sheet table.

## Core equations

$$
\frac{dDebt}{dt} = Lend
$$

$$
\frac{dBorrowers}{dt} = Lend + 0.5 \cdot Spend_{banks} - Interest
$$

$$
\frac{dSavers}{dt} = 0.5 \cdot Spend_{banks}
$$

$$
\frac{dBanks}{dt} = Interest - Spend_{banks}
$$

$$
Money = Borrowers + Savers
$$

$$
GDP = Velocity \cdot Money
$$

@equations BOMD_Equations x=176 y=224
  # Parameters
  Velocity ~ 2 ; mode=param
  Lend_frac ~ 0.02 ; mode=param
  Interest_rate ~ 0.05 ; mode=param
  Spend_rate ~ 0.1 ; mode=param
  Fee_rate ~ 0.5 ; mode=param
  Reserves ~ 100 ; mode=param

  # Auxiliary variables
  Money ~ Borrowers + Savers
  GDP ~ Velocity * Money
  Lend ~ Lend_frac * GDP
  Interest ~ Interest_rate * Debt
  Fee ~ Fee_rate * Interest
  Spend_banks ~ Spend_rate * Banks
  Private_Sector ~ Borrowers + Savers
  Borrower_Equity ~ Borrowers - Debt
  Debt_to_GDP ~ 100 * Debt / max(GDP, 0.001)
  Bank_Balance_Check ~ Reserves + Debt - Money - Banks

  # Integrated stocks
  Debt ~ integrate(Lend) ; initial=0
  Borrowers ~ integrate(Lend + 0.5 * Spend_banks - Interest) ; initial=10
  Savers ~ integrate(0.5 * Spend_banks) ; initial=80
  Banks ~ integrate(Interest - Spend_banks) ; initial=10
@end

@matrix Transaction_Flow_Matrix x=512 y=160
  type: transaction_flow

| Transaction                | Borrowers            | Savers              | Banks              |
|---------------------------|----------------------|---------------------|--------------------|
| New credit                | Lend                 |                     | -Lend              |
| Interest payments         | -Interest            |                     | Interest           |
| Bank spending to borrowers| 0.5 * Spend_banks    |                     | -0.5 * Spend_banks |
| Bank spending to savers   |                      | 0.5 * Spend_banks   | -0.5 * Spend_banks |
@end

@matrix Balance_Sheet x=512 y=368
  type: balance_sheet

| Stock / Instrument | Private_Sector | Banks    | Central_Bank |
|--------------------|----------------|----------|--------------|
| Deposits           | Money          | -Money   |              |
| Loans              | -Debt          | Debt     |              |
| Bank Equity        | Banks          | -Banks   |              |
| Reserves           |                | Reserves | -Reserves    |
@end

@hints
  Velocity: Turnover of deposits into nominal GDP
  Lend_frac: New credit as a fraction of GDP
  Interest_rate: Interest rate paid on outstanding debt
  Spend_rate: Fraction of bank equity spent back into the economy
  Fee_rate: Auxiliary fee parameter from the original circuit
  Debt: Outstanding bank-created debt
  Borrowers: Deposits held by borrowers
  Savers: Deposits held by savers
  Banks: Bank equity / retained earnings
  Money: Total deposits in the private sector
  GDP: Nominal output implied by money turnover
  Bank_Balance_Check: Should remain near zero if stocks and flows are consistent
@end

@info
# Endogenous Money Model (BOMD)

This is an SFCR restatement of the raw BOMD circuit.

## Interpretation

- `Debt` is the stock of bank loans.
- `Borrowers` and `Savers` are deposit stocks on the liability side of bank balance sheets.
- `Banks` is bank equity.
- `Reserves` is treated as an exogenous central-bank liability so the balance sheet closes.

## Accounting logic

A new loan creates:

- a bank asset: `Debt`
- a private-sector deposit: `Borrowers`

Interest transfers income from borrowers to banks. Bank spending recycles that income back into the private sector, split equally between borrowers and savers as in the original circuit layout.

## Relation to the documentation page

The page at the endogenous-money example shows the government-extended BOMD model. This file keeps the simpler no-government core of the existing raw circuit while expressing it in an SFC/SFCR structure.
@end
