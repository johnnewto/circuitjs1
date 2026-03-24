package com.lushprojects.circuitjs1.client.elements.electronics.semiconductors;


public class PDarlingtonElm extends DarlingtonElm {



    public PDarlingtonElm(int xx, int yy) {
	super(xx, yy, true);
    }


    protected Class getDumpClass() {
	return DarlingtonElm.class;
    }
}
