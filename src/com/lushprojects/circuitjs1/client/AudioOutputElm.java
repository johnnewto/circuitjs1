package com.lushprojects.circuitjs1.client;

import java.util.Date;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.lushprojects.circuitjs1.client.core.SimulationContext;
import com.lushprojects.circuitjs1.client.util.Locale;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

public class AudioOutputElm extends CircuitElm {
	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Document")
	private static class DocumentLike {
		@JsProperty(name = "audioBlob") native String getAudioBlob();
		@JsProperty(name = "audioBlob") native void setAudioBlob(String audioBlob);
		@JsProperty(name = "audioObject") native ElementLike getAudioObject();
		@JsProperty(name = "audioObject") native void setAudioObject(ElementLike audioObject);
		@JsMethod native AudioLike createElement(String tagName);
		@JsProperty(name = "body") native ElementLike getBody();
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Element")
	private static class ElementLike {
		@JsMethod native ElementLike appendChild(ElementLike child);
		@JsMethod native ElementLike removeChild(ElementLike child);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "ArrayBuffer")
	private static class ArrayBufferLike {
		public ArrayBufferLike(int byteLength) {}
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "DataView")
	private static class DataViewLike {
		public DataViewLike(ArrayBufferLike buffer) {}
		@JsMethod native void setUint8(int byteOffset, int value);
		@JsMethod native void setUint32(int byteOffset, int value, boolean littleEndian);
		@JsMethod native void setInt16(int byteOffset, int value, boolean littleEndian);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "BlobPropertyBag")
	private static class BlobPropertyBagLike {
		public BlobPropertyBagLike() {}
		@JsProperty(name = "type") native void setType(String type);
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Blob")
	private static class BlobLike {
		public BlobLike(Object[] parts, BlobPropertyBagLike options) {}
	}

	@JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "HTMLAudioElement")
	private static class AudioLike extends ElementLike {
		@JsProperty(name = "src") native void setSrc(String src);
		@JsMethod native void play();
	}

	@JsProperty(namespace = JsPackage.GLOBAL, name = "document")
	private static native DocumentLike getDocument();

	@JsMethod(namespace = JsPackage.GLOBAL, name = "URL.createObjectURL")
	private static native String createObjectURL(BlobLike blob);

	@JsMethod(namespace = JsPackage.GLOBAL, name = "URL.revokeObjectURL")
	private static native void revokeObjectURL(String url);

    int dataCount, dataPtr;
    double data[];
    boolean dataFull;
    Button button;
    int samplingRate;
    int labelNum;
    double duration;
    double sampleStep;
    double dataStart;
    static int lastSamplingRate = 8000;
    static boolean okToChangeTimeStep;
    
	public AudioOutputElm(int xx, int yy) {
	    super(xx, yy);
	    duration = 1;
	    samplingRate = lastSamplingRate;
	    labelNum = getNextLabelNum();
	    setDataCount();
	    createButton();
	}
	public AudioOutputElm(int xa, int ya, int xb, int yb, int f,
			 StringTokenizer st) {
	    super(xa, ya, xb, yb, f);
	    duration = Double.parseDouble(st.nextToken());
	    samplingRate = Integer.parseInt(st.nextToken());
	    labelNum = Integer.parseInt(st.nextToken());
	    setDataCount();
	    createButton();
	}
	String dump() { 
	    return super.dump() + " " + duration + " " + samplingRate + " " + labelNum;
	}
	
	void draggingDone() {
	    setTimeStep();
	}
	
	// get next unused labelNum value
	int getNextLabelNum() {
	    int i;
	    int num = 1;
	    if (sim.elmList == null)
		return 0;
	    for (i = 0; i != sim.elmList.size(); i++) {
		CircuitElm ce = sim.getElm(i);
		if (!(ce instanceof AudioOutputElm))
		    continue;
		int ln = ((AudioOutputElm)ce).labelNum;
		if (ln >= num)
		    num = ln+1;
	    }
	    return num;
	}
	
	int getDumpType() { return 211; }
	int getPostCount() { return 1; }
	void reset() {
	    dataPtr = 0;
	    dataFull = false;
	    dataSampleCount = 0;
	    nextDataSample = 0;
	    dataSample = 0;
	}
	void setPoints() {
	    super.setPoints();
	    lead1 = new Point();
	}
	void draw(Graphics g) {
	    g.save();
	    boolean selected = (needsHighlight());
	    Font f = new Font("SansSerif", selected ? Font.BOLD : 0, 14);
	    String s = "Audio Out";
	    if (labelNum > 1)
		s = "Audio " + labelNum;
	    g.setFont(f);
	    int textWidth = (int)g.context.measureText(s).getWidth();
	    g.setColor(Color.darkGray);
	    int pct = (dataFull) ? textWidth : textWidth*dataPtr/dataCount;
	    g.fillRect(x2-textWidth/2, y2-10, pct, 20);
	    g.setColor(selected ? selectColor : whiteColor);
	    interpPoint(point1, point2, lead1, 1-(textWidth/2.+8)/dn);
	    setBbox(point1, lead1, 0);
	    drawCenteredText(g, s, x2, y2, true);
	    setVoltageColor(g, volts[0]);
	    if (selected)
		g.setColor(selectColor);
	    drawThickLine(g, point1, lead1);
	    drawPosts(g);
	    g.restore();
	}
	double getVoltageDiff() { return volts[0]; }
	void getInfo(String arr[]) {
	    SimulationContext context = getSimulationContext();
	    arr[0] = "audio output";
	    arr[1] = "V = " + getVoltageText(volts[0]);
	    int ct = (dataFull ? dataCount : dataPtr);
	    double dur = sampleStep * ct;
	    arr[2] = "start = " + getUnitText(dataFull ? context.getTime()-duration : dataStart, "s");
	    arr[3] = "dur = " + getUnitText(dur, "s");
	    arr[4] = "samples = " + ct + (dataFull ? "" : "/" + dataCount);
	}
	
	int dataSampleCount = 0;
	double nextDataSample = 0;
	double dataSample;
	
	void stepFinished() {
	    SimulationContext context = getSimulationContext();
	    dataSample += volts[0];
	    dataSampleCount++;
	    if (context.getTime() >= nextDataSample) {
		nextDataSample += sampleStep;
		data[dataPtr++] = dataSample/dataSampleCount;
		dataSampleCount = 0;
		dataSample = 0;
		if (dataPtr >= dataCount) {
		    dataPtr = 0;
		    dataFull = true;
		}
	    }
	}
	
	void setDataCount() {
	    SimulationContext context = getSimulationContext();
	    dataCount = (int) (samplingRate * duration);
	    data = new double[dataCount];
	    dataStart = context.getTime();
	    dataPtr = 0;
	    dataFull = false;
	    sampleStep = 1./samplingRate;
	    nextDataSample = context.getTime()+sampleStep;
	}
	
	int samplingRateChoices[] = { 8000, 11025, 16000, 22050, 44100, 48000 };
	
	public EditInfo getEditInfo(int n) {
	    if (n == 0) {
		EditInfo ei = new EditInfo("Duration (s)", duration, 0, 5);
		return ei;
	    }
	    if (n == 1) {
		EditInfo ei =  new EditInfo("Sampling Rate", 0, -1, -1);
		ei.choice = new Choice();
		int i;
		for (i = 0; i != samplingRateChoices.length; i++) {
		    ei.choice.add(samplingRateChoices[i] + "");
		    if (samplingRateChoices[i] == samplingRate)
			ei.choice.select(i);
		}
		return ei;
	    }
            if (n == 2) {
                EditInfo ei = new EditInfo("", 0, -1, -1);
                String url=getLastBlob();
                if (url == null)
                    return null;
                Date date = new Date();
                DateTimeFormat dtf = DateTimeFormat.getFormat("yyyyMMdd-HHmm");
                String fname = "audio-"+ dtf.format(date) + ".circuitjs.wav";
                Anchor a=new Anchor(Locale.LS("Download last played audio"), url);
                a.getElement().setAttribute("Download", fname);
                ei.widget = a;
                return ei;
            }
	    
	    return null;
	}
	public void setEditValue(int n, EditInfo ei) {
	    if (n == 0 && ei.value > 0) {
		duration = ei.value;
		setDataCount();
	    }
	    if (n == 1) {
		int nsr = samplingRateChoices[ei.choice.getSelectedIndex()];
		if (nsr != samplingRate) {
		    samplingRate = nsr;
		    lastSamplingRate = nsr;
		    setDataCount();
		    setTimeStep();
		}
	    }
	}
	
	void setTimeStep() {
	    /*
	    // timestep must be smaller than 1/sampleRate
	    if (sim.getTimeStep() > sampleStep)
		sim.setTimeStep(sampleStep);
	    else {
		// make sure sampleStep/timeStep is an integer.  otherwise we get distortion
//		int frac = (int)Math.round(sampleStep/sim.getTimeStep());
//		sim.setTimeStep(sampleStep / frac);
		
		// actually, just make timestep = 1/sampleRate
		sim.setTimeStep(sampleStep);
	    }
	    */
	    
//	    int frac = (int)Math.round(Math.max(sampleStep*33000, 1));
	    double target = sampleStep/8;
	    if (sim.getMaxTimeStep() != target) {
                if (okToChangeTimeStep || Window.confirm(Locale.LS("Adjust timestep for best audio quality and performance?"))) {
                    sim.setMaxTimeStep(target);
                    okToChangeTimeStep = true;
                }
	    }
	}
	
        void createButton() {
            String label = "&#9654; " + Locale.LS("Play Audio");
            if (labelNum > 1)
        	label += " " + labelNum;
			sim.getUiPanelManager().addWidgetToVerticalPanel(button = new Button(label));
            button.setStylePrimaryName("topButton");
            button.addClickHandler(new ClickHandler() {
        	public void onClick(ClickEvent event) {
        	    play();
        	}
            });
            
        }
        void delete() {
			sim.getUiPanelManager().removeWidgetFromVerticalPanel(button);
            super.delete();
        }
        
	private static void writeAscii(DataViewLike view, int offset, String s) {
		for (int i = 0; i < s.length(); i++)
			view.setUint8(offset + i, s.charAt(i));
	}

	private static String createWavBlobUrl(int[] samples, int sampleRate) {
		int dataSize = samples.length * 2;
		ArrayBufferLike wav = new ArrayBufferLike(44 + dataSize);
		DataViewLike view = new DataViewLike(wav);

		writeAscii(view, 0, "RIFF");
		view.setUint32(4, 36 + dataSize, true);
		writeAscii(view, 8, "WAVE");
		writeAscii(view, 12, "fmt ");
		view.setUint32(16, 16, true);
		view.setInt16(20, 1, true);
		view.setInt16(22, 1, true);
		view.setUint32(24, sampleRate, true);
		view.setUint32(28, sampleRate * 2, true);
		view.setInt16(32, 2, true);
		view.setInt16(34, 16, true);
		writeAscii(view, 36, "data");
		view.setUint32(40, dataSize, true);

		int p = 44;
		for (int sample : samples) {
			int clamped = Math.max(-32768, Math.min(32767, sample));
			view.setInt16(p, clamped, true);
			p += 2;
		}

		BlobPropertyBagLike options = new BlobPropertyBagLike();
		options.setType("audio/wav");
		BlobLike blob = new BlobLike(new Object[] { wav }, options);
		return createObjectURL(blob);
	}

	private static void playWavBlobUrl(String url) {
		DocumentLike document = getDocument();
		if (document == null)
			return;

		String oldBlobUrl = document.getAudioBlob();
		ElementLike oldAudio = document.getAudioObject();
		if (oldAudio != null) {
			ElementLike body = document.getBody();
			if (body != null)
				body.removeChild(oldAudio);
		}
		if (oldBlobUrl != null)
			revokeObjectURL(oldBlobUrl);

		AudioLike audio = document.createElement("audio");
		document.setAudioObject(audio);
		document.setAudioBlob(url);
		audio.setSrc(url);
		ElementLike body = document.getBody();
		if (body != null)
			body.appendChild(audio);
		audio.play();
	}

		static String getLastBlob() {
			DocumentLike document = getDocument();
			return document == null ? null : document.getAudioBlob();
		}
        
        void play() {
            int i;
            int ct = dataPtr;
            int base = 0;
            if (dataFull) {
        	ct = dataCount;
        	base = dataPtr;
            }
            if (ct * sampleStep < .05) {
        	Window.alert(Locale.LS("Audio data is not ready yet.  Increase simulation speed to make data ready sooner."));
        	return;
            }
            
            // rescale data to maximize
            double max = -1e8;
            double min =  1e8;
            for (i = 0; i != ct; i++) {
        	if (data[i] > max) max = data[i];
        	if (data[i] < min) min = data[i];
            }
            
            double adj = -(max+min)/2;
            double mult = (.25*32766)/(max+adj);
            
            // fade in over 1/20 sec
	    int fadeLen = samplingRate/20;
	    int fadeOut = ct-fadeLen;
	    
	    double fadeMult = mult/fadeLen;
	    int[] samples = new int[ct];
            for (i = 0; i != ct; i++) {
		double fade = (i < fadeLen) ? i*fadeMult : (i > fadeOut) ? (ct-i)*fadeMult : mult;
        	int s = (int)((data[(i+base)%dataCount]+adj)*fade);
		samples[i] = s;
            }
	    playWavBlobUrl(createWavBlobUrl(samples, samplingRate));
        }
}
