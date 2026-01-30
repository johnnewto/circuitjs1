# Table Priority System

## Overview

The priority system controls which table becomes the "master" for shared stock variables. When multiple tables have columns with the same stock name, the table with the **highest priority** becomes the master and drives that stock's voltage.

## Key Features

- **Priority Range**: 1-9 (integer values)
- **Default Priority**: 5
- **Master Selection**: Higher priority tables become masters for shared stocks
- **Tie Breaking**: When priorities are equal, the first table to register becomes master

## Usage

### Setting Priority

1. Right-click a table → Edit
2. Find "Priority (1-9, higher=master)" dropdown
3. Select value (default is 5)
4. Click OK

### How It Works

**Example: Two tables with shared stock "Cash"**

- **Table A**: Priority = 9, has column "Cash"
- **Table B**: Priority = 5, has column "Cash"

**Result**: Table A becomes master for "Cash"
- Table A computes and drives the voltage
- Table B reads the voltage but doesn't drive it
- Only Table A's output affects the circuit

**Equal Priority Example:**
- Table A: Priority = 5, registered first
- Table B: Priority = 5, registered second
- Result: Table A remains master (first-come, first-served)

## Use Cases

### 1. Primary vs. Backup Tables
Set primary table to priority 9, backup to priority 1

### 2. Master Balance Sheet
Balance sheet at priority 8, transaction tables at priority 5

### 3. Override During Testing
Temporarily set test table to priority 9 to override other tables

### 4. Hierarchical Systems
- Level 1 (Strategy): Priority 9
- Level 2 (Operations): Priority 6
- Level 3 (Details): Priority 3

## Notes

- **A-L-E Columns**: Never registered for master selection (computed display only)
- **Priority Changes**: Take effect after circuit reset
- **Backward Compatibility**: Old files default to priority 5
