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

package com.lushprojects.circuitjs1.client.elements.economics;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;
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
    
    /**
     * Simple logging - just logs the message with method name prefix
     * For line numbers, manually include them in the message string
     * Example: log("methodName", "Starting sync"); 
     */
    private static void log(String methodName, String message) {
    }
    
    private static void MRDlog(String message) {
    }
    
    private static void SRTlog(String message) {
    }
    
    // ========== REGISTRY DATA STRUCTURES ==========
    
    // Map: stock name → list of registered table views
    private static Map<String, List<StockTableView>> stockToTables = new HashMap<String, List<StockTableView>>();
    
    // Cache of merged rows per stock (invalidated on changes)
    private static Map<String, LinkedHashSet<String>> mergedRowsCache = new HashMap<String, LinkedHashSet<String>>();
    
    // Synchronization guard to prevent infinite recursion
    private static Set<TableElm> currentlySynchronizing = new HashSet<TableElm>();

    private static TableElm asTableElm(StockTableView table) {
        if (table instanceof TableElm) {
            return (TableElm) table;
        }
        return null;
    }
    
    /**
     * Register a table's stock (column) for synchronization tracking
     */
    public static void registerStock(String stockName, StockTableView table) {
        if (stockName == null || stockName.trim().isEmpty()) return;
        
        if (!stockToTables.containsKey(stockName)) {
            stockToTables.put(stockName, new ArrayList<StockTableView>());
        }
        List<StockTableView> tables = stockToTables.get(stockName);
        if (!tables.contains(table)) {
            tables.add(table);
            invalidateCache(stockName); // Cache is now stale
        }
    }
    
    /**
     * Unregister a table (e.g., when deleted or stock name changed)
     */
    public static void unregisterStock(String stockName, StockTableView table) {
        if (stockToTables.containsKey(stockName)) {
            stockToTables.get(stockName).remove(table);
            invalidateCache(stockName);
        }
    }
    
    /**
     * Unregister all stocks for a table (e.g., on deletion)
     */
    public static void unregisterAllStocks(StockTableView table) {
        List<String> stocksToRemove = new ArrayList<String>();
        for (Map.Entry<String, List<StockTableView>> entry : stockToTables.entrySet()) {
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
    public static List<StockTableView> getTablesForStock(String stockName) {
        List<StockTableView> tables = stockToTables.get(stockName);
        return tables != null ? tables : new ArrayList<StockTableView>();
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
        for (Map.Entry<String, List<StockTableView>> entry : stockToTables.entrySet()) {
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
        List<StockTableView> tables = stockToTables.get(stockName);
        return tables != null && tables.size() > 1;
    }
    
    /**
     * Check if a name is registered as a stock (in any table)
     */
    public static boolean isStock(String name) {
        return stockToTables.containsKey(name);
    }
    
    /**
     * Get all registered stock variable names
     */
    public static Set<String> getAllStockNames() {
        return new HashSet<String>(stockToTables.keySet());
    }
    
    /**
     * Get all output names from EquationTableElm instances
     * These are computed variables that can be referenced by other elements
     */
    public static Set<String> getAllEquationOutputNames() {
        Set<String> outputs = new HashSet<String>();
        CirSim sim = CirSim.getInstance();
        if (sim == null || sim.elmList == null) return outputs;
        
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.elementAt(i);
            if (elm instanceof EquationTableElm) {
                EquationTableElm eqn = (EquationTableElm) elm;
                String[] names = eqn.getOutputNames();
                for (String name : names) {
                    if (name != null && !name.trim().isEmpty()) {
                        String trimmed = name.trim();
                        if (!EquationTableElm.isCommentRowName(trimmed)) {
                            outputs.add(trimmed);
                        }
                    }
                }
            }
        }
        return outputs;
    }
    
    /**
     * Extract all variable names (identifiers) from all table cell equations
     * Returns variables that appear in any cell equation across all tables
     */
    public static Set<String> getAllCellEquationVariables() {
        Set<String> variables = new HashSet<String>();
        
        // Get all tables from circuit via CirSim
        CirSim sim = CirSim.getInstance();
        if (sim == null || sim.elmList == null) {
            return variables;
        }
        
        // Iterate through all circuit elements to find TableElm instances
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.elementAt(i);
            if (elm instanceof TableElm) {
                TableElm table = (TableElm) elm;
                
                // Get dimensions
                int rows = table.getRows();
                int cols = table.getCols();
                
                // Extract variables from each cell equation
                for (int row = 0; row < rows; row++) {
                    for (int col = 0; col < cols; col++) {
                        String equation = table.getCellEquation(row, col);
                        if (equation != null && !equation.trim().isEmpty()) {
                            extractVariablesFromEquation(equation, variables);
                        }
                    }
                }
            }
        }
        
        return variables;
    }
    
    /**
     * Extract variable names from a single equation string
     * Adds found variables to the provided set
     */
    private static void extractVariablesFromEquation(String equation, Set<String> variables) {
        if (equation == null || equation.trim().isEmpty()) {
            return;
        }
        
        // Use a more careful tokenizer that preserves Greek symbols (backslash + letters)
        int i = 0;
        while (i < equation.length()) {
            char c = equation.charAt(i);
            
            // Skip whitespace and operators
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' ||
                c == '+' || c == '-' || c == '*' || c == '/' || c == '(' || c == ')' ||
                c == ',' || c == '<' || c == '>' || c == '=' || c == '!' || c == '&' || c == '|') {
                i++;
                continue;
            }
            
            // Check for Greek symbol (backslash followed by letters, then optionally subscripts/superscripts)
            if (c == '\\' && i + 1 < equation.length()) {
                char next = equation.charAt(i + 1);
                if ((next >= 'a' && next <= 'z') || (next >= 'A' && next <= 'Z')) {
                    // Extract the full Greek symbol name including subscripts/superscripts
                    int start = i;
                    i++; // skip backslash
                    // First, collect the Greek letter name (letters only)
                    while (i < equation.length()) {
                        char ch = equation.charAt(i);
                        if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                            i++;
                        } else {
                            break;
                        }
                    }
                    // Now continue collecting subscripts, superscripts, brackets, numbers
                    // Include backslashes for nested Greek symbols inside braces/brackets
                    while (i < equation.length()) {
                        char ch = equation.charAt(i);
                        if ((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'z') || 
                            (ch >= 'A' && ch <= 'Z') || ch == '_' || ch == '^' || 
                            ch == '{' || ch == '}' || ch == '[' || ch == ']' || ch == '\\') {
                            i++;
                        } else {
                            break;
                        }
                    }
                    String token = equation.substring(start, i);
                    if (!isKeyword(token)) {
                        variables.add(token);
                    }
                    continue;
                }
            }
            
            // Check for regular identifier (letter or underscore, then letters/numbers/underscores/brackets/carets)
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
                int start = i;
                while (i < equation.length()) {
                    char ch = equation.charAt(i);
                    if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') ||
                        (ch >= '0' && ch <= '9') || ch == '_' || ch == '^' || 
                        ch == '{' || ch == '}' || ch == '[' || ch == ']') {
                        i++;
                    } else {
                        break;
                    }
                }
                String token = equation.substring(start, i);
                if (!isKeyword(token)) {
                    variables.add(token);
                }
                continue;
            }
            
            // Skip numbers (and handle scientific notation)
            if (c >= '0' && c <= '9') {
                while (i < equation.length()) {
                    char ch = equation.charAt(i);
                    if ((ch >= '0' && ch <= '9') || ch == '.' || ch == 'e' || ch == 'E') {
                        i++;
                        // Handle sign after 'e' in scientific notation (e.g., 1e-3, 2E+5)
                        if ((ch == 'e' || ch == 'E') && i < equation.length()) {
                            char nextCh = equation.charAt(i);
                            if (nextCh == '+' || nextCh == '-') {
                                i++; // skip the sign
                            }
                        }
                    } else {
                        break;
                    }
                }
                continue;
            }
            
            // Skip any other character
            i++;
        }
    }
    
    /**
     * Check if a token is a valid identifier
     */
    private static boolean isValidIdentifier(String token) {
        if (token == null || token.length() == 0) {
            return false;
        }
        
        // Check for Greek symbols (start with backslash followed by letters, then optionally subscripts/superscripts)
        // e.g., \alpha, \beta_{workers}^B, \gamma^{2}, \alpha^{\beta}, etc.
        if (token.charAt(0) == '\\' && token.length() > 1) {
            // Must have at least one letter after the backslash
            char second = token.charAt(1);
            if (!((second >= 'a' && second <= 'z') || (second >= 'A' && second <= 'Z'))) {
                return false;
            }
            // After the Greek name (letters), we can have numbers, underscores, brackets, carets, backslashes (for nested Greek)
            boolean inGreekName = true;
            for (int i = 2; i < token.length(); i++) {
                char c = token.charAt(i);
                if (inGreekName && ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                    // Still in the Greek letter name part
                    continue;
                } else if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || 
                           (c >= 'A' && c <= 'Z') || c == '_' || c == '^' || 
                           c == '{' || c == '}' || c == '[' || c == ']' || c == '\\') {
                    // Subscripts/superscripts after the Greek name (including nested Greek symbols)
                    inGreekName = false;
                    continue;
                } else {
                    // Invalid character
                    return false;
                }
            }
            return true;
        }
        
        // Standard identifier: first character must be letter or underscore
        char first = token.charAt(0);
        if (!((first >= 'a' && first <= 'z') || (first >= 'A' && first <= 'Z') || first == '_')) {
            return false;
        }
        
        // Remaining characters must be letters, numbers, underscores, brackets, or carets
        for (int i = 1; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || 
                  (c >= '0' && c <= '9') || c == '_' || c == '^' || 
                  c == '{' || c == '}' || c == '[' || c == ']')) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if a token is a reserved keyword/function
     */
    private static boolean isKeyword(String token) {
        String lower = token.toLowerCase();
        return lower.equals("sin") || lower.equals("cos") || lower.equals("tan") ||
               lower.equals("abs") || lower.equals("exp") || lower.equals("log") ||
               lower.equals("sqrt") || lower.equals("min") || lower.equals("max") ||
               lower.equals("pow") || lower.equals("floor") || lower.equals("ceil") ||
               lower.equals("round") || lower.equals("random") || lower.equals("if") ||
               lower.equals("pwl") || lower.equals("mod") || lower.equals("step") ||
               lower.equals("select") || lower.equals("clamp") || lower.equals("pwr") ||
               lower.equals("pwrs") || lower.equals("pi") || lower.equals("e") ||
               lower.equals("t") || lower.equals("dt") || lower.equals("true") ||
               lower.equals("false") || lower.startsWith("last");
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
    private static LinkedHashSet<String> getMergedRowDescriptions(String stockName, StockTableView priorityTable) {
        // Can't use cache if we have a priority table (order matters)
        if (priorityTable == null && mergedRowsCache.containsKey(stockName)) {
            LinkedHashSet<String> cached = mergedRowsCache.get(stockName);
            MRDlog( "Using cached rows for stock '" + stockName + "': " + cached);
            return new LinkedHashSet<String>(cached);
        }
        
        LinkedHashSet<String> mergedRows = new LinkedHashSet<String>();
        List<StockTableView> tables = getTablesForStock(stockName);
        
        MRDlog( "Merging rows for stock '" + stockName + "' from " + tables.size() + " table(s)");
        
        // If we have a priority table, collect its rows first to establish order
        if (priorityTable != null && tables.contains(priorityTable)) {
            MRDlog( "Priority table: " + priorityTable.getTableTitle() + " (establishes row order)");
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
        
        // Now collect rows from all other tables (new rows will be added at end)
        for (StockTableView table : tables) {
            // Skip priority table since we already processed it
            if (table == priorityTable) continue;
            
            // Null safety check for table
            if (table == null) {
                MRDlog( "Skipping null table in tables list");
                continue;
            }
            
            MRDlog( "Processing table: " + table.getTableTitle());
            int rowsAdded = 0;
            
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
    private static boolean synchronizeTable(TableElm table, TableElm priorityTable) {
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
            return synchronizeElements(table, priorityTable);
        } finally {
            currentlySynchronizing.remove(table);
        }
    }
    
    /**
     * Synchronize elements from priority table to target table
     * Propagates ALL equations (including deletions/empty values) where both flow and stock exist (or creates flow if needed)
     */
    private static boolean synchronizeElements(TableContentView targetTable, TableContentView sourceTable) {
        if (targetTable == null || sourceTable == null) {
            MRDlog("Cannot synchronize with null table(s)");
            return false;
        }

        List<SyncPatch> patches = computeSyncPatches(sourceTable, targetTable);
        if (patches.isEmpty()) {
            return false;
        }
        TableElm targetElm = asTableElm(targetTable);
        if (targetElm == null) {
            MRDlog("Cannot apply sync patches to non-TableElm target");
            return false;
        }
        applySyncPatches(patches, targetElm);
        return true;
    }

    public static List<SyncPatch> computeSyncPatches(TableContentView sourceTable, TableContentView targetTable) {
        List<SyncPatch> patches = new ArrayList<SyncPatch>();
        if (sourceTable == null || targetTable == null) {
            return patches;
        }

        Map<String, Integer> targetFlowRows = new HashMap<String, Integer>();
        int targetRows = targetTable.getRows();
        for (int row = 0; row < targetRows; row++) {
            String flowDesc = targetTable.getRowDescription(row);
            if (flowDesc != null && !flowDesc.trim().isEmpty()) {
                targetFlowRows.put(flowDesc.trim(), row);
            }
        }

        Map<String, Map<Integer, String>> flowsToAdd = new LinkedHashMap<String, Map<Integer, String>>();

        int sourceRows = sourceTable.getRows();
        for (int sourceRow = 0; sourceRow < sourceRows; sourceRow++) {
            String flowDesc = sourceTable.getRowDescription(sourceRow);
            if (flowDesc == null || flowDesc.trim().isEmpty()) {
                continue;
            }
            flowDesc = flowDesc.trim();

            int sourceCols = sourceTable.getCols();
            for (int sourceCol = 0; sourceCol < sourceCols; sourceCol++) {
                if (sourceCol == sourceCols - 1 && sourceCols >= 4) {
                    continue;
                }

                String stockName = sourceTable.getColumnHeader(sourceCol);
                if (stockName == null || stockName.trim().isEmpty()) {
                    continue;
                }

                int targetCol = targetTable.findColumnByStockName(stockName);
                if (targetCol < 0) {
                    continue;
                }

                int targetCols = targetTable.getCols();
                if (targetCol == targetCols - 1 && targetCols >= 4) {
                    continue;
                }

                String normalizedEquation = normalizeEquation(sourceTable.getCellEquation(sourceRow, sourceCol));

                Integer existingRow = targetFlowRows.get(flowDesc);
                if (existingRow != null) {
                    String normalizedExisting = normalizeEquation(targetTable.getCellEquation(existingRow.intValue(), targetCol));
                    if (!normalizedEquation.equals(normalizedExisting)) {
                        patches.add(SyncPatch.setCellEquation(existingRow.intValue(), targetCol, normalizedEquation));
                    }
                } else if (!normalizedEquation.isEmpty()) {
                    Map<Integer, String> rowEquations = flowsToAdd.get(flowDesc);
                    if (rowEquations == null) {
                        rowEquations = new LinkedHashMap<Integer, String>();
                        flowsToAdd.put(flowDesc, rowEquations);
                    }
                    rowEquations.put(Integer.valueOf(targetCol), normalizedEquation);
                }
            }
        }

        for (Map.Entry<String, Map<Integer, String>> entry : flowsToAdd.entrySet()) {
            patches.add(SyncPatch.addRow(entry.getKey(), entry.getValue()));
        }

        int sourceCols = sourceTable.getCols();
        for (int sourceCol = 0; sourceCol < sourceCols; sourceCol++) {
            if (sourceCol == sourceCols - 1 && sourceCols >= 4) {
                continue;
            }

            String stockName = sourceTable.getColumnHeader(sourceCol);
            if (stockName == null || stockName.trim().isEmpty()) {
                continue;
            }

            int targetCol = targetTable.findColumnByStockName(stockName);
            if (targetCol < 0) {
                continue;
            }

            int targetCols = targetTable.getCols();
            if (targetCol == targetCols - 1 && targetCols >= 4) {
                continue;
            }

            double sourceInitialValue = sourceTable.getInitialValue(sourceCol);
            double targetInitialValue = targetTable.getInitialValue(targetCol);
            if (Math.abs(sourceInitialValue - targetInitialValue) > 1e-10) {
                patches.add(SyncPatch.setInitialValue(targetCol, sourceInitialValue));
            }
        }

        return patches;
    }

    private static void applySyncPatches(List<SyncPatch> patches, TableElm targetTable) {
        if (patches == null || patches.isEmpty() || targetTable == null) {
            return;
        }

        ArrayList<SyncPatch> addRowPatches = new ArrayList<SyncPatch>();
        for (SyncPatch patch : patches) {
            if (patch.kind == SyncPatch.Kind.ADD_ROW) {
                addRowPatches.add(patch);
                continue;
            }

            if (patch.kind == SyncPatch.Kind.SET_CELL_EQUATION) {
                String previous = normalizeEquation(targetTable.getCellEquation(patch.row, patch.col));
                targetTable.setCellEquation(patch.row, patch.col, patch.equation);
                String flowDesc = targetTable.getRowDescription(patch.row);
                String stockName = targetTable.getColumnHeader(patch.col);
                log("synchronizeElements", "Updated [" + patch.row + "," + patch.col + "] " +
                    flowDesc + " → " + stockName + ": `" + patch.equation + "` (was: `" + previous + "`)");
                continue;
            }

            if (patch.kind == SyncPatch.Kind.SET_INITIAL_VALUE) {
                String stockName = targetTable.getColumnHeader(patch.col);
                double previous = targetTable.getInitialValue(patch.col);
                targetTable.setInitialConditionValue(patch.col, patch.initialValue);
                log("synchronizeElements", "Synced initial value for stock '" + stockName +
                    "': " + patch.initialValue + " (was: " + previous + ")");
            }
        }

        if (!addRowPatches.isEmpty()) {
            int originalRows = targetTable.getRows();
            targetTable.resizeTable(originalRows + addRowPatches.size(), targetTable.getCols());
            int newRow = originalRows;
            for (SyncPatch patch : addRowPatches) {
                targetTable.setRowDescription(newRow, patch.flowDesc);
                for (Map.Entry<Integer, String> eqEntry : patch.rowEquations.entrySet()) {
                    int col = eqEntry.getKey().intValue();
                    String equation = eqEntry.getValue();
                    targetTable.setCellEquation(newRow, col, equation);
                    String stockName = targetTable.getColumnHeader(col);
                    log("synchronizeElements", "Created row " + newRow + ": " +
                        patch.flowDesc + " → " + stockName + ": `" + equation + "`");
                }
                newRow++;
            }
        }
    }

    private static String normalizeEquation(String equation) {
        if (equation == null) {
            return "";
        }
        String trimmed = equation.trim();
        if (trimmed.isEmpty() || "0".equals(trimmed)) {
            return "";
        }
        return trimmed;
    }
    
    // ========== RELATED TABLE SYNCHRONIZATION ==========

    /**
     * Synchronize all tables that share stocks with the given table
     * Unidirectional master-to-follower sync: Only master tables push changes to non-master tables.
     * Non-master tables are locked and cannot be edited, so no pull phase is needed.
     *
     * @param triggerTable The table that triggered synchronization
     */
    public static void synchronizeRelatedTables(TableElm triggerTable) {
        if (triggerTable == null) {
            SRTlog("Cannot synchronize: trigger table is null");
            return;
        }
        
        SRTlog("Starting master-to-follower synchronization");
   

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
                List<StockTableView> tables = getTablesForStock(stockName);
                if (tables.size() > 1) {
                    // This stock is shared by multiple tables
                    sharedStocks.add(stockName);
                }
                for (StockTableView tableView : tables) {
                    TableElm table = asTableElm(tableView);
                    if (table != null) {
                        affectedTables.add(table);
                    }
                }
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
        
        // Sync FROM master tables TO follower tables
        // For each shared stock, find the master table and push its equations to followers
        SRTlog("=== Syncing from master tables to followers ===");
        int tablesUpdated = 0;
        
        // Build map of stock -> master table for efficient lookup
        Map<String, TableElm> stockMasters = new HashMap<String, TableElm>();
        for (String stockName : sharedStocks) {
            TableElm masterTable = ComputedValues.getMasterTable(stockName);
            if (masterTable != null) {
                stockMasters.put(stockName, masterTable);
                SRTlog("  Stock '" + stockName + "' is mastered by: " + masterTable.getTableTitle());
            }
        }
        
        // For each affected table, sync from its master tables
        for (TableElm followerTable : affectedTables) {
            // Collect all master tables that this follower needs to sync from
            Set<TableElm> masterTables = new HashSet<TableElm>();
            
            for (int col = 0; col < followerTable.getCols(); col++) {
                String stockName = followerTable.getColumnHeader(col);
                if (stockName == null || stockName.trim().isEmpty()) continue;
                
                TableElm masterTable = stockMasters.get(stockName);
                if (masterTable != null && masterTable != followerTable) {
                    masterTables.add(masterTable);
                }
            }
            
            // Skip if this table is master for all its stocks (nothing to sync)
            if (masterTables.isEmpty()) {
                SRTlog("  Table '" + followerTable.getTableTitle() + "' is master for all its stocks - no sync needed");
                continue;
            }
            
            SRTlog("--- Syncing to follower table: " + followerTable.getTableTitle() + " ---");
            
            // Sync from each master table
            boolean wasModified = false;
            for (TableElm masterTable : masterTables) {
                SRTlog("  Syncing from master: " + masterTable.getTableTitle());
                boolean modified = synchronizeTable(followerTable, masterTable);
                if (modified) {
                    wasModified = true;
                }
            }
            
            if (wasModified) {
                tablesUpdated++;
                SRTlog("  ✅ Table '" + followerTable.getTableTitle() + "' updated from master table(s)");
            }
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
     * propagating equations between tables. For full master-to-follower sync with
     * equation propagation, use synchronizeRelatedTables() instead.
     */
    public static void synchronizeAllTables() {
        // Get all unique tables from registry
        Set<TableElm> allTables = new HashSet<TableElm>();
        for (List<StockTableView> tables : stockToTables.values()) {
            for (StockTableView tableView : tables) {
                TableElm table = asTableElm(tableView);
                if (table != null) {
                    allTables.add(table);
                }
            }
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
        
        for (Map.Entry<String, List<StockTableView>> entry : stockToTables.entrySet()) {
            sb.append("  '").append(entry.getKey()).append("' → ");
            sb.append(entry.getValue().size()).append(" table(s)");
            if (entry.getValue().size() > 1) {
                sb.append(" [SHARED]");
            }
            sb.append("\n");
            
            // Show table details with object IDs
            for (StockTableView table : entry.getValue()) {
                if (table == null) {
                    sb.append("    - null\n");
                } else {
                    sb.append("    - ").append(table.getTableTitle())
                      .append(" (Object ID: #").append(System.identityHashCode(table)).append(")\n");
                }
            }
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
            List<StockTableView> tables = getTablesForStock(stock);
            for (StockTableView tableView : tables) {
                TableElm table = asTableElm(tableView);
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

    public static class TestSupport {
        public static void seedStockEntries(String stockName, int entries) {
            ArrayList<StockTableView> tables = new ArrayList<StockTableView>();
            for (int i = 0; i < entries; i++) {
                tables.add(null);
            }
            stockToTables.put(stockName, tables);
        }

        public static void seedMergedRowsCache(String stockName, LinkedHashSet<String> rows) {
            mergedRowsCache.put(stockName, rows);
        }

        public static boolean isMergedRowsCached(String stockName) {
            return mergedRowsCache.containsKey(stockName);
        }
    }
}
