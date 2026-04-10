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

package com.lushprojects.circuitjs1.client.elements.annotation;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.elements.economics.TableColumn;
import com.lushprojects.circuitjs1.client.elements.economics.TableColumn.ColumnType;
import com.lushprojects.circuitjs1.client.elements.economics.TableElm;
import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramModel.*;

import java.util.ArrayList;
import java.util.Vector;

/**
 * Parser for PlantUML sequence diagram syntax and table-to-diagram generation.
 * This class is stateless and provides static parsing methods for testability.
 */
public class SequenceDiagramParser {
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONSTANTS
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Prefix indicating auto-generation from a table source */
    public static final String AUTO_SOURCE_PREFIX = "source:";
    
    /** Threshold for considering transaction values as zero */
    private static final double ZERO_THRESHOLD = 1e-10;
    
    // ══════════════════════════════════════════════════════════════════════════
    // SOURCE RESOLUTION
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Result of source resolution containing the rendered source and table reference.
     */
    public static class SourceResolution {
        public String renderedSource;
        public String sourceTableName;
        public TableElm sourceTable;
    }
    
    /**
     * Resolves the PlantUML source, handling table expansion if needed.
     * 
     * @param plantUmlSource User-entered source (may contain source: directive)
     * @param sim            Circuit simulator for table lookup
     * @param defaultDiagram Fallback diagram if source is empty
     * @return SourceResolution with rendered source and table info
     */
    public static SourceResolution resolveSource(String plantUmlSource, CirSim sim, String defaultDiagram) {
        SourceResolution result = new SourceResolution();
        
        result.sourceTableName = extractSourceTableName(plantUmlSource);
        
        // No source directive - use raw source
        if (result.sourceTableName == null) {
            result.sourceTable = null;
            result.renderedSource = plantUmlSource;
        } else {
            // Look up the referenced table
            result.sourceTable = findSourceTable(result.sourceTableName, sim);
            
            result.renderedSource = (result.sourceTable != null) 
                ? buildDiagramFromSourceTable(result.sourceTable)
                : buildMissingSourceDiagram(result.sourceTableName);
        }
        
        // Fallback to default if source is empty
        if (result.renderedSource == null || result.renderedSource.isEmpty()) {
            result.renderedSource = defaultDiagram;
        }
        
        return result;
    }
    
    /**
     * Extracts table name from "source:TableName" directive in source text.
     * 
     * @param sourceText PlantUML source text to scan
     * @return Table name if directive found, null otherwise
     */
    public static String extractSourceTableName(String sourceText) {
        if (sourceText == null || sourceText.isEmpty()) {
            return null;
        }
        
        for (String rawLine : sourceText.split("\n")) {
            String line = (rawLine != null) ? rawLine.trim() : "";
            
            // Case-insensitive prefix match
            if (!line.regionMatches(true, 0, AUTO_SOURCE_PREFIX, 0, AUTO_SOURCE_PREFIX.length())) {
                continue;
            }
            
            // Extract value after prefix, strip comments
            String value = line.substring(AUTO_SOURCE_PREFIX.length()).trim();
            int commentIdx = value.indexOf('#');
            if (commentIdx >= 0) {
                value = value.substring(0, commentIdx).trim();
            }
            return value;
        }
        return null;
    }
    
    /**
     * Finds a TableElm by name, or auto-selects the first compatible table.
     * 
     * @param requestedName Table name to find, or empty/null for auto-select
     * @param sim           Circuit simulator
     * @return Matching TableElm or null if not found
     */
    public static TableElm findSourceTable(String requestedName, CirSim sim) {
        if (sim == null || sim.elmList == null) {
            return null;
        }

        boolean autoSelect = (requestedName == null || requestedName.isEmpty());
        
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            if (!(elm instanceof TableElm)) continue;
            
            TableElm table = (TableElm) elm;
            
            if (autoSelect) {
                // Auto-select: return first table with diagram-participant columns
                if (hasAnySectorColumn(table)) {
                    return table;
                }
            } else {
                // Name match: return table with matching title
                if (requestedName.equals(table.tableTitle)) {
                    return table;
                }
            }
        }
        return null;
    }
    
    /**
     * Checks if table has at least one non-computed column type.
     * Required for auto-selection compatibility with SFC tables.
     */
    public static boolean hasAnySectorColumn(TableElm table) {
        if (table == null || table.columns == null) {
            return false;
        }
        for (int i = 0; i < table.columns.size(); i++) {
            TableColumn col = table.columns.get(i);
            if (isDiagramColumn(col)) {
                return true;
            }
        }
        return false;
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // TABLE-TO-DIAGRAM GENERATION
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
    * Generates PlantUML diagram source from a TableElm.
    * Creates participants from non-computed columns and messages from row transactions.
     * 
     * @param table Source table to extract diagram from
     * @return Generated PlantUML source
     */
    public static String buildDiagramFromSourceTable(TableElm table) {
        String title = (table.tableTitle != null && !table.tableTitle.isEmpty())
            ? table.tableTitle 
            : "Sequence Diagram";
        
        ArrayList<String> sectorNames = collectSectorNames(table);
        StringBuilder sb = new StringBuilder();
        
        // Header
        sb.append("@startuml\n");
        sb.append("title ").append(sanitizeText(title)).append("\n");

        // Handle empty sectors case
        if (sectorNames.isEmpty()) {
            sb.append("note across\nNo sectors defined\nend note\n");
            sb.append("@end");
            return sb.toString();
        }

        // Declare participants
        for (String name : sectorNames) {
            sb.append("participant ").append(sanitizeText(name)).append("\n");
        }

        // Generate messages from table rows
        int messageCount = 0;
        for (int row = 0; row < table.rows; row++) {
            FlowEndpoints endpoints = extractFlowEndpoints(table, row);
            
            // Skip rows without valid source/target pair
            if (endpoints == null || endpoints.sourceSector == null || endpoints.targetSector == null) {
                continue;
            }

            // Build message line: Source -> Target : Label (value)
            String rowLabel = getRowLabel(table, row);
            sb.append(sanitizeText(endpoints.sourceSector))
              .append(" -> ")
              .append(sanitizeText(endpoints.targetSector))
              .append(" : ")
              .append(sanitizeText(rowLabel));
            
            if (endpoints.hasFlowValue) {
                sb.append("\\n(").append(formatFlowValue(endpoints.flowValue)).append(")");
            }
            sb.append("\n");
            messageCount++;
        }

        // Note if no transactions found
        if (messageCount == 0) {
            sb.append("note across\nNo paired source/target flows found\nend note\n");
        }

        sb.append("@end");
        return sb.toString();
    }
    
    /**
     * Generates error diagram when source table is not found.
     */
    public static String buildMissingSourceDiagram(String requestedName) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title Sequence Diagram\n");
        sb.append("note across\n");
        
        if (requestedName == null || requestedName.isEmpty()) {
            sb.append("No compatible source table found\n");
        } else {
            sb.append("Source table not found:\\n");
            sb.append(sanitizeText(requestedName)).append("\n");
        }
        
        sb.append("end note\n");
        sb.append("@end");
        return sb.toString();
    }
    
    /**
     * Collects unique non-computed column names from table.
     */
    public static ArrayList<String> collectSectorNames(TableElm table) {
        ArrayList<String> names = new ArrayList<String>();
        if (table == null || table.columns == null) {
            return names;
        }
        
        for (int col = 0; col < table.columns.size(); col++) {
            TableColumn column = table.columns.get(col);
            if (!isDiagramColumn(column)) {
                continue;
            }
            
            String name = column.getStockName();
            if (name != null && !name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }
    
    /**
    * Extracts source/target sectors from a table row.
    * Source = column with negative value (outflow)
    * Target = column with positive value (inflow)
    *
    * <p>Direction precedence is numeric-first: if both a nonzero numeric source and target are
    * available, those decide the arrow direction. Equation sign hints are only used as a
    * fallback when the evaluated row is currently zero or incomplete.
     * 
     * @param table Table to analyze
     * @param row   Row index
     * @return FlowEndpoints with source, target, and magnitude
     */
    public static FlowEndpoints extractFlowEndpoints(TableElm table, int row) {
        if (table == null || table.columns == null) {
            return null;
        }
        
        FlowEndpoints endpoints = new FlowEndpoints();
        String equationSource = null;
        String equationTarget = null;
        String numericSource = null;
        String numericTarget = null;
        double numericMagnitude = 0;
        
        for (int col = 0; col < table.columns.size(); col++) {
            TableColumn column = table.columns.get(col);
            if (!isDiagramColumn(column)) {
                continue;
            }

            int signHint = getEquationSignHint(table.getCellEquation(row, col));
            if (signHint < 0 && equationSource == null) {
                equationSource = column.getStockName();
                endpoints.hasFlowValue = true;
            } else if (signHint > 0 && equationTarget == null) {
                equationTarget = column.getStockName();
                endpoints.hasFlowValue = true;
            }

            double value = getTransactionValue(table, row, col);
            if (Math.abs(value) >= ZERO_THRESHOLD) {
                endpoints.hasFlowValue = true;
                if (value < 0 && numericSource == null) {
                    numericSource = column.getStockName();
                    numericMagnitude = Math.abs(value);
                } else if (value > 0 && numericTarget == null) {
                    numericTarget = column.getStockName();
                    if (numericMagnitude == 0) {
                        numericMagnitude = value;
                    }
                }
            }
        }

        if (numericSource != null && numericTarget != null) {
            endpoints.sourceSector = numericSource;
            endpoints.targetSector = numericTarget;
            endpoints.flowValue = numericMagnitude;
            return endpoints;
        }

        if (equationSource != null && equationTarget != null) {
            endpoints.sourceSector = equationSource;
            endpoints.targetSector = equationTarget;
            endpoints.flowValue = numericMagnitude;
            return endpoints;
        }

        endpoints.sourceSector = numericSource != null ? numericSource : equationSource;
        endpoints.targetSector = numericTarget != null ? numericTarget : equationTarget;
        endpoints.flowValue = numericMagnitude;
        if (endpoints.sourceSector == null && equationSource != null) {
            endpoints.sourceSector = equationSource;
        }
        if (endpoints.targetSector == null && equationTarget != null) {
            endpoints.targetSector = equationTarget;
        }
        return endpoints;
    }

    private static boolean isDiagramColumn(TableColumn column) {
        return column != null && column.getType() != ColumnType.COMPUTED;
    }

    /**
     * Infer source/target direction from the original equation string when the
     * evaluated value is currently zero or unavailable.
     */
    static int getEquationSignHint(String equation) {
        if (equation == null) {
            return 0;
        }
        String trimmed = equation.trim();
        if (trimmed.isEmpty() || "0".equals(trimmed)) {
            return 0;
        }

        try {
            double literal = Double.parseDouble(trimmed);
            if (Math.abs(literal) < ZERO_THRESHOLD) {
                return 0;
            }
            return literal < 0 ? -1 : 1;
        } catch (NumberFormatException ignored) {
            // Non-literal expressions fall through to prefix-based sign inference.
        }

        char firstChar = trimmed.charAt(0);
        if (firstChar == '-') {
            return -1;
        }
        if (firstChar == '+') {
            return 1;
        }
        return 1;
    }
    
    /**
     * Gets transaction value from the source table, preferring the same cached
     * display value the table renderer uses so the sequence diagram matches it.
     */
    public static double getTransactionValue(TableElm table, int row, int col) {
        return table != null ? table.getDisplayedTransactionValue(row, col) : 0.0;
    }
    
    /**
     * Gets display label for table row, falling back to generic label.
     */
    public static String getRowLabel(TableElm table, int row) {
        if (table != null && table.rowDescriptions != null 
            && row >= 0 && row < table.rowDescriptions.length) {
            String label = table.rowDescriptions[row];
            if (label != null && !label.isEmpty()) {
                return label;
            }
        }
        return "Transaction " + (row + 1);
    }
    
    /**
     * Formats flow value for display using standard number formatting.
     */
    public static String formatFlowValue(double value) {
        return CircuitElm.showFormat.format(value);
    }
    
    /**
     * Sanitizes text for safe PlantUML inclusion.
     * Escapes special characters that could break parsing.
     */
    public static String sanitizeText(String value) {
        if (value == null) return "";
        return value.replace("\r", " ")
                    .replace("\n", " ")
                    .replace("\\", "\\\\")
                    .replace(":", "-");
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // PLANTUML TEXT PARSING
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Parses PlantUML text into a ParsedDiagram structure.
     * Handles: title, actor, participant, messages, dividers, notes.
     * 
     * @param sourceText PlantUML source to parse
     * @return ParsedDiagram containing participants and elements
     */
    public static ParsedDiagram parseDiagramText(String sourceText) {
        ParsedDiagram diagram = new ParsedDiagram();
        
        // Note parsing state
        boolean inNote = false;
        Vector<String> noteLines = null;
        String noteTarget = null;
        String notePosition = null;
        
        String[] sourceLines = (sourceText != null ? sourceText : "").split("\n");
        
        for (String rawLine : sourceLines) {
            String line = rawLine.trim();
            
            // Skip control directives and empty lines
            if (line.isEmpty() || line.startsWith("@")) continue;
            
            // Handle multi-line note content
            if (inNote) {
                if ("end note".equals(line)) {
                    diagram.elements.add(new Note(noteTarget, notePosition, noteLines));
                    inNote = false;
                } else {
                    noteLines.add(line);
                }
                continue;
            }
            
            // Parse line by type
            if (parseTitle(line, diagram)) continue;
            if (parseActor(line, diagram)) continue;
            if (parseParticipant(line, diagram)) continue;
            if (parseDivider(line, diagram)) continue;
            
            // Check for note start
            NoteStart noteStart = parseNoteStart(line);
            if (noteStart != null) {
                inNote = true;
                noteTarget = noteStart.target;
                notePosition = noteStart.position;
                noteLines = new Vector<String>();
                continue;
            }
            
            // Parse message arrows
            parseMessage(line, diagram);
        }
        
        // Assign message indices after parsing
        int msgIdx = 0;
        for (int i = 0; i < diagram.elements.size(); i++) {
            DiagramElement elem = diagram.elements.get(i);
            if (elem instanceof Message) {
                ((Message) elem).messageIndex = msgIdx++;
            }
        }
        diagram.messageCount = msgIdx;
        
        return diagram;
    }
    
    /**
     * Parses "title ..." line, supporting \\n for multi-line titles.
     * @return true if line was a title directive
     */
    private static boolean parseTitle(String line, ParsedDiagram diagram) {
        if (!line.startsWith("title ")) return false;
        
        String title = line.substring(6);
        String[] parts = title.split("\\\\n");
        if (parts.length > 0) diagram.titleLine1 = parts[0];
        if (parts.length > 1) diagram.titleLine2 = parts[1];
        return true;
    }
    
    /**
     * Parses "actor <name>" declaration.
     * @return true if line was an actor declaration
     */
    private static boolean parseActor(String line, ParsedDiagram diagram) {
        if (!line.startsWith("actor ")) return false;
        
        String name = line.substring(6).trim();
        diagram.participants.add(new Participant(name, true));
        return true;
    }
    
    /**
     * Parses "participant <name>" declaration.
     * @return true if line was a participant declaration
     */
    private static boolean parseParticipant(String line, ParsedDiagram diagram) {
        if (!line.startsWith("participant ")) return false;
        
        String name = line.substring(12).trim();
        diagram.participants.add(new Participant(name, false));
        return true;
    }
    
    /**
     * Parses "== label ==" divider line.
     * @return true if line was a divider
     */
    private static boolean parseDivider(String line, ParsedDiagram diagram) {
        if (!line.startsWith("== ") || !line.endsWith(" ==")) return false;
        
        String label = line.substring(3, line.length() - 3);
        diagram.elements.add(new Divider(label));
        return true;
    }
    
    /**
     * Parses "note ..." start directive.
     * @return NoteStart if successful, null otherwise
     */
    public static NoteStart parseNoteStart(String line) {
        if (!line.startsWith("note ")) return null;
        
        String noteDef = line.substring(5);
        NoteStart result = new NoteStart();
        
        if (noteDef.startsWith("across")) {
            result.position = "across";
            result.target = "across";
        } else if (noteDef.startsWith("right of ")) {
            result.position = "right";
            result.target = noteDef.substring(9).trim();
        } else if (noteDef.startsWith("left of ")) {
            result.position = "left";
            result.target = noteDef.substring(8).trim();
        } else {
            return null; // Unknown note type
        }
        return result;
    }
    
    /**
     * Parses message arrows: "A -> B : label" or "A --> B : label"
     */
    private static void parseMessage(String line, ParsedDiagram diagram) {
        // Determine arrow type
        boolean dashed = line.contains(" --> ");
        String separator = dashed ? " --> " : " -> ";
        
        int sepIdx = line.indexOf(separator);
        if (sepIdx < 0) return; // Not a message line
        
        String from = line.substring(0, sepIdx).trim();
        String rest = line.substring(sepIdx + separator.length()).trim();
        
        // Split target and label
        String to, label = "";
        int colonIdx = rest.indexOf(" : ");
        if (colonIdx >= 0) {
            to = rest.substring(0, colonIdx).trim();
            label = rest.substring(colonIdx + 3).trim();
        } else {
            to = rest;
        }
        
        // Auto-declare participants if not yet declared
        diagram.ensureParticipant(from);
        diagram.ensureParticipant(to);
        
        diagram.elements.add(new Message(from, to, label, dashed));
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // DEFAULT DIAGRAM
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns a default example diagram demonstrating SFC model transactions.
     * Uses Godley & Lavoie's Model SIM as the canonical example.
     */
    public static String getDefaultDiagram() {
        return "@startuml\n" +
               "title Godley & Lavoie (2007) - Model SIM\\nSimplest Stock-Flow Consistent Model with Government Money\n" +
               "actor Households\n" +
               "participant Firms\n" +
               "participant Government\n" +
               "== 1. Government Expenditure (exogenous) ==\n" +
               "Government -> Firms : Pays G\\n(new government money created)\n" +
               "== 2. Production & Wage Payment ==\n" +
               "Firms -> Households : Pays wages WB\\n(WB = W * N)\n" +
               "== 3. Tax Payment ==\n" +
               "Households -> Government : Pays taxes T\\n(T = θ * WB)\n" +
               "note right of Households\n" +
               "  Disposable income:\n" +
               "  YD = WB - T\n" +
               "end note\n" +
               "== 4. Consumption ==\n" +
               "Households -> Firms : Buys consumption goods C\\n(C = α₁·YD + α₂·H₋₁)\n" +
               "note right of Firms\n" +
               "  Accounting identity:\n" +
               "  Y = C + G\n" +
               "  (output / GDP)\n" +
               "end note\n" +
               "== 5. Money Stock Changes (SFC consistency) ==\n" +
               "Households -> Government : ΔHʰ = YD - C\\n(change in household money holdings)\n" +
               "Government -> Households : ΔHˢ = G - T\\n(change in money supply)\n" +
               "note across\n" +
               "  Stock-flow consistency condition:\n" +
               "  ΔHʰ ≡ ΔHˢ\n" +
               "  (hidden equation)\n" +
               "end note\n" +
               "== End of Period ==\n" +
               "note right of Households\n" +
               "  Wealth updated:\n" +
               "  H = H₋₁ + (YD - C)\n" +
               "end note\n" +
               "@end";
    }
}
