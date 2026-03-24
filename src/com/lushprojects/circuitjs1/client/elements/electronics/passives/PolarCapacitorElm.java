package com.lushprojects.circuitjs1.client.elements.electronics.passives;

import com.lushprojects.circuitjs1.client.ui.EditInfo;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;

public class PolarCapacitorElm extends CapacitorElm {
    	double maxNegativeVoltage;
    	
	public PolarCapacitorElm(int xx, int yy) {
	    super(xx, yy);
	    maxNegativeVoltage = 1;
	}
	public PolarCapacitorElm(int xa, int ya, int xb, int yb, int f,
			    StringTokenizer st) {
	    super(xa, ya, xb, yb, f, st);
	    maxNegativeVoltage = Double.parseDouble(st.nextToken());
	}
	protected int getDumpType() { return 209; }
	protected String dump() {
	    return super.dump() + " " + maxNegativeVoltage;
	}
	
	Point plusPoint;
	
	protected void setPoints() {
	    super.setPoints();
	    double f = (dn/2-4)/dn;
	    int i;
	    platePoints = newPointArray(14);
	    int maxI = platePoints.length-1;
	    double midI = maxI/2.;
	    for (i = 0; i <= maxI; i++) {
		double q = (i - midI)*.9/midI;
		platePoints[i] = interpPoint(plate2[0], plate2[1], i/(double) maxI, 5*(1-Math.sqrt(1-q*q)));
	    }
	    plusPoint = interpPoint(point1, point2, f-8/dn, -10*dsign);
	    if (y2 > y)
		plusPoint.y += 4;
	    if (y > y2)
		plusPoint.y += 3;
	}
	
	protected void draw(Graphics g) {
	    super.draw(g);
            g.setColor(whiteColor);
            g.setFont(unitsFont);
            int w = (int)g.context.measureText("+").getWidth();;
            g.drawString("+", plusPoint.x-w/2, plusPoint.y);
	}
	protected void getInfo(String arr[]) {
	    super.getInfo(arr);
	    arr[0] = "capacitor (polarized)";
	}
	public EditInfo getEditInfo(int n) {
	    if (n == 4)
		return new EditInfo("Max Reverse Voltage", maxNegativeVoltage, 0, 0);
	    return super.getEditInfo(n);
	}
	public void setEditValue(int n, EditInfo ei) {
	    if (n == 4 && ei.value >= 0)
		maxNegativeVoltage = ei.value;
	    super.setEditValue(n, ei);
	}
	
	protected void stepFinished() {
	    if (getVoltageDiff() < 0 && getVoltageDiff() < -maxNegativeVoltage)
		sim.stop("capacitor exceeded max reverse voltage", this);
	    super.stepFinished();
	}
	protected int getShortcut() { return 'C'; }
}
