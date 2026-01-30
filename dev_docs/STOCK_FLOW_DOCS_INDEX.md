# Stock-Flow Documentation Index

## Overview

Documentation for stock-flow modeling features in CircuitJS1.

## Documents

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

- **TableElm** - Main stock-flow table
- **GodlyTableElm** - Table with integration capabilities
- **StockFlowRegistry** - Synchronization service
- **ComputedValues** - Stock value registry
