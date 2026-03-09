# Stock-Flow Documentation Index

## Overview

Documentation for stock-flow modeling features in CircuitJS1.

This index reflects the current mixed architecture in the codebase:
- MNA-backed stock-flow elements (`GodlyTableElm`, `EquationTableElm` in MNA mode, `SFCStockElm`, `SFCFlowElm`)
- Pure computational/display elements (`SFCTableElm`, `EquationTableElm` in pure mode, display tables)
- Registry-driven integration via `ComputedValues` and `StockFlowRegistry`

## Documents

### PURE_COMPUTATIONAL_TABLES.md
**Purpose:** Architecture document for table computation modes and bridging patterns  
**Key Points:**
- `SFCTableElm` is display-only/pure computational (no MNA stamping)
- `EquationTableElm` supports both pure computational and MNA modes
- `GodlyTableElm` has no visible posts but does stamp MNA voltage-source structures
- `ComputedValues` double-buffering remains central to cross-element reads

### SFCR_FORMAT_REFERENCE.md
**Purpose:** Human-readable text format for SFC models (inspired by R sfcr package)  
**Key Points:**
- Human-readable alternative to binary dump format
- Compatible with sfcr R package syntax
- Supports `@matrix`, `@equations`, `@parameters`, `@hints`, `@scope` blocks
- Import via `File → Import From Text...` or `File → Open`
- Export via `File → Export As SFCR...`

### ~~EQUATION_TABLE_SIMPLIFICATION.md~~ *(removed — content merged into [EQUATION_TABLE_REFERENCE.md](EQUATION_TABLE_REFERENCE.md))*

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

### SFC_MNA_ELEMENTS_REFERENCE.md
**Purpose:** Reference for MNA-native stock-flow elements (`SFCStockElm`, `SFCFlowElm`)  
**Key Points:**
- `SFCStockElm` (dump type 268) models stocks as capacitor-backed nodes
- `SFCFlowElm` (dump type 269) models flows as nonlinear current sources
- KCL/KCL-based accounting identity enforcement through solver behavior

### EQUATION_TABLE_CURRENT_FLOW_MODE.md
**Purpose:** Detailed reference for EquationTable row output modes and current-flow behavior  
**Key Points:**
- Documents `VOLTAGE_MODE`, `FLOW_MODE`, and `PARAM_MODE` (legacy notes may mention removed `STOCK_MODE`)
- Includes stamping and lifecycle details (`stamp`, `doStep`, `startIteration`, `stepFinished`)
- Covers `ComputedValues` timing and convergence considerations

### MNA_SFC_STOCK_FLOW_USING_CURRENT_INVESTIGATION.md
**Purpose:** Design/analysis narrative for current-as-flow SFC modeling in MNA  
**Key Points:**
- Background rationale and electrical/economic mapping
- Historical proposal context for `SFCStockElm` / `SFCFlowElm`
- Complements the implementation reference docs

### ARCHITECTURE.md
**Purpose:** System-level architecture across MNA, computed-value registry, and bridging  
**Key Points:**
- Pure-computational and MNA-integrated stock-flow elements
- `ComputedValues` double-buffering lifecycle
- Circuit-global expression slot system (`E_GSLOT`) and reset-safe registration flow

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

### TABLE_PRIORITY_SYSTEM.md
**Purpose:** Priority and ownership behavior for table evaluation/mastering  
**Key Points:**
- Describes stock master ownership resolution and weighted priority behavior
- Explains ordering implications for shared stock names

## Related Components

- **TableElm** - Base stock-flow table (still has electrical posts)
- **GodlyTableElm** - MNA-backed table with hidden-node voltage-source driving for master stocks
- **SFCTableElm** - Display-only/pure computational SFC transaction matrix
- **EquationTableElm** - Dual-mode equation table (MNA mode or pure computational mode)
- **ComputedValueSourceElm** - Bridge element: reads ComputedValues, outputs voltage
- **SFCRParser** - Parser for human-readable SFCR format
- **StockFlowRegistry** - Synchronization service
- **ComputedValues** - Stock value registry
- **CurrentTransactionsMatrixElm** - Auto-populated matrix over master stocks
- **SFCStockElm / SFCFlowElm** - MNA-native stock/flow primitives

## Test Files

- `tests/sfcr-sim-model.txt` - SIM model in SFCR block format
- `tests/sfcr-r-style.txt` - SIM model in R sfcr-style syntax
- `tests/sfcr-sim-mna-model.txt` - MNA-oriented SFCR sample
- `tests/sfcr-pc-model.txt` - pure-computational SFCR sample

## UI Entry Points

- **Main menu (hardcoded fallback) includes:**
	- `Add Master Stocks Table` (`StockMasterElm`)
	- `Add Flows Table` (`FlowsMasterElm`)
	- `Add Current Transactions Matrix` (`CurrentTransactionsMatrixElm`)
