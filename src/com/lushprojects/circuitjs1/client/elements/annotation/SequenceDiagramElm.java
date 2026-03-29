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
import com.lushprojects.circuitjs1.client.util.*;
import com.lushprojects.circuitjs1.client.ui.EditInfo;

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
    
    private String plantUmlSource;
    private Vector<String> lines;
    
    // Parsed diagram elements
    private Vector<Participant> participants;
    private Vector<DiagramElement> elements;
    private String titleLine1;
    private String titleLine2;
    
    // Layout constants
    private int fontSize = 13;
    private int participantWidth = 100;
    private int participantSpacing = 200;
    private int lifelineStartY = 135;
    private int messageSpacing = 30;
    private int noteWidth = 150;
    private int dividerHeight = 30;
    
    // Calculated layout
    private int diagramWidth = 560;
    private int diagramHeight = 1000;
    private int currentY;
    
    // Colors
    private String bgColor = "#FFFFFF";
    private String participantBgColor = "#E2E2F0";
    private String noteBgColor = "#FEFFDD";
    private String lineColor = "#181818";
    private String dividerColor = "#000000";
    private String dividerBgColor = "#EEEEEE";
    
    public SequenceDiagramElm(int xx, int yy) {
        super(xx, yy);
        plantUmlSource = getDefaultDiagram();
        parseDiagram();
    }
    
    public SequenceDiagramElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        if (st.hasMoreTokens()) {
            plantUmlSource = CustomLogicModel.unescape(st.nextToken());
        } else {
            plantUmlSource = getDefaultDiagram();
        }
        parseDiagram();
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
        return super.dump() + " " + CustomLogicModel.escape(plantUmlSource);
    }
    
    protected int getDumpType() { return 467; }  // Unique dump type
    
    protected void drag(int xx, int yy) {
        x = xx;
        y = yy;
        x2 = xx + 16;
        y2 = yy;
    }
    
    // ========== PlantUML Parsing ==========
    
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
            
            // Draw label above arrow
            if (label != null && !label.isEmpty()) {
                String[] labelLines = label.split("\\\\n");
                int labelY = y - 5 - (labelLines.length - 1) * 15;
                int labelX = Math.min(x1, x2) + Math.abs(x2 - x1) / 2;
                
                g.setColor("#000000");
                for (String line : labelLines) {
                    int textWidth = (int) g.context.measureText(line).getWidth();
                    g.drawString(line, labelX - textWidth / 2, labelY);
                    labelY += 15;
                }
            }
            
            // Draw arrow line
            g.setColor(elm.lineColor);
            if (dashed) {
                elm.drawDashedLine(g, x1, y, x2, y);
            } else {
                g.drawLine(x1, y, x2, y);
            }
            
            // Draw arrowhead
            int arrowDir = (x2 > x1) ? 1 : -1;
            int arrowX = x2 - (10 * arrowDir);
            g.context.beginPath();
            g.context.moveTo(x2, y);
            g.context.lineTo(arrowX, y - 4);
            g.context.lineTo(arrowX, y + 4);
            g.context.closePath();
            g.context.fill();
        }
        
        int getHeight() {
            if (label == null) return 40;
            String[] lines = label.split("\\\\n");
            return 25 + lines.length * 15;
        }
    }
    
    private static class Divider extends DiagramElement {
        String label;
        
        Divider(String label) {
            this.label = label;
        }
        
        void draw(Graphics g, SequenceDiagramElm elm, int y) {
            int left = elm.x + 5;
            int right = elm.x + elm.diagramWidth - 5;
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
                noteX = elm.x + 50;
                noteWidth = elm.diagramWidth - 100;
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
        participants = new Vector<Participant>();
        elements = new Vector<DiagramElement>();
        titleLine1 = null;
        titleLine2 = null;
        
        String[] sourceLines = plantUmlSource.split("\n");
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
        
        // Calculate spacing based on number of participants
        participantSpacing = Math.max(150, diagramWidth / (numParticipants + 1));
        
        for (int i = 0; i < numParticipants; i++) {
            Participant p = participants.get(i);
            p.x = x + participantSpacing * (i + 1);
        }
        
        // Calculate diagram height based on content
        int contentHeight = lifelineStartY;
        for (int i = 0; i < elements.size(); i++) {
            contentHeight += elements.get(i).getHeight();
        }
        diagramHeight = contentHeight + 100;  // Extra space for participant footers
    }
    
    // ========== Drawing ==========
    
    protected void draw(Graphics g) {
        g.save();
        
        // Recalculate positions relative to element position
        calculateLayout();
        
        // Set font
        Font f = new Font("SansSerif", 0, fontSize);
        g.setFont(f);
        
        // Draw background
        if (sim.printableCheckItem.getState()) {
            bgColor = "#FFFFFF";
        }
        g.context.setFillStyle(bgColor);
        g.context.fillRect(x, y, diagramWidth, diagramHeight);
        
        // Draw title
        int titleY = y + 30;
        if (titleLine1 != null) {
            g.setColor("#000000");
            Font titleFont = new Font("SansSerif", Font.BOLD, 14);
            g.setFont(titleFont);
            int textWidth = (int) g.context.measureText(titleLine1).getWidth();
            g.drawString(titleLine1, x + (diagramWidth - textWidth) / 2, titleY);
            titleY += 17;
        }
        if (titleLine2 != null) {
            g.setColor("#000000");
            Font titleFont = new Font("SansSerif", Font.BOLD, 14);
            g.setFont(titleFont);
            int textWidth = (int) g.context.measureText(titleLine2).getWidth();
            g.drawString(titleLine2, x + (diagramWidth - textWidth) / 2, titleY);
        }
        
        // Reset font for rest of diagram
        g.setFont(f);
        
        // Draw participants at top
        int headerY = y + 60;
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            drawParticipant(g, p, headerY);
        }
        
        // Draw lifelines
        currentY = y + lifelineStartY;
        int lifelineEndY = y + diagramHeight - 80;
        g.setColor(lineColor);
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            drawDashedLine(g, p.x, currentY, p.x, lifelineEndY);
        }
        
        // Draw diagram elements (messages, dividers, notes)
        currentY = y + lifelineStartY + 30;
        for (int i = 0; i < elements.size(); i++) {
            DiagramElement elem = elements.get(i);
            elem.draw(g, this, currentY);
            currentY += elem.getHeight();
        }
        
        // Draw participants at bottom
        int footerY = lifelineEndY + 10;
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            drawParticipant(g, p, footerY);
        }
        
        // Set bounding box
        setBbox(x, y, x + diagramWidth, y + diagramHeight);
        
        // Highlight if selected
        if (needsHighlight()) {
            g.setColor(selectColor);
            g.drawRect(boundingBox.x, boundingBox.y, 
                      boundingBox.width, boundingBox.height);
        }
        
        x2 = x + diagramWidth;
        y2 = y + diagramHeight;
        
        g.restore();
    }
    
    private void drawParticipant(Graphics g, Participant p, int topY) {
        if (p.isActor) {
            drawActor(g, p, topY);
        } else {
            drawParticipantBox(g, p, topY);
        }
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
        g.setColor("#000000");
        int textWidth = (int) g.context.measureText(p.name).getWidth();
        g.drawString(p.name, cx - textWidth / 2, bodyBot + 35);
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
    
    private void drawDashedLine(Graphics g, int x1, int y1, int x2, int y2) {
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
        
        for (int i = 0; i < numDashes; i++) {
            g.drawLine((int) curX, (int) curY, 
                      (int) (curX + xInc), (int) (curY + yInc));
            curX += xInc + xGap;
            curY += yInc + yGap;
        }
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
        }
    }
    
    protected void getInfo(String arr[]) {
        arr[0] = "Sequence Diagram";
        arr[1] = participants.size() + " participants";
        arr[2] = elements.size() + " elements";
    }
}
