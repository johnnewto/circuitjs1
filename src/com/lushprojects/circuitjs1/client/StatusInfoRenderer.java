package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import com.google.gwt.canvas.dom.client.Context2d;
import com.lushprojects.circuitjs1.client.elements.economics.*;
import com.lushprojects.circuitjs1.client.elements.electronics.passives.*;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.LabeledNodeElm;
import com.lushprojects.circuitjs1.client.elements.misc.ScopeElm;
import com.lushprojects.circuitjs1.client.util.Locale;

final class StatusInfoRenderer {
    private final CirSim sim;

    StatusInfoRenderer(CirSim sim) {
        this.sim = sim;
    }

    void drawHintTooltip(Graphics g) {
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (mouseElm == null)
            return;

        String hint = null;
        String valueStr = null;
        String label = null;
        ArrayList<String> tooltipLines = new ArrayList<String>();
        boolean isInScope = false;

        if (mouseElm instanceof LabeledNodeElm) {
            LabeledNodeElm lne = (LabeledNodeElm) mouseElm;
            hint = HintRegistry.getHint(lne.text);
            label = lne.text;
            valueStr = CircuitElm.showFormat.format(lne.volts[0]);
        } else if (mouseElm instanceof EquationTableElm) {
            EquationTableElm ete = (EquationTableElm) mouseElm;
            int hoveredRow = ete.getHoveredRow();
            if (hoveredRow >= 0 && hoveredRow < ete.getRowCount()) {
                hint = "Equation";
                String hintExpandedEquation = ete.getHintExpandedEquationForDisplay(hoveredRow);
                if (hintExpandedEquation != null && !hintExpandedEquation.trim().isEmpty()) {
                    tooltipLines.add(hintExpandedEquation);
                }
            }
        } else if (mouseElm instanceof ScopeElm) {
            ScopeElm se = (ScopeElm) mouseElm;
            CircuitElm plotElm = se.elmScope.getElm();
            if (plotElm instanceof LabeledNodeElm) {
                LabeledNodeElm lne = (LabeledNodeElm) plotElm;
                hint = HintRegistry.getHint(lne.text);
                label = lne.text;
                isInScope = true;
            }
        }

        if (hint != null && !hint.trim().isEmpty()) {
            if (tooltipLines.isEmpty()) {
                String displayText;
                if (sim.getScopeManager().getScopeSelected() != -1 || isInScope)
                    displayText = hint + ":   " + label;
                else
                    displayText = hint + ":   " + label + " = " + valueStr;
                tooltipLines.add(displayText);
            }

            g.context.setFont("500 12px system-ui, -apple-system, sans-serif");
            int hintWidth = 0;
            for (String line : tooltipLines) {
                int lineWidth = (int) g.context.measureText(line).getWidth() + 16;
                if (lineWidth > hintWidth)
                    hintWidth = lineWidth;
            }
            if (hintWidth <= 0)
                return;

            int lineHeight = 16;
            int verticalPadding = 8;
            int hintHeight = verticalPadding * 2 + tooltipLines.size() * lineHeight;
            int radius = 6;

            int tooltipX = sim.getMouseCursorX() - hintWidth / 2;
            int tooltipY = sim.getMouseCursorY() - hintHeight - 10;

            if (tooltipX < 8)
                tooltipX = 8;
            if (tooltipX + hintWidth > sim.canvasWidth - 8)
                tooltipX = sim.canvasWidth - hintWidth - 8;
            if (tooltipY < 8)
                tooltipY = sim.getMouseCursorY() + 20;

            g.context.setShadowColor("rgba(0, 0, 0, 0.25)");
            g.context.setShadowBlur(8);
            g.context.setShadowOffsetX(0);
            g.context.setShadowOffsetY(2);

            g.context.beginPath();
            g.context.moveTo(tooltipX + radius, tooltipY);
            g.context.lineTo(tooltipX + hintWidth - radius, tooltipY);
            g.context.quadraticCurveTo(tooltipX + hintWidth, tooltipY, tooltipX + hintWidth, tooltipY + radius);
            g.context.lineTo(tooltipX + hintWidth, tooltipY + hintHeight - radius);
            g.context.quadraticCurveTo(tooltipX + hintWidth, tooltipY + hintHeight, tooltipX + hintWidth - radius,
                    tooltipY + hintHeight);
            g.context.lineTo(tooltipX + radius, tooltipY + hintHeight);
            g.context.quadraticCurveTo(tooltipX, tooltipY + hintHeight, tooltipX, tooltipY + hintHeight - radius);
            g.context.lineTo(tooltipX, tooltipY + radius);
            g.context.quadraticCurveTo(tooltipX, tooltipY, tooltipX + radius, tooltipY);
            g.context.closePath();

            g.context.setFillStyle("#1e1e2e");
            g.context.fill();

            g.context.setShadowColor("transparent");
            g.context.setShadowBlur(0);
            g.context.setShadowOffsetX(0);
            g.context.setShadowOffsetY(0);

            g.context.setStrokeStyle("#6c7086");
            g.context.setLineWidth(1);
            g.context.stroke();

            g.context.setFillStyle("#cdd6f4");
            int textY = tooltipY + verticalPadding + 12;
            for (int i = 0; i < tooltipLines.size(); i++)
                g.context.fillText(tooltipLines.get(i), tooltipX + 8, textY + i * lineHeight);
        }
    }

    void drawActionSchedulerMessage(Graphics g, Context2d context) {
        ActionScheduler scheduler = ActionScheduler.getInstance();
        if (scheduler != null && scheduler.hasDisplayMessage()) {
            String message = scheduler.getDisplayMessage();
            g.save();
            if (sim.printableCheckItem.getState())
                g.context.setFillStyle("#000000");
            else
                g.context.setFillStyle("#FFFFFF");

            g.context.setFont("bold 24px sans-serif");
            g.context.setTextAlign("center");
            g.context.setTextBaseline("top");

            int centerX = (context == sim.cvcontext) ? sim.circuitArea.width / 2 : (int) (context.getCanvas().getWidth() / 2);
            int topY = 15;

            g.context.fillText(message, centerX, topY);

            g.restore();
        }
    }

    void drawBottomArea(Graphics g) {
        int leftX = 0;
        int h = 0;
        CircuitElm mouseElm = sim.getMouseElmForRouting();
        if (sim.stopMessage == null && sim.scopeCount == 0) {
            leftX = sim.max(sim.canvasWidth - CirSim.infoWidth, 0);
            int h0 = (int) (sim.canvasHeight * sim.getScopeManager().getScopeHeightFraction());
            h = (mouseElm == null) ? 70 : h0;
            if (sim.hideInfoBox)
                h = 0;
        }
        if (sim.stopMessage != null && sim.circuitArea.height > sim.canvasHeight - 30)
            h = 30;
        g.setColor(sim.printableCheckItem.getState() ? "#eee" : "#202020");
        g.fillRect(leftX, sim.circuitArea.height - h, sim.circuitArea.width, sim.canvasHeight - sim.circuitArea.height + h);
        g.setFont(CircuitElm.unitsFont);
        int ct = sim.scopeCount;
        if (sim.stopMessage != null)
            ct = 0;
        int i;
        Scope.clearCursorInfo();
        for (i = 0; i != ct; i++)
            sim.scopes[i].selectScope(sim.getMouseCursorX(), sim.getMouseCursorY(), sim.isDragging());
        if (sim.scopeElmArr != null)
            for (i = 0; i != sim.scopeElmArr.length; i++)
                sim.scopeElmArr[i].selectScope(sim.getMouseCursorX(), sim.getMouseCursorY(), sim.isDragging());
        for (i = 0; i != ct; i++)
            sim.scopes[i].draw(g);
        if (sim.isMouseWasOverSplitter()) {
            g.setColor(CircuitElm.selectColor);
            g.setLineWidth(4.0);
            g.drawLine(0, sim.circuitArea.height - 2, sim.circuitArea.width, sim.circuitArea.height - 2);
            g.setLineWidth(1.0);
        }
        if (sim.scopeCount > 0 && sim.getScopeManager().mouseIsOverScopeMinMaxButton(sim.getMouseCursorX(), sim.getMouseCursorY())) {
            int lineEndX = sim.circuitArea.width - CirSim.SCOPE_MIN_MAX_BUTTON_SIZE - 20;
            g.setColor(CircuitElm.selectColor);
            g.setLineWidth(3.0);
            g.drawLine(0, sim.circuitArea.height - 2, lineEndX, sim.circuitArea.height - 2);
            g.setLineWidth(1.0);
        }
        if (sim.scopeCount > 0)
            sim.getScopeManager().drawScopeMinMaxButton(g);
        g.setColor(CircuitElm.whiteColor);

        if (sim.stopMessage != null) {
            g.drawString(sim.stopMessage, 10, sim.canvasHeight - 10);
        } else if (!sim.hideInfoBox) {
            String info[] = new String[10];
            int infoIdx = 0;

            if (mouseElm != null) {
                if (sim.getMousePost() == -1) {
                    String[] elmInfo = new String[10];
                    mouseElm.getInfo(elmInfo);
                    for (int idx = 0; idx < elmInfo.length && elmInfo[idx] != null; idx++)
                        info[infoIdx++] = Locale.LS(elmInfo[idx]);
                } else {
                    info[infoIdx++] = "V = " + CircuitElm.getUnitText(mouseElm.getPostVoltage(sim.getMousePost()), "V");
                    String nodeName = LabeledNodeElm.getNameByNode(mouseElm.nodes[sim.getMousePost()]);
                    if (nodeName != null)
                        info[infoIdx++] = "Node: " + nodeName;
                }
            } else {
                info[0] = Locale.LS("time step = ") + CircuitElm.getUnitText(sim.getTimeStep(), "s");
            }
            if (sim.hintType != -1) {
                for (i = 0; info[i] != null; i++)
                    ;
                String s = getHint();
                if (s == null)
                    sim.hintType = -1;
                else
                    info[i] = s;
            }
            int x = leftX + 5;
            if (ct != 0)
                x = sim.scopes[ct - 1].rightEdge();

            int lineCount = 0;
            for (lineCount = 0; info[lineCount] != null; lineCount++)
                ;
            int badnodes = sim.badConnectionList.size();
            if (badnodes > 0)
                info[lineCount++] = badnodes
                        + ((badnodes == 1) ? Locale.LS(" bad connection") : Locale.LS(" bad connections"));
            if (sim.savedFlag)
                info[lineCount++] = "(saved)";

            int snapX = sim.snapGrid(sim.inverseTransformX(sim.getMouseCursorX()));
            int snapY = sim.snapGrid(sim.inverseTransformY(sim.getMouseCursorY()));
            info[lineCount++] = "cursor: (" + snapX + ", " + snapY + ")";

            info[lineCount++] = "EqnTable: " + (sim.isEquationTableMnaMode() ? "MNA" : "Computed");

            int requiredHeight = 15 * (lineCount + 1);
            int availableHeight = sim.canvasHeight - (sim.circuitArea.height - h);

            int ybase = sim.circuitArea.height - h;
            if (requiredHeight > availableHeight)
                ybase = sim.canvasHeight - requiredHeight;

            for (i = 0; info[i] != null; i++)
                g.drawString(info[i], x, ybase + 15 * (i + 1));
        }
        if (sim.stopMessage == null && sim.warningMessage != null && !sim.warningMessage.isEmpty()) {
            g.setColor(Color.red);
            g.drawString(sim.warningMessage, 10, sim.canvasHeight - 10);
            g.setColor(CircuitElm.whiteColor);
        }
    }

    Color getBackgroundColor() {
        if (sim.printableCheckItem.getState())
            return Color.white;
        return Color.black;
    }

    void updateEquationParameterCollisionWarning() {
        if (sim.elmList == null || sim.elmList.isEmpty()) {
            sim.warningMessage = null;
            return;
        }

        HashSet<String> labeledNames = new HashSet<String>();
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof LabeledNodeElm) {
                LabeledNodeElm lne = (LabeledNodeElm) ce;
                if (lne.text != null) {
                    String name = lne.text.trim();
                    if (!name.isEmpty())
                        labeledNames.add(name);
                }
            }
        }

        if (labeledNames.isEmpty()) {
            sim.warningMessage = null;
            return;
        }

        HashSet<String> collisions = new HashSet<String>();
        for (int i = 0; i < sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (!(ce instanceof EquationTableElm))
                continue;

            EquationTableElm table = (EquationTableElm) ce;
            int rows = table.getRowCount();
            for (int row = 0; row < rows; row++) {
                if (table.getOutputMode(row) != EquationTableElm.RowOutputMode.PARAM_MODE)
                    continue;
                String outputName = table.getOutputName(row);
                if (outputName == null)
                    continue;
                String paramName = outputName.trim();
                if (!paramName.isEmpty() && labeledNames.contains(paramName))
                    collisions.add(paramName);
            }
        }

        if (collisions.isEmpty()) {
            sim.warningMessage = null;
            return;
        }

        ArrayList<String> sorted = new ArrayList<String>(collisions);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        sb.append("Warning: PARAM/LabeledNode name collision: ");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(sorted.get(i));
        }
        sim.warningMessage = sb.toString();
    }

    String getHint() {
        CircuitElm c1 = sim.getElm(sim.hintItem1);
        CircuitElm c2 = sim.getElm(sim.hintItem2);
        if (c1 == null || c2 == null)
            return null;
        if (sim.hintType == CirSim.HINT_LC) {
            if (!(c1 instanceof InductorElm))
                return null;
            if (!(c2 instanceof CapacitorElm))
                return null;
            InductorElm ie = (InductorElm) c1;
            CapacitorElm ce = (CapacitorElm) c2;
            return Locale.LS("res.f = ")
                    + CircuitElm.getUnitText(1 / (2 * CirSim.pi * Math.sqrt(ie.getInductance() * ce.getCapacitance())), "Hz");
        }
        if (sim.hintType == CirSim.HINT_RC) {
            if (!(c1 instanceof ResistorElm))
                return null;
            if (!(c2 instanceof CapacitorElm))
                return null;
            ResistorElm re = (ResistorElm) c1;
            CapacitorElm ce = (CapacitorElm) c2;
            return "RC = " + CircuitElm.getUnitText(re.getResistance() * ce.getCapacitance(), "s");
        }
        if (sim.hintType == CirSim.HINT_3DB_C) {
            if (!(c1 instanceof ResistorElm))
                return null;
            if (!(c2 instanceof CapacitorElm))
                return null;
            ResistorElm re = (ResistorElm) c1;
            CapacitorElm ce = (CapacitorElm) c2;
            return Locale.LS("f.3db = ")
                    + CircuitElm.getUnitText(1 / (2 * CirSim.pi * re.getResistance() * ce.getCapacitance()), "Hz");
        }
        if (sim.hintType == CirSim.HINT_3DB_L) {
            if (!(c1 instanceof ResistorElm))
                return null;
            if (!(c2 instanceof InductorElm))
                return null;
            ResistorElm re = (ResistorElm) c1;
            InductorElm ie = (InductorElm) c2;
            return Locale.LS("f.3db = ")
                    + CircuitElm.getUnitText(re.getResistance() / (2 * CirSim.pi * ie.getInductance()), "Hz");
        }
        if (sim.hintType == CirSim.HINT_TWINT) {
            if (!(c1 instanceof ResistorElm))
                return null;
            if (!(c2 instanceof CapacitorElm))
                return null;
            ResistorElm re = (ResistorElm) c1;
            CapacitorElm ce = (CapacitorElm) c2;
            return Locale.LS("fc = ")
                    + CircuitElm.getUnitText(1 / (2 * CirSim.pi * re.getResistance() * ce.getCapacitance()), "Hz");
        }
        return null;
    }
}
