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

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.elements.economics.ComputedValues;
import com.lushprojects.circuitjs1.client.elements.economics.TableColumn;
import com.lushprojects.circuitjs1.client.elements.economics.TableColumn.ColumnType;
import com.lushprojects.circuitjs1.client.elements.economics.TableElm;
import com.lushprojects.circuitjs1.client.util.*;
import com.lushprojects.circuitjs1.client.ui.EditInfo;

import java.util.ArrayList;
import java.util.Vector;

/**
 * SequenceDiagramElm - Renders UML sequence diagrams on the circuit canvas.
 * 
 * <p>This element supports a subset of PlantUML sequence diagram syntax for
 * visualizing economic flows between sectors. It can either use manually-entered
 * PlantUML source or auto-generate diagrams from TableElm data.
 * 
 * <h3>Supported PlantUML Syntax:</h3>
 * <ul>
 *   <li>{@code title <text>} - Diagram title (supports \n for line breaks)</li>
 *   <li>{@code actor <name>} - Stick figure participant</li>
 *   <li>{@code participant <name>} - Box participant</li>
 *   <li>{@code A -> B : label} - Solid arrow message</li>
 *   <li>{@code A --> B : label} - Dashed arrow message</li>
 *   <li>{@code == text ==} - Horizontal divider</li>
 *   <li>{@code note left/right of X} - Note attached to participant</li>
 *   <li>{@code note across} - Note spanning full width</li>
 * </ul>
 * 
 * <h3>Auto-Generation:</h3>
 * <p>Include {@code source:<TableName>} to auto-generate from a TableElm.
 * The diagram extracts flow transactions showing source/target sectors.
 * 
 * @see TableElm
 * @see GraphicElm
 */
public class SequenceDiagramElm extends GraphicElm {
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONSTANTS - Layout & Sizing
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Padding between frame edge and diagram content */
    private static final int FRAME_PADDING = 2;
    
    /** Horizontal margin for participant positioning */
    private static final int PARTICIPANT_SIDE_MARGIN = 48;
    
    /** Minimum allowed diagram scale factor */
    private static final double MIN_SCALE = 0.1;
    
    /** Maximum allowed diagram scale factor */
    private static final double MAX_SCALE = 10.0;
    
    /** Minimum frame dimension (width/height) in pixels */
    private static final int MIN_FRAME_SIZE = 16;
    
    /** Default diagram width when creating new element */
    private static final int DEFAULT_WIDTH = 560;
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONSTANTS - Drawing
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Stroke width for vertical lifelines */
    private static final double LIFELINE_STROKE_WIDTH = 1.8;
    
    /** Stroke width for transaction arrows */
    private static final double TRANSACTION_STROKE_WIDTH = 2.0;
    
    /** Dashed line segment length */
    private static final int DASH_LENGTH = 5;
    
    /** Gap between dashed line segments */
    private static final int DASH_GAP = 5;
    
    /** Corner radius for rounded rectangles */
    private static final int CORNER_RADIUS = 3;
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONSTANTS - Auto-Source & Parsing
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Prefix indicating auto-generation from a table source */
    private static final String AUTO_SOURCE_PREFIX = "source:";
    
    /** Threshold for considering transaction values as zero */
    private static final double ZERO_THRESHOLD = 1e-10;
    
    // ══════════════════════════════════════════════════════════════════════════
    // COLORS - Theme Configuration
    // ══════════════════════════════════════════════════════════════════════════
    
    private String bgColor = "#FFFFFF";              // Diagram background
    private String participantBgColor = "#E2E2F0";   // Participant box fill
    private String noteBgColor = "#FEFFDD";          // Note background (pale yellow)
    private String lineColor = "#181818";            // Lines and borders
    private String dividerColor = "#000000";         // Divider border lines
    private String dividerBgColor = "#EEEEEE";       // Divider background
    
    // ══════════════════════════════════════════════════════════════════════════
    // STATE - Source & Parsing
    // ══════════════════════════════════════════════════════════════════════════
    
    /** User-entered PlantUML source (may contain source: directive) */
    private String plantUmlSource;
    
    /** Resolved/rendered PlantUML source (after table expansion) */
    private String renderedPlantUmlSource;
    
    /** Name of source table (extracted from source: directive), null if none */
    private String sourceTableName;
    
    /** Reference to source table for auto-generation, null if manual */
    private TableElm sourceTable;
    
    // ══════════════════════════════════════════════════════════════════════════
    // STATE - Parsed Diagram Structure
    // ══════════════════════════════════════════════════════════════════════════
    
    /** List of participants (actors/boxes) in declaration order */
    private Vector<Participant> participants;
    
    /** List of diagram elements (messages, dividers, notes) in order */
    private Vector<DiagramElement> elements;
    
    /** First line of diagram title, null if no title */
    private String titleLine1;
    
    /** Second line of diagram title (from \n split), null if single line */
    private String titleLine2;
    
    // ══════════════════════════════════════════════════════════════════════════
    // STATE - Layout Configuration
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Font size for labels and text */
    private int fontSize = 14;
    
    /** Horizontal spacing between adjacent participants */
    private int participantSpacing = 200;
    
    /** Y coordinate where lifelines begin (below participant headers) */
    private int lifelineStartY = 112;
    
    /** Logical diagram width in diagram coordinates (before scaling) */
    private int diagramWidth = 400;
    
    /** Calculated diagram height based on content */
    private int diagramHeight = 1000;
    
    /** Current scale factor for fitting diagram to frame */
    private double diagramScale = 1.0;
    
    /** Current Y position during drawing (tracks vertical progress) */
    private int currentY;
    
    // ══════════════════════════════════════════════════════════════════════════
    // INNER CLASSES - Data Structures
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Represents the source and target sectors for a flow transaction,
     * extracted from TableElm column analysis.
     */
    private static class FlowEndpoints {
        String sourceSector;   // Sector with negative value (outflow)
        String targetSector;   // Sector with positive value (inflow)
        double flowValue;      // Absolute magnitude of the flow
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS & SERIALIZATION
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new sequence diagram element at the specified position.
     * Initializes with a default SFC model example diagram.
     * 
     * @param xx X coordinate for placement
     * @param yy Y coordinate for placement
     */
    public SequenceDiagramElm(int xx, int yy) {
        super(xx, yy);
        plantUmlSource = getDefaultDiagram();
        parseDiagram();
        syncFrameToScale();
    }
    
    /**
     * Constructs element from serialized circuit data.
     * 
     * @param xa Start X coordinate
     * @param ya Start Y coordinate
     * @param xb End X coordinate (defines frame width)
     * @param yb End Y coordinate (defines frame height)
     * @param f  Element flags
     * @param st StringTokenizer with: [source] [width] [scale]
     */
    public SequenceDiagramElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        
        // Parse PlantUML source (required)
        plantUmlSource = st.hasMoreTokens() 
            ? CustomLogicModel.unescape(st.nextToken()) 
            : getDefaultDiagram();
        
        // Parse diagram width (optional)
        diagramWidth = parseIntToken(st, DEFAULT_WIDTH);
        
        // Parse diagram scale (optional)
        diagramScale = clampScale(parseDoubleToken(st, 1.0));
        
        parseDiagram();
        initializeFrameFromBounds();
    }
    
    /**
     * Serializes element state for circuit file storage.
     * Format: [baseData] [escapedSource] [width] [scale]
     */
    @Override
    protected String dump() {
        return super.dump() + " " 
            + CustomLogicModel.escape(plantUmlSource) + " " 
            + diagramWidth + " " 
            + diagramScale;
    }
    
    /** Returns unique dump type identifier for this element class */
    @Override
    protected int getDumpType() { 
        return 467; 
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS - Token Parsing
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Safely parses an integer from tokenizer with fallback default.
     */
    private int parseIntToken(StringTokenizer st, int defaultValue) {
        if (!st.hasMoreTokens()) return defaultValue;
        try {
            return Integer.parseInt(st.nextToken());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Safely parses a double from tokenizer with fallback default.
     */
    private double parseDoubleToken(StringTokenizer st, double defaultValue) {
        if (!st.hasMoreTokens()) return defaultValue;
        try {
            return Double.parseDouble(st.nextToken());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Returns a default example diagram demonstrating SFC model transactions.
     * Uses Godley & Lavoie's Model SIM as the canonical example.
     */
    private String getDefaultDiagram() {
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
               "@enduml";
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // GEOMETRY & FRAME MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void setPoints() {
        super.setPoints();
        setBbox(getFrameLeft(), getFrameTop(), getFrameRight(), getFrameBottom());
    }

    @Override
    protected int getNumHandles() {
        return 2;
    }

    /**
     * Handles drag of resize handles, enforcing minimum size constraints.
     * 
     * @param n  Handle index (0 = top-left, 1 = bottom-right)
     * @param dx Horizontal displacement
     * @param dy Vertical displacement
     */
    @Override
    protected void movePoint(int n, int dx, int dy) {
        if (n == 0) {
            x += dx;
            y += dy;
        } else {
            x2 += dx;
            y2 += dy;
        }
        enforceMinimumFrameSize(n);
        updateScaleFromFrame();
        setPoints();
    }
    
    /**
     * Handles interactive dragging during element creation.
     */
    @Override
    protected void drag(int xx, int yy) {
        x2 = xx;
        y2 = yy;
        enforceMinimumFrameSize(1);
        updateScaleFromFrame();
        setBbox(x, y, x2, y2);
    }

    /**
     * Initializes frame bounds from serialized coordinates.
     * Falls back to scale-based sizing if bounds are too small.
     */
    private void initializeFrameFromBounds() {
        if (getFrameWidth() < 32 || getFrameHeight() < 32) {
            syncFrameToScale();
        } else {
            updateScaleFromFrame();
            setPoints();
        }
    }

    /**
     * Sets frame size based on current diagram dimensions and scale.
     * Called after scale changes to update the visual frame.
     */
    private void syncFrameToScale() {
        int contentWidth = Math.max(1, (int) Math.round(diagramWidth * diagramScale));
        int contentHeight = Math.max(1, (int) Math.round(diagramHeight * diagramScale));
        x2 = x + contentWidth + FRAME_PADDING * 2;
        y2 = y + contentHeight + FRAME_PADDING * 2;
        setPoints();
    }

    /**
     * Enforces minimum frame dimensions when resizing.
     * Adjusts the appropriate coordinate based on which handle is being moved.
     * 
     * @param handleIndex 0 for start point, 1 for end point
     */
    private void enforceMinimumFrameSize(int handleIndex) {
        int minDimension = FRAME_PADDING * 2 + MIN_FRAME_SIZE;
        
        if (getFrameWidth() < minDimension) {
            int signedMin = signedLength(x, x2, minDimension);
            if (handleIndex == 0) {
                x = x2 - signedMin;
            } else {
                x2 = x + signedMin;
            }
        }
        
        if (getFrameHeight() < minDimension) {
            int signedMin = signedLength(y, y2, minDimension);
            if (handleIndex == 0) {
                y = y2 - signedMin;
            } else {
                y2 = y + signedMin;
            }
        }
    }

    /**
     * Returns length with sign matching the direction from start to end.
     */
    private int signedLength(int start, int end, int length) {
        return (end >= start) ? length : -length;
    }

    /**
     * Recalculates scale factor to fit diagram within current frame.
     */
    private void updateScaleFromFrame() {
        diagramScale = calculateFitScale();
    }

    /**
     * Calculates scale factor that fits diagram within available frame space.
     * Maintains aspect ratio by using the more restrictive dimension.
     * 
     * @return Scale factor in range [MIN_SCALE, MAX_SCALE]
     */
    private double calculateFitScale() {
        double availableWidth = getFrameWidth() - FRAME_PADDING * 2;
        double availableHeight = getFrameHeight() - FRAME_PADDING * 2;
        
        // Guard against division by zero
        if (availableWidth <= 0 || availableHeight <= 0 || 
            diagramWidth <= 0 || diagramHeight <= 0) {
            return MIN_SCALE;
        }
        
        double widthScale = availableWidth / diagramWidth;
        double heightScale = availableHeight / diagramHeight;
        return clampScale(Math.min(widthScale, heightScale));
    }

    /**
     * Constrains scale factor to valid range.
     */
    private double clampScale(double scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }
    
    // Frame coordinate accessors (handle inverted coordinates)
    private int getFrameLeft()   { return min(x, x2); }
    private int getFrameTop()    { return min(y, y2); }
    private int getFrameRight()  { return max(x, x2); }
    private int getFrameBottom() { return max(y, y2); }
    private int getFrameWidth()  { return Math.abs(x2 - x); }
    private int getFrameHeight() { return Math.abs(y2 - y); }
    
    // ══════════════════════════════════════════════════════════════════════════
    // PLANTUML PARSING - Source Resolution
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Refreshes the rendered source if it has changed.
     * Handles both manual source and auto-generated source from tables.
     * Uses dirty-checking to avoid unnecessary re-parsing.
     */
    private void refreshRenderedSourceIfNeeded() {
        String nextSource = buildRenderedSource();
        
        // Fallback to default if source is empty
        if (nextSource == null || nextSource.isEmpty()) {
            nextSource = getDefaultDiagram();
        }
        
        // Only re-parse if source has changed
        if (!nextSource.equals(renderedPlantUmlSource)) {
            renderedPlantUmlSource = nextSource;
            parseDiagramText(renderedPlantUmlSource);
        }
    }

    /**
     * Builds the rendered PlantUML source.
     * If source contains "source:TableName", generates diagram from that table.
     * Otherwise returns the raw plantUmlSource.
     * 
     * @return PlantUML source text ready for parsing
     */
    private String buildRenderedSource() {
        sourceTableName = extractSourceTableName(plantUmlSource);
        
        // No source directive - use raw source
        if (sourceTableName == null) {
            sourceTable = null;
            return plantUmlSource;
        }

        // Look up the referenced table
        sourceTable = findSourceTable(sourceTableName);
        
        return (sourceTable != null) 
            ? buildDiagramFromSourceTable(sourceTable)
            : buildMissingSourceDiagram(sourceTableName);
    }

    /**
     * Extracts table name from "source:TableName" directive in source text.
     * 
     * @param sourceText PlantUML source text to scan
     * @return Table name if directive found, null otherwise
     */
    private String extractSourceTableName(String sourceText) {
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
     * @return Matching TableElm or null if not found
     */
    private TableElm findSourceTable(String requestedName) {
        if (sim == null || sim.elmList == null) {
            return null;
        }

        boolean autoSelect = (requestedName == null || requestedName.isEmpty());
        
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            if (!(elm instanceof TableElm)) continue;
            
            TableElm table = (TableElm) elm;
            
            if (autoSelect) {
                // Auto-select: return first table with SECTOR columns
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
     * Checks if table has at least one SECTOR column type.
     * Required for auto-selection compatibility.
     */
    private boolean hasAnySectorColumn(TableElm table) {
        if (table == null || table.columns == null) {
            return false;
        }
        for (int i = 0; i < table.columns.size(); i++) {
            TableColumn col = table.columns.get(i);
            if (col != null && col.getType() == ColumnType.SECTOR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates error diagram when source table is not found.
     */
    private String buildMissingSourceDiagram(String requestedName) {
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
        sb.append("@enduml");
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PLANTUML PARSING - Table-to-Diagram Generation
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Generates PlantUML diagram source from a TableElm.
     * Creates participants from SECTOR columns and messages from row transactions.
     * 
     * @param table Source table to extract diagram from
     * @return Generated PlantUML source
     */
    private String buildDiagramFromSourceTable(TableElm table) {
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
            sb.append("@enduml");
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
            
            if (endpoints.flowValue > 0) {
                sb.append("\\n(").append(formatFlowValue(endpoints.flowValue)).append(")");
            }
            sb.append("\n");
            messageCount++;
        }

        // Note if no transactions found
        if (messageCount == 0) {
            sb.append("note across\nNo paired source/target flows found\nend note\n");
        }

        sb.append("@enduml");
        return sb.toString();
    }

    /**
     * Collects unique SECTOR column names from table.
     */
    private ArrayList<String> collectSectorNames(TableElm table) {
        ArrayList<String> names = new ArrayList<String>();
        if (table == null || table.columns == null) {
            return names;
        }
        
        for (int col = 0; col < table.columns.size(); col++) {
            TableColumn column = table.columns.get(col);
            if (column == null || column.getType() != ColumnType.SECTOR) {
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
     * @param table Table to analyze
     * @param row   Row index
     * @return FlowEndpoints with source, target, and magnitude
     */
    private FlowEndpoints extractFlowEndpoints(TableElm table, int row) {
        if (table == null || table.columns == null) {
            return null;
        }
        
        FlowEndpoints endpoints = new FlowEndpoints();
        
        for (int col = 0; col < table.columns.size(); col++) {
            TableColumn column = table.columns.get(col);
            if (column == null || column.getType() != ColumnType.SECTOR) {
                continue;
            }
            
            double value = getTransactionValue(table, row, col);
            if (Math.abs(value) < ZERO_THRESHOLD) {
                continue;
            }
            
            // Negative = source (outflow), Positive = target (inflow)
            if (value < 0 && endpoints.sourceSector == null) {
                endpoints.sourceSector = column.getStockName();
                endpoints.flowValue = Math.abs(value);
            } else if (value > 0 && endpoints.targetSector == null) {
                endpoints.targetSector = column.getStockName();
                if (endpoints.flowValue == 0) {
                    endpoints.flowValue = value;
                }
            }
        }
        return endpoints;
    }

    /**
     * Gets transaction value from cell, preferring ComputedValues if available.
     */
    private double getTransactionValue(TableElm table, int row, int col) {
        // First try to get published flow value from equation label
        String equation = table.getCellEquation(row, col);
        if (equation != null) {
            String trimmed = equation.trim();
            if (!trimmed.isEmpty() && !"0".equals(trimmed)) {
                Double publishedFlow = ComputedValues.getComputedFlowValue(trimmed);
                if (publishedFlow != null) {
                    return publishedFlow;
                }
            }
        }
        // Fallback to cell voltage
        return table.getVoltageForCell(row, col);
    }

    /**
     * Gets display label for table row, falling back to generic label.
     */
    private String getRowLabel(TableElm table, int row) {
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
    private String formatFlowValue(double value) {
        return CircuitElm.showFormat.format(value);
    }

    /**
     * Sanitizes text for safe PlantUML inclusion.
     * Escapes special characters that could break parsing.
     */
    private String sanitizeText(String value) {
        if (value == null) return "";
        return value.replace("\r", " ")
                    .replace("\n", " ")
                    .replace("\\", "\\\\")
                    .replace(":", "-");
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // INNER CLASSES - Diagram Elements
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Represents a participant (actor or box) in the sequence diagram.
     */
    private static class Participant {
        final String name;     // Display name
        final String alias;    // Alias for referencing (defaults to name)
        final boolean isActor; // True = stick figure, False = box
        int x;                 // Calculated X position (set during layout)
        
        Participant(String name, boolean isActor) {
            this.name = name;
            this.alias = name;
            this.isActor = isActor;
        }
    }
    
    /**
     * Abstract base class for drawable diagram elements.
     * Subclasses: Message, Divider, Note
     */
    private static abstract class DiagramElement {
        /** Draw the element at the given Y position */
        abstract void draw(Graphics g, SequenceDiagramElm elm, int y);
        
        /** Return the vertical space this element consumes */
        abstract int getHeight();
    }
    
    /**
     * Represents an arrow message between two participants.
     */
    private static class Message extends DiagramElement {
        final String from;     // Source participant name
        final String to;       // Target participant name
        final String label;    // Message text (may contain \\n for line breaks)
        final boolean dashed;  // True for --> dashed arrows
        
        Message(String from, String to, String label, boolean dashed) {
            this.from = from;
            this.to = to;
            this.label = label;
            this.dashed = dashed;
        }
        
        @Override
        void draw(Graphics g, SequenceDiagramElm elm, int y) {
            Participant fromP = elm.findParticipant(from);
            Participant toP = elm.findParticipant(to);
            if (fromP == null || toP == null) return;
            
            int x1 = fromP.x;
            int x2 = toP.x;
            int arrowY = y + getLabelBlockHeight();
            
            // Draw label above arrow line
            drawLabel(g, elm, x1, x2, y);
            
            // Draw arrow line
            g.setColor(elm.lineColor);
            if (dashed) {
                elm.drawDashedLine(g, x1, arrowY, x2, arrowY, TRANSACTION_STROKE_WIDTH);
            } else {
                g.context.setLineWidth(TRANSACTION_STROKE_WIDTH);
                g.drawLine(x1, arrowY, x2, arrowY);
                g.context.setLineWidth(1);
            }
            
            // Draw arrowhead pointing to target
            drawArrowhead(g, x2, arrowY, x2 > x1);
        }
        
        /** Draws multi-line label centered above the arrow */
        private void drawLabel(Graphics g, SequenceDiagramElm elm, int x1, int x2, int y) {
            if (label == null || label.isEmpty()) return;
            
            String[] lines = label.split("\\\\n");
            int centerX = Math.min(x1, x2) + Math.abs(x2 - x1) / 2;
            int labelY = y - 3;
            
            for (String line : lines) {
                elm.drawCenteredMaskedString(g, line, centerX, labelY);
                labelY += 15;
            }
        }
        
        /** Draws filled triangular arrowhead */
        private void drawArrowhead(Graphics g, int tipX, int tipY, boolean pointRight) {
            int direction = pointRight ? 1 : -1;
            int baseX = tipX - (10 * direction);
            
            g.context.beginPath();
            g.context.moveTo(tipX, tipY);
            g.context.lineTo(baseX, tipY - 4);
            g.context.lineTo(baseX, tipY + 4);
            g.context.closePath();
            g.context.fill();
        }
        
        /** Calculates vertical space needed for label lines */
        private int getLabelBlockHeight() {
            if (label == null || label.isEmpty()) return 10;
            int lineCount = label.split("\\\\n").length;
            return Math.max(10, lineCount * 15 - 6);
        }
        
        @Override
        int getHeight() {
            return getLabelBlockHeight() + 18;
        }
    }
    
    /**
     * Represents a horizontal divider with centered label.
     */
    private static class Divider extends DiagramElement {
        final String label;
        
        Divider(String label) {
            this.label = label;
        }
        
        @Override
        void draw(Graphics g, SequenceDiagramElm elm, int y) {
            int left = 5;
            int right = elm.diagramWidth - 5;
            int height = 23;
            int halfHeight = height / 2;
            
            // Draw background bar
            g.context.setFillStyle(elm.dividerBgColor);
            g.context.fillRect(left, y - halfHeight, right - left, height);
            
            // Draw border lines (top and bottom)
            g.setColor(elm.dividerColor);
            g.drawLine(left, y - halfHeight, right, y - halfHeight);
            g.drawLine(left, y + halfHeight, right, y + halfHeight);
            
            // Draw centered label
            if (label != null) {
                g.setColor("#000000");
                int textWidth = (int) g.context.measureText(label).getWidth();
                int textX = (left + right - textWidth) / 2;
                g.drawString(label, textX, y + 5);
            }
        }
        
        @Override
        int getHeight() {
            return 35;
        }
    }
    
    /**
     * Represents a note box attached to a participant or spanning full width.
     */
    private static class Note extends DiagramElement {
        final String target;       // Participant name or "across"
        final String position;     // "left", "right", or "across"
        final Vector<String> lines;
        
        Note(String target, String position, Vector<String> lines) {
            this.target = target;
            this.position = position;
            this.lines = lines;
        }
        
        @Override
        void draw(Graphics g, SequenceDiagramElm elm, int y) {
            int noteHeight = lines.size() * 15 + 20;
            int noteX, noteWidth;
            
            // Calculate position based on type
            if ("across".equals(position)) {
                noteX = 10;
                noteWidth = elm.diagramWidth - 20;
            } else {
                Participant p = elm.findParticipant(target);
                if (p == null) return;
                
                noteWidth = 150;
                noteX = "right".equals(position) 
                    ? p.x + 10 
                    : p.x - noteWidth - 10;
            }
            
            // Draw note background with folded corner effect
            drawNoteBackground(g, elm, noteX, y, noteWidth, noteHeight);
            
            // Draw text lines centered
            g.setColor("#000000");
            int textY = y + 17;
            for (String line : lines) {
                int textWidth = (int) g.context.measureText(line).getWidth();
                g.drawString(line, noteX + (noteWidth - textWidth) / 2, textY);
                textY += 15;
            }
        }
        
        /** Draws note shape with folded corner (top-right) */
        private void drawNoteBackground(Graphics g, SequenceDiagramElm elm, 
                                        int x, int y, int width, int height) {
            int foldSize = 10;
            
            // Fill background
            g.context.setFillStyle(elm.noteBgColor);
            g.context.beginPath();
            g.context.moveTo(x, y);
            g.context.lineTo(x + width - foldSize, y);
            g.context.lineTo(x + width, y + foldSize);
            g.context.lineTo(x + width, y + height);
            g.context.lineTo(x, y + height);
            g.context.closePath();
            g.context.fill();
            
            // Draw border
            g.setColor(elm.lineColor);
            g.drawLine(x, y, x + width - foldSize, y);                          // Top
            g.drawLine(x + width - foldSize, y, x + width - foldSize, y + foldSize); // Fold vertical
            g.drawLine(x + width - foldSize, y + foldSize, x + width, y + foldSize); // Fold horizontal
            g.drawLine(x + width, y + foldSize, x + width, y + height);          // Right
            g.drawLine(x + width, y + height, x, y + height);                    // Bottom
            g.drawLine(x, y + height, x, y);                                     // Left
        }
        
        @Override
        int getHeight() {
            return lines.size() * 15 + 30;
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // PLANTUML PARSING - Text Parser
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Entry point for diagram parsing. Refreshes source and parses if needed.
     */
    private void parseDiagram() {
        refreshRenderedSourceIfNeeded();
    }

    /**
     * Parses PlantUML text into participants and diagram elements.
     * Handles: title, actor, participant, messages, dividers, notes.
     * 
     * @param sourceText PlantUML source to parse
     */
    private void parseDiagramText(String sourceText) {
        // Initialize collections
        participants = new Vector<Participant>();
        elements = new Vector<DiagramElement>();
        titleLine1 = null;
        titleLine2 = null;
        
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
                    elements.add(new Note(noteTarget, notePosition, noteLines));
                    inNote = false;
                } else {
                    noteLines.add(line);
                }
                continue;
            }
            
            // Parse line by type
            if (parseTitle(line)) continue;
            if (parseActor(line)) continue;
            if (parseParticipant(line)) continue;
            if (parseDivider(line)) continue;
            
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
            parseMessage(line);
        }
        
        // Calculate positions after parsing
        calculateLayout();
    }
    
    /**
     * Parses "title ..." line, supporting \\n for multi-line titles.
     * @return true if line was a title directive
     */
    private boolean parseTitle(String line) {
        if (!line.startsWith("title ")) return false;
        
        String title = line.substring(6);
        String[] parts = title.split("\\\\n");
        if (parts.length > 0) titleLine1 = parts[0];
        if (parts.length > 1) titleLine2 = parts[1];
        return true;
    }
    
    /**
     * Parses "actor <name>" declaration.
     * @return true if line was an actor declaration
     */
    private boolean parseActor(String line) {
        if (!line.startsWith("actor ")) return false;
        
        String name = line.substring(6).trim();
        participants.add(new Participant(name, true));
        return true;
    }
    
    /**
     * Parses "participant <name>" declaration.
     * @return true if line was a participant declaration
     */
    private boolean parseParticipant(String line) {
        if (!line.startsWith("participant ")) return false;
        
        String name = line.substring(12).trim();
        participants.add(new Participant(name, false));
        return true;
    }
    
    /**
     * Parses "== label ==" divider line.
     * @return true if line was a divider
     */
    private boolean parseDivider(String line) {
        if (!line.startsWith("== ") || !line.endsWith(" ==")) return false;
        
        String label = line.substring(3, line.length() - 3);
        elements.add(new Divider(label));
        return true;
    }
    
    /** Helper class for note parsing result */
    private static class NoteStart {
        String target;
        String position;
    }
    
    /**
     * Parses "note ..." start directive.
     * @return NoteStart if successful, null otherwise
     */
    private NoteStart parseNoteStart(String line) {
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
    private void parseMessage(String line) {
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
        ensureParticipant(from);
        ensureParticipant(to);
        
        elements.add(new Message(from, to, label, dashed));
    }
    
    /**
     * Ensures a participant exists, creating it if necessary.
     */
    private void ensureParticipant(String name) {
        if (findParticipant(name) == null) {
            participants.add(new Participant(name, false));
        }
    }
    
    /**
     * Finds a participant by name or alias.
     */
    private Participant findParticipant(String name) {
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            if (p.name.equals(name) || p.alias.equals(name)) {
                return p;
            }
        }
        return null;
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // LAYOUT CALCULATION
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Calculates participant positions and diagram dimensions.
     * Called after parsing to prepare for rendering.
     */
    private void calculateLayout() {
        int count = participants.size();
        if (count == 0) return;
        
        // Calculate horizontal spacing
        int usableWidth = Math.max(1, diagramWidth - PARTICIPANT_SIDE_MARGIN * 2);
        
        if (count == 1) {
            // Single participant: center it
            participantSpacing = 0;
            participants.get(0).x = diagramWidth / 2;
        } else {
            // Multiple participants: distribute evenly
            participantSpacing = Math.max(110, usableWidth / (count - 1));
            int startX = Math.max(PARTICIPANT_SIDE_MARGIN, 
                                  (diagramWidth - participantSpacing * (count - 1)) / 2);
            
            for (int i = 0; i < count; i++) {
                participants.get(i).x = startX + participantSpacing * i;
            }
        }

        // Calculate vertical positioning
        lifelineStartY = getTopParticipantBottomY(46);
        
        // Sum element heights for total diagram height
        int contentHeight = lifelineStartY;
        for (int i = 0; i < elements.size(); i++) {
            contentHeight += elements.get(i).getHeight();
        }
        diagramHeight = contentHeight + 78;  // Footer space for bottom participants
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // DRAWING - Main Render Pipeline
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Main drawing entry point. Renders the complete sequence diagram.
     * Pipeline: refresh source → calculate layout → transform → draw components.
     */
    @Override
    protected void draw(Graphics g) {
        refreshRenderedSourceIfNeeded();
        calculateLayout();
        
        g.save();
        
        // Apply transform to center and scale diagram within frame
        applyDiagramTransform(g);
        
        // Set base font for diagram
        Font baseFont = new Font("SansSerif", 0, fontSize);
        g.setFont(baseFont);
        
        // Render diagram layers in order
        drawBackground(g);
        drawTitle(g);
        g.setFont(baseFont);  // Reset after title font change
        
        drawParticipantHeaders(g, 46);
        drawLifelines(g, 46);
        drawDiagramElements(g);
        drawParticipantFooters(g);
        
        g.restore();
        
        // Finalize bounds for selection
        updateScaleFromFrame();
        setBbox(getFrameLeft(), getFrameTop(), getFrameRight(), getFrameBottom());
        drawSelectionHighlight(g);
    }
    
    /**
     * Applies canvas transform to center and scale diagram in frame.
     */
    private void applyDiagramTransform(Graphics g) {
        double fitScale = calculateFitScale();
        int renderedWidth = Math.max(1, (int) Math.round(diagramWidth * fitScale));
        int renderedHeight = Math.max(1, (int) Math.round(diagramHeight * fitScale));
        
        // Center diagram within frame
        int drawX = getFrameLeft() + (getFrameWidth() - renderedWidth) / 2;
        int drawY = getFrameTop() + (getFrameHeight() - renderedHeight) / 2;
        
        g.context.translate(drawX, drawY);
        g.context.scale(fitScale, fitScale);
    }
    
    /**
     * Fills diagram background with appropriate color for print/screen mode.
     */
    private void drawBackground(Graphics g) {
        String bg = sim.printableCheckItem.getState() ? "#FFFFFF" : bgColor;
        g.context.setFillStyle(bg);
        g.context.fillRect(0, 0, diagramWidth, diagramHeight);
    }
    
    /**
     * Draws diagram title (1-2 lines, centered, bold).
     */
    private void drawTitle(Graphics g) {
        if (titleLine1 == null && titleLine2 == null) return;
        
        Font titleFont = new Font("SansSerif", Font.BOLD, 14);
        g.setFont(titleFont);
        g.setColor("#000000");
        
        int titleY = 24;
        if (titleLine1 != null) {
            drawCenteredString(g, titleLine1, diagramWidth / 2, titleY);
            titleY += 15;
        }
        if (titleLine2 != null) {
            drawCenteredString(g, titleLine2, diagramWidth / 2, titleY);
        }
    }
    
    /**
     * Draws participant boxes/actors at top of diagram.
     */
    private void drawParticipantHeaders(Graphics g, int headerY) {
        for (int i = 0; i < participants.size(); i++) {
            drawParticipant(g, participants.get(i), headerY);
        }
    }
    
    /**
     * Draws vertical dashed lifelines for all participants.
     */
    private void drawLifelines(Graphics g, int headerY) {
        int topLifelineY = getTopParticipantBottomY(headerY);
        currentY = topLifelineY;
        int lifelineEndY = diagramHeight - 49;
        
        g.setColor(lineColor);
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            drawDashedLine(g, p.x, currentY, p.x, lifelineEndY, LIFELINE_STROKE_WIDTH);
        }
    }
    
    /**
     * Draws all diagram elements (messages, dividers, notes) in sequence.
     */
    private void drawDiagramElements(Graphics g) {
        currentY = lifelineStartY + 24;
        for (int i = 0; i < elements.size(); i++) {
            DiagramElement elem = elements.get(i);
            elem.draw(g, this, currentY);
            currentY += elem.getHeight();
        }
    }
    
    /**
     * Draws participant boxes/actors at bottom of diagram (mirror of header).
     */
    private void drawParticipantFooters(Graphics g) {
        int footerY = diagramHeight - 49;
        for (int i = 0; i < participants.size(); i++) {
            drawParticipant(g, participants.get(i), footerY);
        }
    }
    
    /**
     * Draws selection rectangle if element is highlighted.
     */
    private void drawSelectionHighlight(Graphics g) {
        if (needsHighlight()) {
            g.setColor(selectColor);
            g.drawRect(boundingBox.x, boundingBox.y,
                      boundingBox.width, boundingBox.height);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAWING - Participant Rendering
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Draws a participant based on its type (actor or box).
     */
    private void drawParticipant(Graphics g, Participant p, int topY) {
        if (p.isActor) {
            drawActor(g, p, topY);
        } else {
            drawParticipantBox(g, p, topY);
        }
    }
    
    /**
     * Draws a stick figure actor with name label below.
     */
    private void drawActor(Graphics g, Participant p, int topY) {
        int cx = p.x;
        int radius = 8;
        
        // Head (filled circle with border)
        g.context.setFillStyle(participantBgColor);
        g.context.beginPath();
        g.context.arc(cx, topY + radius, radius, 0, 2 * Math.PI);
        g.context.fill();
        g.setColor(lineColor);
        g.context.beginPath();
        g.context.arc(cx, topY + radius, radius, 0, 2 * Math.PI);
        g.context.stroke();
        
        // Body (vertical line)
        int bodyTop = topY + radius * 2;
        int bodyBot = bodyTop + 27;
        g.drawLine(cx, bodyTop, cx, bodyBot);
        
        // Arms (horizontal line)
        int armY = bodyTop + 8;
        g.drawLine(cx - 13, armY, cx + 13, armY);
        
        // Legs (two diagonal lines)
        g.drawLine(cx, bodyBot, cx - 13, bodyBot + 15);
        g.drawLine(cx, bodyBot, cx + 13, bodyBot + 15);
        
        // Name label
        drawCenteredMaskedString(g, p.name, cx, bodyBot + 35);
    }
    
    /**
     * Draws a rounded rectangle participant box with centered name.
     */
    private void drawParticipantBox(Graphics g, Participant p, int topY) {
        int textWidth = (int) g.context.measureText(p.name).getWidth();
        int boxWidth = textWidth + 14;
        int boxHeight = 30;
        int boxX = p.x - boxWidth / 2;
        
        // Draw rounded rectangle
        drawRoundedRect(g, boxX, topY, boxWidth, boxHeight, CORNER_RADIUS);
        
        // Draw centered name
        g.setColor("#000000");
        g.drawString(p.name, p.x - textWidth / 2, topY + 20);
    }
    
    /**
     * Draws a filled rounded rectangle with border.
     */
    private void drawRoundedRect(Graphics g, int x, int y, int w, int h, int r) {
        g.context.setFillStyle(participantBgColor);
        g.context.beginPath();
        g.context.moveTo(x + r, y);
        g.context.lineTo(x + w - r, y);
        g.context.quadraticCurveTo(x + w, y, x + w, y + r);
        g.context.lineTo(x + w, y + h - r);
        g.context.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        g.context.lineTo(x + r, y + h);
        g.context.quadraticCurveTo(x, y + h, x, y + h - r);
        g.context.lineTo(x, y + r);
        g.context.quadraticCurveTo(x, y, x + r, y);
        g.context.closePath();
        g.context.fill();
        
        g.setColor(lineColor);
        g.context.stroke();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAWING - Text & Line Utilities
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the Y coordinate of the bottom of the tallest participant header.
     */
    private int getTopParticipantBottomY(int topY) {
        int bottomY = topY;
        for (int i = 0; i < participants.size(); i++) {
            int connectorY = getParticipantConnectorBottom(participants.get(i), topY);
            bottomY = Math.max(bottomY, connectorY);
        }
        return bottomY;
    }

    /**
     * Returns the Y coordinate where lifeline connects to participant bottom.
     */
    private int getParticipantConnectorBottom(Participant p, int topY) {
        return p.isActor ? topY + 58 : topY + 30;
    }

    /**
     * Draws text centered at X with background mask for visibility over lifelines.
     */
    private void drawCenteredMaskedString(Graphics g, String text, int centerX, int baselineY) {
        if (text == null || text.isEmpty()) return;
        int textWidth = (int) g.context.measureText(text).getWidth();
        drawMaskedString(g, text, centerX - textWidth / 2, baselineY);
    }

    /**
     * Draws text with a white background rectangle for contrast.
     */
    private void drawMaskedString(Graphics g, String text, int textX, int baselineY) {
        if (text == null || text.isEmpty()) return;
        
        int textWidth = (int) g.context.measureText(text).getWidth();
        
        // Draw background mask
        g.context.setFillStyle(bgColor);
        g.context.fillRect(textX - 3, baselineY - 12, textWidth + 6, 16);
        
        // Draw text
        g.setColor("#000000");
        g.drawString(text, textX, baselineY);
    }

    /**
     * Draws text centered horizontally at the given position.
     */
    private void drawCenteredString(Graphics g, String text, int centerX, int baselineY) {
        int textWidth = (int) g.context.measureText(text).getWidth();
        g.drawString(text, centerX - textWidth / 2, baselineY);
    }
    
    /**
     * Draws a dashed line between two points with specified stroke width.
     * Uses DASH_LENGTH and DASH_GAP constants for consistent appearance.
     */
    private void drawDashedLine(Graphics g, int x1, int y1, int x2, int y2, double strokeWidth) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        // Avoid division by zero for zero-length lines
        if (distance < 1) return;
        
        // Calculate dash pattern
        double dashGap = DASH_LENGTH + DASH_GAP;
        int numDashes = (int) (distance / dashGap);
        
        // Unit vectors scaled to dash/gap lengths
        double xDash = dx / distance * DASH_LENGTH;
        double yDash = dy / distance * DASH_LENGTH;
        double xGap = dx / distance * DASH_GAP;
        double yGap = dy / distance * DASH_GAP;
        
        // Draw dash segments
        double curX = x1;
        double curY = y1;
        g.context.setLineWidth(strokeWidth);
        
        for (int i = 0; i < numDashes; i++) {
            g.drawLine((int) curX, (int) curY, 
                      (int) (curX + xDash), (int) (curY + yDash));
            curX += xDash + xGap;
            curY += yDash + yGap;
        }
        
        g.context.setLineWidth(1);
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // EDIT DIALOG
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns edit dialog configuration for the given parameter index.
     * 
     * @param n Parameter index (0=source, 1=width, 2=scale)
     * @return EditInfo for the parameter, or null if index out of range
     */
    @Override
    public EditInfo getEditInfo(int n) {
        switch (n) {
            case 0:  // PlantUML source text area
                EditInfo ei = new EditInfo("PlantUML Source", 0, -1, -1);
                ei.textArea = new com.google.gwt.user.client.ui.TextArea();
                ei.textArea.setVisibleLines(15);
                ei.textArea.setCharacterWidth(60);
                ei.textArea.setText(plantUmlSource);
                return ei;
                
            case 1:  // Diagram logical width
                return new EditInfo("Diagram Width", diagramWidth, 300, 800);
                
            case 2:  // Diagram scale factor
                return new EditInfo("Diagram Scale", diagramScale, 0.25, 4);
                
            default:
                return null;
        }
    }
    
    /**
     * Applies value from edit dialog to the specified parameter.
     * 
     * @param n  Parameter index
     * @param ei EditInfo containing the new value
     */
    @Override
    public void setEditValue(int n, EditInfo ei) {
        switch (n) {
            case 0:  // Update source and re-parse
                plantUmlSource = ei.textArea.getText();
                parseDiagram();
                break;
                
            case 1:  // Update width and recalculate scale
                diagramWidth = (int) ei.value;
                parseDiagram();
                updateScaleFromFrame();
                break;
                
            case 2:  // Update scale and resize frame
                diagramScale = Math.max(0.25, ei.value);
                syncFrameToScale();
                break;
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // INFO DISPLAY & PUBLIC ACCESSORS
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Fills array with element information for tooltip display.
     */
    @Override
    protected void getInfo(String[] arr) {
        refreshRenderedSourceIfNeeded();
        
        arr[0] = "Sequence Diagram";
        arr[1] = participants.size() + " participants";
        arr[2] = elements.size() + " elements";
        
        // Show source table info if applicable
        if (sourceTable != null) {
            arr[3] = "Source: " + sourceTable.getTableTitle();
        } else if (sourceTableName != null) {
            arr[3] = sourceTableName.isEmpty() 
                ? "Source: (auto)" 
                : "Source: " + sourceTableName + " (not found)";
        }
    }

    /** Returns the raw PlantUML source text (may contain source: directive) */
    public String getPlantUmlSource() {
        return plantUmlSource;
    }

    /** Returns the rendered/expanded PlantUML source after table expansion */
    public String getRenderedPlantUmlSource() {
        refreshRenderedSourceIfNeeded();
        return renderedPlantUmlSource;
    }

    /** Returns the source table name from source: directive, or null */
    public String getSourceTableName() {
        refreshRenderedSourceIfNeeded();
        return sourceTableName;
    }

    /** Returns the logical diagram width (before scaling) */
    public int getDiagramWidth() {
        return diagramWidth;
    }

    /** Returns the calculated diagram height (before scaling) */
    public int getDiagramHeight() {
        return diagramHeight;
    }

    /** Returns the current scale factor used for rendering */
    public double getDiagramScale() {
        return calculateFitScale();
    }

    /** Returns the actual rendered width in canvas pixels */
    public int getRenderedDiagramWidth() {
        return getFrameWidth();
    }

    /** Returns the actual rendered height in canvas pixels */
    public int getRenderedDiagramHeight() {
        return getFrameHeight();
    }
}
