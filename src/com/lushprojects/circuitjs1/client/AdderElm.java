/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

// import com.google.gwt.canvas.client.Canvas;
// import com.lushprojects.circuitjs1.client.util.Locale;

class AdderElm extends MultiplyElm {
    public AdderElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f, st);
    }
    public AdderElm(int xx, int yy) {
        super(xx, yy);
        exprString = "a";
        for (int i = 1; i != inputCount; i++)
            exprString += "+" + (char)('a' + i);
        parseExpr();
        // Override the size set by parent to be small by default
        flags |= FLAG_SMALL; // Set flag to persist small size
        setSize(1); // Set to small size
        setPoints(); // Recalculate points with new size
    }


    int getDumpType() { return 251; }

    void draw(Graphics g) {
        drawChip(g);
        String label = "+"; // Adder symbol instead of X
        // Calculate midpoint using rectPointsX and rectPointsY arrays
        int mid_x = (rectPointsX[0] + rectPointsX[1] + rectPointsX[2] + rectPointsX[3]) / 4;
        int mid_y = (rectPointsY[0] + rectPointsY[1] + rectPointsY[2] + rectPointsY[3]) / 4;

        boolean selected = needsHighlight();
        Font f = new Font("SansSerif", selected ? Font.BOLD : 0, 30);
        g.setFont(f);
        g.setColor(selected ? selectColor : whiteColor);

        drawCenteredText(g, label, mid_x, mid_y, true);

        // Restore original font
        g.restore();
    }

    public void setChipEditValue(int n, EditInfo ei) {
        if (n == 0) {
            if (ei.value < 0 || ei.value > 4)
                return;
            inputCount = (int) ei.value;
            exprString = "a";
            for (int i = 1; i != inputCount; i++)
                exprString += "+" + (char)('a' + i);
            setupPins();
            allocNodes();
            setPoints();
        }
        if (n == 1) {
            flags = ei.changeFlag(flags, FLAG_SMALL);
            setSize((flags & FLAG_SMALL) != 0 ? 1 : 2);
            setupPins();
            allocNodes();
            setPoints();
        }
    }
}