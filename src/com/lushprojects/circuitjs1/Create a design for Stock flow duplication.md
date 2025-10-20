# Create a design for Stock flow duplication across different tables

### Main Challenge: Row Synchronization

In stock-flow modeling, multiple tables may contribute flows to the same stock. When one table adds a new flow row, all tables sharing that stock should automatically include that row to maintain consistency.

**Example Scenarios:**
The first Column is the Transaction Description, the next columns are StockNames followed by Flow equations.

| Transaction Description | Stock_A | Stock_B |
|-------------------------|--------:|--------:|   
| Sales                   | 100     |         |
| Interest                | -50     | -100    |
| Wages                   | -20     | -40     |

#### Initial State:

Table A:      
| Flows↓/Stock Vars →     | Stock_A | Stock_B |
|-------------------------|--------:|--------:|
| Sales                   | 100     |         |
| Interest                | -50     | -100    |
| Wages                   | -20     | -40     |


Table B: 
| Flows↓/Stock Vars →     | Stock_A | Stock_C |
|-------------------------|--------:|--------:|
| Rent                    | -10     |         |
| Utilities               |         | -10     |


#### Case 1: On Synchronization:

Table A:
| Flows↓/Stock Vars →     | Stock_A | Stock_B |
|-------------------------|--------:|--------:|
| Sales                   | 100     |         |
| Interest                | -50     | -100    |
| Wages                   | -20     | -40     |
| Rent                    | -10     |         |


Table B:  
| Flows↓/Stock Vars →     | Stock_A | Stock_C |
|-------------------------|--------:|--------:|
| Sales                   | 100     |         |
| Interest                | -50     | -100    |
| Wages                   | -20     | -40     |
| Rent                    | -10     |         |
| Utilities               |         | -10     |

###  Case 2: Handling Row Deletion

When a row is deleted from one table, it should also be removed from all other tables sharing that stock.
#### Delete "Wages" from Table A:

Table A:
| Flows↓/Stock Vars →     | Stock_A | Stock_B |
|-------------------------|--------:|--------:|
| Sales                   | 100     |         |
| Interest                | -50     | -100    |
| Rent                    | -10     |         |

Table B:
| Flows↓/Stock Vars →     | Stock_A | Stock_C |
|-------------------------|--------:|--------:|
| Sales                   | 100     |         |
| Interest                | -50     | -100    |
| Rent                    | -10     |         |
| Utilities               |         | -10     |


### Case 3: Handling Row Modification

When a row is modified in one table, the change should propagate to all other tables sharing that stock.
#### Modify "Interest" in Table B to -150:

Table A:
| Flows↓/Stock Vars → | Stock_A | Stock_B |
|---------------------|--------:|--------:|
| Sales               | 100     |         |
| Interest            | -150    | -100    |
| Rent                | -10     |         |

Table B:
| Flows↓/Stock Vars → | Stock_A | Stock_C |
|---------------------|--------:|--------:|
| Sales               | 100     |         |
| Interest            | -150    | -100    |
| Rent                | -10     |         |
| Utilities           |         | -10     |


### Case 4: Handling Stock Renaming

When a stock is renamed in one table, all other tables sharing that stock should update to reflect the new name.
#### Rename "Cash" to "Main Cash" in Table A:
Table A:
| Flows↓/Stock Vars → | Main Cash | Stock_B |
|---------------------|----------:|--------:|
| Sales               | 100       |         |
| Interest            | -150      | -100    |
| Rent                | -10       |         |
Table B:
| Flows↓/Stock Vars → | Main Cash | Stock_C |
|---------------------|----------:|--------:|
| Sales               | 100       |         |
| Interest            | -150      | -100    |
| Rent                | -10       |         |
| Utilities           |           | -10     |


#### Case 5: Handling Stock Deletion

When a stock is deleted from one table, Just remove it from that table. Other tables remain unaffected.

#### Case 6: Handling Stock Addition

When a new stock is added in one table, if that stock exists in other tables, rows referencing it will synchronize as usual. If the row does not exist in the table then it is added at the end of the table.

When a stock is deleted from one table, Just remove it from that table. Other tables remain unaffected.
#### Case 7: Handling Table Creation

When a new table is created that shares stocks with existing tables, it should automatically populate rows from those stocks.


#### Case 8: Handling Table Deletion

When a table is deleted, it should not affect other tables. The rows in other tables remain intact.

#### Case 9: Handling Table Loading

When loading a table from a file, it should synchronize rows with existing tables sharing the same stocks.

#### Case 10: Handling Table Updates

When a table is updated (e.g., user edits a cell), it should trigger synchronization with other tables sharing the same stocks.

#### Case 11: Handling Table Synchronization

When a table is synchronized (e.g., user clicks a "Sync" button), it should ensure all rows are consistent across tables sharing the same stocks.





###  Reducing Calculation with Duplicate Stock Names
When multiple table elements have identical stock output names, the current implementation creates order-dependent behavior which prob ok. For debug Colour the background of the computing stock cell yellow to indicate its priority:

```java
// Current behavior - first element wins
boolean alreadyComputed = ComputedValues.isComputedThisStep(name);
if (alreadyComputed) {
    // Use pre-computed value
} else {
    // Calculate and register value (first one to run)
}
```

This only has to be performed during TableEditing or loading a new table. A procedure could be added to re-scan all tables and ensure that rows are synchronized across all tables sharing the same stock name. 

Can the solution use a simple approach of merging rows from all tables sharing the same stock name, This would ensure that all contributions to a stock are considered regardless of which table was processed first.

I dont want the complexity of central registry for stocks, just a simple merging mechanism during table updates.

### Proposed Solution:
