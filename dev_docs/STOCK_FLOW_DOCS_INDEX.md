# Stock-Flow Documentation Index

## Overview

Documentation for stock-flow modeling features in CircuitJS1.

## Documents

### PURE_COMPUTATIONAL_TABLES.md
**Purpose:** Architecture document for pure computational table elements  
**Key Points:**
- GodlyTableElm, EquationTableElm, SFCTableElm are pure computational (no MNA)
- Values written to ComputedValues registry
- Use ComputedValueSourceElm to bridge to electrical domain
- Double-buffering ensures order-independent evaluation

### SFCR_FORMAT_PROPOSAL.md
**Purpose:** Human-readable text format for SFC models (inspired by R sfcr package)  
**Key Points:**
- Human-readable alternative to binary dump format
- Compatible with sfcr R package syntax
- Supports `@matrix`, `@equations`, `@parameters`, `@hints`, `@scope` blocks
- Can parse R-style `sfcr_matrix()` and `sfcr_set()` definitions

### STOCK_FLOW_SYNC_SUMMARY.md
**Purpose:** Row synchronization between tables sharing stocks  
**Key Points:**
- When tables share stock names, their rows are automatically synchronized
- Equations remain table-specific
- Visual feedback with yellow highlighting for shared stocks

### STOCK_FLOW_DISPLAY_ELEMENTS.md
**Purpose:** Display elements for debugging stock-flow models  
**Key Points:**
- StockMasterElm - shows master table assignments
- FlowsMasterElm - shows all flows across tables

### STOCK_MASTER_ELM_REFERENCE.md
**Purpose:** Reference for the Master Stocks display element  
**Key Points:**
- Shows which table "owns" each stock variable
- Real-time voltage values
- Menu: Add Master Stocks Table

### FLOWS_MASTER_ELM_REFERENCE.md
**Purpose:** Reference for the All Flows display element  
**Key Points:**
- Lists all flow names across all tables
- Highlights shared flows in blue
- Helps catch naming inconsistencies

## Related Components

- **TableElm** - Base stock-flow table (still has electrical posts)
- **GodlyTableElm** - Pure computational table with integration
- **SFCTableElm** - Pure computational SFC transaction flow matrix
- **EquationTableElm** - Pure computational equation table
- **ComputedValueSourceElm** - Bridge element: reads ComputedValues, outputs voltage
- **SFCRParser** - Parser for human-readable SFCR format
- **StockFlowRegistry** - Synchronization service
- **ComputedValues** - Stock value registry

## Test Files

- `tests/sfcr-sim-model.txt` - SIM model in SFCR block format
- `tests/sfcr-r-style.txt` - SIM model in R sfcr-style syntax
