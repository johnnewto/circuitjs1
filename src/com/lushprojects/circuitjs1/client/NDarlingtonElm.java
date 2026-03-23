package com.lushprojects.circuitjs1.client;

public class NDarlingtonElm extends DarlingtonElm {



    public NDarlingtonElm(int xx, int yy) {
	super(xx, yy, false);
    }


    protected Class getDumpClass() {
	return DarlingtonElm.class;
    }
}
