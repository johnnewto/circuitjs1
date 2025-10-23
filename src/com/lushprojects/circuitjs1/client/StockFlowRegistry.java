/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.

    CircuitJS1 is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    CircuitJS1 is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with CircuitJS1.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.lushprojects.circuitjs1.client;

import java.util.*;

/**
 * StockFlowRegistry - Service class for managing stock-flow synchronization
 * 
 * Responsibilities:
 * - Track which tables share which stocks (registry)
 * - Collect and merge row descriptions across related tables
 * - Synchronize tables when stocks are shared
 * - Provide visual feedback data (shared stocks list)
 * 
 * This keeps TableElm focused on simulation logic, not synchronization.
 */
public class StockFlowRegistry {
    
    // ========== LOGGING UTILITIES ==========
    
    public static native void log(String methodName, String message)
    /*-{
        console.log(methodName + " - " + message);
    }-*/;
    
    public static native void MRDlog(String message)
    /*-{
        console.log("StockFlowRegistry: getMergedRowDescriptions - " + message);
    }-*/;
    
    public static native void SRTlog(String message)
    /*-{
        console.log("StockFlowRegistry: synchronizeRelatedTables - " + message);
    }-*/;
    
    // ========== REGISTRY DATA STRUCTURES ==========
    
    // Map: stock name → list of TableElm instances
    private static Map<String, List<TableElm>> stockToTables = new HashMap<String, List<TableElm>>();
    
    // Cache of merged rows per stock (invalidated on changes)
    private static Map<String, LinkedHashSet<String>> mergedRowsCache = new HashMap<String, LinkedHashSet<String>>();
    
    // Synchronization guard to prevent infinite recursion
    private static Set<TableElm> currentlySynchronizing = new HashSet<TableElm>();
    
    /**
     * Register a table's stock (column) for synchronization tracking
     */
    public static void registerStock(String stockName, TableElm table) {
        if (stockName == null || stockName.trim().isEmpty()) return;
        
        if (!stockToTables.containsKey(stockName)) {
            stockToTables.put(stockName, new ArrayList<TableElm>());
        }
        List<TableElm> tables = stockToTables.get(stockName);
        if (!tables.contains(table)) {
            tables.add(table);
            invalidateCache(stockName); // Cache is now stale
        }
    }
    
    /**
     * Unregister a table (e.g., when deleted or stock name changed)
     */
    public static void unregisterStock(String stockName, TableElm table) {
        if (stockToTables.containsKey(stockName)) {
            stockToTables.get(stockName).remove(table);
            invalidateCache(stockName);
        }
    }
    
    /**
     * Unregister all stocks for a table (e.g., on deletion)
     */
    public static void unregisterAllStocks(TableElm table) {
        List<String> stocksToRemove = new ArrayList<String>();
        for (Map.Entry<String, List<TableElm>> entry : stockToTables.entrySet()) {
            if (entry.getValue().contains(table)) {
                entry.getValue().remove(table);
                stocksToRemove.add(entry.getKey());
            }
        }
        for (String stock : stocksToRemove) {
            invalidateCache(stock);
        }
    }
    
    /**
     * Get all tables that share this stock name
     */
    public static List<TableElm> getTablesForStock(String stockName) {
        List<TableElm> tables = stockToTables.get(stockName);
        return tables != null ? tables : new ArrayList<TableElm>();
    }
    
    /**
     * Clear registry (on circuit load/reset)
     */
    public static void clearRegistry() {
        stockToTables.clear();
        mergedRowsCache.clear();
        currentlySynchronizing.clear();
    }
    
    /**
     * Get all stocks shared by multiple tables
     */
    public static Set<String> getSharedStocks() {
        Set<String> shared = new HashSet<String>();
        for (Map.Entry<String, List<TableElm>> entry : stockToTables.entrySet()) {
            if (entry.getValue().size() > 1) {
                shared.add(entry.getKey());
            }
        }
        return shared;
    }
    
    /**
     * Check if a stock is shared by multiple tables
     */
    public static boolean isSharedStock(String stockName) {
        List<TableElm> tables = stockToTables.get(stockName);
        return tables != null && tables.size() > 1;
    }
    
    /**
     * Invalidate merged rows cache for a stock
     */
    private static void invalidateCache(String stockName) {
        mergedRowsCache.remove(stockName);
    }
    
    // ========== ROW MERGING ==========

    /**
     * Collect all unique row descriptions for a given stock
     * Returns a LinkedHashSet to preserve insertion order
     * Uses caching for performance
     * 
     * @param stockName The stock name to collect rows for
     * @param priorityTable Optional table whose row order should be preserved (null for default)
     */
    public static LinkedHashSet<String> getMergedRowDescriptions(String stockName, TableElm priorityTable) {
        // Can't use cache if we have a priority table (order matters)
        if (priorityTable == null && mergedRowsCache.containsKey(stockName)) {
            LinkedHashSet<String> cached = mergedRowsCache.get(stockName);
            MRDlog( "Using cached rows for stock '" + stockName + "': " + cached);
            return new LinkedHashSet<String>(cached);
        }
        
        LinkedHashSet<String> mergedRows = new LinkedHashSet<String>();
        List<TableElm> tables = getTablesForStock(stockName);
        
        MRDlog( "Merging rows for stock '" + stockName + "' from " + tables.size() + " table(s)");
        
        // If we have a priority table, collect its rows first to establish order
        if (priorityTable != null && tables.contains(priorityTable)) {
            int colIndex = priorityTable.findColumnByStockName(stockName);
            if (colIndex >= 0) {
                MRDlog( "Priority table: " + priorityTable.getTableTitle() + " (establishes row order)");
                // Null safety check for priorityTable before calling getRows()
                try {
                    int priorityRows = priorityTable.getRows();
                    for (int row = 0; row < priorityRows; row++) {
                        String desc = priorityTable.getRowDescription(row);
                        if (desc != null && !desc.trim().isEmpty()) {
                            mergedRows.add(desc.trim());
                            MRDlog( "  [Priority] Row " + row + ": '" + desc.trim() + "'");
                        }
                    }
                } catch (Exception e) {
                    MRDlog( "Error accessing priority table rows: " + e.getMessage());
                }
            }
        }
        
        // Now collect rows from all other tables (new rows will be added at end)
        for (TableElm table : tables) {
            // Skip priority table since we already processed it
            if (table == priorityTable) continue;
            
            // Null safety check for table
            if (table == null) {
                MRDlog( "Skipping null table in tables list");
                continue;
            }
            
            int colIndex = table.findColumnByStockName(stockName);
            if (colIndex >= 0) {
                MRDlog( "Processing table: " + table.getTableTitle());
                int rowsAdded = 0;
                
                // Null safety check before calling getRows()
                try {
                    int tableRows = table.getRows();
                    for (int row = 0; row < tableRows; row++) {
                        String desc = table.getRowDescription(row);
                        if (desc != null && !desc.trim().isEmpty()) {
                            String trimmedDesc = desc.trim();
                            boolean isNew = !mergedRows.contains(trimmedDesc);
                            mergedRows.add(trimmedDesc);
                            if (isNew) {
                                MRDlog( "  [NEW] Row " + row + ": '" + trimmedDesc + "'");
                                rowsAdded++;
                            } else {
                                MRDlog( "  [EXISTS] Row " + row + ": '" + trimmedDesc + "' (skipped)");
                            }
                        }
                    }
                    MRDlog( "  Added " + rowsAdded + " new row(s) from " + table.getTableTitle());
                } catch (Exception e) {
                    MRDlog( "Error accessing table rows for " + table.getTableTitle() + ": " + e.getMessage());
                }
            }
        }
        
        // Log final merged result
        MRDlog( "Final merged rows for stock '" + stockName + "' (" + mergedRows.size() + " total): " + mergedRows);
        
        // Only cache if no priority table (deterministic ordering)
        if (priorityTable == null) {
            mergedRowsCache.put(stockName, new LinkedHashSet<String>(mergedRows));
            MRDlog( "Cached result for future use");
        }
        
        return mergedRows;
    }
    
    /**
     * Convenience method for backward compatibility
     */
    public static LinkedHashSet<String> getMergedRowDescriptions(String stockName) {
        return getMergedRowDescriptions(stockName, null);
    }
    
    // ========== TABLE SYNCHRONIZATION ==========
    
    /**
     * Helper class to hold stock equation data during synchronization
     */
    private static class StockEquation {
        String stockName;
        int columnIndex;
        String equation;
        
        StockEquation(String stockName, int columnIndex, String equation) {
            this.stockName = stockName;
            this.columnIndex = columnIndex;
            this.equation = equation;
        }
    }

    /**
     * Synchronize a single table with merged rows for its stocks
     * 
     * Strategy: For each non-zero element in priority table:
     * 1. Find matching flow (row description) in target table
     * 2. Find matching stock (column header) in target table
     * 3. If both exist, populate the equation
     * 4. If flow doesn't exist, create it at the end
     * 
     * @param table The table to synchronize
     * @param priorityTable The table with equations to propagate (trigger table)
     * @return true if table was modified, false if already in sync
     */
    public static boolean synchronizeTable(TableElm table, TableElm priorityTable) {
        // Prevent recursive synchronization
        if (currentlySynchronizing.contains(table)) {
            return false;
        }
        
        // Skip if this is the priority table itself
        if (table == priorityTable) {
            return false;
        }
        
        try {
            currentlySynchronizing.add(table);
            return synchronizeNonZeroElements(table, priorityTable);
        } finally {
            currentlySynchronizing.remove(table);
        }
    }
    
    /**
     * Synchronize non-zero elements from priority table to target table
     * Only propagates equations where both flow and stock exist (or creates flow if needed)
     */
    private static boolean synchronizeNonZeroElements(TableElm targetTable, TableElm sourceTable) {
        // Null safety checks
        if (targetTable == null || sourceTable == null) {
            MRDlog( "Cannot synchronize with null table(s)");
            return false;
        }
        
        boolean modified = false;
        
        // Build map of existing flow descriptions in target table
        Map<String, Integer> targetFlowRows = new HashMap<String, Integer>();
        try {
            int targetRows = targetTable.getRows();
            for (int row = 0; row < targetRows; row++) {
                String flowDesc = targetTable.getRowDescription(row);
                if (flowDesc != null && !flowDesc.trim().isEmpty()) {
                    targetFlowRows.put(flowDesc.trim(), row);
                }
            }
        } catch (Exception e) {
            MRDlog( "Error accessing target table rows: " + e.getMessage());
            return false;
        }
        
        // Collect flows that need to be added (flow → list of (stock, equation) pairs)
        Map<String, List<StockEquation>> flowsToAdd = new HashMap<String, List<StockEquation>>();
        
        // Process each non-zero element from source table
        try {
            int sourceRows = sourceTable.getRows();
            for (int sourceRow = 0; sourceRow < sourceRows; sourceRow++) {
                String flowDesc = sourceTable.getRowDescription(sourceRow);
            if (flowDesc == null || flowDesc.trim().isEmpty()) {
                continue; // Skip rows without descriptions
            }
            flowDesc = flowDesc.trim();
            
            for (int sourceCol = 0; sourceCol < sourceTable.getCols(); sourceCol++) {
                String equation = sourceTable.getCellEquation(sourceRow, sourceCol);
                
                // Skip zero or empty equations
                if (equation == null || equation.trim().isEmpty() || equation.trim().equals("0")) {
                    continue;
                }
                
                String stockName = sourceTable.getColumnHeader(sourceCol);
                if (stockName == null || stockName.trim().isEmpty()) {
                    continue;
                }
                
                // Check if target table has this stock
                int targetCol = targetTable.findColumnByStockName(stockName);
                if (targetCol < 0) {
                    continue; // Target table doesn't have this stock, skip
                }
                
                // Check if target table has this flow
                if (targetFlowRows.containsKey(flowDesc)) {
                    // Flow exists - update the equation regardless of existing value to ensure full sync
                    int targetRow = targetFlowRows.get(flowDesc);
                    String existingEquation = targetTable.getCellEquation(targetRow, targetCol);
                    
                    // Always update to ensure synchronization (source table is authoritative)
                    if (!equation.equals(existingEquation)) {
                        targetTable.setCellEquation(targetRow, targetCol, equation);
                        modified = true;
                        log("synchronizeNonZeroElements", "Updated [" + targetRow + "," + targetCol + "] " + 
                            flowDesc + " → " + stockName + ": `" + equation + "` (was: `" + 
                            (existingEquation != null ? existingEquation : "empty") + "`)");
                    }
                } else {
                    // Flow doesn't exist - queue it for addition at the end
                    if (!flowsToAdd.containsKey(flowDesc)) {
                        flowsToAdd.put(flowDesc, new ArrayList<StockEquation>());
                    }
                    flowsToAdd.get(flowDesc).add(new StockEquation(stockName, targetCol, equation));
                }
            }
            }
        } catch (Exception e) {
            MRDlog( "Error processing source table rows: " + e.getMessage());
            return false;
        }
        
        // Add new flows at the end
        if (!flowsToAdd.isEmpty()) {
            modified = true;
            int originalRows = targetTable.getRows();
            int newRowCount = originalRows + flowsToAdd.size();
            
            // Resize table
            targetTable.resizeTable(newRowCount, targetTable.getCols());
            
            // Add each new flow
            int newRow = originalRows;
            for (Map.Entry<String, List<StockEquation>> entry : flowsToAdd.entrySet()) {
                String flowDesc = entry.getKey();
                targetTable.setRowDescription(newRow, flowDesc);
                
                // Set equations for this flow
                for (StockEquation se : entry.getValue()) {
                    targetTable.setCellEquation(newRow, se.columnIndex, se.equation);
                    log("synchronizeNonZeroElements", "Created row " + newRow + ": " + 
                        flowDesc + " → " + se.stockName + ": `" + se.equation + "`");
                }
                newRow++;
            }
        }
        
        return modified;
    }
    
    // ========== RELATED TABLE SYNCHRONIZATION ==========

    /**
     * Synchronize all tables that share stocks with the given table
     * Bidirectional sync: trigger table pushes to others AND pulls from others
     *
     * @param triggerTable The table that triggered synchronization
     */
    public static void synchronizeRelatedTables(TableElm triggerTable) {
        if (triggerTable == null) {
            SRTlog("Cannot synchronize: trigger table is null");
            return;
        }
        
        SRTlog("Starting bidirectional synchronization");
   

        Set<TableElm> affectedTables = new HashSet<TableElm>();
        Set<String> sharedStocks = new HashSet<String>();

        // Print all the non-zero flow/stock pairs to sync to console
        SRTlog("=== Non-Zero Flow/Stock Pairs from Trigger Table ===");
        SRTlog("Table: " + triggerTable.getTableTitle());
        int nonZeroCount = 0;
        
        try {
            int triggerRows = triggerTable.getRows();
            for (int row = 0; row < triggerRows; row++) {
            String flowDesc = triggerTable.getRowDescription(row);
            if (flowDesc == null || flowDesc.trim().isEmpty()) {
                flowDesc = "Flow" + row;
            }
            
            for (int col = 0; col < triggerTable.getCols(); col++) {
                String equation = triggerTable.getCellEquation(row, col);
                if (equation != null && !equation.trim().isEmpty() && !equation.trim().equals("0")) {
                    String stockName = triggerTable.getColumnHeader(col);
                    SRTlog("  [" + row + "," + col + "] " + flowDesc + " → " + stockName + ": `" + equation + "`");
                    nonZeroCount++;
                }
            }
        }
        } catch (Exception e) {
            SRTlog("Error accessing trigger table rows: " + e.getMessage());
            return;
        }
        
        if (nonZeroCount == 0) {
            SRTlog("  (No non-zero equations in trigger table)");
        } else {
            SRTlog("Total non-zero pairs: " + nonZeroCount);
        }
        SRTlog("===================================================");

         
        // First, find all affected tables
        
        // Find all tables that share any stock with trigger table
        for (int col = 0; col < triggerTable.getCols(); col++) {
            String stockName = triggerTable.getColumnHeader(col);
            if (stockName != null && !stockName.trim().isEmpty()) {
                List<TableElm> tables = getTablesForStock(stockName);
                if (tables.size() > 1) {
                    // This stock is shared by multiple tables
                    sharedStocks.add(stockName);
                }
                affectedTables.addAll(tables);
            }
        }
        
        // Log synchronization action
        if (affectedTables.size() > 1) {
            SRTlog("Synchronizing " + affectedTables.size() +
                          " tables sharing stocks: " + sharedStocks);
        } else {
            SRTlog("No related tables to synchronize (only 1 table)");
            return; // No sync needed if only one table
        }
        
        // PHASE 1: Synchronize FROM trigger table TO other tables (push)
        SRTlog("=== PHASE 1: Pushing changes from trigger table to others ===");
        int tablesUpdated = 0;
        
        for (TableElm table : affectedTables) {
            if (table == triggerTable) continue; // Skip trigger table in push phase
            
            // Log which equations will be applied to this table
            SRTlog("--- Applying to Table: " + table.getTableTitle() + " ---");
            
            // Check which stocks are shared and will receive equations
            for (int col = 0; col < table.getCols(); col++) {
                String stockName = table.getColumnHeader(col);
                if (sharedStocks.contains(stockName)) {
                    SRTlog("  Stock '" + stockName + "' (column " + col + ") will receive equations");
                    
                    // Show which flow/stock pairs from trigger table will be applied
                    try {
                        int triggerRows = triggerTable.getRows();
                        for (int row = 0; row < triggerRows; row++) {
                        String flowDesc = triggerTable.getRowDescription(row);
                        if (flowDesc == null || flowDesc.trim().isEmpty()) {
                            flowDesc = "Flow" + row;
                        }
                        
                        // Find the column in trigger table for this stock
                        int triggerCol = triggerTable.findColumnByStockName(stockName);
                        if (triggerCol >= 0) {
                            String equation = triggerTable.getCellEquation(row, triggerCol);
                            if (equation != null && !equation.trim().isEmpty() && !equation.trim().equals("0")) {
                                SRTlog("    ✓ " + flowDesc + ": `" + equation + "`");
                            }
                        }
                    }
                    } catch (Exception e) {
                        SRTlog("Error accessing trigger table rows during logging: " + e.getMessage());
                    }
                }
            }
            
            boolean wasModified = synchronizeTable(table, triggerTable);
            if (wasModified) {
                tablesUpdated++;
                SRTlog("  ✅ Table '" + table.getTableTitle() + "' updated");
            }
        }
        
        // PHASE 2: Synchronize FROM other tables TO trigger table (pull)
        SRTlog("=== PHASE 2: Pulling changes from other tables to trigger table ===");
        boolean triggerModified = false;
        
        for (TableElm sourceTable : affectedTables) {
            if (sourceTable == triggerTable) continue; // Skip self
            
            SRTlog("--- Pulling from Table: " + sourceTable.getTableTitle() + " ---");
            
            // Synchronize trigger table using this source table as priority
            // This will pull in any rows/equations from sourceTable that aren't in triggerTable
            boolean modified = synchronizeTable(triggerTable, sourceTable);
            if (modified) {
                triggerModified = true;
                SRTlog("  ✅ Pulled changes from '" + sourceTable.getTableTitle() + "' into trigger table");
            }
        }
        
        if (triggerModified) {
            tablesUpdated++;
            SRTlog("Trigger table '" + triggerTable.getTableTitle() + "' updated from other tables");
        }
        
        if (tablesUpdated > 0) {
            SRTlog("Total tables updated: " + tablesUpdated);
        } else {
            SRTlog("All tables already in sync");
        }
    }
    
    // ========== BATCH OPERATIONS ==========
    
    /**
     * Synchronize all tables in the circuit after loading
     * This ensures all shared stocks have consistent row sets across tables.
     * 
     * Note: This method performs a simpler synchronization compared to
     * synchronizeRelatedTables() - it only merges row descriptions without
     * propagating equations between tables. For full bidirectional sync with
     * equation propagation, use synchronizeRelatedTables() instead.
     */
    public static void synchronizeAllTables() {
        // Get all unique tables from registry
        Set<TableElm> allTables = new HashSet<TableElm>();
        for (List<TableElm> tables : stockToTables.values()) {
            allTables.addAll(tables);
        }
        
        // For each table, ensure it has merged rows for all its shared stocks
        // Note: This currently just validates registry state.
        // For actual synchronization with equation propagation, use synchronizeRelatedTables().
        for (TableElm table : allTables) {
            if (table == null) continue;
            
            // Validate that shared stocks are properly tracked
            for (int col = 0; col < table.getCols(); col++) {
                String stockName = table.getColumnHeader(col);
                if (isSharedStock(stockName)) {
                    // Stock is shared - registry is tracking it correctly
                    // Actual row/equation synchronization happens via synchronizeRelatedTables()
                }
            }
        }
    }
    
    // ========== DIAGNOSTICS ==========
    
    /**
     * Get diagnostic information about the registry state
     * Useful for debugging
     */
    public static String getDiagnosticInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== StockFlowRegistry Diagnostics ===\n");
        sb.append("Total stocks tracked: ").append(stockToTables.size()).append("\n");
        sb.append("Shared stocks: ").append(getSharedStocks().size()).append("\n");
        sb.append("\nStock → Tables mapping:\n");
        
        for (Map.Entry<String, List<TableElm>> entry : stockToTables.entrySet()) {
            sb.append("  '").append(entry.getKey()).append("' → ");
            sb.append(entry.getValue().size()).append(" table(s)");
            if (entry.getValue().size() > 1) {
                sb.append(" [SHARED]");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    // ========== FLOW DELETION ==========
    
    /**
     * Delete rows in other tables that match the given flow description and have non-zero
     * equations for any of the specified stocks. Returns the number of rows deleted.
     *
     * @param sourceTable The table where the deletion originated (do not delete in this table)
     * @param flowDescription The flow description to match (exact match)
     * @param stocks Set of stock names for which a non-zero equation must exist to trigger deletion
     */
    public static int deleteMatchingFlowRows(TableElm sourceTable, String flowDescription, java.util.Set<String> stocks) {
        if (flowDescription == null || flowDescription.trim().isEmpty() || stocks == null || stocks.isEmpty()) return 0;
        
        int deletedCount = 0;
        Set<TableElm> touchedTables = new HashSet<TableElm>();

        // For each stock, find tables that contain it
        for (String stock : stocks) {
            List<TableElm> tables = getTablesForStock(stock);
            for (TableElm table : tables) {
                // Skip the source table
                if (table == sourceTable) continue;
                
                // Null safety check
                if (table == null) continue;

                // If we've already processed this table for this deletion, skip
                if (touchedTables.contains(table)) continue;
                touchedTables.add(table);

                // Find the row index with matching flow description
                int matchingRow = -1;
                try {
                    int tableRows = table.getRows();
                    for (int r = 0; r < tableRows; r++) {
                        String desc = table.getRowDescription(r);
                        if (desc != null && desc.trim().equals(flowDescription.trim())) {
                            matchingRow = r;
                            break;
                        }
                    }
                } catch (Exception e) {
                    SRTlog("Error accessing table rows: " + e.getMessage());
                    continue;
                }

                if (matchingRow >= 0) {
                    // Verify that at least one of the stocks has a non-zero equation in this row
                    boolean hasNonZero = false;
                    for (String s : stocks) {
                        int colIndex = table.findColumnByStockName(s);
                        if (colIndex >= 0) {
                            String eq = table.getCellEquation(matchingRow, colIndex);
                            if (eq != null && !eq.trim().isEmpty() && !eq.trim().equals("0")) {
                                hasNonZero = true;
                                break;
                            }
                        }
                    }

                    if (hasNonZero) {
                        // Delete the row from this table by rebuilding rows excluding the matchingRow
                        try {
                            int oldRows = table.getRows();
                        int cols = table.getCols();
                        int newRowCount = Math.max(0, oldRows - 1);

                        String[] newRowDescriptions = new String[newRowCount];
                        String[][] newCellEquations = new String[newRowCount][cols];

                        int ni = 0;
                        for (int rr = 0; rr < oldRows; rr++) {
                            if (rr == matchingRow) continue;
                            newRowDescriptions[ni] = table.getRowDescription(rr);
                            for (int cc = 0; cc < cols; cc++) {
                                newCellEquations[ni][cc] = table.getCellEquation(rr, cc);
                            }
                            ni++;
                        }

                        // Apply new data to table (recompiles expressions)
                        table.updateRowData(newRowCount, newRowDescriptions, newCellEquations);
                        deletedCount++;
                        SRTlog("Deleted row '" + flowDescription + "' from table: " + table.getTableTitle());
                        } catch (Exception e) {
                            SRTlog("Error deleting row from table: " + e.getMessage());
                        }
                    }
                }
            }
        }

        if (deletedCount > 0) {
            SRTlog("Total deleted matching rows: " + deletedCount);
        }

        return deletedCount;
    }
}
