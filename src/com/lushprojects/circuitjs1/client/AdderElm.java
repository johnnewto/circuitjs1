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
        setupPins();
    }


    int getDumpType() { return 251; }


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
    }
}