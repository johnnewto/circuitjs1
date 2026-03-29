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
 * SequenceDiagramElm - Renders PlantUML sequence diagrams on the canvas.
 * 
 * Supports a subset of PlantUML sequence diagram syntax:
 * - title, actor, participant declarations
 * - -> and --> messages with labels
 * - == dividers
 * - note left/right of, note across
 */
public class SequenceDiagramElm extends GraphicElm {
    private static final int FRAME_PADDING = 2;
    private static final String AUTO_SOURCE_PREFIX = "source:";
    private static final int PARTICIPANT_SIDE_MARGIN = 48;
    private static final double LIFELINE_STROKE_WIDTH = 1.8;
    private static final double TRANSACTION_STROKE_WIDTH = 2.0;
    
    private String plantUmlSource;
    private String renderedPlantUmlSource;
    private String sourceTableName;
    private TableElm sourceTable;
    
    // Parsed diagram elements
    private Vector<Participant> participants;
    private Vector<DiagramElement> elements;
    private String titleLine1;
    private String titleLine2;
    
    // Layout constants
    private int fontSize = 14;
    private int participantSpacing = 200;
    private int lifelineStartY = 112;
    
    // Calculated layout
    private int diagramWidth = 400;
    private int diagramHeight = 1000;
    private double diagramScale = 1.0;
    private int currentY;
    
    // Colors
    private String bgColor = "#FFFFFF";
    private String participantBgColor = "#E2E2F0";
    private String noteBgColor = "#FEFFDD";
    private String lineColor = "#181818";
    private String dividerColor = "#000000";
    private String dividerBgColor = "#EEEEEE";

    private static class FlowEndpoints {
        String sourceSector;
        String targetSector;
        double flowValue;
    }
    
    public SequenceDiagramElm(int xx, int yy) {
        super(xx, yy);
        plantUmlSource = getDefaultDiagram();
        parseDiagram();
        syncFrameToScale();
    }
    
    public SequenceDiagramElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        if (st.hasMoreTokens()) {
            plantUmlSource = CustomLogicModel.unescape(st.nextToken());
        } else {
            plantUmlSource = getDefaultDiagram();
        }
        if (st.hasMoreTokens()) {
            try {
                diagramWidth = Integer.parseInt(st.nextToken());
            } catch (Exception e) {
                diagramWidth = 560;
            }
        }
        if (st.hasMoreTokens()) {
            try {
                diagramScale = Math.max(.1, Double.parseDouble(st.nextToken()));
            } catch (Exception e) {
                diagramScale = 1.0;
            }
        }
        parseDiagram();
        initializeFrameFromBounds();
    }
    
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
    
    protected String dump() {
        return super.dump() + " " + CustomLogicModel.escape(plantUmlSource) + " " + diagramWidth + " " + diagramScale;
    }
    
    protected int getDumpType() { return 467; }  // Unique dump type

    protected void setPoints() {
        super.setPoints();
        setBbox(getFrameLeft(), getFrameTop(), getFrameRight(), getFrameBottom());
    }

    protected int getNumHandles() {
        return 2;
    }

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
    
    protected void drag(int xx, int yy) {
        x2 = xx;
        y2 = yy;
        enforceMinimumFrameSize(1);
        updateScaleFromFrame();
        setBbox(x, y, x2, y2);
    }

    private void initializeFrameFromBounds() {
        if (getFrameWidth() < 32 || getFrameHeight() < 32) {
            syncFrameToScale();
            return;
        }
        updateScaleFromFrame();
        setPoints();
    }

    private void syncFrameToScale() {
        int contentWidth = Math.max(1, (int) Math.round(diagramWidth * diagramScale));
        int contentHeight = Math.max(1, (int) Math.round(diagramHeight * diagramScale));
        x2 = x + contentWidth + FRAME_PADDING * 2;
        y2 = y + contentHeight + FRAME_PADDING * 2;
        setPoints();
    }

    private void enforceMinimumFrameSize(int handleIndex) {
        int minWidth = FRAME_PADDING * 2 + 16;
        int minHeight = FRAME_PADDING * 2 + 16;
        if (getFrameWidth() < minWidth) {
            if (handleIndex == 0)
                x = x2 - signPreservingLength(x, x2, minWidth);
            else
                x2 = x + signPreservingLength(x, x2, minWidth);
        }
        if (getFrameHeight() < minHeight) {
            if (handleIndex == 0)
                y = y2 - signPreservingLength(y, y2, minHeight);
            else
                y2 = y + signPreservingLength(y, y2, minHeight);
        }
    }

    private int signPreservingLength(int start, int end, int length) {
        return (end >= start) ? length : -length;
    }

    private void updateScaleFromFrame() {
        diagramScale = getFitScaleForFrame();
    }

    private double getFitScaleForFrame() {
        double availableWidth = getFrameWidth() - FRAME_PADDING * 2;
        double availableHeight = getFrameHeight() - FRAME_PADDING * 2;
        if (availableWidth <= 0 || availableHeight <= 0 || diagramWidth <= 0 || diagramHeight <= 0)
            return .1;
        double widthScale = availableWidth / (double) diagramWidth;
        double heightScale = availableHeight / (double) diagramHeight;
        return clampDiagramScale(Math.min(widthScale, heightScale));
    }

    private int getFrameLeft() {
        return min(x, x2);
    }

    private int getFrameTop() {
        return min(y, y2);
    }

    private int getFrameRight() {
        return max(x, x2);
    }

    private int getFrameBottom() {
        return max(y, y2);
    }

    private int getFrameWidth() {
        return Math.abs(x2 - x);
    }

    private int getFrameHeight() {
        return Math.abs(y2 - y);
    }

    private double clampDiagramScale(double scale) {
        return Math.max(.1, Math.min(10, scale));
    }
    
    // ========== PlantUML Parsing ==========

    private void refreshRenderedSourceIfNeeded() {
        String nextRenderedSource = buildRenderedSource();
        if (nextRenderedSource == null || nextRenderedSource.isEmpty()) {
            nextRenderedSource = getDefaultDiagram();
        }
        if (renderedPlantUmlSource == null || !renderedPlantUmlSource.equals(nextRenderedSource)) {
            renderedPlantUmlSource = nextRenderedSource;
            parseDiagramText(renderedPlantUmlSource);
        }
    }

    private String buildRenderedSource() {
        sourceTableName = extractSourceTableName(plantUmlSource);
        if (sourceTableName == null) {
            sourceTable = null;
            return plantUmlSource;
        }

        sourceTable = findSourceTable(sourceTableName);
        if (sourceTable == null) {
            return buildMissingSourceDiagram(sourceTableName);
        }
        return buildDiagramFromSourceTable(sourceTable);
    }

    private String extractSourceTableName(String sourceText) {
        if (sourceText == null || sourceText.isEmpty()) {
            return null;
        }
        String[] sourceLines = sourceText.split("\n");
        for (String rawLine : sourceLines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.regionMatches(true, 0, AUTO_SOURCE_PREFIX, 0, AUTO_SOURCE_PREFIX.length())) {
                continue;
            }
            String value = line.substring(AUTO_SOURCE_PREFIX.length()).trim();
            int commentIdx = value.indexOf('#');
            if (commentIdx >= 0) {
                value = value.substring(0, commentIdx).trim();
            }
            return value;
        }
        return null;
    }

    private TableElm findSourceTable(String requestedName) {
        if (sim == null || sim.elmList == null) {
            return null;
        }

        boolean autoSelect = requestedName == null || requestedName.isEmpty();
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm elm = sim.elmList.get(i);
            if (!(elm instanceof TableElm)) {
                continue;
            }
            TableElm table = (TableElm) elm;
            if (autoSelect) {
                if (isSequenceSourceCompatible(table)) {
                    return table;
                }
                continue;
            }
            if (table.tableTitle != null && table.tableTitle.equals(requestedName)) {
                return table;
            }
        }
        return null;
    }

    private boolean isSequenceSourceCompatible(TableElm table) {
        if (table == null || table.columns == null) {
            return false;
        }
        for (int i = 0; i < table.columns.size(); i++) {
            TableColumn column = table.columns.get(i);
            if (column != null && column.getType() == ColumnType.SECTOR) {
                return true;
            }
        }
        return false;
    }

    private String buildMissingSourceDiagram(String requestedName) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title Sequence Diagram\n");
        sb.append("note across\n");
        if (requestedName == null || requestedName.isEmpty()) {
            sb.append("No compatible source table found\n");
        } else {
            sb.append("Source table not found:\\n");
            sb.append(sanitizeDiagramText(requestedName)).append("\n");
        }
        sb.append("end note\n");
        sb.append("@enduml");
        return sb.toString();
    }

    private String buildDiagramFromSourceTable(TableElm table) {
        String title = (table.tableTitle != null && !table.tableTitle.isEmpty())
            ? table.tableTitle : "Sequence Diagram";
        ArrayList<String> sectorNames = collectSectorNames(table);

        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title ").append(sanitizeDiagramText(title)).append("\n");

        if (sectorNames.isEmpty()) {
            sb.append("note across\n");
            sb.append("No sectors defined\n");
            sb.append("end note\n");
            sb.append("@enduml");
            return sb.toString();
        }

        for (int i = 0; i < sectorNames.size(); i++) {
            sb.append("participant ").append(sanitizeDiagramText(sectorNames.get(i))).append("\n");
        }

        int messageCount = 0;
        for (int row = 0; row < table.rows; row++) {
            FlowEndpoints endpoints = getFlowEndpoints(table, row);
            if (endpoints == null || endpoints.sourceSector == null || endpoints.targetSector == null) {
                continue;
            }

            String rowName = getRowLabel(table, row);
            sb.append(sanitizeDiagramText(endpoints.sourceSector))
              .append(" -> ")
              .append(sanitizeDiagramText(endpoints.targetSector))
              .append(" : ")
              .append(sanitizeDiagramText(rowName));
            if (endpoints.flowValue > 0) {
                sb.append("\\n(")
                  .append(formatFlowValue(endpoints.flowValue))
                  .append(")");
            }
            sb.append("\n");
            messageCount++;
        }

        if (messageCount == 0) {
            sb.append("note across\n");
            sb.append("No paired source/target flows found\n");
            sb.append("end note\n");
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private ArrayList<String> collectSectorNames(TableElm table) {
        ArrayList<String> sectorNames = new ArrayList<String>();
        if (table == null || table.columns == null) {
            return sectorNames;
        }
        for (int col = 0; col < table.columns.size(); col++) {
            TableColumn column = table.columns.get(col);
            if (column == null || column.getType() != ColumnType.SECTOR) {
                continue;
            }
            String name = column.getStockName();
            if (name == null || name.isEmpty() || sectorNames.contains(name)) {
                continue;
            }
            sectorNames.add(name);
        }
        return sectorNames;
    }

    private FlowEndpoints getFlowEndpoints(TableElm table, int row) {
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
            if (Math.abs(value) < 1e-10) {
                continue;
            }
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

    private double getTransactionValue(TableElm table, int row, int col) {
        String label = table.getCellEquation(row, col);
        if (label != null) {
            String trimmed = label.trim();
            if (!trimmed.isEmpty() && !"0".equals(trimmed)) {
                Double publishedFlow = ComputedValues.getComputedFlowValue(trimmed);
                if (publishedFlow != null) {
                    return publishedFlow.doubleValue();
                }
            }
        }
        return table.getVoltageForCell(row, col);
    }

    private String getRowLabel(TableElm table, int row) {
        if (table != null && table.rowDescriptions != null && row >= 0 && row < table.rowDescriptions.length) {
            String label = table.rowDescriptions[row];
            if (label != null && !label.isEmpty()) {
                return label;
            }
        }
        return "Transaction " + (row + 1);
    }

    private String formatFlowValue(double value) {
        return CircuitElm.showFormat.format(value);
    }

    private String sanitizeDiagramText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ")
            .replace("\n", " ")
            .replace("\\", "\\\\")
            .replace(":", "-");
    }
    
    private static class Participant {
        String name;
        String alias;
        boolean isActor;
        int x;  // Calculated X position
        
        Participant(String name, boolean isActor) {
            this.name = name;
            this.alias = name;
            this.isActor = isActor;
        }
    }
    
    private static abstract class DiagramElement {
        abstract void draw(Graphics g, SequenceDiagramElm elm, int y);
        abstract int getHeight();
    }
    
    private static class Message extends DiagramElement {
        String from;
        String to;
        String label;
        boolean dashed;
        
        Message(String from, String to, String label, boolean dashed) {
            this.from = from;
            this.to = to;
            this.label = label;
            this.dashed = dashed;
        }
        
        void draw(Graphics g, SequenceDiagramElm elm, int y) {
            Participant fromP = elm.findParticipant(from);
            Participant toP = elm.findParticipant(to);
            if (fromP == null || toP == null) return;
            
            int x1 = fromP.x;
            int x2 = toP.x;
            int arrowY = y + getLabelBlockHeight();
            
            // Draw label in the reserved space above the arrow
            if (label != null && !label.isEmpty()) {
                String[] labelLines = label.split("\\\\n");
                int labelY = y - 3;
                int labelX = Math.min(x1, x2) + Math.abs(x2 - x1) / 2;

                for (String line : labelLines) {
                    elm.drawCenteredMaskedString(g, line, labelX, labelY);
                    labelY += 15;
                }
            }
            
            // Draw arrow line
            g.setColor(elm.lineColor);
            if (dashed) {
                elm.drawDashedLine(g, x1, arrowY, x2, arrowY, TRANSACTION_STROKE_WIDTH);
            } else {
                g.context.setLineWidth(TRANSACTION_STROKE_WIDTH);
                g.drawLine(x1, arrowY, x2, arrowY);
                g.context.setLineWidth(1);
            }
            
            // Draw arrowhead
            int arrowDir = (x2 > x1) ? 1 : -1;
            int arrowX = x2 - (10 * arrowDir);
            g.context.beginPath();
            g.context.moveTo(x2, arrowY);
            g.context.lineTo(arrowX, arrowY - 4);
            g.context.lineTo(arrowX, arrowY + 4);
            g.context.closePath();
            g.context.fill();
        }

        private int getLabelBlockHeight() {
            if (label == null || label.isEmpty()) {
                return 10;
            }
            String[] lines = label.split("\\\\n");
            return Math.max(10, lines.length * 15 - 6);
        }
        
        int getHeight() {
            return getLabelBlockHeight() + 18;
        }
    }
    
    private static class Divider extends DiagramElement {
        String label;
        
        Divider(String label) {
            this.label = label;
        }
        
        void draw(Graphics g, SequenceDiagramElm elm, int y) {
            int left = 5;
            int right = elm.diagramWidth - 5;
            int height = 23;
            
            // Draw background bar
            g.context.setFillStyle(elm.dividerBgColor);
            g.context.fillRect(left, y - height/2, right - left, height);
            
            // Draw top and bottom lines
            g.setColor(elm.dividerColor);
            g.drawLine(left, y - height/2, right, y - height/2);
            g.drawLine(left, y + height/2, right, y + height/2);
            
            // Draw label centered
            if (label != null) {
                g.setColor("#000000");
                int textWidth = (int) g.context.measureText(label).getWidth();
                int textX = (left + right) / 2 - textWidth / 2;
                g.drawString(label, textX, y + 5);
            }
        }
        
        int getHeight() {
            return 35;
        }
    }
    
    private static class Note extends DiagramElement {
        String target;  // Participant name or "across"
        String position; // "left", "right", "across"
        Vector<String> lines;
        
        Note(String target, String position, Vector<String> lines) {
            this.target = target;
            this.position = position;
            this.lines = lines;
        }
        
        void draw(Graphics g, SequenceDiagramElm elm, int y) {
            int noteX, noteWidth;
            int noteHeight = lines.size() * 15 + 20;
            
            if ("across".equals(position)) {
                noteX = 10;
                noteWidth = elm.diagramWidth - 20;
            } else {
                Participant p = elm.findParticipant(target);
                if (p == null) return;
                
                noteWidth = 150;
                if ("right".equals(position)) {
                    noteX = p.x + 10;
                } else {
                    noteX = p.x - noteWidth - 10;
                }
            }
            
            // Draw note background with folded corner
            g.context.setFillStyle(elm.noteBgColor);
            g.context.beginPath();
            g.context.moveTo(noteX, y);
            g.context.lineTo(noteX + noteWidth - 10, y);
            g.context.lineTo(noteX + noteWidth, y + 10);
            g.context.lineTo(noteX + noteWidth, y + noteHeight);
            g.context.lineTo(noteX, y + noteHeight);
            g.context.closePath();
            g.context.fill();
            
            // Draw note border
            g.setColor(elm.lineColor);
            g.drawLine(noteX, y, noteX + noteWidth - 10, y);
            g.drawLine(noteX + noteWidth - 10, y, noteX + noteWidth - 10, y + 10);
            g.drawLine(noteX + noteWidth - 10, y + 10, noteX + noteWidth, y + 10);
            g.drawLine(noteX + noteWidth, y + 10, noteX + noteWidth, y + noteHeight);
            g.drawLine(noteX + noteWidth, y + noteHeight, noteX, y + noteHeight);
            g.drawLine(noteX, y + noteHeight, noteX, y);
            
            // Draw text
            g.setColor("#000000");
            int textY = y + 17;
            for (String line : lines) {
                int textX = noteX + (noteWidth - (int) g.context.measureText(line).getWidth()) / 2;
                g.drawString(line, textX, textY);
                textY += 15;
            }
        }
        
        int getHeight() {
            return lines.size() * 15 + 30;
        }
    }
    
    private void parseDiagram() {
        refreshRenderedSourceIfNeeded();
    }

    private void parseDiagramText(String sourceText) {
        participants = new Vector<Participant>();
        elements = new Vector<DiagramElement>();
        titleLine1 = null;
        titleLine2 = null;
        
        String[] sourceLines = (sourceText == null ? "" : sourceText).split("\n");
        Vector<String> noteLines = null;
        String noteTarget = null;
        String notePosition = null;
        boolean inNote = false;
        
        for (String line : sourceLines) {
            line = line.trim();
            
            // Skip @startuml and @enduml
            if (line.startsWith("@") || line.isEmpty()) continue;
            
            // Handle note blocks
            if (inNote) {
                if (line.equals("end note")) {
                    elements.add(new Note(noteTarget, notePosition, noteLines));
                    inNote = false;
                } else {
                    if (noteLines == null) {
                        noteLines = new Vector<String>();
                    }
                    noteLines.add(line);
                }
                continue;
            }
            
            // Title
            if (line.startsWith("title ")) {
                String title = line.substring(6);
                // Handle \n in title
                String[] parts = title.split("\\\\n");
                if (parts.length > 0) titleLine1 = parts[0];
                if (parts.length > 1) titleLine2 = parts[1];
                continue;
            }
            
            // Actor declaration
            if (line.startsWith("actor ")) {
                String name = line.substring(6).trim();
                participants.add(new Participant(name, true));
                continue;
            }
            
            // Participant declaration
            if (line.startsWith("participant ")) {
                String name = line.substring(12).trim();
                participants.add(new Participant(name, false));
                continue;
            }
            
            // Divider (== text ==)
            if (line.startsWith("== ") && line.endsWith(" ==")) {
                String label = line.substring(3, line.length() - 3);
                elements.add(new Divider(label));
                continue;
            }
            
            // Note starting
            if (line.startsWith("note ")) {
                String noteDef = line.substring(5);
                if (noteDef.startsWith("across")) {
                    notePosition = "across";
                    noteTarget = "across";
                } else if (noteDef.startsWith("right of ")) {
                    notePosition = "right";
                    noteTarget = noteDef.substring(9).trim();
                } else if (noteDef.startsWith("left of ")) {
                    notePosition = "left";
                    noteTarget = noteDef.substring(8).trim();
                }
                noteLines = new Vector<String>();
                inNote = true;
                continue;
            }
            
            // Message arrows: -> or -->
            if (line.contains(" -> ") || line.contains(" --> ")) {
                boolean dashed = line.contains(" --> ");
                String separator = dashed ? " --> " : " -> ";
                int sepIdx = line.indexOf(separator);
                String from = line.substring(0, sepIdx).trim();
                String rest = line.substring(sepIdx + separator.length()).trim();
                
                String to, label = "";
                int colonIdx = rest.indexOf(" : ");
                if (colonIdx >= 0) {
                    to = rest.substring(0, colonIdx).trim();
                    label = rest.substring(colonIdx + 3).trim();
                } else {
                    to = rest;
                }
                
                // Auto-add participants if not declared
                if (findParticipant(from) == null) {
                    participants.add(new Participant(from, false));
                }
                if (findParticipant(to) == null) {
                    participants.add(new Participant(to, false));
                }
                
                elements.add(new Message(from, to, label, dashed));
            }
        }
        
        // Calculate participant positions
        calculateLayout();
    }
    
    private Participant findParticipant(String name) {
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            if (p.name.equals(name) || p.alias.equals(name)) {
                return p;
            }
        }
        return null;
    }
    
    private void calculateLayout() {
        int numParticipants = participants.size();
        if (numParticipants == 0) return;
        
        // Calculate spacing with a small fixed side margin so the diagram fills more of the width.
        int usableWidth = Math.max(1, diagramWidth - PARTICIPANT_SIDE_MARGIN * 2);
        if (numParticipants == 1) {
            participantSpacing = 0;
            participants.get(0).x = diagramWidth / 2;
        } else {
            participantSpacing = Math.max(110, usableWidth / (numParticipants - 1));
            int startX = Math.max(PARTICIPANT_SIDE_MARGIN, (diagramWidth - participantSpacing * (numParticipants - 1)) / 2);
            for (int i = 0; i < numParticipants; i++) {
                Participant p = participants.get(i);
                p.x = startX + participantSpacing * i;
            }
        }

        lifelineStartY = getTopParticipantBottomY(46);
        
        // Calculate diagram height based on content
        int contentHeight = lifelineStartY;
        for (int i = 0; i < elements.size(); i++) {
            contentHeight += elements.get(i).getHeight();
        }
        diagramHeight = contentHeight + 78;  // Extra space for participant footers
    }
    
    // ========== Drawing ==========
    
    protected void draw(Graphics g) {
        refreshRenderedSourceIfNeeded();
        g.save();
        
        calculateLayout();
    double fitScale = getFitScaleForFrame();
    int renderedWidth = Math.max(1, (int) Math.round(diagramWidth * fitScale));
    int renderedHeight = Math.max(1, (int) Math.round(diagramHeight * fitScale));
    int drawX = getFrameLeft() + (getFrameWidth() - renderedWidth) / 2;
    int drawY = getFrameTop() + (getFrameHeight() - renderedHeight) / 2;
    g.context.translate(drawX, drawY);
    g.context.scale(fitScale, fitScale);
        
        // Set font
        Font f = new Font("SansSerif", 0, fontSize);
        g.setFont(f);
        
        // Draw background
        if (sim.printableCheckItem.getState()) {
            bgColor = "#FFFFFF";
        }
        g.context.setFillStyle(bgColor);
        g.context.fillRect(0, 0, diagramWidth, diagramHeight);
        
        // Draw title
        int titleY = 24;
        if (titleLine1 != null) {
            g.setColor("#000000");
            Font titleFont = new Font("SansSerif", Font.BOLD, 14);
            g.setFont(titleFont);
            int textWidth = (int) g.context.measureText(titleLine1).getWidth();
            g.drawString(titleLine1, (diagramWidth - textWidth) / 2, titleY);
            titleY += 15;
        }
        if (titleLine2 != null) {
            g.setColor("#000000");
            Font titleFont = new Font("SansSerif", Font.BOLD, 14);
            g.setFont(titleFont);
            int textWidth = (int) g.context.measureText(titleLine2).getWidth();
            g.drawString(titleLine2, (diagramWidth - textWidth) / 2, titleY);
        }
        
        // Reset font for rest of diagram
        g.setFont(f);
        
        // Draw participants at top
        int headerY = 46;
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            drawParticipant(g, p, headerY);
        }
        
        // Draw lifelines
        int topLifelineY = getTopParticipantBottomY(headerY);
        currentY = topLifelineY;
        int lifelineEndY = diagramHeight - 49;
        g.setColor(lineColor);
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            drawDashedLine(g, p.x, currentY, p.x, lifelineEndY, LIFELINE_STROKE_WIDTH);
        }
        
        // Draw diagram elements (messages, dividers, notes)
        currentY = topLifelineY + 24;
        for (int i = 0; i < elements.size(); i++) {
            DiagramElement elem = elements.get(i);
            elem.draw(g, this, currentY);
            currentY += elem.getHeight();
        }
        
        // Draw participants at bottom
        int footerY = lifelineEndY;
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            drawParticipant(g, p, footerY);
        }
        
        g.restore();
        updateScaleFromFrame();
        setBbox(getFrameLeft(), getFrameTop(), getFrameRight(), getFrameBottom());

        if (needsHighlight()) {
            g.setColor(selectColor);
            g.drawRect(boundingBox.x, boundingBox.y,
                      boundingBox.width, boundingBox.height);
        }
    }
    
    private void drawParticipant(Graphics g, Participant p, int topY) {
        if (p.isActor) {
            drawActor(g, p, topY);
        } else {
            drawParticipantBox(g, p, topY);
        }
    }

    private void drawCenteredMaskedString(Graphics g, String text, int centerX, int baselineY) {
        int textWidth = (int) g.context.measureText(text).getWidth();
        drawMaskedString(g, text, centerX - textWidth / 2, baselineY);
    }

    private void drawMaskedString(Graphics g, String text, int textX, int baselineY) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int textWidth = (int) g.context.measureText(text).getWidth();
        g.context.setFillStyle(bgColor);
        g.context.fillRect(textX - 3, baselineY - 12, textWidth + 6, 16);
        g.setColor("#000000");
        g.drawString(text, textX, baselineY);
    }

    private int getTopParticipantBottomY(int topY) {
        int bottomY = topY;
        for (int i = 0; i < participants.size(); i++) {
            bottomY = Math.max(bottomY, getParticipantConnectorBottom(participants.get(i), topY));
        }
        return bottomY;
    }

    private int getParticipantConnectorBottom(Participant p, int topY) {
        return p.isActor ? topY + 58 : topY + 30;
    }
    
    private void drawActor(Graphics g, Participant p, int topY) {
        int cx = p.x;
        int radius = 8;
        
        // Draw head (circle)
        g.context.setFillStyle(participantBgColor);
        g.context.beginPath();
        g.context.arc(cx, topY + radius, radius, 0, 2 * Math.PI);
        g.context.fill();
        g.setColor(lineColor);
        g.context.beginPath();
        g.context.arc(cx, topY + radius, radius, 0, 2 * Math.PI);
        g.context.stroke();
        
        // Draw body
        int bodyTop = topY + radius * 2;
        int bodyBot = bodyTop + 27;
        g.drawLine(cx, bodyTop, cx, bodyBot);
        
        // Draw arms
        int armY = bodyTop + 8;
        g.drawLine(cx - 13, armY, cx + 13, armY);
        
        // Draw legs
        g.drawLine(cx, bodyBot, cx - 13, bodyBot + 15);
        g.drawLine(cx, bodyBot, cx + 13, bodyBot + 15);
        
        // Draw name
        drawCenteredMaskedString(g, p.name, cx, bodyBot + 35);
    }
    
    private void drawParticipantBox(Graphics g, Participant p, int topY) {
        int textWidth = (int) g.context.measureText(p.name).getWidth();
        int boxWidth = textWidth + 14;
        int boxHeight = 30;
        int boxX = p.x - boxWidth / 2;
        
        // Draw box with rounded corners
        g.context.setFillStyle(participantBgColor);
        g.context.beginPath();
        int r = 3;  // corner radius
        g.context.moveTo(boxX + r, topY);
        g.context.lineTo(boxX + boxWidth - r, topY);
        g.context.quadraticCurveTo(boxX + boxWidth, topY, boxX + boxWidth, topY + r);
        g.context.lineTo(boxX + boxWidth, topY + boxHeight - r);
        g.context.quadraticCurveTo(boxX + boxWidth, topY + boxHeight, boxX + boxWidth - r, topY + boxHeight);
        g.context.lineTo(boxX + r, topY + boxHeight);
        g.context.quadraticCurveTo(boxX, topY + boxHeight, boxX, topY + boxHeight - r);
        g.context.lineTo(boxX, topY + r);
        g.context.quadraticCurveTo(boxX, topY, boxX + r, topY);
        g.context.closePath();
        g.context.fill();
        
        // Draw border
        g.setColor(lineColor);
        g.context.stroke();
        
        // Draw name
        g.setColor("#000000");
        g.drawString(p.name, p.x - textWidth / 2, topY + 20);
    }
    
    private void drawDashedLine(Graphics g, int x1, int y1, int x2, int y2, double strokeWidth) {
        int dashLength = 5;
        int gapLength = 5;
        
        double dx = x2 - x1;
        double dy = y2 - y1;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double dashGap = dashLength + gapLength;
        int numDashes = (int) (distance / dashGap);
        
        double xInc = dx / distance * dashLength;
        double yInc = dy / distance * dashLength;
        double xGap = dx / distance * gapLength;
        double yGap = dy / distance * gapLength;
        
        double curX = x1;
        double curY = y1;
        g.context.setLineWidth(strokeWidth);
        
        for (int i = 0; i < numDashes; i++) {
            g.drawLine((int) curX, (int) curY, 
                      (int) (curX + xInc), (int) (curY + yInc));
            curX += xInc + xGap;
            curY += yInc + yGap;
        }
        g.context.setLineWidth(1);
    }
    
    // ========== Edit Dialog ==========
    
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("PlantUML Source", 0, -1, -1);
            ei.textArea = new com.google.gwt.user.client.ui.TextArea();
            ei.textArea.setVisibleLines(15);
            ei.textArea.setCharacterWidth(60);
            ei.textArea.setText(plantUmlSource);
            return ei;
        }
        if (n == 1) {
            return new EditInfo("Diagram Width", diagramWidth, 300, 800);
        }
        if (n == 2) {
            return new EditInfo("Diagram Scale", diagramScale, .25, 4);
        }
        return null;
    }
    
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0) {
            plantUmlSource = ei.textArea.getText();
            parseDiagram();
        }
        if (n == 1) {
            diagramWidth = (int) ei.value;
            parseDiagram();
            updateScaleFromFrame();
        }
        if (n == 2) {
            diagramScale = Math.max(.25, ei.value);
            syncFrameToScale();
        }
    }
    
    protected void getInfo(String arr[]) {
        refreshRenderedSourceIfNeeded();
        arr[0] = "Sequence Diagram";
        arr[1] = participants.size() + " participants";
        arr[2] = elements.size() + " elements";
        if (sourceTable != null) {
            arr[3] = "Source: " + sourceTable.getTableTitle();
        } else if (sourceTableName != null) {
            arr[3] = sourceTableName.isEmpty() ? "Source: (auto)" : "Source: " + sourceTableName + " (not found)";
        }
    }

    public String getPlantUmlSource() {
        return plantUmlSource;
    }

    public String getRenderedPlantUmlSource() {
        refreshRenderedSourceIfNeeded();
        return renderedPlantUmlSource;
    }

    public String getSourceTableName() {
        refreshRenderedSourceIfNeeded();
        return sourceTableName;
    }

    public int getDiagramWidth() {
        return diagramWidth;
    }

    public int getDiagramHeight() {
        return diagramHeight;
    }

    public double getDiagramScale() {
        return getFitScaleForFrame();
    }

    public int getRenderedDiagramWidth() {
        return getFrameWidth();
    }

    public int getRenderedDiagramHeight() {
        return getFrameHeight();
    }
}
