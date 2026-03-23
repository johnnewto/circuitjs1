/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.core.SimulationContext;
import com.lushprojects.circuitjs1.client.util.Locale;

class ScenarioElm extends CircuitElm {
    static final int MODE_ADD = 0;
    static final int MODE_MULTIPLY = 1;
    static final int MODE_REPLACE = 2;

    String targetName;
    int mode;
    double startTime;
    double endTime;
    double magnitude;
    boolean enabled;
    boolean resetPlotsOnActivate;
    boolean openPlotlyOnActivate;

    private boolean wasActive;
    private String sourceKey;

    public ScenarioElm(int xx, int yy) {
        super(xx, yy);
        targetName = "alpha0";
        mode = MODE_ADD;
        startTime = 20;
        endTime = -1;
        magnitude = 10;
        enabled = true;
        resetPlotsOnActivate = true;
        openPlotlyOnActivate = false;
        initSourceKey();
    }

    public ScenarioElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        targetName = "alpha0";
        mode = MODE_ADD;
        startTime = 20;
        endTime = -1;
        magnitude = 10;
        enabled = true;
        resetPlotsOnActivate = true;
        openPlotlyOnActivate = false;

        if (st.hasMoreTokens())
            targetName = CustomLogicModel.unescape(st.nextToken());
        if (st.hasMoreTokens())
            mode = Integer.parseInt(st.nextToken());
        if (st.hasMoreTokens())
            startTime = Double.parseDouble(st.nextToken());
        if (st.hasMoreTokens())
            endTime = Double.parseDouble(st.nextToken());
        if (st.hasMoreTokens())
            magnitude = Double.parseDouble(st.nextToken());
        if (st.hasMoreTokens())
            enabled = Boolean.parseBoolean(st.nextToken());
        if (st.hasMoreTokens())
            resetPlotsOnActivate = Boolean.parseBoolean(st.nextToken());
        if (st.hasMoreTokens())
            openPlotlyOnActivate = Boolean.parseBoolean(st.nextToken());

        initSourceKey();
    }

    private void initSourceKey() {
        sourceKey = "ScenarioElm@" + Integer.toHexString(hashCode());
    }

    @Override
    int getDumpType() {
        return 236;
    }

    @Override
    String dump() {
        return super.dump() + " " +
            CustomLogicModel.escape(targetName) + " " +
            mode + " " +
            startTime + " " +
            endTime + " " +
            magnitude + " " +
            enabled + " " +
            resetPlotsOnActivate + " " +
            openPlotlyOnActivate;
    }

    @Override
    int getPostCount() {
        return 0;
    }

    @Override
    boolean nonLinear() {
        return true;
    }

    @Override
    void stamp() {
    }

    @Override
    void reset() {
        wasActive = false;
        clearOverride();
    }

    @Override
    void delete() {
        clearOverride();
        super.delete();
    }

    private void clearOverride() {
        if (sourceKey != null && targetName != null) {
            ComputedValues.clearScenarioOverride(targetName, sourceKey);
        }
    }

    @Override
    void doStep() {
        SimulationContext context = getSimulationContext();
        boolean active = false;
        if (enabled) {
            double t = context.getTime();
            boolean inStart = (t >= startTime);
            boolean inEnd = (endTime < 0 || t <= endTime);
            active = inStart && inEnd;
        }

        if (targetName != null && !targetName.isEmpty()) {
            ComputedValues.setScenarioOverride(targetName, sourceKey, mode, magnitude, active);
        }

        if (active && !wasActive) {
            sim.onScenarioActivated(resetPlotsOnActivate, openPlotlyOnActivate);
        }

        wasActive = active;
    }

    @Override
    void draw(Graphics g) {
        g.save();
        boolean selected = needsHighlight();
        int cx = (x + x2) / 2;
        int cy = (y + y2) / 2;

        int width = 240;
        int height = 84;
        int left = cx - width/2;
        int top = cy - height/2;

        g.setColor(enabled ? new Color(232, 245, 233) : new Color(224, 224, 224));
        g.fillRect(left, top, width, height);

        g.setColor(selected ? selectColor : Color.black);
        g.setLineWidth(2.0);
        g.drawRect(left, top, width, height);

        g.setColor(selected ? selectColor : whiteColor);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        drawCenteredText(g, Locale.LS("Scenario"), cx, top + 16, true);

        g.setFont(new Font("SansSerif", 0, 12));
        String target = "target=" + targetName;
        String modeText = "mode=" + modeToString(mode) + " mag=" + getShortUnitText(magnitude, "");
        String windowText = "t=[" + CircuitElm.showFormat.format(startTime) + ", " + (endTime < 0 ? "∞" : CircuitElm.showFormat.format(endTime)) + "]";
        drawCenteredText(g, target, cx, top + 34, true);
        drawCenteredText(g, modeText, cx, top + 50, true);
        drawCenteredText(g, windowText, cx, top + 66, true);

        if (!enabled) {
            g.setColor(selected ? selectColor : Color.gray);
            g.drawLine(left + 8, top + 8, left + width - 8, top + height - 8);
        }

        setBbox(left - 2, top - 2, left + width + 2, top + height + 2);
        g.restore();
    }

    private String modeToString(int m) {
        if (m == MODE_MULTIPLY)
            return "MULTIPLY";
        if (m == MODE_REPLACE)
            return "REPLACE";
        return "ADD";
    }

    @Override
    void getInfo(String arr[]) {
        arr[0] = "scenario";
        arr[1] = "enabled = " + (enabled ? "yes" : "no");
        arr[2] = "target = " + targetName;
        arr[3] = "mode = " + modeToString(mode);
        arr[4] = "magnitude = " + getShortUnitText(magnitude, "");
        arr[5] = "window = [" + getUnitText(startTime, "s") + ", " + (endTime < 0 ? "∞" : getUnitText(endTime, "s")) + "]";
    }

    @Override
    public EditInfo getEditInfo(int n) {
        if (n == 0) {
            EditInfo ei = new EditInfo("Target Name", 0);
            ei.text = targetName;
            return ei;
        }
        if (n == 1)
            return new EditInfo("Mode (0=ADD, 1=MULTIPLY, 2=REPLACE)", mode, 0, 2);
        if (n == 2)
            return new EditInfo("Start Time", startTime, 0, 1e9);
        if (n == 3)
            return new EditInfo("End Time (-1 = forever)", endTime, -1, 1e9);
        if (n == 4)
            return new EditInfo("Magnitude", magnitude, -1e9, 1e9);
        if (n == 5) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Enabled", enabled);
            return ei;
        }
        if (n == 6) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Reset Plots on Activate", resetPlotsOnActivate);
            return ei;
        }
        if (n == 7) {
            EditInfo ei = new EditInfo("", 0, -1, -1);
            ei.checkbox = new Checkbox("Open Plotly Viewer on Activate", openPlotlyOnActivate);
            return ei;
        }
        return null;
    }

    @Override
    public void setEditValue(int n, EditInfo ei) {
        if (n == 0)
            targetName = ei.textf.getText().trim();
        if (n == 1)
            mode = Math.min(2, Math.max(0, (int) ei.value));
        if (n == 2)
            startTime = ei.value;
        if (n == 3)
            endTime = ei.value;
        if (n == 4)
            magnitude = ei.value;
        if (n == 5)
            enabled = ei.checkbox.getState();
        if (n == 6)
            resetPlotsOnActivate = ei.checkbox.getState();
        if (n == 7)
            openPlotlyOnActivate = ei.checkbox.getState();
    }

    @Override
    void setNodeVoltage(int n, double c) {
    }
}
