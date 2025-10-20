# Stock-Flow Synchronization: Architecture Comparison

## Before: Scattered Logic ❌

```
TableElm.java
├── collectRowDescriptionsForSharedStocks()  [static, 30 lines]
├── findColumnByName()                        [private, 10 lines]
├── synchronizeRowsWithSharedStocks()        [public, 40 lines]
├── synchronizeColumnRows()                   [private, 50 lines]
├── registerAllStocks()                       [private, 10 lines]
├── setColumnHeader()                         [public, 15 lines]
└── delete()                                  [override, 10 lines]
                                              ──────────────────
                                              ~165 lines in TableElm

TableEditDialog.java
├── applyChanges()                            [modified, 30 lines]
└── synchronizeAllRelatedTables()            [private, 25 lines]
                                              ──────────────────
                                              ~55 lines in Dialog

StockFlowRegistry.java
├── registerStock()                           [static, 8 lines]
├── unregisterStock()                         [static, 5 lines]
├── getTablesForStock()                       [static, 3 lines]
├── clearRegistry()                           [static, 3 lines]
└── getSharedStocks()                         [static, 8 lines]
                                              ──────────────────
                                              ~27 lines (minimal)

CirSim.java
├── synchronizeAllTableElements()             [private, 15 lines]
└── readCircuit() modifications               [5 lines]
                                              ──────────────────
                                              ~20 lines

TOTAL: ~267 lines spread across 4 classes
```

### Problems:
- ❌ TableElm bloated with synchronization logic
- ❌ Complex static methods in element class
- ❌ Logic duplicated between TableElm and Dialog
- ❌ Hard to test synchronization independently
- ❌ No clear separation of concerns
- ❌ Registry is just a dumb map

---

## After: Service-Oriented Design ✅

```
StockFlowRegistry.java (SERVICE CLASS)
├── Registry Management
│   ├── registerStock()                       [8 lines]
│   ├── unregisterStock()                     [5 lines]
│   ├── unregisterAllStocks()                 [10 lines]
│   ├── getTablesForStock()                   [3 lines]
│   ├── clearRegistry()                       [5 lines]
│   ├── getSharedStocks()                     [8 lines]
│   └── isSharedStock()                       [4 lines]
│
├── Row Merging Logic
│   ├── getMergedRowDescriptions()            [30 lines + caching]
│   ├── invalidateCache()                     [3 lines]
│   └── checkRowsAlreadyMatch()              [15 lines]
│
├── Synchronization Algorithms
│   ├── synchronizeTable()                    [25 lines + guards]
│   ├── synchronizeTableColumn()              [55 lines]
│   ├── synchronizeRelatedTables()            [15 lines]
│   └── synchronizeAllTables()                [10 lines]
│
├── Circular Dependency Prevention
│   └── currentlySynchronizing guard          [built-in]
│
└── Diagnostics
    └── getDiagnosticInfo()                   [20 lines]
                                              ──────────────────
                                              ~350 lines (ALL LOGIC)

TableElm.java (THIN INTERFACE)
├── findColumnByStockName()                   [8 lines] ← helper
├── updateRowData()                           [10 lines] ← setter
├── synchronizeWithRelatedTables()            [3 lines] ← delegates
├── registerAllStocks()                       [8 lines] ← delegates
└── delete()                                  [3 lines] ← single call
                                              ──────────────────
                                              ~32 lines (minimal)

TableEditDialog.java (SIMPLE TRIGGER)
└── applyChanges() modification               [5 lines]
    → StockFlowRegistry.synchronizeRelatedTables(table)

TableRenderer.java (SIMPLE QUERY)
└── highlightSharedStocks()                   [10 lines]
    → StockFlowRegistry.getSharedStocks()

CirSim.java (LIFECYCLE HOOKS)
├── readCircuit() modification                [2 lines]
│   → StockFlowRegistry.clearRegistry()
│   → StockFlowRegistry.synchronizeAllTables()
└── (no helper method needed)                 [0 lines]
                                              ──────────────────
                                              ~17 lines (minimal)

TOTAL: ~410 lines
- StockFlowRegistry: 350 lines (85%)
- Other classes:      60 lines (15%)
```

### Benefits:
- ✅ **85% of logic in one service class**
- ✅ TableElm stays focused on simulation
- ✅ Clear separation of concerns
- ✅ Easy to test (mock StockFlowRegistry)
- ✅ Easy to extend (add features to registry)
- ✅ Built-in performance optimization (caching)
- ✅ Built-in circular dependency prevention
- ✅ Diagnostic capabilities for debugging

---

## Key Architecture Improvements

### 1. Single Responsibility Principle

**Before:**
```java
// TableElm doing too many things
public class TableElm extends ChipElm {
    // Circuit simulation
    void doStep() { ... }
    void stamp() { ... }
    
    // Row synchronization (WRONG!)
    void synchronizeRowsWithSharedStocks() { ... }
    static Map<String, Set<String>> collectRowDescriptionsForSharedStocks() { ... }
}
```

**After:**
```java
// TableElm focused on simulation
public class TableElm extends ChipElm {
    // Circuit simulation
    void doStep() { ... }
    void stamp() { ... }
    
    // Minimal interface for synchronization
    void synchronizeWithRelatedTables() { 
        StockFlowRegistry.synchronizeRelatedTables(this); 
    }
}

// StockFlowRegistry handles all synchronization
public class StockFlowRegistry {
    public static void synchronizeRelatedTables(TableElm table) {
        // ALL logic here
    }
}
```

### 2. Dependency Inversion

**Before:**
```java
// TableEditDialog knows about TableElm synchronization internals
private void synchronizeAllRelatedTables() {
    Set<TableElm> affectedTables = new HashSet<>();
    for (int col = 0; col < dataCols; col++) {
        List<TableElm> tables = StockFlowRegistry.getTablesForStock(stockValues[col]);
        affectedTables.addAll(tables);
    }
    for (TableElm table : affectedTables) {
        table.synchronizeRowsWithSharedStocks(); // Tight coupling
    }
}
```

**After:**
```java
// TableEditDialog depends on service interface only
private void applyChanges() {
    // ... apply changes ...
    
    // Simple delegation - no knowledge of internals
    StockFlowRegistry.synchronizeRelatedTables(tableElement);
}
```

### 3. Testability

**Before:**
```java
// Hard to test TableElm without full circuit setup
@Test
public void testSynchronization() {
    // Need to create multiple TableElm instances
    // Need to set up CirSim
    // Need to configure stocks
    // Hard to isolate synchronization logic
}
```

**After:**
```java
// Easy to test StockFlowRegistry independently
@Test
public void testMergeRows() {
    // Create mock tables with test data
    MockTableElm table1 = new MockTableElm("Cash", ["Sales", "Interest"]);
    MockTableElm table2 = new MockTableElm("Cash", ["Wages", "Rent"]);
    
    // Register and synchronize
    StockFlowRegistry.registerStock("Cash", table1);
    StockFlowRegistry.registerStock("Cash", table2);
    
    LinkedHashSet<String> merged = StockFlowRegistry.getMergedRowDescriptions("Cash");
    
    // Verify merge result
    assertEquals(4, merged.size());
    assertTrue(merged.contains("Sales"));
    assertTrue(merged.contains("Wages"));
}
```

### 4. Performance Optimization

**Before:**
```java
// Recalculates merged rows every time
public void synchronizeRowsWithSharedStocks() {
    Map<String, Set<String>> stockToRows = collectRowDescriptionsForSharedStocks();
    // ↑ Expensive: iterates all tables, all rows, every call
}
```

**After:**
```java
// Built-in caching in StockFlowRegistry
private static Map<String, LinkedHashSet<String>> mergedRowsCache = new HashMap<>();

public static LinkedHashSet<String> getMergedRowDescriptions(String stockName) {
    // Check cache first
    if (mergedRowsCache.containsKey(stockName)) {
        return new LinkedHashSet<>(mergedRowsCache.get(stockName));
    }
    
    // Calculate once, cache result
    LinkedHashSet<String> mergedRows = /* ... calculate ... */;
    mergedRowsCache.put(stockName, mergedRows);
    return mergedRows;
}

// Invalidate when needed
private static void invalidateCache(String stockName) {
    mergedRowsCache.remove(stockName);
}
```

### 5. Extension Points

**Before:**
```java
// Adding new sync feature requires modifying TableElm
// Risk of breaking existing simulation code
```

**After:**
```java
// Adding features to StockFlowRegistry is safe
public class StockFlowRegistry {
    // Easy to add new features without touching TableElm:
    
    public static void synchronizeSubset(List<TableElm> tables) { ... }
    
    public static Map<String, ConflictInfo> detectConflicts() { ... }
    
    public static void exportSyncState() { ... }
    
    public static void importSyncState() { ... }
}
```

---

## Code Metrics Comparison

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| **TableElm lines** | +165 | +32 | **80% reduction** |
| **Service class lines** | 27 | 350 | **Proper service** |
| **Dialog complexity** | High | Low | **Simple trigger** |
| **Test isolation** | Hard | Easy | **Unit testable** |
| **Circular guard** | Manual | Built-in | **Automatic** |
| **Performance** | No cache | Cached | **Optimized** |
| **Extension** | Risky | Safe | **Open/Closed** |
| **Logic centralization** | 60% | 85% | **Better separation** |

---

## Summary

### Service-Oriented Design Wins:

1. ✅ **TableElm reduced by 80%** - stays focused on simulation
2. ✅ **85% logic centralized** - single service class
3. ✅ **Easy to test** - can unit test StockFlowRegistry independently
4. ✅ **Easy to extend** - add features without touching TableElm
5. ✅ **Better performance** - built-in caching
6. ✅ **Safer** - built-in circular dependency prevention
7. ✅ **Cleaner API** - simple delegation methods
8. ✅ **Better diagnostics** - getDiagnosticInfo() for debugging

### The Pattern:

```
Heavy Logic → StockFlowRegistry (service class)
Thin Interface → TableElm (minimal delegation)
Simple Triggers → UI classes (one-line calls)
```

This is **proper separation of concerns** following SOLID principles! 🎯
