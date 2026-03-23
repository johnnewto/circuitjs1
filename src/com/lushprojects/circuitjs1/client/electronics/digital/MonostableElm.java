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

package com.lushprojects.circuitjs1.client.electronics.digital;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.core.SimulationContext;

    public class MonostableElm extends ChipElm {

	//Used to detect rising edge
	private boolean prevInputValue=false;
	private boolean retriggerable=false;
	private boolean triggered=false;
	private double lastRisingEdge=0;
	private double delay=0.01;

	public MonostableElm(int xx, int yy) {
	    super(xx, yy);
	    reset();
	}
	public MonostableElm(int xa, int ya, int xb, int yb, int f,
			    StringTokenizer st) {
	    super(xa, ya, xb, yb, f, st);
	    retriggerable=Boolean.parseBoolean(st.nextToken());
	    delay=Double.parseDouble(st.nextToken());
	    reset();
	}
	protected String getChipName() { return "Monostable"; }
	protected void setupPins() {
	    sizeX = 2;
	    sizeY = 2;
	    pins = new Pin[getPostCount()];
	    pins[0] = new Pin(0, SIDE_W, "");
	    pins[0].clock = true;
	    pins[1] = new Pin(0, SIDE_E, "Q");
	    pins[1].output=true;
	    pins[2] = new Pin(1, SIDE_E, "Q");
	    pins[2].output=true;
	    pins[2].lineOver=true;
	}
	
	protected void reset() {
	    super.reset();
	    pins[2].value = true;
	    triggered = prevInputValue = false;
	}
	protected int getPostCount() {
	return 3;
	}
	protected int getVoltageSourceCount() { return 2; }

	protected void execute() {
			SimulationContext context = getSimulationContext();

			if(pins[0].value&&prevInputValue!=pins[0].value&&(retriggerable||!triggered)){
			lastRisingEdge=context.getTime();
			pins[1].value=true;
			pins[2].value=false;
			triggered=true;
			}

			if(triggered&&context.getTime()>lastRisingEdge+delay)
			{
			pins[1].value=false;
			pins[2].value=true;
			triggered=false;
			}
		prevInputValue=pins[0].value;
	   	}
	protected String dump(){
	   return super.dump() + " " + retriggerable + " " + delay;
	}
	protected int getDumpType() { return 194; }
	public EditInfo getChipEditInfo(int n) {
	    if (n == 0) {
		EditInfo ei = new EditInfo("", 0, -1, -1);
		ei.checkbox=new Checkbox("Retriggerable",retriggerable);
		return ei;
	    }
	    if (n == 1) {
		EditInfo ei = new EditInfo("Period (s)",delay, 0.001,0.1);
		return ei;
	    }
	    return super.getChipEditInfo(n);
	}
	public void setChipEditValue(int n, EditInfo ei) {
	    if (n == 0) {
		retriggerable=ei.checkbox.getState();
	    }
	    if (n == 1) {
		delay=ei.value;
	    }
	    super.setChipEditValue(n, ei);
	}
    }
