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

package com.lushprojects.circuitjs1.client.elements.annotation;

import com.lushprojects.circuitjs1.client.*;
import com.lushprojects.circuitjs1.client.elements.economics.TableElm;
import com.lushprojects.circuitjs1.client.util.*;
import com.lushprojects.circuitjs1.client.ui.EditInfo;
import com.lushprojects.circuitjs1.client.ui.Checkbox;
import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramModel.*;
import com.lushprojects.circuitjs1.client.elements.annotation.SequenceDiagramParser.SourceResolution;

/**
 * SequenceDiagramElm - Renders UML sequence diagrams on the circuit canvas.
 * 
 * <p>This element supports a subset of PlantUML sequence diagram syntax for
 * visualizing economic flows between sectors. It can either use manually-entered
 * PlantUML source or auto-generate diagrams from TableElm data.
 * 
 * <h3>Supported PlantUML Syntax:</h3>
 * <ul>
 *   <li>{@code title <text>} - Diagram title (supports \n for line breaks)</li>
 *   <li>{@code actor <name>} - Stick figure participant</li>
 *   <li>{@code participant <name>} - Box participant</li>
 *   <li>{@code A -> B : label} - Solid arrow message</li>
 *   <li>{@code A --> B : label} - Dashed arrow message</li>
 *   <li>{@code == text ==} - Horizontal divider</li>
 *   <li>{@code note left/right of X} - Note attached to participant</li>
 *   <li>{@code note across} - Note spanning full width</li>
 * </ul>
 * 
 * <h3>Auto-Generation:</h3>
 * <p>Include {@code source:<TableName>} to auto-generate from a TableElm.
 * The diagram extracts flow transactions showing source/target sectors.
 * 
 * @see TableElm
 * @see GraphicElm
 * @see SequenceDiagramModel
 * @see SequenceDiagramParser
 */
public class SequenceDiagramElm extends GraphicElm implements DiagramRenderer {
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONSTANTS - Layout & Sizing
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Padding between frame edge and diagram content */
    private static final int FRAME_PADDING = 2;
    
    /** Horizontal margin for participant positioning */
    private static final int PARTICIPANT_SIDE_MARGIN = 48;
    
    /** Minimum allowed diagram scale factor */
    private static final double MIN_SCALE = 0.1;
    
    /** Maximum allowed diagram scale factor */
    private static final double MAX_SCALE = 10.0;
    
    /** Minimum frame dimension (width/height) in pixels */
    private static final int MIN_FRAME_SIZE = 16;
    
    /** Default diagram width when creating new element */
    private static final int DEFAULT_WIDTH = 560;
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONSTANTS - Drawing
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Stroke width for vertical lifelines */
    private static final double LIFELINE_STROKE_WIDTH = 1.8;
    
    /** Stroke width for transaction arrows */
    private static final double TRANSACTION_STROKE_WIDTH = 2.0;
    
    /** Dashed line segment length */
    private static final int DASH_LENGTH = 5;
    
    /** Gap between dashed line segments */
    private static final int DASH_GAP = 5;
    
    /** Corner radius for rounded rectangles */
    private static final int CORNER_RADIUS = 3;
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONSTANTS - Animation
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Default cycle duration in milliseconds (time to animate through all transactions) */
    private static final int DEFAULT_ANIMATION_CYCLE_MS = 500;
    
    /** Color for active/highlighted transaction arrow */
    private static final String HIGHLIGHT_COLOR = "#FF8C00";  // Dark orange
    
    /** Color for completed transactions (cumulative mode) */
    private static final String COMPLETED_COLOR = "#FFA500";  // Orange
    
    // ══════════════════════════════════════════════════════════════════════════
    // COLORS - Theme Configuration
    // ══════════════════════════════════════════════════════════════════════════
    
    private String bgColor = "#FFFFFF";              // Diagram background
    private String participantBgColor = "#E2E2F0";   // Participant box fill
    private String noteBgColor = "#FEFFDD";          // Note background (pale yellow)
    private String lineColor = "#181818";            // Lines and borders
    private String dividerColor = "#000000";         // Divider border lines
    private String dividerBgColor = "#EEEEEE";       // Divider background
    
    // ══════════════════════════════════════════════════════════════════════════
    // STATE - Source & Parsing
    // ══════════════════════════════════════════════════════════════════════════
    
    /** User-entered PlantUML source (may contain source: directive) */
    private String plantUmlSource;
    
    /** Resolved/rendered PlantUML source (after table expansion) */
    private String renderedPlantUmlSource;
    
    /** Name of source table (extracted from source: directive), null if none */
    private String sourceTableName;
    
    /** Reference to source table for auto-generation, null if manual */
    private TableElm sourceTable;
    
    /** Parsed diagram structure */
    private ParsedDiagram diagram;
    
    // ══════════════════════════════════════════════════════════════════════════
    // STATE - Layout Configuration
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Font size for labels and text */
    private int fontSize = 14;
    
    /** Horizontal spacing between adjacent participants */
    private int participantSpacing = 200;
    
    /** Y coordinate where lifelines begin (below participant headers) */
    private int lifelineStartY = 112;
    
    /** Logical diagram width in diagram coordinates (before scaling) */
    private int diagramWidth = 400;
    
    /** Calculated diagram height based on content */
    private int diagramHeight = 1000;
    
    /** Current scale factor for fitting diagram to frame */
    private double diagramScale = 1.0;
    
    /** Current Y position during drawing (tracks vertical progress) */
    private int currentY;
    
    // ══════════════════════════════════════════════════════════════════════════
    // STATE - Animation
    // ══════════════════════════════════════════════════════════════════════════
    
    /** Animation state machine controller */
    private final SequenceDiagramAnimationController animController = new SequenceDiagramAnimationController();
    
    // ══════════════════════════════════════════════════════════════════════════
    // CONSTRUCTORS & SERIALIZATION
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a new sequence diagram element at the specified position.
     * Initializes with a default SFC model example diagram.
     * 
     * @param xx X coordinate for placement
     * @param yy Y coordinate for placement
     */
    public SequenceDiagramElm(int xx, int yy) {
        super(xx, yy);
        plantUmlSource = SequenceDiagramParser.getDefaultDiagram();
        parseDiagram();
        syncFrameToScale();
    }
    
    /**
     * Constructs element from serialized circuit data.
     * 
     * @param xa Start X coordinate
     * @param ya Start Y coordinate
     * @param xb End X coordinate (defines frame width)
     * @param yb End Y coordinate (defines frame height)
     * @param f  Element flags
     * @param st StringTokenizer with: [source] [width] [scale]
     */
    public SequenceDiagramElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
        super(xa, ya, xb, yb, f);
        
        // Parse PlantUML source (required)
        plantUmlSource = st.hasMoreTokens() 
            ? CustomLogicModel.unescape(st.nextToken()) 
            : SequenceDiagramParser.getDefaultDiagram();
        
        // Parse diagram width (optional)
        diagramWidth = parseIntToken(st, DEFAULT_WIDTH);
        
        // Parse diagram scale (optional)
        diagramScale = clampScale(parseDoubleToken(st, 1.0));
        
        // Parse animation settings (optional, added in later version)
        // Note: pauseDuring and everyN are parsed for backward compatibility but ignored
        boolean animEnabled = parseIntToken(st, 1) != 0;
        int animStepMs = parseIntToken(st, SequenceDiagramAnimationController.DEFAULT_STEP_MS);
        parseIntToken(st, 0);  // Skip legacy pauseDuring
        parseIntToken(st, 1);  // Skip legacy everyN
        animController.loadConfig(animEnabled, animStepMs);
        
        parseDiagram();
        initializeFrameFromBounds();
    }
    
    /**
     * Serializes element state for circuit file storage.
     * Format: [baseData] [escapedSource] [width] [scale] [animEnabled] [animStepMs]
     */
    @Override
    protected String dump() {
        int[] animConfig = animController.getConfigForDump();
        return super.dump() + " " 
            + CustomLogicModel.escape(plantUmlSource) + " " 
            + diagramWidth + " " 
            + diagramScale + " "
            + animConfig[0] + " "
            + animConfig[1];
    }
    
    /** Returns unique dump type identifier for this element class */
    @Override
    protected int getDumpType() { 
        return 467; 
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS - Token Parsing
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Safely parses an integer from tokenizer with fallback default.
     */
    private int parseIntToken(StringTokenizer st, int defaultValue) {
        if (!st.hasMoreTokens()) return defaultValue;
        try {
            return Integer.parseInt(st.nextToken());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Safely parses a double from tokenizer with fallback default.
     */
    private double parseDoubleToken(StringTokenizer st, double defaultValue) {
        if (!st.hasMoreTokens()) return defaultValue;
        try {
            return Double.parseDouble(st.nextToken());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // GEOMETRY & FRAME MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void setPoints() {
        super.setPoints();
        setBbox(getFrameLeft(), getFrameTop(), getFrameRight(), getFrameBottom());
    }

    @Override
    protected int getNumHandles() {
        return 2;
    }

    /**
     * Handles drag of resize handles, enforcing minimum size constraints.
     * 
     * @param n  Handle index (0 = top-left, 1 = bottom-right)
     * @param dx Horizontal displacement
     * @param dy Vertical displacement
     */
    @Override
    protected void movePoint(int n, int dx, int dy) {
        if (n == 0) {
            x += dx;
            y += dy;
        } else {
            x2 += dx;
            y2 += dy;
        }
        enforceMinimumFrameSize(n);
        updateScaleFromFrame();
        setPoints();
    }
    
    /**
     * Handles interactive dragging during element creation.
     */
    @Override
    protected void drag(int xx, int yy) {
        x2 = xx;
        y2 = yy;
        enforceMinimumFrameSize(1);
        updateScaleFromFrame();
        setBbox(x, y, x2, y2);
    }

    /**
     * Initializes frame bounds from serialized coordinates.
     * Falls back to scale-based sizing if bounds are too small.
     */
    private void initializeFrameFromBounds() {
        if (getFrameWidth() < 32 || getFrameHeight() < 32) {
            syncFrameToScale();
        } else {
            updateScaleFromFrame();
            setPoints();
        }
    }

    /**
     * Sets frame size based on current diagram dimensions and scale.
     * Called after scale changes to update the visual frame.
     */
    private void syncFrameToScale() {
        int contentWidth = Math.max(1, (int) Math.round(diagramWidth * diagramScale));
        int contentHeight = Math.max(1, (int) Math.round(diagramHeight * diagramScale));
        x2 = x + contentWidth + FRAME_PADDING * 2;
        y2 = y + contentHeight + FRAME_PADDING * 2;
        setPoints();
    }

    /**
     * Enforces minimum frame dimensions when resizing.
     * Adjusts the appropriate coordinate based on which handle is being moved.
     * 
     * @param handleIndex 0 for start point, 1 for end point
     */
    private void enforceMinimumFrameSize(int handleIndex) {
        int minDimension = FRAME_PADDING * 2 + MIN_FRAME_SIZE;
        
        if (getFrameWidth() < minDimension) {
            int signedMin = signedLength(x, x2, minDimension);
            if (handleIndex == 0) {
                x = x2 - signedMin;
            } else {
                x2 = x + signedMin;
            }
        }
        
        if (getFrameHeight() < minDimension) {
            int signedMin = signedLength(y, y2, minDimension);
            if (handleIndex == 0) {
                y = y2 - signedMin;
            } else {
                y2 = y + signedMin;
            }
        }
    }

    /**
     * Returns length with sign matching the direction from start to end.
     */
    private int signedLength(int start, int end, int length) {
        return (end >= start) ? length : -length;
    }

    /**
     * Recalculates scale factor to fit diagram within current frame.
     */
    private void updateScaleFromFrame() {
        diagramScale = calculateFitScale();
    }

    /**
     * Calculates scale factor that fits diagram within available frame space.
     * Maintains aspect ratio by using the more restrictive dimension.
     * 
     * @return Scale factor in range [MIN_SCALE, MAX_SCALE]
     */
    private double calculateFitScale() {
        double availableWidth = getFrameWidth() - FRAME_PADDING * 2;
        double availableHeight = getFrameHeight() - FRAME_PADDING * 2;
        
        // Guard against division by zero
        if (availableWidth <= 0 || availableHeight <= 0 || 
            diagramWidth <= 0 || diagramHeight <= 0) {
            return MIN_SCALE;
        }
        
        double widthScale = availableWidth / diagramWidth;
        double heightScale = availableHeight / diagramHeight;
        return clampScale(Math.min(widthScale, heightScale));
    }

    /**
     * Constrains scale factor to valid range.
     */
    private double clampScale(double scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }
    
    // Frame coordinate accessors (handle inverted coordinates)
    private int getFrameLeft()   { return min(x, x2); }
    private int getFrameTop()    { return min(y, y2); }
    private int getFrameRight()  { return max(x, x2); }
    private int getFrameBottom() { return max(y, y2); }
    private int getFrameWidth()  { return Math.abs(x2 - x); }
    private int getFrameHeight() { return Math.abs(y2 - y); }
    
    // ══════════════════════════════════════════════════════════════════════════
    // PLANTUML PARSING - Source Resolution
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Refreshes the rendered source if it has changed.
     * Handles both manual source and auto-generated source from tables.
     * Uses dirty-checking to avoid unnecessary re-parsing.
     */
    private void refreshRenderedSourceIfNeeded() {
        SourceResolution resolution = SequenceDiagramParser.resolveSource(
            plantUmlSource, sim, SequenceDiagramParser.getDefaultDiagram());
        
        String nextSource = resolution.renderedSource;
        sourceTableName = resolution.sourceTableName;
        sourceTable = resolution.sourceTable;
        
        // Only re-parse if source has changed
        if (!nextSource.equals(renderedPlantUmlSource)) {
            renderedPlantUmlSource = nextSource;
            diagram = SequenceDiagramParser.parseDiagramText(renderedPlantUmlSource);
        }
    }

    /**
     * Entry point for diagram parsing. Refreshes source and parses if needed.
     */
    private void parseDiagram() {
        refreshRenderedSourceIfNeeded();
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // LAYOUT CALCULATION
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Calculates participant positions and diagram dimensions.
     * Called after parsing to prepare for rendering.
     */
    private void calculateLayout() {
        if (diagram == null || diagram.participants.size() == 0) return;
        
        int count = diagram.participants.size();
        
        // Calculate horizontal spacing
        int usableWidth = Math.max(1, diagramWidth - PARTICIPANT_SIDE_MARGIN * 2);
        
        if (count == 1) {
            // Single participant: center it
            participantSpacing = 0;
            diagram.participants.get(0).x = diagramWidth / 2;
        } else {
            // Multiple participants: distribute evenly
            participantSpacing = Math.max(110, usableWidth / (count - 1));
            int startX = Math.max(PARTICIPANT_SIDE_MARGIN, 
                                  (diagramWidth - participantSpacing * (count - 1)) / 2);
            
            for (int i = 0; i < count; i++) {
                diagram.participants.get(i).x = startX + participantSpacing * i;
            }
        }

        // Calculate vertical positioning
        lifelineStartY = getTopParticipantBottomY(46);
        
        // Sum element heights for total diagram height
        int contentHeight = lifelineStartY;
        for (int i = 0; i < diagram.elements.size(); i++) {
            DiagramElement elem = diagram.elements.get(i);
            contentHeight += elem.getHeight();
        }
        diagramHeight = contentHeight + 78;  // Footer space for bottom participants
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // DRAWING - Main Render Pipeline
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Main drawing entry point. Renders the complete sequence diagram.
     * Pipeline: refresh source → calculate layout → update animation → transform → draw components.
     */
    @Override
    protected void draw(Graphics g) {
        refreshRenderedSourceIfNeeded();
        calculateLayout();
        updateAnimationState();
        
        g.save();
        
        // Apply transform to center and scale diagram within frame
        applyDiagramTransform(g);
        
        // Set base font for diagram
        Font baseFont = new Font("SansSerif", 0, fontSize);
        g.setFont(baseFont);
        
        // Render diagram layers in order
        drawBackground(g);
        drawTitle(g);
        g.setFont(baseFont);  // Reset after title font change
        
        drawParticipantHeaders(g, 46);
        drawLifelines(g, 46);
        drawDiagramElements(g);
        drawParticipantFooters(g);
        
        g.restore();
        
        // Finalize bounds for selection
        updateScaleFromFrame();
        setBbox(getFrameLeft(), getFrameTop(), getFrameRight(), getFrameBottom());
        drawSelectionHighlight(g);
        
        // Auto-repaint only for running animations. In paused/manual step mode,
        // each Step command advances the animation one frame.
        if (animController.isAnimating() && sim != null && sim.simIsRunning()) {
            sim.repaint();
        }
    }
    
    /**
     * Applies canvas transform to center and scale diagram in frame.
     */
    private void applyDiagramTransform(Graphics g) {
        double fitScale = calculateFitScale();
        int renderedWidth = Math.max(1, (int) Math.round(diagramWidth * fitScale));
        int renderedHeight = Math.max(1, (int) Math.round(diagramHeight * fitScale));
        
        // Center diagram within frame
        int drawX = getFrameLeft() + (getFrameWidth() - renderedWidth) / 2;
        int drawY = getFrameTop() + (getFrameHeight() - renderedHeight) / 2;
        
        g.context.translate(drawX, drawY);
        g.context.scale(fitScale, fitScale);
    }
    
    /**
     * Fills diagram background with appropriate color for print/screen mode.
     */
    private void drawBackground(Graphics g) {
        String bg = sim.printableCheckItem.getState() ? "#FFFFFF" : bgColor;
        g.context.setFillStyle(bg);
        g.context.fillRect(0, 0, diagramWidth, diagramHeight);
    }
    
    /**
     * Draws diagram title (1-2 lines, centered, bold).
     */
    private void drawTitle(Graphics g) {
        if (diagram == null) return;
        if (diagram.titleLine1 == null && diagram.titleLine2 == null) return;
        
        Font titleFont = new Font("SansSerif", Font.BOLD, 14);
        g.setFont(titleFont);
        g.setColor("#000000");
        
        int titleY = 24;
        if (diagram.titleLine1 != null) {
            drawCenteredString(g, diagram.titleLine1, diagramWidth / 2, titleY);
            titleY += 15;
        }
        if (diagram.titleLine2 != null) {
            drawCenteredString(g, diagram.titleLine2, diagramWidth / 2, titleY);
        }
    }
    
    /**
     * Draws participant boxes/actors at top of diagram.
     */
    private void drawParticipantHeaders(Graphics g, int headerY) {
        if (diagram == null) return;
        for (int i = 0; i < diagram.participants.size(); i++) {
            drawParticipant(g, diagram.participants.get(i), headerY);
        }
    }
    
    /**
     * Draws vertical dashed lifelines for all participants.
     */
    private void drawLifelines(Graphics g, int headerY) {
        if (diagram == null) return;
        int topLifelineY = getTopParticipantBottomY(headerY);
        currentY = topLifelineY;
        int lifelineEndY = diagramHeight - 49;
        
        g.setColor(lineColor);
        for (int i = 0; i < diagram.participants.size(); i++) {
            Participant p = diagram.participants.get(i);
            drawDashedLine(g, p.x, currentY, p.x, lifelineEndY, LIFELINE_STROKE_WIDTH);
        }
    }
    
    /**
     * Draws all diagram elements (messages, dividers, notes) in sequence.
     */
    private void drawDiagramElements(Graphics g) {
        if (diagram == null) return;
        currentY = lifelineStartY + 24;
        for (int i = 0; i < diagram.elements.size(); i++) {
            DiagramElement elem = diagram.elements.get(i);
            elem.draw(g, this, currentY);
            currentY += elem.getHeight();
        }
    }
    
    /**
     * Draws participant boxes/actors at bottom of diagram (mirror of header).
     */
    private void drawParticipantFooters(Graphics g) {
        if (diagram == null) return;
        int footerY = diagramHeight - 49;
        for (int i = 0; i < diagram.participants.size(); i++) {
            drawParticipant(g, diagram.participants.get(i), footerY);
        }
    }
    
    /**
     * Draws selection rectangle if element is highlighted.
     */
    private void drawSelectionHighlight(Graphics g) {
        if (needsHighlight()) {
            g.setColor(selectColor);
            g.drawRect(boundingBox.x, boundingBox.y,
                      boundingBox.width, boundingBox.height);
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // ANIMATION LOGIC
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Updates animation state by delegating to the animation controller.
     */
    private void updateAnimationState() {
        int messageCount = (diagram != null) ? diagram.messageCount : 0;
        boolean simRunning = (sim != null) && sim.simIsRunning();
        double simTime = (sim != null) ? sim.getTime() : 0;
        
        animController.update(simTime, simRunning, messageCount, System.currentTimeMillis());
    }

    /**
     * Returns true if the message should be visible at the current animation step.
     */
    private boolean shouldShowMessageElement(int msgIndex) {
        return animController.isVisible(msgIndex);
    }
    
    /**
     * Advances one paused/manual animation frame. Returns true if the user's
     * step request should be consumed by animation instead of simulation.
     */
    private boolean advanceManualAnimationStep() {
        if (sim == null || sim.simIsRunning()) {
            return false;
        }
        refreshRenderedSourceIfNeeded();
        calculateLayout();
        return animController.advanceManualStep();
    }
    
    /**
     * Called by the simulator's manual Step command. If any sequence diagram is
     * paused mid-animation, advance that animation frame instead of simulation.
     */
    public static boolean advanceManualAnimationStep(CirSim sim) {
        if (sim == null || sim.simIsRunning()) {
            return false;
        }
        boolean consumed = false;
        for (int i = 0; i < sim.getElementCount(); i++) {
            CircuitElm elm = sim.getElm(i);
            if (!(elm instanceof SequenceDiagramElm)) {
                continue;
            }
            if (((SequenceDiagramElm) elm).advanceManualAnimationStep()) {
                consumed = true;
            }
        }
        return consumed;
    }
    
    /**
     * Returns the color to use for an arrow based on animation state.
     * Flash mode: only the currently active transaction is highlighted.
     */
    private String getArrowColor(int msgIndex) {
        if (animController.isHighlighted(msgIndex)) {
            return HIGHLIGHT_COLOR;
        }
        return lineColor;
    }
    
    /**
     * Returns the stroke width for an arrow based on animation state.
     * Active transaction gets a thicker line for emphasis.
     */
    private double getArrowStrokeWidth(int msgIndex) {
        if (animController.isHighlighted(msgIndex)) {
            return TRANSACTION_STROKE_WIDTH * SequenceDiagramAnimationController.HIGHLIGHT_STROKE_MULTIPLIER;
        }
        return TRANSACTION_STROKE_WIDTH;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DIAGRAM RENDERER INTERFACE IMPLEMENTATION
    // ══════════════════════════════════════════════════════════════════════════
    
    @Override
    public void drawMessage(Graphics g, Message msg, int y) {
        Participant fromP = (diagram != null) ? diagram.findParticipant(msg.from) : null;
        Participant toP = (diagram != null) ? diagram.findParticipant(msg.to) : null;
        if (fromP == null || toP == null) return;
        if (!shouldShowMessageElement(msg.messageIndex)) return;
        
        int x1 = fromP.x;
        int x2 = toP.x;
        int arrowY = y + msg.getLabelBlockHeight();
        
        // Draw label above arrow line (already passed visibility check above)
        drawMessageLabel(g, msg, x1, x2, y);
        
        // Determine arrow color based on animation state
        String arrowColor = getArrowColor(msg.messageIndex);
        double strokeWidth = getArrowStrokeWidth(msg.messageIndex);
        
        // Draw arrow line
        g.setColor(arrowColor);
        if (msg.dashed) {
            drawDashedLine(g, x1, arrowY, x2, arrowY, strokeWidth);
        } else {
            g.context.setLineWidth(strokeWidth);
            g.drawLine(x1, arrowY, x2, arrowY);
            g.context.setLineWidth(1);
        }
        
        // Draw arrowhead pointing to target
        drawArrowhead(g, x2, arrowY, x2 > x1, arrowColor);
    }
    
    /** Draws multi-line label centered above the arrow */
    private void drawMessageLabel(Graphics g, Message msg, int x1, int x2, int y) {
        if (msg.label == null || msg.label.isEmpty()) return;
        
        String[] lines = msg.label.split("\\\\n");
        int centerX = Math.min(x1, x2) + Math.abs(x2 - x1) / 2;
        int labelY = y - 3;
        
        for (String line : lines) {
            drawCenteredMaskedString(g, line, centerX, labelY);
            labelY += 15;
        }
    }
    
    /** Draws filled triangular arrowhead */
    private void drawArrowhead(Graphics g, int tipX, int tipY, boolean pointRight, String color) {
        int direction = pointRight ? 1 : -1;
        int baseX = tipX - (10 * direction);
        
        g.context.setFillStyle(color);
        g.context.beginPath();
        g.context.moveTo(tipX, tipY);
        g.context.lineTo(baseX, tipY - 4);
        g.context.lineTo(baseX, tipY + 4);
        g.context.closePath();
        g.context.fill();
    }
    
    @Override
    public void drawDivider(Graphics g, Divider divider, int y) {
        int left = 5;
        int right = diagramWidth - 5;
        int height = 23;
        int halfHeight = height / 2;
        
        // Draw background bar
        g.context.setFillStyle(dividerBgColor);
        g.context.fillRect(left, y - halfHeight, right - left, height);
        
        // Draw border lines (top and bottom)
        g.setColor(dividerColor);
        g.drawLine(left, y - halfHeight, right, y - halfHeight);
        g.drawLine(left, y + halfHeight, right, y + halfHeight);
        
        // Draw centered label
        if (divider.label != null) {
            g.setColor("#000000");
            int textWidth = (int) g.context.measureText(divider.label).getWidth();
            int textX = (left + right - textWidth) / 2;
            g.drawString(divider.label, textX, y + 5);
        }
    }
    
    @Override
    public void drawNote(Graphics g, Note note, int y) {
        int noteHeight = note.lines.size() * 15 + 20;
        int noteX, noteWidth;
        
        // Calculate position based on type
        if ("across".equals(note.position)) {
            noteX = 10;
            noteWidth = diagramWidth - 20;
        } else {
            Participant p = (diagram != null) ? diagram.findParticipant(note.target) : null;
            if (p == null) return;
            
            noteWidth = 150;
            noteX = "right".equals(note.position) 
                ? p.x + 10 
                : p.x - noteWidth - 10;
        }
        
        // Draw note background with folded corner effect
        drawNoteBackground(g, noteX, y, noteWidth, noteHeight);
        
        // Draw text lines centered
        g.setColor("#000000");
        int textY = y + 17;
        for (String line : note.lines) {
            int textWidth = (int) g.context.measureText(line).getWidth();
            g.drawString(line, noteX + (noteWidth - textWidth) / 2, textY);
            textY += 15;
        }
    }
    
    /** Draws note shape with folded corner (top-right) */
    private void drawNoteBackground(Graphics g, int x, int y, int width, int height) {
        int foldSize = 10;
        
        // Fill background
        g.context.setFillStyle(noteBgColor);
        g.context.beginPath();
        g.context.moveTo(x, y);
        g.context.lineTo(x + width - foldSize, y);
        g.context.lineTo(x + width, y + foldSize);
        g.context.lineTo(x + width, y + height);
        g.context.lineTo(x, y + height);
        g.context.closePath();
        g.context.fill();
        
        // Draw border
        g.setColor(lineColor);
        g.drawLine(x, y, x + width - foldSize, y);                          // Top
        g.drawLine(x + width - foldSize, y, x + width - foldSize, y + foldSize); // Fold vertical
        g.drawLine(x + width - foldSize, y + foldSize, x + width, y + foldSize); // Fold horizontal
        g.drawLine(x + width, y + foldSize, x + width, y + height);          // Right
        g.drawLine(x + width, y + height, x, y + height);                    // Bottom
        g.drawLine(x, y + height, x, y);                                     // Left
    }
    
    @Override
    public int getDiagramWidth() {
        return diagramWidth;
    }
    
    @Override
    public String getBgColor() {
        return bgColor;
    }
    
    @Override
    public String getDividerBgColor() {
        return dividerBgColor;
    }
    
    @Override
    public String getDividerColor() {
        return dividerColor;
    }
    
    @Override
    public String getLineColor() {
        return lineColor;
    }
    
    @Override
    public String getNoteBgColor() {
        return noteBgColor;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAWING - Participant Rendering
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Draws a participant based on its type (actor or box).
     */
    private void drawParticipant(Graphics g, Participant p, int topY) {
        if (p.isActor) {
            drawActor(g, p, topY);
        } else {
            drawParticipantBox(g, p, topY);
        }
    }
    
    /**
     * Draws a stick figure actor with name label below.
     */
    private void drawActor(Graphics g, Participant p, int topY) {
        int cx = p.x;
        int radius = 8;
        
        // Head (filled circle with border)
        g.context.setFillStyle(participantBgColor);
        g.context.beginPath();
        g.context.arc(cx, topY + radius, radius, 0, 2 * Math.PI);
        g.context.fill();
        g.setColor(lineColor);
        g.context.beginPath();
        g.context.arc(cx, topY + radius, radius, 0, 2 * Math.PI);
        g.context.stroke();
        
        // Body (vertical line)
        int bodyTop = topY + radius * 2;
        int bodyBot = bodyTop + 27;
        g.drawLine(cx, bodyTop, cx, bodyBot);
        
        // Arms (horizontal line)
        int armY = bodyTop + 8;
        g.drawLine(cx - 13, armY, cx + 13, armY);
        
        // Legs (two diagonal lines)
        g.drawLine(cx, bodyBot, cx - 13, bodyBot + 15);
        g.drawLine(cx, bodyBot, cx + 13, bodyBot + 15);
        
        // Name label
        drawCenteredMaskedString(g, p.name, cx, bodyBot + 35);
    }
    
    /**
     * Draws a rounded rectangle participant box with centered name.
     */
    private void drawParticipantBox(Graphics g, Participant p, int topY) {
        int textWidth = (int) g.context.measureText(p.name).getWidth();
        int boxWidth = textWidth + 14;
        int boxHeight = 30;
        int boxX = p.x - boxWidth / 2;
        
        // Draw rounded rectangle
        drawRoundedRect(g, boxX, topY, boxWidth, boxHeight, CORNER_RADIUS);
        
        // Draw centered name
        g.setColor("#000000");
        g.drawString(p.name, p.x - textWidth / 2, topY + 20);
    }
    
    /**
     * Draws a filled rounded rectangle with border.
     */
    private void drawRoundedRect(Graphics g, int x, int y, int w, int h, int r) {
        g.context.setFillStyle(participantBgColor);
        g.context.beginPath();
        g.context.moveTo(x + r, y);
        g.context.lineTo(x + w - r, y);
        g.context.quadraticCurveTo(x + w, y, x + w, y + r);
        g.context.lineTo(x + w, y + h - r);
        g.context.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        g.context.lineTo(x + r, y + h);
        g.context.quadraticCurveTo(x, y + h, x, y + h - r);
        g.context.lineTo(x, y + r);
        g.context.quadraticCurveTo(x, y, x + r, y);
        g.context.closePath();
        g.context.fill();
        
        g.setColor(lineColor);
        g.context.stroke();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DRAWING - Text & Line Utilities
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the Y coordinate of the bottom of the tallest participant header.
     */
    private int getTopParticipantBottomY(int topY) {
        if (diagram == null) return topY;
        int bottomY = topY;
        for (int i = 0; i < diagram.participants.size(); i++) {
            int connectorY = getParticipantConnectorBottom(diagram.participants.get(i), topY);
            bottomY = Math.max(bottomY, connectorY);
        }
        return bottomY;
    }

    /**
     * Returns the Y coordinate where lifeline connects to participant bottom.
     */
    private int getParticipantConnectorBottom(Participant p, int topY) {
        return p.isActor ? topY + 58 : topY + 30;
    }

    /**
     * Draws text centered at X with background mask for visibility over lifelines.
     */
    private void drawCenteredMaskedString(Graphics g, String text, int centerX, int baselineY) {
        if (text == null || text.isEmpty()) return;
        int textWidth = (int) g.context.measureText(text).getWidth();
        drawMaskedString(g, text, centerX - textWidth / 2, baselineY);
    }

    /**
     * Draws text with a white background rectangle for contrast.
     */
    private void drawMaskedString(Graphics g, String text, int textX, int baselineY) {
        if (text == null || text.isEmpty()) return;
        
        int textWidth = (int) g.context.measureText(text).getWidth();
        
        // Draw background mask
        g.context.setFillStyle(bgColor);
        g.context.fillRect(textX - 3, baselineY - 12, textWidth + 6, 16);
        
        // Draw text
        g.setColor("#000000");
        g.drawString(text, textX, baselineY);
    }

    /**
     * Draws text centered horizontally at the given position.
     */
    private void drawCenteredString(Graphics g, String text, int centerX, int baselineY) {
        int textWidth = (int) g.context.measureText(text).getWidth();
        g.drawString(text, centerX - textWidth / 2, baselineY);
    }
    
    /**
     * Draws a dashed line between two points with specified stroke width.
     * Uses DASH_LENGTH and DASH_GAP constants for consistent appearance.
     */
    private void drawDashedLine(Graphics g, int x1, int y1, int x2, int y2, double strokeWidth) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        // Avoid division by zero for zero-length lines
        if (distance < 1) return;
        
        // Calculate dash pattern
        double dashGap = DASH_LENGTH + DASH_GAP;
        int numDashes = (int) (distance / dashGap);
        
        // Unit vectors scaled to dash/gap lengths
        double xDash = dx / distance * DASH_LENGTH;
        double yDash = dy / distance * DASH_LENGTH;
        double xGap = dx / distance * DASH_GAP;
        double yGap = dy / distance * DASH_GAP;
        
        // Draw dash segments
        double curX = x1;
        double curY = y1;
        g.context.setLineWidth(strokeWidth);
        
        for (int i = 0; i < numDashes; i++) {
            g.drawLine((int) curX, (int) curY, 
                      (int) (curX + xDash), (int) (curY + yDash));
            curX += xDash + xGap;
            curY += yDash + yGap;
        }
        
        g.context.setLineWidth(1);
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // EDIT DIALOG
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns edit dialog configuration for the given parameter index.
     * 
     * @param n Parameter index (0=source, 1=width, 2=scale, 3=animation, 4=cycle, 5=pause, 6=everyN)
     * @return EditInfo for the parameter, or null if index out of range
     */
    @Override
    public EditInfo getEditInfo(int n) {
        switch (n) {
            case 0:  // PlantUML source text area
                EditInfo ei = new EditInfo("PlantUML Source", 0, -1, -1);
                ei.textArea = new com.google.gwt.user.client.ui.TextArea();
                ei.textArea.setVisibleLines(15);
                ei.textArea.setCharacterWidth(60);
                ei.textArea.setText(plantUmlSource);
                return ei;
                
            case 1:  // Diagram logical width
                return new EditInfo("Diagram Width", diagramWidth, 300, 800);
                
            case 2:  // Diagram scale factor
                return new EditInfo("Diagram Scale", diagramScale, 0.25, 4);
                
            case 3:  // Animation enabled checkbox
                EditInfo animEi = new EditInfo("", 0, -1, -1);
                animEi.checkbox = new Checkbox("Animate Transactions", animController.isEnabled());
                return animEi;
                
            case 4:  // Animation step duration
                return new EditInfo("Time Per Step (ms)", animController.getStepMs(), 50, 5000);
                
            default:
                return null;
        }
    }
    
    /**
     * Applies value from edit dialog to the specified parameter.
     * 
     * @param n  Parameter index
     * @param ei EditInfo containing the new value
     */
    @Override
    public void setEditValue(int n, EditInfo ei) {
        switch (n) {
            case 0:  // Update source and re-parse
                plantUmlSource = ei.textArea.getText();
                parseDiagram();
                break;
                
            case 1:  // Update width and recalculate scale
                diagramWidth = (int) ei.value;
                parseDiagram();
                updateScaleFromFrame();
                break;
                
            case 2:  // Update scale and resize frame
                diagramScale = Math.max(0.25, ei.value);
                syncFrameToScale();
                break;
                
            case 3:  // Toggle animation
                animController.setEnabled(ei.checkbox.getState());
                animController.reset();
                break;
                
            case 4:  // Update animation step duration
                animController.setStepMs(Math.max(50, (int) ei.value));
                break;
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // INFO DISPLAY & PUBLIC ACCESSORS
    // ══════════════════════════════════════════════════════════════════════════
    
    /**
     * Fills array with element information for tooltip display.
     */
    @Override
    protected void getInfo(String[] arr) {
        refreshRenderedSourceIfNeeded();
        
        int participantCount = (diagram != null) ? diagram.participants.size() : 0;
        int messageCount = (diagram != null) ? diagram.messageCount : 0;
        
        arr[0] = "Sequence Diagram";
        arr[1] = participantCount + " participants, " + messageCount + " transactions";
        
        arr[2] = animController.isEnabled() ? "Animation: ON" : "Animation: OFF";
        
        if (animController.isEnabled()) {
            int activeIdx = animController.getActiveIndex();
            if (animController.isAnimating() && activeIdx >= 0 && messageCount > 0) {
                arr[3] = "Animate step: " + (activeIdx + 1) + "/" + messageCount;
            } else {
                arr[3] = "Animate step: idle";
            }
        }
        
        // Show source table info if applicable
        if (sourceTable != null) {
            arr[4] = "Source: " + sourceTable.getTableTitle();
        } else if (sourceTableName != null) {
            arr[4] = sourceTableName.isEmpty() 
                ? "Source: (auto)" 
                : "Source: " + sourceTableName + " (not found)";
        }
    }

    /** Returns the raw PlantUML source text (may contain source: directive) */
    public String getPlantUmlSource() {
        return plantUmlSource;
    }

    /** Returns the rendered/expanded PlantUML source after table expansion */
    public String getRenderedPlantUmlSource() {
        refreshRenderedSourceIfNeeded();
        return renderedPlantUmlSource;
    }

    /** Returns the source table name from source: directive, or null */
    public String getSourceTableName() {
        refreshRenderedSourceIfNeeded();
        return sourceTableName;
    }

    /** Returns the logical diagram width (before scaling) */
    public int getLogicalDiagramWidth() {
        return diagramWidth;
    }

    /** Returns the calculated diagram height (before scaling) */
    public int getDiagramHeight() {
        return diagramHeight;
    }

    /** Returns the current scale factor used for rendering */
    public double getDiagramScale() {
        return calculateFitScale();
    }

    /** Returns the actual rendered width in canvas pixels */
    public int getRenderedDiagramWidth() {
        return getFrameWidth();
    }

    /** Returns the actual rendered height in canvas pixels */
    public int getRenderedDiagramHeight() {
        return getFrameHeight();
    }
}
