# Stock-Flow Display Elements - Quick Reference

## Overview

Two specialized display elements for monitoring stock-flow models:

1. **StockMasterElm** - Displays master **stocks** (columns/output names)
2. **FlowsMasterElm** - Displays all **flows** (rows/operations)

## Quick Comparison

| Aspect | StockMasterElm | FlowsMasterElm |
|--------|----------------|----------------|
| **Menu** | Add Master Stocks Table | Add Flows Table |
| **Dump Type** | 450 | 451 |
| **Displays** | Stock columns | Flow rows |
| **Data Source** | ComputedValues registry | Table.rowDescriptions[] |
| **Shows** | Stock name, value, master table | Flow name, count, table list |
| **Values** | Current voltage (V) | Usage count |
| **Highlighting** | Voltage colors | Blue for shared flows |
| **Primary Use** | Monitor stock levels | Verify flow consistency |

## Visual Comparison

### StockMasterElm Display
```
┌──────────────────────────────────┐
│       Master Stocks              │
├──────────┬────────┬──────────────┤
│ Stock    │ Value  │ Master Table │
├──────────┼────────┼──────────────┤
│ Firms    │ 90 V   │ Firms        │
│ Debt     │ 100 V  │ Firms        │
│ Reserves │ 100 V  │ Banks        │
└──────────┴────────┴──────────────┘
```

### FlowsMasterElm Display
```
┌──────────────────┬───────┬──────────────┐
│ Flow             │ Count │ Tables       │
├──────────────────┼───────┼──────────────┤
│ Lending          │ 2     │ Firms, Banks │
│ Pay Interest     │ 2     │ Firms, Banks │
│ Govt Taxes       │ 2     │ Banks, Treas │
└──────────────────┴───────┴──────────────┘
```

## When to Use Each

### Use StockMasterElm When:
- ✅ Monitoring stock levels during simulation
- ✅ Debugging stock synchronization issues
- ✅ Verifying which table drives each stock
- ✅ Checking accounting balance (A-L-E values)
- ✅ Watching real-time stock evolution

### Use FlowsMasterElm When:
- ✅ Verifying flow name consistency across tables
- ✅ Finding which tables share flows
- ✅ Documenting all operations in the model
- ✅ Checking for typos in flow names
- ✅ Understanding inter-table dependencies

## Data Architecture

```
Circuit Model
    │
    ├─── TableElm / GodleyTableElm
    │    ├─── outputNames[] ──────────┐
    │    │     (Stock columns)        │
    │    │                            ▼
    │    └─── rowDescriptions[]   StockMasterElm
    │          (Flow rows)            │
    │                                 │
    │                                 ├─ Reads: ComputedValues
    │                                 ├─ Shows: Stock values
    │                                 └─ Colors: Voltage scale
    │
    └─── FlowsMasterElm
         │
         ├─ Scans: All table.rowDescriptions[]
         ├─ Shows: Unique flow names
         └─ Highlights: Shared flows
```

## Implementation Details

### StockMasterElm
- **Data Source**: `ComputedValues.getComputedValueNames()`
- **Update Frequency**: 500ms
- **Storage**: Cached in `List<StockInfo>`
- **Display Logic**: Query registry for values and master tables

### FlowsMasterElm
- **Data Source**: Direct scan of `sim.elmList`
- **Update Frequency**: 500ms
- **Storage**: Cached in `List<FlowInfo>`
- **Display Logic**: Build HashMap of flow→tables, then convert to list

## Code Examples

### Getting Stock Info (StockMasterElm)
```java
String[] names = ComputedValues.getComputedValueNames();
for (String name : names) {
    Double value = ComputedValues.getComputedValue(name);
    Object master = ComputedValues.getComputingTable(name);
    // Display: name, value, master.tableTitle
}
```

### Getting Flow Info (FlowsMasterElm)
```java
HashMap<String, List<String>> flowToTables = new HashMap<>();
for (CircuitElm ce : sim.elmList) {
    if (ce instanceof TableElm) {
        TableElm table = (TableElm) ce;
        for (String flow : table.rowDescriptions) {
            flowToTables.put(flow, table.tableTitle);
        }
    }
}
```

## Use Case: Debugging a Stock-Flow Model

### Scenario: Incorrect Stock Values

1. **Use StockMasterElm** to see:
   - Current value of "Debt" stock
   - Which table is master (e.g., Firms)
   - If A-L-E shows non-zero (accounting error)

2. **Use FlowsMasterElm** to verify:
   - "Lending" flow exists in both Firms and Banks
   - No typos ("Lendin" vs "Lending")
   - Flow appears in expected tables

3. **Use TableMarkdownDebugDialog** for details:
   - Exact equations for each cell
   - Flow-by-flow breakdown
   - Non-zero flow/stock pairs

## Feature Comparison Matrix

| Feature | StockMasterElm | FlowsMasterElm |
|---------|----------------|----------------|
| Auto-discovery | ✓ | ✓ |
| Real-time updates | ✓ (500ms) | ✓ (500ms) |
| Voltage coloring | ✓ | ✗ |
| Shared highlighting | ✗ | ✓ (blue) |
| Shows values | ✓ (voltages) | ✗ |
| Shows counts | ✗ | ✓ |
| Editable title | ✓ | ✓ |
| Electrical connections | ✗ | ✗ |
| Sortable | ✓ (alphabetical) | ✓ (alphabetical) |
| Resizable | ✓ (drag diagonal) | ✓ (drag diagonal) |

## Configuration

Both elements support:
- **Right-click → Edit**: Change title
- **Move/Resize**: Drag to reposition, drag diagonal endpoint to resize width
- **Delete**: Standard circuit element deletion

## Integration with Workflow

### Circuit Design Phase
1. Add tables with flows and stocks
2. Add **FlowsMasterElm** to verify flow names
3. Check for consistency and typos

### Simulation Phase
1. Add **StockMasterElm** to monitor stock levels
2. Watch for accounting errors (A-L-E ≠ 0)
3. Verify master assignments are correct

### Debugging Phase
1. Use both elements side-by-side
2. FlowsMasterElm: Check which flows are shared
3. StockMasterElm: Monitor problematic stock
4. TableMarkdownDebugDialog: Deep dive into equations

## Tips and Best Practices

### StockMasterElm
- Place near main simulation area for visibility
- Watch A-L-E values (should be near zero)
- Note which stocks are shared (multiple tables)

### FlowsMasterElm
- Add early in model design
- Use to establish flow naming conventions
- Blue highlighting = inter-sector transactions

### Combined Use
- Place both elements together for complete overview
- FlowsMasterElm shows structure (flows)
- StockMasterElm shows behavior (stock levels)

## Performance Notes

Both elements:
- Cache data (refresh every 500ms)
- No electrical computation overhead
- Minimal rendering impact
- Scale well with model size

## File Format

### StockMasterElm
```
450 x1 y1 x2 y2 flags [voltages] title
```

### FlowsMasterElm
```
451 x1 y1 x2 y2 flags [voltages] title
```

## Related Documentation

- `TABLE_MASTER_ELM_REFERENCE.md` - Detailed StockMasterElm docs
- `FLOWS_MASTER_ELM_REFERENCE.md` - Detailed FlowsMasterElm docs
- `STOCK_FLOW_IMPLEMENTATION_COMPLETE.md` - Overall architecture
- `TableElm.java` - Table implementation
- `ComputedValues.java` - Stock registry

## Future Enhancements

Potential improvements for both:
- Export to CSV
- Filter/search functionality
- Click to highlight related elements
- Historical value tracking
- Customizable colors
- Tooltip details
