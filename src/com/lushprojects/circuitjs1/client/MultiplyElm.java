/*    
    Copyright (C) Paul Falstad
    
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

import com.google.gwt.canvas.client.Canvas;
import com.lushprojects.circuitjs1.client.util.Locale;

	class MultiplyElm extends VCCSElm {
		public MultiplyElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
		super(xa, ya, xb, yb, f, st);
	}

	public MultiplyElm(int xx, int yy) {
	    super(xx, yy);
	    exprString = "a";
		int i;
		for (i = 1; i != inputCount; i++)
			exprString += "*"+(char)('a'+i);
	 	parseExpr();
	    setupPins();
		// setSize(sim.smallGridCheckItem.getState() ? 1 : 2);


	}
	
	void setupPins() {
		// V- is internal, always node 0 (ground)
	    sizeX = 2;
            sizeY = inputCount > 2 ? inputCount : 2;
            pins = new Pin[inputCount+2];
	    int i;
	    for (i = 0; i != inputCount; i++) {
			// pins[i] = new Pin(i, SIDE_W, Character.toString((char)('A'+i)));
			pins[i] = new Pin(i, SIDE_W, ""); 
			// Posts closer together: use smaller spacing
        	// pins[i].y = (i * sizeY) / (inputCount - 1 < 1 ? 1 : inputCount - 1);
		}
	    pins[inputCount] = new Pin(0, SIDE_E, exprString);
	    pins[inputCount].output = true;
        // pins[inputCount+1] = new Pin(1, SIDE_E, "V-"); no V- pin
	    lastVolts = new double[inputCount];
	    exprState = new ExprState(inputCount);
	    allocNodes();
	}
	String getChipName() { return "Multipy"; } 
	
	void stamp() {
            int vn = pins[inputCount].voltSource + sim.nodeList.size();
            sim.stampNonLinear(vn);
            sim.stampVoltageSource(0, nodes[inputCount], pins[inputCount].voltSource);
	}

	void doStep() {
		int i;
		// converged yet?
		double convergeLimit = getConvergeLimit();
		for (i = 0; i != inputCount; i++) {
            double diff = Math.abs(volts[i]-lastVolts[i]);
			if (diff > convergeLimit)
				sim.converged = false;
			// if (Double.isNaN(volts[i]))
			//    volts[i] = 0;
		}
		int vn = pins[inputCount].voltSource + sim.nodeList.size();
		if (expr != null) {
			// calculate output
			for (i = 0; i != inputCount; i++)
				exprState.values[i] = volts[i];

			exprState.t = sim.t;
			double v0 = expr.eval(exprState);
			double vMinus = 0; // V- is always ground
			if (Math.abs(volts[inputCount]-vMinus-v0) > Math.abs(v0)*.01 && sim.subIterations < 100)
				sim.converged = false;
			double rs = v0;
			
			// calculate and stamp output derivatives
			for (i = 0; i != inputCount; i++) {
				double dv = volts[i]-lastVolts[i];
				if (Math.abs(dv) < 1e-6)
				dv = 1e-6;
				exprState.values[i] = volts[i];
				double v = expr.eval(exprState);
				exprState.values[i] = volts[i]-dv;
				double v2 = expr.eval(exprState);
				double dx = (v-v2)/dv;
				if (Math.abs(dx) < 1e-6)
				dx = sign(dx, 1e-6);
	//        	    if (sim.subIterations > 1)
	//        		sim.console("ccedx " + i + " " + dx + " v " + v + " v2 " + v2 + " dv " + dv + " lv " + lastVolts[i] + " " + volts[i] + " " + sim.subIterations + " " + sim.t);
				sim.stampMatrix(vn,  nodes[i], -dx);
				// adjust right side
				rs -= dx*volts[i];
				exprState.values[i] = volts[i];
			}
			
			sim.stampRightSide(vn, rs);
		}

		for (i = 0; i != inputCount; i++)
			lastVolts[i] = volts[i];
	}

	void stepFinished() {
		double vMinus = 0; // V- is always ground
		exprState.updateLastValues(volts[inputCount]-vMinus);
	}

	void draw(Graphics g) {
		drawChip(g);
		String label = "X"; // Or any dynamic text you want
		// Calculate midpoint using rectPointsX and rectPointsY arrays
		int mid_x = (rectPointsX[0] + rectPointsX[1] + rectPointsX[2] + rectPointsX[3]) / 4;
		int mid_y = (rectPointsY[0] + rectPointsY[1] + rectPointsY[2] + rectPointsY[3]) / 4;

		// Save current font
		// java.awt.Font oldFont = g.getFont();
		// Make a new font 2x larger
		// java.awt.Font newFont = oldFont.deriveFont(oldFont.getSize2D() * 2.0f);
		boolean selected = needsHighlight();
	    Font f = new Font("SansSerif", selected ? Font.BOLD : 0, 20);
	    g.setFont(f);
	    g.setColor(selected ? selectColor : whiteColor);
	    // String s = showVoltage() ? getUnitTextWithScale(volts[0], "V", scale, isFixed()) : Locale.LS("out");
	

		drawCenteredText(g, label, mid_x, mid_y, true);

		// Restore original font
		g.restore();
	}

	int getPostCount() { return inputCount+1; } // since V- is not a post anymore):
	int getVoltageSourceCount() { return 1; }
	int getDumpType() { return 250; }

	boolean hasCurrentOutput() { return false; }

	void setCurrent(int vn, double c) {
		if (pins[inputCount].voltSource == vn) {
			pins[inputCount].current = c;
			// pins[inputCount+1].current = -c; // no V- pin V- is always ground 
		}
	}

	public EditInfo getChipEditInfo(int n) {
		// if (n == 0) {
		// 	EditInfo ei = new EditInfo(EditInfo.makeLink("customfunction.html", "Output Function"), 0, -1, -1);
		// 	ei.text = exprString;
		// 	ei.disallowSliders();
		// 	return ei;
		// }
		if (n == 0)
			return new EditInfo("# of Inputs", inputCount, 1, 8).
				setDimensionless();
		return null;
	}
	public void setChipEditValue(int n, EditInfo ei) {
		// if (n == 0) {
		// 	exprString = ei.textf.getText();
		// 	parseExpr();
		// 	return;
		// }
		if (n == 0) {
			if (ei.value < 0 || ei.value > 4)
				return;
			inputCount = (int) ei.value;
			exprString = "a";
			int i;
			for (i = 1; i != inputCount; i++)
				exprString += "*"+(char)('a'+i);
			setupPins();
			allocNodes();
			setPoints();
		}
	}

}