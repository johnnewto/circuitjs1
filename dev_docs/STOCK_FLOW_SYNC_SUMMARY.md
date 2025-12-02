# Stock-Flow Row Synchronization - Quick Summary

## The Problem

When multiple `TableElm` or `GodlyTableElm` instances share the same stock name (column header), their rows should be synchronized so all tables see the same set of flow descriptions. Currently, each table maintains its own independent row set, leading to inconsistencies.

**Example:**
```
Table A: "Cash" stock with rows [Sales, Interest]
Table B: "Cash" stock with rows [Wages, Rent, Utilities]

DESIRED: Both tables show all 5 rows: Sales, Interest, Wages, Rent, Utilities
CURRENT: Tables have different row counts (2 vs 3)
```

## The Solution (Service-Oriented Design)

### 1. **Service Class** (StockFlowRegistry.java ~350 lines)
**Heavy lifting** - all synchronization logic lives here:
- Track which tables share which stocks
- Map: `stockName → List<TableElm>`
- Merge row descriptions from all related tables
- Synchronize tables (single, related, or all)
- Cache merged rows for performance
- Prevent circular synchronization

### 2. **Minimal TableElm Interface** (~30 lines added)
**Thin wrapper** - delegates to registry:
- `findColumnByStockName()` - helper for registry access
- `updateRowData()` - apply synchronized data
- `synchronizeWithRelatedTables()` - delegates to registry
- `registerAllStocks()` - delegates to registry
- `delete()` - single unregister call

### 3. **Simple Triggers**
**One-line calls** - no complex logic:
- `TableEditDialog`: `StockFlowRegistry.synchronizeRelatedTables(table)`
- `CirSim`: `StockFlowRegistry.synchronizeAllTables()`
- `TableRenderer`: `StockFlowRegistry.getSharedStocks()`

## Key Design Decisions

✅ **Row descriptions synchronized** - All tables see same flow names
✅ **Equations remain table-specific** - Each table keeps its own values
✅ **User-triggered sync** - On edit/load, not every simulation step
✅ **Visual feedback** - Yellow highlighting for shared stocks
✅ **No computation changes** - Execution-order behavior preserved

## Implementation Files

### New Files
- **`StockFlowRegistry.java`** (~350 lines) - Centralized service for all synchronization logic

### Modified Files (Minimal Changes)
- **`TableElm.java`** (~30 lines) - Thin interface methods, delegates to registry
- **`TableEditDialog.java`** (~5 lines) - Single trigger call
- **`TableRenderer.java`** (~10 lines) - Query shared stocks for visual feedback
- **`CirSim.java`** (~5 lines) - Clear/sync registry on circuit lifecycle

### Code Distribution: ~85% in StockFlowRegistry
```
StockFlowRegistry:  350 lines (85%) ← Heavy lifting
TableElm:            30 lines (7%)  ← Minimal interface
Other files:         30 lines (8%)  ← Simple triggers
────────────────────────────────────
Total:             ~410 lines (100%)
```

## Example Code Flow

```java
// User edits Table A in TableEditDialog
TableEditDialog.applyChanges()
    → StockFlowRegistry.synchronizeRelatedTables(tableA)
        ↓
    [StockFlowRegistry does all the work:]
        → Find all tables sharing stocks with tableA
        → For each shared stock:
            → Collect row descriptions from all related tables
            → Merge into unified set [Sales, Interest, Wages, Rent, Utilities]
        → For each related table (A, B, C):
            → synchronizeTable(table)
                → Update rows with merged set
                → Preserve existing equations, add empty for new rows
                → Call table.updateRowData() to apply changes

// Result: All tables sharing "Cash" now have 5 synchronized rows
```

**Key: TableElm.updateRowData() is a simple setter - all logic is in the registry.**

## Benefits

1. **Consistency**: All tables sharing a stock see the same flow structure
2. **Separation of Concerns**: TableElm stays focused on simulation, registry handles sync
3. **Simplicity**: Single service class vs. scattered logic
4. **Performance**: Built-in caching and optimization
5. **Maintainability**: All sync logic in one place
6. **User-friendly**: Clear visual feedback for shared stocks
7. **Testability**: Service class can be unit tested independently

## Edge Cases Handled (in StockFlowRegistry)

- ✅ Circular sync prevention (`currentlySynchronizing` guard)
- ✅ Stock name changes (unregister old, register new, sync)
- ✅ Table deletion (`unregisterAllStocks()`)
- ✅ Empty row descriptions (skipped during merge)
- ✅ Row ordering (LinkedHashSet preserves first-seen)
- ✅ Circuit load/reset (registry cleared and rebuilt)
- ✅ Performance (caching of merged rows)
- ✅ Already-synced tables (skip if rows match)

## Visual Feedback

**Shared Stock Indicator:**
```
┌─────────────────────────┐
│ 💰 Cash  (Yellow BG)    │  ← Yellow background = shared
│ ━━━━━━━━━━━━━━━━━━━━━━ │
│ Sales      │ 100        │
│ Interest   │ 5          │
│ Wages      │ 0          │  ← Empty = this table doesn't contribute
│ Rent       │ 0          │
│ Utilities  │ 0          │
└─────────────────────────┘
```

## Testing Checklist

- [ ] Create 2 tables with shared stock
- [ ] Add row to Table 1 → verify Table 2 updates
- [ ] Delete row from Table 2 → verify Table 1 updates
- [ ] Rename stock → verify re-sync
- [ ] Delete table → verify unregistration
- [ ] Load circuit with shared stocks → verify sync
- [ ] Visual: yellow highlighting appears on shared stocks
- [ ] Performance: 10 tables, 50 rows (should be instant)

## Next Steps

1. **Implement `StockFlowRegistry.java`** (Phase 1 - Core service)
2. **Add minimal interface to `TableElm`** (Phase 2 - ~30 lines)
3. **Update `TableEditDialog`** (Phase 3 - Replace old code with single call)
4. **Add visual feedback to `TableRenderer`** (Phase 4 - Query registry)
5. **Integrate with `CirSim`** (Phase 5 - Lifecycle management)
6. **Test with multiple shared stocks** (Phase 6)

## Architecture Benefits

✅ **Single Responsibility**: Each class has one clear job  
✅ **Open/Closed**: Easy to extend StockFlowRegistry without touching TableElm  
✅ **Dependency Inversion**: TableElm depends on registry interface, not implementation  
✅ **DRY**: No duplicated synchronization logic  
✅ **Testable**: Can mock StockFlowRegistry for TableElm tests

## Complexity Estimate

- **New Code**: ~350 lines (StockFlowRegistry service class)
- **Modified Code**: ~80 lines (minimal interfaces + triggers)
- **Total**: ~430 lines
- **Effort**: 1-2 days development + testing
- **Risk**: Low (isolated service, no simulation logic changes)
- **Code Quality**: High (centralized, testable, maintainable)

**Key Metric: 85% of logic centralized in one service class** ✨

---

See `STOCK_FLOW_SYNC_DESIGN.md` for complete technical details, full code examples, and comprehensive edge case handling.
