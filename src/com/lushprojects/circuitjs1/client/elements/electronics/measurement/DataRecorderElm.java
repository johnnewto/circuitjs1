package com.lushprojects.circuitjs1.client.elements.electronics.measurement;

import com.lushprojects.circuitjs1.client.ui.EditInfo;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.util.*;

import java.util.Date;

import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.client.ui.Anchor;
import com.lushprojects.circuitjs1.client.util.Locale;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

public class DataRecorderElm extends CircuitElm {
	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
	private static class BlobOptionsLike {
		BlobOptionsLike() {}
		@JsProperty(name = "type") native void setType(String type);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Blob")
	private static class BlobLike {
		BlobLike(Object parts, BlobOptionsLike options) {}
	}

	@JsMethod(namespace = JsPackage.GLOBAL, name = "URL.createObjectURL")
	private static native String createObjectURL(BlobLike blob);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "URL.revokeObjectURL")
	private static native void revokeObjectURL(String url);

	private static String lastRecorderBlobUrl;

    private int dataCount;
    private int dataPtr;
    private int lastTimeStepCount;
    private double[] data;
    private boolean dataFull;
    
	public DataRecorderElm(int xx, int yy) {
	    super(xx, yy);
	    setDataCount(10240);
	}
	public DataRecorderElm(int xa, int ya, int xb, int yb, int f,
			 StringTokenizer st) {
	    super(xa, ya, xb, yb, f);
	    setDataCount(Integer.parseInt(st.nextToken()));
	}
	protected String dump() { 
	    return super.dump() + " " + dataCount;
	}
	protected int getDumpType() { return 210; }
	protected int getPostCount() { return 1; }
	protected void reset() {
	    dataPtr = 0;
	    dataFull = false;
	    lastTimeStepCount = 0;
	}
	protected void setPoints() {
	    super.setPoints();
	    lead1 = interpPoint(point1, point2, 1-8/dn);
	}
	protected void draw(Graphics g) {
	    g.save();
	    boolean selected = (needsHighlight());
	    Font f = new Font("SansSerif", selected ? Font.BOLD : 0, 14);
	    g.setFont(f);
	    g.setColor(selected ? selectColor : whiteColor);
	    setBbox(point1, lead1, 0);
	    String s = Locale.LS("export");
	    drawLabeledNode(g, s, point1, lead1);
	    setVoltageColor(g, volts[0]);
	    if (selected)
		g.setColor(selectColor);
	    drawThickLine(g, point1, lead1);
	    drawPosts(g);
	    g.restore();
	}
	protected double getVoltageDiff() { return volts[0]; }
	protected void getInfo(String arr[]) {
	    arr[0] = "data export";
	    arr[1] = "V = " + getVoltageText(volts[0]);
	    arr[2] = (dataFull ? dataCount : dataPtr) + "/" + dataCount;
	}
	protected void stepFinished() {
	    if (lastTimeStepCount == sim.getTimingState().timeStepCount)
		return;
	    data[dataPtr++] = volts[0];
	    lastTimeStepCount = sim.getTimingState().timeStepCount;
	    if (dataPtr >= dataCount) {
		dataPtr = 0;
		dataFull = true;
	    }
	}
	
	private void setDataCount(int ct) {
	    dataCount = ct;
	    data = new double[dataCount];
	    dataPtr = 0;
	    dataFull = false;
	}
	
	private static String getBlobUrl(String data) {
		if (lastRecorderBlobUrl != null)
			revokeObjectURL(lastRecorderBlobUrl);
		String[] parts = new String[] { data };
		BlobOptionsLike options = new BlobOptionsLike();
		options.setType("text/plain");
		BlobLike blob = new BlobLike(parts, options);
		String url = createObjectURL(blob);
		lastRecorderBlobUrl = url;
		return url;
	}

	public EditInfo getEditInfo(int n) {
	    if (n == 0) {
		EditInfo ei = new EditInfo("# of Data Points", dataCount, -1, -1).setDimensionless();
		return ei;
	    }
	    if (n == 1) {
		EditInfo ei = new EditInfo("", 0, -1, -1);
		String dataStr = "# time step = " + sim.getTimeStep() + " sec\n";
		int i;
		if (dataFull) {
		    for (i = 0; i != dataCount; i++)
			dataStr += data[(i+dataPtr) % dataCount] + "\n";
		} else {
		    for (i = 0; i != dataPtr; i++)
			dataStr += data[i] + "\n";
		}
                String url=getBlobUrl(dataStr);
                Date date = new Date();
                DateTimeFormat dtf = DateTimeFormat.getFormat("yyyyMMdd-HHmm");
                String fname = "data-"+ dtf.format(date) + ".circuitjs.txt";
                Anchor a=new Anchor(fname, url);
                a.getElement().setAttribute("Download", fname);
                ei.widget = a;
                return ei;
            }
	    return null;
	}
	public void setEditValue(int n, EditInfo ei) {
	    if (n == 0 && ei.value > 0) {
		setDataCount((int)ei.value);
	    }
	    if (n == 1)
		return;
	}
}
