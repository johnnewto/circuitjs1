package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.scope.Scope;

import com.lushprojects.circuitjs1.client.*;

import com.lushprojects.circuitjs1.client.util.*;

import com.google.gwt.dom.client.Style.FontWeight;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusWidget;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.lushprojects.circuitjs1.client.elements.electronics.semiconductors.TransistorElm;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.RadioButton;
import com.lushprojects.circuitjs1.client.ui.Dialog;
import com.lushprojects.circuitjs1.client.ui.EditDialog;
import com.lushprojects.circuitjs1.client.ui.Scrollbar;

import java.util.Vector;

class ScopeCheckBox extends CheckBox {
    String menuCmd;
    
    ScopeCheckBox(String text, String menu) {
	super(text);
	menuCmd = menu;
    }
    
    void setValue(boolean x) {
	if (getValue() == x)
	    return;
	super.setValue(x);
    }
}



public class ScopePropertiesDialogCore extends Dialog implements ValueChangeHandler<Boolean> {

	
private Panel fp;
    private Panel channelButtonsp;
    private Panel channelSettingsp;
private HorizontalPanel hp;
private HorizontalPanel vModep;
private CirSim sim;
//RichTextArea textBox;
TextArea textArea;
private RadioButton autoButton;
    private RadioButton maxButton;
    private RadioButton manualButton;
private RadioButton acButton;
    private RadioButton dcButton;
private CheckBox scaleBox;
    private CheckBox voltageBox;
    private CheckBox currentBox;
    private CheckBox powerBox;
    private CheckBox peakBox;
    private CheckBox negPeakBox;
    private CheckBox freqBox;
    private CheckBox spectrumBox;
    CheckBox manualScaleBox;
private CheckBox rmsBox;
    private CheckBox dutyBox;
    private CheckBox viBox;
    private CheckBox xyBox;
    private CheckBox resistanceBox;
    private CheckBox ibBox;
    private CheckBox icBox;
    private CheckBox ieBox;
    private CheckBox vbeBox;
    private CheckBox vbcBox;
    private CheckBox vceBox;
    private CheckBox vceIcBox;
    private CheckBox logSpectrumBox;
    private CheckBox averageBox;
    private CheckBox multiLhsAxesBox;
private CheckBox elmInfoBox;
private TextBox labelTextBox;
    private TextBox titleTextBox;
    private TextBox manualScaleTextBox;
    private TextBox divisionsTextBox;
    private TextBox maxScaleTextBox;
private Button applyButton;
    private Button scaleUpButton;
    private Button scaleDownButton;
private Scrollbar speedBar;
    private Scrollbar positionBar;
private Scope scope;
private Grid grid;
    private Grid vScaleGrid;
    private Grid hScaleGrid;
private int nx;
    private int ny;
private Label scopeSpeedLabel;
    private Label manualScaleLabel;
    Label vScaleList;
    private Label manualScaleId;
    private Label positionLabel;
    private Label divisionsLabel;
    private Label maxScaleLabel;
private expandingLabel vScaleLabel;
    private expandingLabel hScaleLabel;
private Vector <Button> chanButtons = new Vector <Button>();
private int plotSelection = 0;
private labelledGridManager gridLabels;
private boolean maxScaleTextBoxHasFocus = false;
	
    class PlotClickHandler implements ClickHandler {
	int num;

	PlotClickHandler(int n) {
	    num = n;
	}

	public void onClick(ClickEvent event) {
	    plotSelection = num;
	    for (int i =0; i < chanButtons.size(); i++) {
		if (i==num)
		    chanButtons.get(i).addStyleName("chsel");
		else
		    chanButtons.get(i).removeStyleName("chsel");
	    }
	    updateUi();
	}
    }
    
    class manualScaleTextHandler implements ValueChangeHandler<String> {
	
	public void onValueChange(ValueChangeEvent<String> event) {
	    apply();
	    updateUi();
	}
	
    }
    
    class downClickHandler implements ClickHandler{
	downClickHandler() {
	}
	
	public void onClick(ClickEvent event) {
	    double lasts, s;
	if (!scope.isManualScale() || plotSelection > scope.getVisiblePlotCount())
		return;
	    double d = getManualScaleValue();
	    if (d==0)
		return;
	    d=d*0.999; // Go just below last check point
	    s=Scope.MIN_MAN_SCALE;
	    lasts=s;
	    for(int a=0; s<d; a++) { // Iterate until we go over the target and then use the last value
		lasts = s;
		s*=Scope.multa[a%3];
	    }
	    scope.setManualScaleValue(plotSelection, lasts);
	    syncModelInfoEditor();
	    updateUi();
	}
	
    }

    
    class upClickHandler implements ClickHandler{
	upClickHandler() {
	}
	
	public void onClick(ClickEvent event) {
	    double  s;
	if (!scope.isManualScale() || plotSelection > scope.getVisiblePlotCount())
		return;
	    double d = getManualScaleValue();
	    if (d==0)
		return;
	    s=nextHighestScale(d);
	    scope.setManualScaleValue(plotSelection, s);
	    syncModelInfoEditor();
	    updateUi();
	}
	
    }
    
    public static double nextHighestScale(double d) {
	    d=d*1.001; // Go just above last check point
	    double s;
	    s=Scope.MIN_MAN_SCALE;
	    for(int a=0; s<d; a++) { // Iterate until we go over the target
		s*=Scope.multa[a%3];
	    }
	    return s;
    }
    
    private void positionBarChanged() {
	if (!scope.isManualScale() || plotSelection > scope.getVisiblePlotCount())
	    return;
	int p = positionBar.getValue();
	scope.setPlotPosition(plotSelection, p);
	syncModelInfoEditor();
    }

    private void syncModelInfoEditor() {
	if (sim != null)
	    sim.getUiPanelManager().refreshModelInfoEditorAfterCircuitMutation();
    }
    
    private String getChannelButtonLabel(int i) {
	    Scope.VisiblePlotView p = scope.getVisiblePlotView(i);
	    String l = "<span style=\"color: " + p.color + ";\">&#x25CF;</span>&nbsp;CH " + String.valueOf(i + 1);
	    switch (p.units) {
	    	case Scope.UNITS_V: 
	    	    l += " (V)";
	    	    break;
	    	case Scope.UNITS_A:
	    	    l += " (I)";
	    	    break;
	    	case Scope.UNITS_OHMS:
	    	    l += " (R)";
	    	    break;
	    	case Scope.UNITS_W:
	    	    l += " (P)";
	    	    break;
	    }
	    return l;
	
    }
    
    private void updateChannelButtons() {
	if (plotSelection >= scope.getVisiblePlotCount())
	    plotSelection = 0;
	// More buttons than plots - remove extra buttons
	for (int i = chanButtons.size()-1; i >= scope.getVisiblePlotCount(); i--) {
	    channelButtonsp.remove(chanButtons.get(i));
	    chanButtons.remove(i);
	}
	// Now go though all the channels, adding new buttons if necessary
	for (int i = 0; i < scope.getVisiblePlotCount(); i++) {
	    if (i>=chanButtons.size()) {
		Button b = new Button();
		chanButtons.add(b);
		chanButtons.get(i).addClickHandler(new PlotClickHandler(i));
		b.addStyleName("chbut");
		if (CircuitElm.whiteColor == Color.white)
			b.addStyleName("chbut-black");
		    else
			b.addStyleName("chbut-white");
		channelButtonsp.add(b);
	    }
	    Button b = chanButtons.get(i);
	    b.setHTML(getChannelButtonLabel(i));
	    if (i==plotSelection)
		b.addStyleName("chsel");
	    else
		b.removeStyleName("chsel");
	}
    }
    
    class expandingLabel {
	HorizontalPanel p;
	Label l;
	Button b;
	Boolean expanded;
	
	expandingLabel(String s, Boolean ex) {
	    expanded = ex;
	    p = new HorizontalPanel();
	    b = new Button(ex?"-":"+");
	    b.addClickHandler(new ClickHandler() {
		public void onClick(ClickEvent event) {
		    expanded=!expanded;
		    b.setHTML(expanded?"-":"+");
		    updateUi();
		}
	    });
	    b.addStyleName("expand-but");
	    p.add(b);
	    l = new Label (s);
	    l.getElement().getStyle().setFontWeight(FontWeight.BOLD);
	    p.add(l);
	    p.setCellVerticalAlignment(l, HasVerticalAlignment.ALIGN_BOTTOM);
	}
	
    }

	public ScopePropertiesDialogCore(CirSim asim, Scope s) {
		super();
		// We are going to try and keep the panel below the target height (defined to give some space)
		int allowedHeight = Window.getClientHeight()*4/5;
		boolean displayAll = allowedHeight > 600; // We can display everything as maximum height can be shown
		boolean displayScales = allowedHeight > 470; // We can display the scales and any one other section. So expand scales and collapse rest
		sim=asim;
		scope = s;
		Button okButton, applyButton2;
		fp=new FlowPanel();
		setWidget(fp);
		
		// Prevent keyboard events on the dialog from propagating to circuit editor
		fp.addDomHandler(new com.google.gwt.event.dom.client.KeyDownHandler() {
		    public void onKeyDown(com.google.gwt.event.dom.client.KeyDownEvent event) {
			event.stopPropagation();
		    }
		}, com.google.gwt.event.dom.client.KeyDownEvent.getType());
		fp.addDomHandler(new com.google.gwt.event.dom.client.KeyPressHandler() {
		    public void onKeyPress(com.google.gwt.event.dom.client.KeyPressEvent event) {
			event.stopPropagation();
		    }
		}, com.google.gwt.event.dom.client.KeyPressEvent.getType());
		fp.addDomHandler(new com.google.gwt.event.dom.client.KeyUpHandler() {
		    public void onKeyUp(com.google.gwt.event.dom.client.KeyUpEvent event) {
			event.stopPropagation();
		    }
		}, com.google.gwt.event.dom.client.KeyUpEvent.getType());
		
		setText(Locale.LS("Scope Properties"));
		
		// Register this dialog so keyboard shortcuts are disabled
		CirSimDialogCoordinator.setDialogShowing(this);

// *************** VERTICAL SCALE ***********************************************************
		Grid vSLG = new Grid(1,1); // Stupid grid to force labels to align without diving deep in to table CSS
		vScaleLabel = new expandingLabel(Locale.LS("Vertical Scale"), displayScales);
		vSLG.setWidget(0,0,vScaleLabel.p);
		fp.add(vSLG);
		
				
		vModep = new HorizontalPanel();
		autoButton = new RadioButton("vMode", Locale.LS("Auto"));
		autoButton.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
	            public void onValueChange(ValueChangeEvent<Boolean> e) {
	        	scope.setManualScale(false, false);
	        	scope.setMaxScale(false);
	        	syncModelInfoEditor();
	        	updateUi();
	            }
	        });
		maxButton = new RadioButton("vMode", Locale.LS("Auto (Max Scale)"));
		maxButton.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
	            public void onValueChange(ValueChangeEvent<Boolean> e) {
	        	scope.setManualScale(false, false);
	        	scope.setMaxScale(true);
	        	syncModelInfoEditor();
	        	updateUi();
	            }
	        });
		manualButton = new RadioButton("vMode", Locale.LS("Manual"));
		manualButton.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
	            public void onValueChange(ValueChangeEvent<Boolean> e) {
	        	scope.setManualScale(true, true);
	        	syncModelInfoEditor();
	        	updateUi();
	            }
	        });
		vModep.add(autoButton);
		vModep.add(maxButton);
		vModep.add(manualButton);
		fp.add(vModep);
		channelSettingsp = new VerticalPanel();
		channelButtonsp = new FlowPanel();
		updateChannelButtons();
		channelSettingsp.add(channelButtonsp);
		fp.add(channelSettingsp);
		
		vScaleGrid = new Grid(5,5);
		dcButton= new RadioButton("acdc", Locale.LS("DC Coupled"));
		dcButton.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
		    public void onValueChange(ValueChangeEvent<Boolean> e) {
		    if (plotSelection < scope.getVisiblePlotCount())
			scope.setVisiblePlotAcCoupled(plotSelection, false);
		    syncModelInfoEditor();
		    updateUi();
		    }
		});
		vScaleGrid.setWidget(0, 0, dcButton);
		acButton= new RadioButton("acdc", Locale.LS("AC Coupled"));
		acButton.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
		    public void onValueChange(ValueChangeEvent<Boolean> e) {
		    if (plotSelection < scope.getVisiblePlotCount())
			scope.setVisiblePlotAcCoupled(plotSelection, true);
		    syncModelInfoEditor();
		    updateUi();
		    }
		});
		vScaleGrid.setWidget(0, 1, acButton);
		
		positionLabel= new Label(Locale.LS("Position"));
		vScaleGrid.setWidget(1,0, positionLabel);
		vScaleGrid.getCellFormatter().setVerticalAlignment(0, 0, HasVerticalAlignment.ALIGN_MIDDLE);
		positionBar = new Scrollbar(Scrollbar.HORIZONTAL,0, 1, -Scope.V_POSITION_STEPS, Scope.V_POSITION_STEPS, new Command() {
		    public void execute() {
			positionBarChanged();
		    }
		});
		vScaleGrid.setWidget(1,1,positionBar);
		Button resetPosButton = new Button(Locale.LS("Reset Position"));
		resetPosButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
			    positionBar.setValue(0);
			    positionBarChanged();
			    updateUi();
			}
		});
		vScaleGrid.setWidget(1, 4, resetPosButton);

		manualScaleId = new Label();
		vScaleGrid.setWidget(2, 0, manualScaleId);
		Grid scaleBoxGrid=new Grid(1,3);
		scaleDownButton=new Button("&#9660;");
		scaleDownButton.addClickHandler(new downClickHandler());
		scaleBoxGrid.setWidget(0,0, scaleDownButton);
		manualScaleTextBox = new TextBox(); 
		manualScaleTextBox.addValueChangeHandler(new manualScaleTextHandler());
		manualScaleTextBox.addStyleName("scalebox");
		preventKeyboardPropagation(manualScaleTextBox);
		scaleBoxGrid.setWidget(0, 1, manualScaleTextBox);
		scaleUpButton=new Button("&#9650;");
		scaleUpButton.addClickHandler(new upClickHandler());
		scaleBoxGrid.setWidget(0,2,scaleUpButton);
		vScaleGrid.setWidget(2,1, scaleBoxGrid);
		manualScaleLabel = new Label("");
		vScaleGrid.setWidget(2,2, manualScaleLabel);
		vScaleGrid.setWidget(2,4, applyButton = new Button(Locale.LS("Apply")));
		divisionsLabel = new Label(Locale.LS("# of Divisions"));
		divisionsTextBox = new TextBox();
		divisionsTextBox.addValueChangeHandler(new manualScaleTextHandler());
		preventKeyboardPropagation(divisionsTextBox);
		vScaleGrid.setWidget(3,0, divisionsLabel);
		vScaleGrid.setWidget(3,1, divisionsTextBox);
		applyButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				apply();
			}
		});
		Button applyButtonDiv;
		vScaleGrid.setWidget(3,4, applyButtonDiv = new Button(Locale.LS("Apply")));
		applyButtonDiv.addClickHandler(new ClickHandler() { public void onClick(ClickEvent event) { apply(); } });

		// Add Max Scale Limit row (only shown in auto mode)
		maxScaleLabel = new Label(Locale.LS("Max Scale Limit"));
		vScaleGrid.setWidget(4, 0, maxScaleLabel);
		maxScaleTextBox = new TextBox();
		maxScaleTextBox.addValueChangeHandler(new manualScaleTextHandler());
		maxScaleTextBox.addFocusHandler(new com.google.gwt.event.dom.client.FocusHandler() {
		    public void onFocus(com.google.gwt.event.dom.client.FocusEvent event) {
			maxScaleTextBoxHasFocus = true;
		    }
		});
		maxScaleTextBox.addBlurHandler(new com.google.gwt.event.dom.client.BlurHandler() {
		    public void onBlur(com.google.gwt.event.dom.client.BlurEvent event) {
			maxScaleTextBoxHasFocus = false;
		    }
		});
		// Prevent keyboard events from propagating to circuit editor
		preventKeyboardPropagation(maxScaleTextBox);
		vScaleGrid.setWidget(4, 1, maxScaleTextBox);
		Label maxScaleLimitHint = new Label(Locale.LS("(blank to disable)"));
		maxScaleLimitHint.addStyleName("hint-text");
		vScaleGrid.setWidget(4, 2, maxScaleLimitHint);
		Button applyMaxButton = new Button(Locale.LS("Apply"));
		applyMaxButton.addClickHandler(new ClickHandler() { public void onClick(ClickEvent event) { apply(); } });
		vScaleGrid.setWidget(4, 4, applyMaxButton);

		vScaleGrid.getCellFormatter().setVerticalAlignment(1, 1, HasVerticalAlignment.ALIGN_MIDDLE);
		fp.add(vScaleGrid);

		// *************** HORIZONTAL SCALE ***********************************************************

		
		hScaleGrid = new Grid(2,4);
		hScaleLabel = new expandingLabel(Locale.LS("Horizontal Scale"), displayScales);
		hScaleGrid.setWidget(0, 0, hScaleLabel.p);
		speedBar = new Scrollbar(Scrollbar.HORIZONTAL, 2, 1, 0, 11, new Command() {
		    public void execute() {
			scrollbarChanged();
		    }
		});
		hScaleGrid.setWidget(1,0, speedBar);
		scopeSpeedLabel = new Label("");
		scopeSpeedLabel.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
		hScaleGrid.setWidget(1, 1, scopeSpeedLabel);
		hScaleGrid.getCellFormatter().setVerticalAlignment(1, 1, HasVerticalAlignment.ALIGN_MIDDLE);

	//	speedGrid.getColumnFormatter().setWidth(0, "40%");
		fp.add(hScaleGrid);
		
		// *************** PLOTS ***********************************************************
		
	CircuitElm elm = scope.getSingleElm();
	boolean transistor = elm != null && elm instanceof TransistorElm;
	if (!transistor) {
	    grid = new Grid(15, 3); // Extra row for multi-LHS axis mode toggle
	    gridLabels = new labelledGridManager(grid);
	    gridLabels.addLabel(Locale.LS("Plots"), displayAll);
	    addItemToGrid(grid, voltageBox = new ScopeCheckBox(Locale.LS("Show Voltage"), "showvoltage"));
	    voltageBox.addValueChangeHandler(this); 
	    addItemToGrid(grid, currentBox = new ScopeCheckBox(Locale.LS("Show Current"), "showcurrent"));
	    currentBox.addValueChangeHandler(this);
	} else {
	    grid = new Grid(17, 3); // Extra row for multi-LHS axis mode toggle
		    gridLabels = new labelledGridManager(grid);
		    gridLabels.addLabel(Locale.LS("Plots"), displayAll);
		    addItemToGrid(grid, ibBox = new ScopeCheckBox(Locale.LS("Show Ib"), "showib"));
		    ibBox.addValueChangeHandler(this);
		    addItemToGrid(grid, icBox = new ScopeCheckBox(Locale.LS("Show Ic"), "showic"));
		    icBox.addValueChangeHandler(this);
		    addItemToGrid(grid, ieBox = new ScopeCheckBox(Locale.LS("Show Ie"), "showie"));
		    ieBox.addValueChangeHandler(this);
		    addItemToGrid(grid, vbeBox = new ScopeCheckBox(Locale.LS("Show Vbe"), "showvbe"));
		    vbeBox.addValueChangeHandler(this);
		    addItemToGrid(grid, vbcBox = new ScopeCheckBox(Locale.LS("Show Vbc"), "showvbc"));
		    vbcBox.addValueChangeHandler(this);
		    addItemToGrid(grid, vceBox = new ScopeCheckBox(Locale.LS("Show Vce"), "showvce"));
		    vceBox.addValueChangeHandler(this);
		}
		addItemToGrid(grid, powerBox = new ScopeCheckBox(Locale.LS("Show Power Consumed"), "showpower"));
		powerBox.addValueChangeHandler(this); 
		addItemToGrid(grid, resistanceBox = new ScopeCheckBox(Locale.LS("Show Resistance"), "showresistance"));
		resistanceBox.addValueChangeHandler(this); 
		addItemToGrid(grid, spectrumBox = new ScopeCheckBox(Locale.LS("Show Spectrum"), "showfft"));
		spectrumBox.addValueChangeHandler(this);
		addItemToGrid(grid, logSpectrumBox = new ScopeCheckBox(Locale.LS("Log Spectrum"), "logspectrum"));
		logSpectrumBox.addValueChangeHandler(this);
		
		gridLabels.addLabel(Locale.LS("X-Y Plots"), displayAll);
		addItemToGrid(grid, viBox = new ScopeCheckBox(Locale.LS("Show V vs I"), "showvvsi"));
		viBox.addValueChangeHandler(this); 
		addItemToGrid(grid, xyBox = new ScopeCheckBox(Locale.LS("Plot X/Y"), "plotxy"));
		xyBox.addValueChangeHandler(this);
		if (transistor) {
		    addItemToGrid(grid, vceIcBox = new ScopeCheckBox(Locale.LS("Show Vce vs Ic"), "showvcevsic"));
		    vceIcBox.addValueChangeHandler(this);
		}
		gridLabels.addLabel(Locale.LS("Show Info"), displayAll);
		addItemToGrid(grid, scaleBox = new ScopeCheckBox(Locale.LS("Show Scale"), "showscale"));
		scaleBox.addValueChangeHandler(this); 
		addItemToGrid(grid, multiLhsAxesBox = new ScopeCheckBox(Locale.LS("Multi-LHS Axes"), "multilhsaxes"));
		multiLhsAxesBox.addValueChangeHandler(this);
		addItemToGrid(grid, peakBox = new ScopeCheckBox(Locale.LS("Show Peak Value"), "showpeak"));
		peakBox.addValueChangeHandler(this); 
		addItemToGrid(grid, negPeakBox = new ScopeCheckBox(Locale.LS("Show Negative Peak Value"), "shownegpeak"));
		negPeakBox.addValueChangeHandler(this); 
		addItemToGrid(grid, freqBox = new ScopeCheckBox(Locale.LS("Show Frequency"), "showfreq"));
		freqBox.addValueChangeHandler(this); 
		addItemToGrid(grid, averageBox = new ScopeCheckBox(Locale.LS("Show Average"), "showaverage"));
		averageBox.addValueChangeHandler(this); 
		addItemToGrid(grid, rmsBox = new ScopeCheckBox(Locale.LS("Show RMS Average"), "showrms"));
		rmsBox.addValueChangeHandler(this); 
		addItemToGrid(grid, dutyBox = new ScopeCheckBox(Locale.LS("Show Duty Cycle"), "showduty"));
		dutyBox.addValueChangeHandler(this);
		addItemToGrid(grid, elmInfoBox = new ScopeCheckBox(Locale.LS("Show Extended Info"), "showelminfo"));
		elmInfoBox.addValueChangeHandler(this); 
		fp.add(grid);

		gridLabels.addLabel(Locale.LS("Title"), displayAll);
		titleTextBox = new TextBox();
		preventKeyboardPropagation(titleTextBox);
		addItemToGrid(grid, titleTextBox);
		String titleText = scope.getTitle();
		if (titleText != null)
		    titleTextBox.setText(titleText);
		addItemToGrid(grid, applyButton2= new Button(Locale.LS("Apply")));
		applyButton2.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				apply();
			}
		});

		gridLabels.addLabel(Locale.LS("Custom Label"), displayAll);
		labelTextBox = new TextBox();
		preventKeyboardPropagation(labelTextBox);
		addItemToGrid(grid, labelTextBox);
		String labelText = scope.getText();
		if (labelText != null)
		    labelTextBox.setText(labelText);
		Button applyButton3;
		addItemToGrid(grid, applyButton3= new Button(Locale.LS("Apply")));
		applyButton3.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				apply();
			}
		});
		
		updateUi();
		hp = new HorizontalPanel();
		hp.setWidth("100%");
		hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
		hp.setStyleName("topSpace");
		fp.add(hp);
		hp.add(okButton = new Button(Locale.LS("OK")));
		okButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				closeDialog();
			}
		});

//		hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);

		
		hp.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_RIGHT);
		Button saveAsDefaultButton;
		hp.add(saveAsDefaultButton = new Button(Locale.LS("Save as Default")));
		saveAsDefaultButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				scope.saveAsDefault();
			}
		});
		this.center();
		show();
	}

	class labelledGridManager {
	    Grid g;
	    Vector <expandingLabel> labels;
	    Vector <Integer> labelRows;
	    
	    labelledGridManager(Grid gIn) {
		g=gIn;
		labels = new Vector <expandingLabel>();
		labelRows = new Vector <Integer>();
	    }
	    
	    void addLabel(String s, boolean e) {
        	    if (nx != 0)
        		ny++;
        	    nx = 0;
        	    expandingLabel l = new expandingLabel(Locale.LS(s), e);
        	    g.setWidget(ny, nx, l.p);
        	    labels.add(l);
        	    labelRows.add(ny);
        	    ny++;
	    }
	    
	    void updateRowVisibility() {
		for (int i=0; i<labels.size(); i++) {
		    int end;
		    int start = labelRows.get(i);
		    if (i<labels.size()-1)
			end = labelRows.get(i+1);
		    else
			end = g.getRowCount();
		    for(int j=start+1; j<end; j++)
			g.getRowFormatter().setVisible(j, labels.get(i).expanded);
		}
	    }
	    
	}
	
	
	private void setScopeSpeedLabel() {
	    scopeSpeedLabel.setText(CircuitElm.getUnitText(scope.calcGridStepX(), "s")+"/div");
	}
	
	private void addItemToGrid(Grid g, FocusWidget scb) {
	    g.setWidget(ny, nx, scb);
	    if (++nx >= grid.getColumnCount()) {
		nx = 0;
		ny++;
	    }
	}
	
	
	private void scrollbarChanged() {
	    int newsp = (int)Math.pow(2,  10-speedBar.getValue());
	    CirSim.console("changed " + scope.getCurrentSpeed() + " " + newsp + " " + speedBar.getValue());
	    if (scope.getCurrentSpeed() != newsp) {
		scope.setSpeed(newsp);
		syncModelInfoEditor();
	    }
	    setScopeSpeedLabel();
	}
	
	private void updateUi() {
	    vModep.setVisible(vScaleLabel.expanded);
	    gridLabels.updateRowVisibility();
	    hScaleGrid.getRowFormatter().setVisible(1, hScaleLabel.expanded);
	    speedBar.setValue(10-(int)Math.round(Math.log(scope.getCurrentSpeed())/Math.log(2)));
	    if (voltageBox != null) {
		voltageBox.setValue(scope.isShowVoltageEnabled() && !scope.showingValue(Scope.VAL_POWER));
		currentBox.setValue(scope.isShowCurrentEnabled() && !scope.showingValue(Scope.VAL_POWER));
		powerBox.setValue(scope.showingValue(Scope.VAL_POWER));
	    }
	    scaleBox.setValue(scope.isShowScaleEnabled());
        multiLhsAxesBox.setValue(scope.isMultiLhsAxesEnabledForUi());
        multiLhsAxesBox.setEnabled(scope.getVisiblePlotCount() > 1 && !scope.isShowFftEnabled() && !scope.isPlot2dEnabled());
	    peakBox.setValue(scope.isShowMaxEnabled());
	    negPeakBox.setValue(scope.isShowMinEnabled());
	    freqBox.setValue(scope.isShowFreqEnabled());
	    spectrumBox.setValue(scope.isShowFftEnabled());
	    logSpectrumBox.setValue(scope.isLogSpectrumEnabled());
	    rmsBox.setValue(scope.isShowRmsEnabled());
	    averageBox.setValue(scope.isShowAverageEnabled());
	    dutyBox.setValue(scope.isShowDutyCycleEnabled());
	    elmInfoBox.setValue(scope.isShowElmInfoEnabled());
	    rmsBox.setEnabled(scope.canShowRMS());
	    viBox.setValue(scope.isPlot2dEnabled() && !scope.isPlotXyEnabled());
	    xyBox.setValue(scope.isPlotXyEnabled());
	    resistanceBox.setValue(scope.showingValue(Scope.VAL_R));
	    resistanceBox.setEnabled(scope.canShowResistance());
	    if (vbeBox != null) {
                ibBox.setValue(scope.showingValue(Scope.VAL_IB));
                icBox.setValue(scope.showingValue(Scope.VAL_IC));
                ieBox.setValue(scope.showingValue(Scope.VAL_IE));
                vbeBox.setValue(scope.showingValue(Scope.VAL_VBE));
                vbcBox.setValue(scope.showingValue(Scope.VAL_VBC));
                vceBox.setValue(scope.showingValue(Scope.VAL_VCE));
                vceIcBox.setValue(scope.isShowingVceAndIc());
	    }
	    if (scope.isManualScale()) {
		manualButton.setValue(true);
		autoButton.setValue(false);
		maxButton.setValue(false);
		applyButton.setVisible(true);
	    }
	    else {
		manualButton.setValue(false);
		autoButton.setValue(!scope.isMaxScaleEnabledForUi());
		maxButton.setValue(scope.isMaxScaleEnabledForUi());
		applyButton.setVisible(false);
	    }
	    updateManualScaleUi();
	    
	    

	    // if you add more here, make sure it still works with transistor scopes
	}
	
	private void updateManualScaleUi() {
	    updateChannelButtons();
	    channelSettingsp.setVisible(scope.isManualScale() && vScaleLabel.expanded);
	    vScaleGrid.setVisible(vScaleLabel.expanded);
	    if (vScaleLabel.expanded) { 
        	    vScaleGrid.getRowFormatter().setVisible(0, scope.isManualScale() && plotSelection < scope.getVisiblePlotCount());
        	    vScaleGrid.getRowFormatter().setVisible(1, scope.isManualScale() && plotSelection < scope.getVisiblePlotCount());
        	    vScaleGrid.getRowFormatter().setVisible(2, (!scope.isManualScale()) || plotSelection < scope.getVisiblePlotCount());
        	    vScaleGrid.getRowFormatter().setVisible(3, scope.isManualScale());
        	    vScaleGrid.getRowFormatter().setVisible(4, !scope.isManualScale()); // Max scale limit row (auto mode only)
	    }
	    scaleUpButton.setVisible(scope.isManualScale());
	    scaleDownButton.setVisible(scope.isManualScale());
	    if (scope.isManualScale()) {
		if (plotSelection < scope.getVisiblePlotCount()) {
		    Scope.VisiblePlotView p = scope.getVisiblePlotView(plotSelection);
		    manualScaleId.setText("CH "+String.valueOf(plotSelection+1)+" "+Locale.LS("Scale"));
		    manualScaleLabel.setText(Scope.getScaleUnitsText(p.units)+Locale.LS("/div"));
		    manualScaleTextBox.setText(EditDialog.unitString(null, p.manualScale));
		    manualScaleTextBox.setEnabled(true);
		    divisionsTextBox.setText(String.valueOf(scope.getManDivisions()));
		    divisionsTextBox.setEnabled(true);
		    positionLabel.setText("CH "+String.valueOf(plotSelection+1)+" "+Locale.LS("Position"));
		    positionBar.setValue(p.manualPosition);
		    dcButton.setEnabled(true);
		    positionBar.enable();
		    dcButton.setValue(!p.acCoupled);
		    acButton.setEnabled(p.canAcCouple);
		    acButton.setValue(p.acCoupled);
		    
		} else {
		    manualScaleId.setText("");
		    manualScaleLabel.setText("");
		    manualScaleTextBox.setText("");
		    manualScaleTextBox.setEnabled(false);
		    positionLabel.setText("");
		    dcButton.setEnabled(false);
		    acButton.setEnabled(false);
		    positionBar.disable();
		    
		}
	    } else {
		manualScaleId.setText("");
		manualScaleLabel.setText(Locale.LS("Max Value") + " (" + scope.getScaleUnitsText() + ")");
		manualScaleTextBox.setText(EditDialog.unitString(null, scope.getScaleValue()));
		manualScaleTextBox.setEnabled(false);
		positionLabel.setText("");
		
		// Update max scale limit field only if it doesn't have focus (to avoid overwriting user edits)
		if (!maxScaleTextBoxHasFocus) {
		    Double limit = scope.getMaxScaleLimit();
		    if (limit != null) {
			maxScaleTextBox.setText(EditDialog.unitString(null, limit));
		    } else {
			maxScaleTextBox.setText("");
		    }
		}
		maxScaleTextBox.setEnabled(true);
	    }
	    setScopeSpeedLabel();
	}
	
	public void refreshDraw() {
	    // Redraw for every step of the simulation (the simulation may run in the background of this
	    // dialog and the scope may automatically rescale
	    // Only update if dialog is showing and not in manual scale mode
	    if (!scope.isManualScale() && isShowing())
		updateManualScaleUi();
	}
	
	public void closeDialog()
	{
	    super.closeDialog();
	    apply();
	}
	
	private double getManualScaleValue()
	{
	    try {
		double d = EditDialog.parseUnits(manualScaleTextBox.getText());
		if (d< Scope.MIN_MAN_SCALE)
		    d= Scope.MIN_MAN_SCALE;
		return d;
	    } catch (Exception e) {
		return 0;
	    }
	}
	
	private int getDivisionsValue()
	{
	    try {
		int n = Integer.parseInt(divisionsTextBox.getText());
		return n;
	    } catch (Exception e) {
		return 0;
	    }
	}
	
	protected void apply() {
	    String label = labelTextBox.getText();
	    if (label.length() == 0)
		label = null;
	    scope.setText(label);
	    
	    String title = titleTextBox.getText();
	    if (title.length() == 0)
		title = null;
	    scope.setTitle(title);
	    
	    if (scope.isManualScale()) {
		double d=getManualScaleValue();
		if (d>0)
		    scope.setManualScaleValue(plotSelection, d);
		int n = getDivisionsValue();
		if (n > 0)
		    scope.setManDivisions(n);
	    } else {
		// Handle max scale limit in auto mode
		String maxScaleText = maxScaleTextBox.getText().trim();
		if (maxScaleText.isEmpty()) {
		    // Blank = disable limit
		    scope.setMaxScaleLimit(null);
		} else {
		    try {
			double limit = EditDialog.parseUnits(maxScaleText);
			if (limit > 0) {
			    scope.setMaxScaleLimit(limit);
			}
		    } catch (Exception e) {
			// Invalid input, ignore
		    }
		}
	    }
	    syncModelInfoEditor();
	}

	public void onValueChange(ValueChangeEvent<Boolean> event) {
	    ScopeCheckBox cb = (ScopeCheckBox) event.getSource();
	    scope.handleMenu(cb.menuCmd, cb.getValue());
	    syncModelInfoEditor();
	    updateUi();
	}


}
