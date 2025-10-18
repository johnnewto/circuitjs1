# TableElm Spreadsheet Visual Example

## Before Enhancement
```
┌────────────┬────────────┬────────────┐
│   Cash     │   Debt     │   Equity   │  Headers
├────────────┼────────────┼────────────┤
│ init: 100$ │ init: 50$  │ init: 50$  │  Initial (if shown)
├────────────┼────────────┼────────────┤
│  a+b: 10$  │  c+d: 5$   │  e+f: 5$   │  Data Row
│  x+y: 20$  │  m+n: 10$  │  p+q: 10$  │  Data Row
│  i+j: 30$  │  r+s: 15$  │  t+u: 15$  │  Data Row
├════════════┼════════════┼════════════┤
│ Cash: 60$  │ Debt: 30$  │ Equity:30$ │  Computed
└────────────┴────────────┴────────────┘
```

## After Enhancement (Spreadsheet-like)
```
┌──────────────┬────────────┬────────────┬────────────┐
│ Description  │   Cash     │   Debt     │   Equity   │  Headers
├──────────────┼────────────┼────────────┼────────────┤
│   (empty)    │   Asset    │ Liability  │   Equity   │  Column Types ★NEW
├──────────────┼────────────┼────────────┼────────────┤
│   Initial    │   100$     │    50$     │    50$     │  Initial (if shown)
├──────────────┼────────────┼────────────┼────────────┤
│ Revenue      │  a+b: 10$  │  c+d: 5$   │  e+f: 5$   │  Data Row ★NEW Label
│ Expenses     │  x+y: 20$  │  m+n: 10$  │  p+q: 10$  │  Data Row ★NEW Label
│ Investments  │  i+j: 30$  │  r+s: 15$  │  t+u: 15$  │  Data Row ★NEW Label
├══════════════┼════════════┼════════════┼════════════┤
│  Computed    │ Cash: 60$  │ Debt: 30$  │ Equity:30$ │  Computed
└──────────────┴────────────┴────────────┴────────────┘
     ★NEW          
  Row Labels
```

## Key Improvements

### 1. Row Description Column (Left)
- **Purpose**: Identifies what each row represents
- **Examples**: 
  - "Revenue" - income flows
  - "Expenses" - cost flows
  - "Investments" - capital flows
  - "Dividends" - equity distributions
- **Editing**: Editable in TableEditDialog
- **Style**: Light gray background

### 2. Column Type Row (Below Headers)
- **Purpose**: Shows the accounting classification of each column
- **Types Displayed**:
  - **Asset** - Resources owned (Cash, Inventory, Equipment)
  - **Liability** - Amounts owed (Debt, Payables)
  - **Equity** - Ownership interest (Stock, Retained Earnings)
  - **A-L-E** - Computed balance (Assets - Liabilities - Equity)
- **Style**: Light gray background

## Real-World Example

### Accounting Balance Sheet Flow Table
```
┌──────────────────┬────────────┬────────────┬────────────┬────────────┐
│   Description    │   Cash     │  Inventory │   Loans    │   Equity   │
├──────────────────┼────────────┼────────────┼────────────┼────────────┤
│     (empty)      │   Asset    │   Asset    │ Liability  │   Equity   │
├──────────────────┼────────────┼────────────┼────────────┼────────────┤
│    Initial       │  1000.00$  │  500.00$   │  800.00$   │  700.00$   │
├──────────────────┼────────────┼────────────┼────────────┼────────────┤
│ Sales Revenue    │  +100.00$  │  -50.00$   │   0.00$    │ +150.00$   │
│ Loan Payment     │  -50.00$   │   0.00$    │  -50.00$   │   0.00$    │
│ Purchase Goods   │  -80.00$   │  +80.00$   │   0.00$    │   0.00$    │
│ Operating Costs  │  -30.00$   │   0.00$    │   0.00$    │  -30.00$   │
├══════════════════┼════════════┼════════════┼════════════┼════════════┤
│    Computed      │  940.00$   │  530.00$   │  750.00$   │  820.00$   │
└──────────────────┴────────────┴────────────┴────────────┴────────────┘

Balance Check: Assets (940 + 530 = 1470) = Liabilities (750) + Equity (820)
                                1470      =      1570  ❌ (Needs +100 adjustment)
```

## Benefits for Users

### 1. Clarity
- **Before**: Users had to remember what each row meant
- **After**: Each row is clearly labeled

### 2. Organization
- **Before**: All data cells looked the same
- **After**: Clear visual hierarchy with labeled sections

### 3. Professional Appearance
- **Before**: Basic table layout
- **After**: Professional spreadsheet appearance

### 4. Educational
- **Before**: Column purposes were implicit
- **After**: Column types are explicit, helping users understand accounting structure

### 5. Flexibility
- **Before**: Generic "Row1", "Row2" labels
- **After**: Custom labels like "Revenue", "Expenses", etc.

## Color Coding Guide

- **White Background**: Headers and labels
- **Light Gray Background**: Structural cells (row descriptions, column types)
- **Voltage-Colored Background**: Data cells showing actual values
  - Green/Blue: Positive voltages (gains/increases)
  - Red/Orange: Negative voltages (losses/decreases)
  - Gray: Zero or neutral values

## Editing Workflow

1. **Create Table**: Drag TableElm component onto circuit
2. **Right-click**: Select "Edit Table Data..."
3. **Edit Row Labels**: Type custom descriptions in "Label" column
4. **Edit Column Types**: Use buttons to set Asset/Liability/Equity/Computed
5. **Enter Equations**: Fill in cell equations using labeled nodes
6. **Save**: Click OK to apply changes

Row descriptions persist through save/load cycles, making your models self-documenting!
