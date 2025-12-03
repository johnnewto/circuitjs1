package com.lushprojects.circuitjs1.client;

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.user.client.ui.*;
import com.lushprojects.circuitjs1.client.util.Locale;

import java.util.HashMap;

public class EconomicsToolbar extends Toolbar {

    private Label modeLabel;
    private HashMap<String, Label> highlightableButtons = new HashMap<>();
    private Label activeButton;  // Currently active button

    public EconomicsToolbar() {
        // Set the overall style of the toolbar
	Style style = getElement().getStyle();
        style.setPadding(2, Style.Unit.PX);
        style.setBackgroundColor("#f8f8f8");
        style.setBorderWidth(1, Style.Unit.PX);
        style.setBorderStyle(Style.BorderStyle.SOLID);
        style.setBorderColor("#ccc");
        style.setDisplay(Style.Display.FLEX);
	setVerticalAlignment(ALIGN_MIDDLE);

        // Add toolbar buttons
	add(createIconButton("ccw", "Undo", new MyCommand("edit", "undo")));
	add(createIconButton("cw",  "Redo", new MyCommand("edit", "redo")));
	// add(createIconButton("scissors", "Cut", new MyCommand("edit", "cut")));
	// add(createIconButton("copy", "Copy", new MyCommand("edit", "copy")));
	// add(createIconButton("paste", "Paste", new MyCommand("edit", "paste")));
	// add(createIconButton("clone", "Duplicate", new MyCommand("edit", "duplicate")));
	add(createIconButton("search", "Find Component...", new MyCommand("edit", "search")));
	add(createIconButton("target", "Center Circuit", new MyCommand("edit", "centrecircuit")));

	add(createIconButton("zoom-11", "Zoom 100%", new MyCommand("zoom", "zoom100")));
	add(createIconButton("zoom-in", "Zoom In", new MyCommand("zoom", "zoomin")));
	add(createIconButton("zoom-out", "Zoom Out", new MyCommand("zoom", "zoomout")));
	add(createIconButton("resize-full-alt", "Toggle Full Screen", new MyCommand("view", "fullscreen")));
	add(createIconButton(viewportIcon, "Zoom to Viewport", new MyCommand("edit", "zoomToViewport")));

	add(createIconButton(wireIcon,  "WireElm")); 
	add(createIconButton(groundIcon, "GroundElm"));
	// Add economics-specific components
	String tableInfo[] = { tableGodlyIcon, "GodlyTableElm", tableCTMIcon, "CurrentTransactionsMatrixElm", tableActionIcon, "ActionTimeElm" ,
        tableStockMasterIcon, "StockMasterElm", tableFlowsMasterIcon, "FlowsMasterElm"
    };

	add(createButtonSet(tableInfo));
	String equationInfo[] = { multiplyIcon, "MultiplyElm", multiplyConstIcon, "MultiplyConstElm", dividerIcon, "DividerElm",
	    differentiatorIcon, "DifferentiatorElm", integratorIcon, "IntegratorElm", odeIcon, "ODEElm",
	    equationIcon, "EquationElm", percentIcon, "PercentElm", adderIcon, "AdderElm", subtracterIcon, "SubtracterElm"
	};
	add(createButtonSet(equationInfo));
	String displayInfo[] = { stopTimeIcon, "StopTimeElm", pieChartIcon, "PieChartElm", viewportIcon, "ViewportElm" };
	add(createButtonSet(displayInfo));
	add(createIconButton(labelNodeIcon, "Labeled Node", new MyCommand("main", "LabeledNodeElm")));
	add(createIconButton(graphIcon, "View All Scopes in Plotly...", new MyCommand("scopes", "viewAllPlotly")));


        // Spacer to push the mode label to the right
        HorizontalPanel spacer = new HorizontalPanel();
        spacer.getElement().getStyle().setProperty("flexGrow", "1");
        add(spacer);

        // Create and add the mode label on the right
        modeLabel = new Label("");
        styleModeLabel(modeLabel);
        add(modeLabel);
    }

    public void setModeLabel(String text) { modeLabel.setText(Locale.LS("Mode: ") + text); }

    private Label createIconButton(String icon, String cls) {
	CirSim sim = CirSim.theSim;
	return createIconButton(icon, sim.getLabelTextForClass(cls), new MyCommand("main", cls));
    }

    private Label createIconButton(String iconClass, String tooltip, MyCommand command) {
        // Create a label to hold the icon
        Label iconLabel = new Label();
        iconLabel.setText(""); // No text, just an icon
	if (iconClass.startsWith("<svg"))
	    iconLabel.getElement().setInnerHTML(makeSvg(iconClass, 24));
        else
	    iconLabel.getElement().addClassName("cirjsicon-" + iconClass);
        iconLabel.setTitle(Locale.LS(tooltip));

        // Style the icon button
	Style style = iconLabel.getElement().getStyle();
        style.setFontSize(24, Style.Unit.PX);
        style.setColor("#333");
        style.setPadding(1, Style.Unit.PX);
        style.setMarginRight(5, Style.Unit.PX);
        style.setCursor(Style.Cursor.POINTER);
	if (iconClass.startsWith("<svg"))
	    style.setPaddingTop(5, Style.Unit.PX);

        // Add hover effect for the button
        iconLabel.addMouseOverHandler(event -> iconLabel.getElement().getStyle().setColor("#007bff"));
        iconLabel.addMouseOutHandler(event -> iconLabel.getElement().getStyle().setColor("#333"));

        // Add a click handler to perform the action
        iconLabel.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
		// un-highlight
        	iconLabel.getElement().getStyle().setColor("#333");
		if (iconLabel == activeButton) {
		    new MyCommand("main", "Select").execute();
		    activeButton = null;
		} else
		    command.execute();
            }
        });

        // Track buttons that belong to the "main" command group
        if (command.getMenuName().equals("main"))
            highlightableButtons.put(command.getItemName(), iconLabel);

        return iconLabel;
    }

    String makeSvg(String s, int size) {
	double scale = size/24.0;
	return "<svg xmlns='http://www.w3.org/2000/svg' width='" + size + "' height='" + size + "'><g transform='scale(" + scale + ")'>" +
                 s.substring(5, s.length()-5) + "<g></svg>";
    }

    // New method for creating variant buttons
    private Label createButtonSet(String info[]) {
	MyCommand mainCommand = new MyCommand("main", info[1]);
	CirSim sim = CirSim.theSim;
	Label iconLabel = createIconButton(info[0], sim.getLabelTextForClass(info[1]), mainCommand);
	
	FlowPanel paletteContainer = new FlowPanel();
	paletteContainer.setVisible(false); // Hidden by default
	paletteContainer.setStyleName("palette-container");

	// Apply CSS styles for positioning and visibility
	Style paletteStyle = paletteContainer.getElement().getStyle();
	paletteStyle.setPosition(Style.Position.ABSOLUTE);
	paletteStyle.setZIndex(1000); // High z-index to appear on top
	paletteStyle.setBackgroundColor("#ffffff");
	paletteStyle.setBorderWidth(1, Style.Unit.PX);
	paletteStyle.setBorderColor("#ccc");
	paletteStyle.setBorderStyle(Style.BorderStyle.SOLID);
	paletteStyle.setPadding(5, Style.Unit.PX);

	int i;
	for (i = 0; i < info.length; i += 2) {
	    // Create each variant button
	    Label variantButton = new Label();
	    variantButton.setText(""); // No text, just an icon
	    variantButton.getElement().setInnerHTML(makeSvg(info[i], 40));
	    variantButton.setTitle(sim.getLabelTextForClass(info[i+1]));

	    // Style the variant button
	    Style variantStyle = variantButton.getElement().getStyle();
	    variantStyle.setColor("#333");
	    variantStyle.setCursor(Style.Cursor.POINTER);

	    final MyCommand command = new MyCommand("main", info[i+1]);
	    final String smallSvg = makeSvg(info[i], 24);

	    // Add click handler to update the main button and execute the command
	    variantButton.addClickHandler(event -> {
		// Change the icon of the main button to reflect the variant selected
		iconLabel.getElement().setInnerHTML(smallSvg);
		highlightableButtons.remove(mainCommand.getItemName());
                highlightableButtons.put(command.getItemName(), iconLabel);
		paletteContainer.setVisible(false);
		mainCommand.setItemName(command.getItemName());
		command.execute();  // Execute the corresponding command for the selected variant
	    });

	    // Append the variant button to the palette container
	    paletteContainer.add(variantButton);
	}

	// Add the palette container to the document
	RootPanel.get().add(paletteContainer);

	// Show palette on mouse-over
	iconLabel.addMouseOverHandler(event -> {
	    paletteContainer.setVisible(true);

	    // Position the palette relative to the icon label
	    int leftOffset = iconLabel.getAbsoluteLeft() - 12;
	    int topOffset = iconLabel.getAbsoluteTop() + iconLabel.getOffsetHeight() - 2;
	    paletteContainer.getElement().getStyle().setLeft(leftOffset, Style.Unit.PX);
	    paletteContainer.getElement().getStyle().setTop(topOffset, Style.Unit.PX);
	});

	// Hide palette on mouse-out
	iconLabel.addMouseOutHandler(event -> { paletteContainer.setVisible(false); });

	// Keep the palette visible when hovering over it
	paletteContainer.addDomHandler(event -> paletteContainer.setVisible(true), MouseOverEvent.getType());
	paletteContainer.addDomHandler(event -> { paletteContainer.setVisible(false); }, MouseOutEvent.getType());

	return iconLabel;
    }

    private void styleModeLabel(Label label) {
	Style style = label.getElement().getStyle();
        style.setFontSize(16, Style.Unit.PX);
        style.setColor("#333");
        style.setPaddingRight(10, Style.Unit.PX);
	style.setProperty("whiteSpace", "nowrap");
    }

    public void highlightButton(String key) {
        // Deactivate the currently active button
        if (activeButton != null) {
            activeButton.getElement().getStyle().setColor("#333"); // Reset color
            activeButton.getElement().getStyle().setBackgroundColor(null);
        }

        // Activate the new button
        Label newActiveButton = highlightableButtons.get(key);
        if (newActiveButton != null) {
            newActiveButton.getElement().getStyle().setColor("#007bff"); // Active color
            newActiveButton.getElement().getStyle().setBackgroundColor("#e6f7ff");
            activeButton = newActiveButton;
        }
    }

    public void setEuroResistors(boolean euro) {
	// Not applicable for economics toolbar
    }

    // Economics-specific icons
    final String wireIcon = "<svg><g transform='scale(0.208) translate(7.5, 32)'>" +
           "<line x1='5' y1='45' x2='95' y2='5' stroke='currentColor' stroke-width='8' /> " +
           "<circle cx='5' cy='45' r='10' fill='currentColor' /><circle cx='95' cy='5' r='10' fill='currentColor' /> " +
           "</g></svg>";

    final String groundIcon = "<svg><defs /><g transform='scale(.6) translate(-826.46,-231.31) scale(1.230769)'><path fill='none' stroke='currentColor' d=' M 688 192 L 688 208' stroke-linecap='round' stroke-width='3' /> <path fill='none' stroke='currentColor' d=' M 698 208 L 678 208' stroke-linecap='round' stroke-width='3' /><path fill='none' stroke='currentColor' d=' M 694 213 L 682 213' stroke-linecap='round' stroke-width='3' /><path fill='none' stroke='currentColor' d=' M 690 218 L 686 218' stroke-linecap='round' stroke-width='3' /><path fill='currentColor' stroke='currentColor' d=' M 691 192 A 3 3 0 1 1 690.9997392252899 191.96044522459943 Z' /> </g></svg>";

     
    final String tableGodlyIcon = "<svg><g transform='scale(1.2)'><rect x='4' y='6' width='16' height='12' fill='none' stroke='currentColor' stroke-width='2' /><line x1='4' y1='10' x2='20' y2='10' stroke='currentColor' stroke-width='1.5' /><line x1='4' y1='14' x2='20' y2='14' stroke='currentColor' stroke-width='1.5' /><line x1='12' y1='6' x2='12' y2='18' stroke='currentColor' stroke-width='1.5' /><text x='12' y='13' fill='green' font-size='15' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>G</text></g></svg>";
    
    final String tableCTMIcon = "<svg><g transform='scale(1.2)'><rect x='4' y='6' width='16' height='12' fill='none' stroke='currentColor' stroke-width='2' /><line x1='4' y1='10' x2='20' y2='10' stroke='currentColor' stroke-width='1.5' /><line x1='4' y1='14' x2='20' y2='14' stroke='currentColor' stroke-width='1.5' /><line x1='12' y1='6' x2='12' y2='18' stroke='currentColor' stroke-width='1.5' /><text x='12' y='13' fill='green' font-size='15' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>C</text></g></svg>";
    
    final String tableActionIcon = "<svg><g transform='scale(1.2)'><rect x='4' y='6' width='16' height='12' fill='none' stroke='currentColor' stroke-width='2' /><line x1='4' y1='10' x2='20' y2='10' stroke='currentColor' stroke-width='1.5' /><line x1='4' y1='14' x2='20' y2='14' stroke='currentColor' stroke-width='1.5' /><line x1='12' y1='6' x2='12' y2='18' stroke='currentColor' stroke-width='1.5' /><circle cx='12' cy='12' r='4' fill='green' stroke='currentColor' stroke-width='1.2' /><line x1='12' y1='12' x2='12' y2='9.5' stroke='currentColor' stroke-width='1.2' /><line x1='12' y1='12' x2='13.5' y2='13' stroke='currentColor' stroke-width='1.2' /></g></svg>";
    
    final String tableStockMasterIcon = "<svg><g transform='scale(1.2)'><rect x='4' y='6' width='16' height='12' fill='none' stroke='currentColor' stroke-width='2' /><line x1='4' y1='10' x2='20' y2='10' stroke='currentColor' stroke-width='1.5' /><line x1='4' y1='14' x2='20' y2='14' stroke='currentColor' stroke-width='1.5' /><line x1='12' y1='6' x2='12' y2='18' stroke='currentColor' stroke-width='1.5' /><text x='12' y='13' fill='green' font-size='15' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>S</text></g></svg>";
    
    final String tableFlowsMasterIcon = "<svg><g transform='scale(1.2)'><rect x='4' y='6' width='16' height='12' fill='none' stroke='currentColor' stroke-width='2' /><line x1='4' y1='10' x2='20' y2='10' stroke='currentColor' stroke-width='1.5' /><line x1='4' y1='14' x2='20' y2='14' stroke='currentColor' stroke-width='1.5' /><line x1='12' y1='6' x2='12' y2='18' stroke='currentColor' stroke-width='1.5' /><text x='12' y='13' fill='green' font-size='15' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>F</text></g></svg>";
    
    // Equation/Math operation icons
    final String multiplyIcon = "<svg><rect x='3' y='3' width='18' height='18' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='14.5' fill='blue' font-size='14' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>×</text></svg>";
    
    final String multiplyConstIcon = "<svg><rect x='3' y='3' width='18' height='18' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='14.5' fill='blue' font-size='11' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>×K</text></svg>";
    
    final String dividerIcon = "<svg><rect x='3' y='3' width='18' height='18' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='14.5' fill='blue' font-size='14' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>÷</text></svg>";
    
    final String differentiatorIcon = "<svg><rect x='3' y='3' width='18' height='18' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='14.5' fill='blue' font-size='9' font-weight='bold' text-anchor='middle' dominant-baseline='middle' font-style='italic'>d/dt</text></svg>";
    
    final String integratorIcon = "<svg><rect x='3' y='3' width='18' height='18' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='14.5' fill='blue' font-size='14' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>∫</text></svg>";
    
    final String odeIcon = "<svg><rect x='3' y='3' width='18' height='18' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='14.5' fill='blue' font-size='8' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>ODE</text></svg>";
    
    final String equationIcon = "<svg><rect x='3' y='3' width='18' height='18' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='14.5' fill='blue' font-size='14' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>=</text></svg>";
    
    final String percentIcon = "<svg><rect x='3' y='3' width='18' height='18' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='14.5' fill='blue' font-size='14' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>%</text></svg>";
    
    final String adderIcon = "<svg><rect x='3' y='3' width='18' height='18' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='14.5' fill='blue' font-size='16' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>+</text></svg>";
    
    final String subtracterIcon = "<svg><rect x='3' y='3' width='18' height='18' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='14.5' fill='blue' font-size='16' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>−</text></svg>";
    
    final String stopTimeIcon = "<svg><g transform='scale(1.3) translate(-3, 0)'><polygon points='12,3 17.5,5.5 20,11 17.5,16.5 12,19 6.5,16.5 4,11 6.5,5.5' fill='red' stroke='white' stroke-width='2' /><text x='12' y='11.5' fill='white' font-size='5' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>STOP</text></g></svg>";
    
    final String pieChartIcon = "<svg><circle cx='12' cy='12' r='8' fill='none' stroke='currentColor' stroke-width='2' /><path d='M 12 12 L 12 4 A 8 8 0 0 1 17.7 9.2 Z' fill='orange' stroke='currentColor' stroke-width='1' /><path d='M 12 12 L 17.7 9.2 A 8 8 0 0 1 17.7 14.8 Z' fill='green' stroke='currentColor' stroke-width='1' /><path d='M 12 12 L 17.7 14.8 A 8 8 0 0 1 12 20 Z' fill='blue' stroke='currentColor' stroke-width='1' /></svg>";
    
    final String viewportIcon = "<svg><rect x='4' y='4' width='16' height='16' fill='none' stroke='#4080FF' stroke-width='2' stroke-dasharray='4,2' /><rect x='7' y='7' width='10' height='10' fill='none' stroke='#4080FF' stroke-width='1' /><line x1='4' y1='4' x2='20' y2='20' stroke='#4080FF' stroke-width='1' /></svg>";
    
    final String graphIcon = "<svg><g transform='scale(0.99)'><rect x='4' y='4' width='16' height='16' fill='none' stroke='currentColor' stroke-width='2' /><polyline points='6,16 9,12 12,14 15,8 18,10' fill='none' stroke='currentColor' stroke-width='1.5' /></g></svg>";
        
    final String labelNodeIcon = "<svg><g transform='scale(0.99)'><rect x='4' y='8' width='16' height='12' rx='2' fill='none' stroke='currentColor' stroke-width='2' /><text x='12' y='15.5' fill='currentColor' font-size='8' text-anchor='middle' dominant-baseline='middle'>L</text></g></svg>";
    
}