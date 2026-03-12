/*    
    Copyright (C) Paul Falstad
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.Duration;
import com.lushprojects.circuitjs1.client.util.Locale;
import com.lushprojects.circuitjs1.client.EquationTableElm.RowOutputMode;

/**
 * EquationTableRenderer — All drawing operations for {@link EquationTableElm}.
 *
 * <h3>Responsibility</h3>
 * Separates visual presentation from circuit simulation logic, following the same
 * delegation pattern as {@code SFCTableRenderer} / {@code TableRenderer}.
 *
 * <h3>Caching Strategy</h3>
 * Static parts of the table (row backgrounds, zebra striping, grid lines, rounded border)
 * are rendered once into an off-screen {@link Canvas} and composited each frame with
 * {@code drawImage}.  Only dynamic content (row text, hover highlight, values, selection
 * border) is drawn directly to the main canvas each frame.
 *
 * The cache is invalidated (via {@link #invalidateCache()}) whenever the table structure
 * changes: rows added/removed, display size changed, or print mode toggled.
 *
 * <h3>Theming</h3>
 * Colors switch automatically between dark mode (default) and light/{@code printable} mode
 * using the {@code theSim.printableCheckItem} state flag.  All theme colors are defined as
 * static constants at the top of the class.
 *
 * <h3>Row Icons</h3>
 * Each data row renders a set of icons in its left margin:
 * <ul>
 *   <li><b>↕ (adjustable)</b>: row equation is a plain number; mouse-wheel adjusts it.</li>
 *   <li><b>I→ / P (mode)</b>: FLOW / PARAM mode indicator.</li>
 *   <li><b>⟳ (classification)</b>: cyclic row (member of a graph cycle).</li>
 * </ul>
 *
 * @see EquationTableElm#draw(Graphics) Main entry point that delegates here
 * @see EquationTableElm#setPoints()   Causes cache invalidation on resize
 */
public class EquationTableRenderer {
    private final EquationTableElm table;

    private String[] cachedDisplayEquationByRow;
    private String[] cachedEquationSourceByRow;
    private String[] cachedSliderVarByRow;
    private double[] cachedSliderValueByRow;
    private String[] cachedOutputNameByRow;
    private String[] cachedRawOutputNameByRow;
    
    // Fonts for different parts of the table
    private Font labelFont;
    private Font valueFont;
    private Font perfFont = new Font("SansSerif", 0, 9);
    private double renderTimeEmaMs = 0;
    private boolean hasRenderTimingSample = false;
    
    // Modern styling configuration (matches TableRenderer)
    private static final boolean MODERN_STYLE = true;
    private static final int CORNER_RADIUS = 8;
    
    // Cached canvas for static parts (grid lines, backgrounds, borders)
    // Only text/hover is redrawn each frame - major performance win for tables
    private Canvas backgroundLayerCanvas;
    private Context2d backgroundLayerCtx;
    private Canvas contentLayerCanvas;
    private Context2d contentLayerCtx;
    private boolean backgroundCacheValid = false;
    private boolean contentCacheValid = false;
    private int backgroundCachedWidth = 0;
    private int backgroundCachedHeight = 0;
    private int backgroundCachedRowCount = 0;
    private boolean backgroundCachedPrintable = false;
    private float backgroundCachedScale = -1;
    private int contentCachedWidth = 0;
    private int contentCachedHeight = 0;
    private int contentCachedRowCount = 0;
    private boolean contentCachedPrintable = false;
    private boolean contentCachedVoltsVisible = false;
    private float contentCachedScale = -1;
    private long contentCachedTimeBucket = -1;
    private static final long CONTENT_CACHE_UPDATE_INTERVAL_MS = 200;
    private int cacheRebuildCount = 0;
    
    // Dark mode colors (matches TableRenderer)
    private static final Color HEADER_BG_DARK = new Color(55, 55, 75);
    private static final Color ROW_EVEN_BG_DARK = new Color(45, 45, 50);
    private static final Color ROW_ODD_BG_DARK = new Color(35, 35, 40);
    private static final Color TABLE_BG_DARK = new Color(30, 30, 35);
    private static final Color GRID_LINE_DARK = new Color(60, 60, 65);
    
    // Light mode colors (matches TableRenderer)
    private static final Color HEADER_BG_LIGHT = new Color(235, 235, 245);
    private static final Color ROW_EVEN_BG_LIGHT = new Color(248, 248, 252);
    private static final Color ROW_ODD_BG_LIGHT = new Color(255, 255, 255);
    private static final Color TABLE_BG_LIGHT = new Color(255, 255, 255);
    private static final Color GRID_LINE_LIGHT = new Color(220, 220, 230);
    
    public EquationTableRenderer(EquationTableElm table) {
        this.table = table;
        initCache();
    }
    
    /**
     * Initialize the cached canvas for static parts.
     */
    private void initCache() {
        backgroundLayerCanvas = Canvas.createIfSupported();
        if (backgroundLayerCanvas != null) {
            backgroundLayerCtx = backgroundLayerCanvas.getContext2d();
        }
        contentLayerCanvas = Canvas.createIfSupported();
        if (contentLayerCanvas != null) {
            contentLayerCtx = contentLayerCanvas.getContext2d();
        }
    }

    private float getRenderScale() {
        if (CirSim.theSim == null) {
            return 1;
        }
        return Math.max(1, CirSim.devicePixelRatio());
    }
    
    /**
     * Invalidate the cached static rendering.
     * Call this when table structure changes (resize, rows added/removed).
     */
    public void invalidateCache() {
        backgroundCacheValid = false;
        contentCacheValid = false;
        clearRowTextCaches();
    }

    private boolean isVoltsVisible() {
        return CirSim.theSim != null && CirSim.theSim.voltsCheckItem != null && CirSim.theSim.voltsCheckItem.getState();
    }

    private void clearRowTextCaches() {
        cachedDisplayEquationByRow = null;
        cachedEquationSourceByRow = null;
        cachedSliderVarByRow = null;
        cachedSliderValueByRow = null;
        cachedOutputNameByRow = null;
        cachedRawOutputNameByRow = null;
    }

    private void ensureRowTextCacheCapacity(int rowCount) {
        if (cachedDisplayEquationByRow == null || cachedDisplayEquationByRow.length != rowCount) {
            cachedDisplayEquationByRow = new String[rowCount];
            cachedEquationSourceByRow = new String[rowCount];
            cachedSliderVarByRow = new String[rowCount];
            cachedSliderValueByRow = new double[rowCount];
            cachedOutputNameByRow = new String[rowCount];
            cachedRawOutputNameByRow = new String[rowCount];
            for (int i = 0; i < rowCount; i++) {
                cachedSliderValueByRow[i] = Double.NaN;
            }
        }
    }
    
    /**
     * Ensure cache is valid and properly sized.
     * Returns true if cache is usable, false if no caching available.
     */
    private boolean ensureCacheValid(int width, int height, int rowCount) {
        if (backgroundLayerCanvas == null || backgroundLayerCtx == null) {
            return false;
        }
        if (CirSim.theSim != null && !CirSim.theSim.tableRenderCacheEnabled) {
            return false;
        }
        
        boolean printable = isPrintable();
        float renderScale = getRenderScale();
        
        // Check if cache needs refresh
        if (!backgroundCacheValid || width != backgroundCachedWidth || height != backgroundCachedHeight || 
            rowCount != backgroundCachedRowCount || printable != backgroundCachedPrintable ||
            renderScale != backgroundCachedScale) {
            
            // Resize canvas if needed
            if (width != backgroundCachedWidth || height != backgroundCachedHeight || renderScale != backgroundCachedScale) {
                backgroundLayerCanvas.setCoordinateSpaceWidth((int) Math.ceil(width * renderScale));
                backgroundLayerCanvas.setCoordinateSpaceHeight((int) Math.ceil(height * renderScale));
            }

            backgroundLayerCtx.setTransform(1, 0, 0, 1, 0, 0);
            backgroundLayerCtx.clearRect(0, 0, backgroundLayerCanvas.getCoordinateSpaceWidth(), backgroundLayerCanvas.getCoordinateSpaceHeight());
            backgroundLayerCtx.setTransform(renderScale, 0, 0, renderScale, 0, 0);
            
            // Draw static parts to cache
            drawStaticToCache(width, height, rowCount);
            cacheRebuildCount++;
            
            // Update cached state
            backgroundCachedWidth = width;
            backgroundCachedHeight = height;
            backgroundCachedRowCount = rowCount;
            backgroundCachedPrintable = printable;
            backgroundCachedScale = renderScale;
            backgroundCacheValid = true;
        }
        
        return true;
    }

    private boolean ensureContentCacheValid(int tableX, int tableY, int width, int height, int rowCount) {
        if (contentLayerCanvas == null || contentLayerCtx == null) {
            return false;
        }
        if (CirSim.theSim != null && !CirSim.theSim.tableRenderCacheEnabled) {
            return false;
        }

        boolean printable = isPrintable();
        boolean voltsVisible = isVoltsVisible();
        float renderScale = getRenderScale();
        long timeBucket = System.currentTimeMillis() / CONTENT_CACHE_UPDATE_INTERVAL_MS;

        if (!contentCacheValid || width != contentCachedWidth || height != contentCachedHeight ||
            rowCount != contentCachedRowCount || printable != contentCachedPrintable ||
            voltsVisible != contentCachedVoltsVisible || renderScale != contentCachedScale ||
            timeBucket != contentCachedTimeBucket) {

            if (width != contentCachedWidth || height != contentCachedHeight || renderScale != contentCachedScale) {
                contentLayerCanvas.setCoordinateSpaceWidth((int) Math.ceil(width * renderScale));
                contentLayerCanvas.setCoordinateSpaceHeight((int) Math.ceil(height * renderScale));
            }

            contentLayerCtx.setTransform(1, 0, 0, 1, 0, 0);
            contentLayerCtx.clearRect(0, 0, contentLayerCanvas.getCoordinateSpaceWidth(), contentLayerCanvas.getCoordinateSpaceHeight());
            contentLayerCtx.setTransform(renderScale, 0, 0, renderScale, 0, 0);

            Graphics cacheGraphics = new Graphics(contentLayerCtx);
            contentLayerCtx.save();
            contentLayerCtx.translate(-tableX, -tableY);
            drawTitleRow(cacheGraphics, tableX, tableY);
            cacheGraphics.setFont(valueFont);
            for (int row = 0; row < rowCount; row++) {
                drawDataRow(cacheGraphics, tableX, tableY, row, true, false);
            }
            contentLayerCtx.restore();

            contentCachedWidth = width;
            contentCachedHeight = height;
            contentCachedRowCount = rowCount;
            contentCachedPrintable = printable;
            contentCachedVoltsVisible = voltsVisible;
            contentCachedScale = renderScale;
            contentCachedTimeBucket = timeBucket;
            contentCacheValid = true;
        }

        return true;
    }
    
    /**
     * Draw static parts (backgrounds, grid lines, borders) to cached canvas.
     * This is only called when cache is invalid.
     */
    private void drawStaticToCache(int width, int height, int rowCount) {
        Context2d ctx = backgroundLayerCtx;
        int rowHeight = table.getRowHeight();
        
        // Clear canvas
        ctx.clearRect(0, 0, width, height);
        
        // Draw table background with rounded corners
        if (MODERN_STYLE) {
            ctx.setFillStyle(getTableBgColor().getHexValue());
            // Simple rounded rect using arc
            ctx.beginPath();
            ctx.moveTo(CORNER_RADIUS, 0);
            ctx.lineTo(width - CORNER_RADIUS, 0);
            ctx.arcTo(width, 0, width, CORNER_RADIUS, CORNER_RADIUS);
            ctx.lineTo(width, height - CORNER_RADIUS);
            ctx.arcTo(width, height, width - CORNER_RADIUS, height, CORNER_RADIUS);
            ctx.lineTo(CORNER_RADIUS, height);
            ctx.arcTo(0, height, 0, height - CORNER_RADIUS, CORNER_RADIUS);
            ctx.lineTo(0, CORNER_RADIUS);
            ctx.arcTo(0, 0, CORNER_RADIUS, 0, CORNER_RADIUS);
            ctx.closePath();
            ctx.fill();
        } else {
            ctx.setFillStyle("#333333");
            ctx.fillRect(0, 0, width, height);
        }
        
        // Draw title row background (header style)
        ctx.setFillStyle(getHeaderBgColor().getHexValue());
        ctx.fillRect(2, 2, width - 4, rowHeight - 2);
        
        // Draw data rows with zebra striping
        for (int row = 0; row < rowCount; row++) {
            int rowY = (row + 1) * rowHeight;
            ctx.setFillStyle(getRowBgColor(row).getHexValue());
            ctx.fillRect(2, rowY, width - 4, rowHeight);
        }
        
        // Draw row separator lines
        ctx.setStrokeStyle(getGridLineColor().getHexValue());
        ctx.setLineWidth(1);
        // Title separator
        ctx.beginPath();
        ctx.moveTo(0, rowHeight);
        ctx.lineTo(width, rowHeight);
        ctx.stroke();
        // Row separators
        for (int row = 0; row < rowCount - 1; row++) {
            int sepY = (row + 2) * rowHeight;
            ctx.beginPath();
            ctx.moveTo(0, sepY);
            ctx.lineTo(width, sepY);
            ctx.stroke();
        }
        
        // Draw border (non-selected state - selected border drawn dynamically)
        if (MODERN_STYLE) {
            ctx.setStrokeStyle(getGridLineColor().getHexValue());
            ctx.beginPath();
            ctx.moveTo(CORNER_RADIUS, 0);
            ctx.lineTo(width - CORNER_RADIUS, 0);
            ctx.arcTo(width, 0, width, CORNER_RADIUS, CORNER_RADIUS);
            ctx.lineTo(width, height - CORNER_RADIUS);
            ctx.arcTo(width, height, width - CORNER_RADIUS, height, CORNER_RADIUS);
            ctx.lineTo(CORNER_RADIUS, height);
            ctx.arcTo(0, height, 0, height - CORNER_RADIUS, CORNER_RADIUS);
            ctx.lineTo(0, CORNER_RADIUS);
            ctx.arcTo(0, 0, CORNER_RADIUS, 0, CORNER_RADIUS);
            ctx.closePath();
            ctx.stroke();
        } else {
            ctx.setStrokeStyle("#808080");
            ctx.strokeRect(0, 0, width, height);
        }
    }

    //=============================================================================
    // THEME-AWARE COLOR HELPERS
    //
    // Each helper checks isPrintable() to select between the light (printable)
    // and dark (normal) color palettes defined above.
    //=============================================================================

    // Helper methods for theme-aware colors
    private boolean isPrintable() {
        return CirSim.theSim.printableCheckItem.getState();
    }
    
    private Color getHeaderBgColor() {
        return isPrintable() ? HEADER_BG_LIGHT : HEADER_BG_DARK;
    }
    
    private Color getRowBgColor(int row) {
        if (isPrintable()) {
            return (row % 2 == 0) ? ROW_EVEN_BG_LIGHT : ROW_ODD_BG_LIGHT;
        }
        return (row % 2 == 0) ? ROW_EVEN_BG_DARK : ROW_ODD_BG_DARK;
    }
    
    private Color getTableBgColor() {
        return isPrintable() ? TABLE_BG_LIGHT : TABLE_BG_DARK;
    }
    
    private Color getGridLineColor() {
        return isPrintable() ? GRID_LINE_LIGHT : GRID_LINE_DARK;
    }
    
    private Color getTextColor() {
        if (MODERN_STYLE && isPrintable()) {
            return new Color(30, 30, 30); // Dark text for light backgrounds
        }
        return CircuitElm.whiteColor;
    }
    
    /**
     * Return a voltage-keyed color for displaying a numeric value on the right side
     * of each row.  When the "Voltage Colors" display option is off, returns plain white.
     * When on, maps the value through the standard {@link CircuitElm#colorScale} gradient
     * (red for negative, green for positive, neutral for zero) using the circuit-wide
     * {@code voltageRange} as the scale.
     *
     * @param volts Value to colorize (treated as a voltage).
     * @return Color from the voltage color scale.
     */
    private Color getVoltageColor(double volts) {
        if (!CirSim.theSim.voltsCheckItem.getState()) {
            return CircuitElm.whiteColor;
        }
        int c = (int) ((volts + CircuitElm.voltageRange) * (CircuitElm.colorScaleCount - 1) /
                       (CircuitElm.voltageRange * 2));
        if (c < 0)
            c = 0;
        if (c >= CircuitElm.colorScaleCount)
            c = CircuitElm.colorScaleCount - 1;
        return CircuitElm.colorScale[c];
    }
    
    //=============================================================================
    // FONT MANAGEMENT
    //=============================================================================
    
    /**
     * Update fonts based on current display size.
     * Called when the table size changes.
     */
    public void updateFonts(int opsize) {
        labelFont = new Font("SansSerif", 0, opsize == 2 ? 12 : 10);
        valueFont = new Font("SansSerif", 0, opsize == 2 ? 10 : 8);
        perfFont = new Font("SansSerif", 0, opsize == 2 ? 9 : 8);
    }
    
    //=============================================================================
    // MAIN DRAWING
    //=============================================================================
    
    /**
     * Draw the table element.
     * Uses cached canvas for static parts (backgrounds, grid lines, borders).
     * Only text and hover effects are drawn each frame.
     */
    public void draw(Graphics g) {
        double renderStartMs = Duration.currentTimeMillis();
        int tableX = table.x;
        int tableY = table.y;
        boolean selected = table.needsHighlight();
        int tableWidth = table.getTableWidth();
        int tableHeight = table.getTableHeight();
        int rowHeight = table.getRowHeight();
        int rowCount = table.getRowCount();
        
        // Try to use cached static rendering
        boolean usingCache = ensureCacheValid(tableWidth, tableHeight, rowCount);
        boolean usingContentCache = false;
        
        if (usingCache) {
            // Blit cached background/grid to main canvas
            g.context.drawImage(backgroundLayerCtx.getCanvas(), tableX, tableY, tableWidth, tableHeight);
            
            // Draw selection border on top if selected (dynamic - not cached)
            if (selected) {
                drawSelectionBorder(g, tableX, tableY, tableWidth, tableHeight);
            }
        } else {
            // Fallback: draw everything directly (no cache available)
            if (MODERN_STYLE) {
                g.setColor(getTableBgColor());
                g.fillRoundRect(tableX + 1, tableY + 1, tableWidth - 2, tableHeight - 2, CORNER_RADIUS);
            } else {
                g.setColor(Color.darkGray);
                g.fillRect(tableX, tableY, tableWidth, tableHeight);
            }
            
            if (MODERN_STYLE) {
                drawRowBackgrounds(g, tableX, tableY, tableWidth, rowHeight);
            }
            
            drawTableBorder(g, tableX, tableY, selected);
        }
        
        // Update hover state
        updateHoveredRow(tableX, tableY);

        if (usingCache) {
            usingContentCache = ensureContentCacheValid(tableX, tableY, tableWidth, tableHeight, rowCount);
        }
        
        // Draw hover highlight (dynamic - not cached)
        drawHoverHighlight(g, tableX, tableY);
        
        if (usingContentCache) {
            g.context.drawImage(contentLayerCtx.getCanvas(), tableX, tableY, tableWidth, tableHeight);
        } else {
            // Draw title row text (dynamic - not cached)
            drawTitleRow(g, tableX, tableY);

            // Draw data rows (text only when using cache, full when not)
            g.setFont(valueFont);
            for (int row = 0; row < rowCount; row++) {
                drawDataRow(g, tableX, tableY, row, usingCache, true);
            }
        }
        
        // Update bounding box
        table.setBbox(tableX, tableY, tableX + tableWidth, tableY + tableHeight);

        double renderMs = Duration.currentTimeMillis() - renderStartMs;
        if (!hasRenderTimingSample) {
            renderTimeEmaMs = renderMs;
            hasRenderTimingSample = true;
        } else {
            renderTimeEmaMs = renderTimeEmaMs * 0.9 + renderMs * 0.1;
        }
        drawRenderTimingOverlay(g, tableX, tableY, selected, usingCache, renderMs);
    }

    private void drawRenderTimingOverlay(Graphics g, int tableX, int tableY, boolean selected, boolean usingCache, double renderMs) {
        if (CirSim.theSim == null || !CirSim.theSim.developerMode || !selected) {
            return;
        }
        String timingText = (usingCache ? "C " : "N ") +
            formatTimingMs(renderMs) + "ms (" +
            formatTimingMs(renderTimeEmaMs) + "ms) R:" + cacheRebuildCount;
        g.setFont(perfFont);
        g.setColor(isPrintable() ? new Color(40, 40, 40) : new Color(170, 255, 210));
        g.drawString(timingText, tableX + 26, tableY + 10);
    }

    private String formatTimingMs(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        String text = Double.toString(rounded);

        int dot = text.indexOf('.');
        if (dot < 0) {
            return text + ".00";
        }

        int decimals = text.length() - dot - 1;
        if (decimals == 0) {
            return text + "00";
        }
        if (decimals == 1) {
            return text + "0";
        }
        return text;
    }
    
    /**
     * Draw selection border (only when table is selected).
     */
    private void drawSelectionBorder(Graphics g, int tableX, int tableY, int tableWidth, int tableHeight) {
        g.setColor(CircuitElm.selectColor);
        if (MODERN_STYLE) {
            g.drawRoundRect(tableX, tableY, tableWidth, tableHeight, CORNER_RADIUS);
            g.drawRoundRect(tableX + 1, tableY + 1, tableWidth - 2, tableHeight - 2, CORNER_RADIUS);
        } else {
            g.drawRect(tableX, tableY, tableWidth, tableHeight);
            g.drawRect(tableX + 1, tableY + 1, tableWidth - 2, tableHeight - 2);
        }
    }
    
    /**
     * Draw hover highlight for the currently hovered row.
     */
    private void drawHoverHighlight(Graphics g, int tableX, int tableY) {
        int hoveredRow = table.getHoveredRow();
        if (hoveredRow >= 0 && hoveredRow < table.getRowCount()) {
            int rowY = tableY + (hoveredRow + 1) * table.getRowHeight();
            Color hoverColor = isPrintable() ? new Color(220, 220, 240) : new Color(65, 65, 85);
            g.setColor(hoverColor);
            g.fillRect(tableX + 1, rowY + 1, table.getTableWidth() - 2, table.getRowHeight() - 1);
        }
    }
    
    /**
     * Draw row backgrounds with zebra striping and header styling.
     */
    private void drawRowBackgrounds(Graphics g, int tableX, int tableY, int tableWidth, int rowHeight) {
        // Title row background (header style)
        g.setColor(getHeaderBgColor());
        g.fillRect(tableX + 2, tableY + 2, tableWidth - 4, rowHeight - 2);
        
        // Data rows with zebra striping
        for (int row = 0; row < table.getRowCount(); row++) {
            int rowY = tableY + (row + 1) * rowHeight;
            g.setColor(getRowBgColor(row));
            g.fillRect(tableX + 2, rowY, tableWidth - 4, rowHeight);
        }
    }
    
    /**
     * Draw the table border, with highlighting when selected.
     * Uses modern rounded corners when MODERN_STYLE is enabled.
     */
    private void drawTableBorder(Graphics g, int tableX, int tableY, boolean selected) {
        if (MODERN_STYLE) {
            g.setColor(selected ? CircuitElm.selectColor : getGridLineColor());
            g.drawRoundRect(tableX, tableY, table.getTableWidth(), table.getTableHeight(), CORNER_RADIUS);
            if (selected) {
                g.drawRoundRect(tableX + 1, tableY + 1, table.getTableWidth() - 2, table.getTableHeight() - 2, CORNER_RADIUS);
            }
        } else {
            g.setColor(selected ? CircuitElm.selectColor : Color.gray);
            g.drawRect(tableX, tableY, table.getTableWidth(), table.getTableHeight());
            if (selected) {
                g.drawRect(tableX + 1, tableY + 1, table.getTableWidth() - 2, table.getTableHeight() - 2);
            }
        }
    }
    
    /**
     * Draw the title row with table name.
     */
    private void drawTitleRow(Graphics g, int tableX, int tableY) {
        g.setFont(labelFont);
        g.setColor(getTextColor());
        int titleY = tableY + table.getRowHeight() - table.getCellPadding();
        table.drawCenteredText(g, table.getTableName(), tableX + table.getTableWidth() / 2, titleY - 2, true);
        
        // Separator line after title
        g.setColor(getGridLineColor());
        g.drawLine(tableX, tableY + table.getRowHeight(), tableX + table.getTableWidth(), tableY + table.getRowHeight());
    }
    
    /**
     * Update which row the mouse is hovering over.
     */
    private void updateHoveredRow(int tableX, int tableY) {
        int newHoveredRow = -1;
        int mouseCircuitX = table.sim.inverseTransformX(table.sim.mouseCursorX);
        int mouseCircuitY = table.sim.inverseTransformY(table.sim.mouseCursorY);
        
        if (mouseCircuitX >= tableX && mouseCircuitX <= tableX + table.getTableWidth() &&
            mouseCircuitY >= tableY && mouseCircuitY <= tableY + table.getTableHeight()) {
            // Calculate row index, accounting for title row
            int relativeY = mouseCircuitY - (tableY + table.getRowHeight());
            if (relativeY >= 0) {
                int mouseRowIndex = relativeY / table.getRowHeight();
                if (mouseRowIndex >= 0 && mouseRowIndex < table.getRowCount()) {
                    newHoveredRow = mouseRowIndex;
                }
            }
        }
        
        // Only update if changed to avoid cursor flickering
        if (newHoveredRow != table.getHoveredRow()) {
            table.setHoveredRow(newHoveredRow);
        }
    }
    
    /**
     * Draw a single data row.
     * @param usingCache If true, skip drawing backgrounds/separators (they're in cached canvas)
     */
    private void drawDataRow(Graphics g, int tableX, int tableY, int row, boolean usingCache, boolean allowHoverStyling) {
        int rowY = tableY + (row + 1) * table.getRowHeight();
        int rowHeight = table.getRowHeight();
        int cellPadding = table.getCellPadding();
        int tableWidth = table.getTableWidth();
        boolean isHovered = allowHoverStyling && (row == table.getHoveredRow());
        int iconAreaStartX = tableX + cellPadding;
        int textX = iconAreaStartX + getRowIconReservedWidth(g);

        if (table.isCommentRow(row)) {
            String comment = table.getCommentText(row);
            g.setFont(new Font("SansSerif", Font.BOLD, table.getOpsize() == 2 ? 10 : 8));
            g.setColor(getTextColor());
            g.drawString("# " + Locale.convertGreekSymbols(comment), textX,
                rowY + rowHeight - cellPadding - 2);
            g.setFont(valueFont);

            if (!usingCache && row < table.getRowCount() - 1) {
                g.setColor(getGridLineColor());
                int sepY = tableY + (row + 2) * rowHeight;
                g.drawLine(tableX, sepY, tableX + tableWidth, sepY);
            }
            return;
        }
        
        // Build display equation with slider value substituted
        String displayEquation = buildDisplayEquation(row);
        String outputName = getCachedOutputName(row);
        String rowText;
        if (containsStandaloneAssignment(displayEquation)) {
            rowText = displayEquation;
        } else {
            rowText = outputName + " = " + displayEquation;
        }
        
        // Draw scroll icon on adjustable rows (numeric equations) to indicate mouse wheel adjustment
        int iconX = iconAreaStartX;
        boolean isAdjustable = table.isAdjustableRow(row);
        if (isAdjustable) {
            // Use larger bold font for icon
            int iconSize = table.getOpsize() == 1 ? 12 : 16;
            g.setFont(new Font("SansSerif", Font.BOLD, iconSize));
            g.setColor(isHovered ? new Color(0, 100, 200) : new Color(0, 60, 140));  // Dark blue, darker on hover
            g.drawString("↕", iconX, rowY + rowHeight - cellPadding - 1);
            int iconWidth = (int) g.context.measureText("↕ ").getWidth();
            g.setFont(valueFont);  // Restore to valueFont (what drawDataRow uses)
            iconX += iconWidth;
        }
        
        // Draw output mode icon (only for non-VOLTAGE_MODE modes)
        RowOutputMode mode = table.getOutputMode(row);
        if (mode != RowOutputMode.VOLTAGE_MODE) {
            String modeIcon;
            Color modeColor;
            if (mode == RowOutputMode.FLOW_MODE) {
                modeIcon = "I→";  // Flow mode
                modeColor = new Color(200, 50, 50);  // Red for flow
            } else {
                modeIcon = "P";  // Parameter mode
                modeColor = new Color(120, 80, 180);  // Purple for parameter
            }
            int modeIconSize = table.getOpsize() == 1 ? 9 : 11;
            g.setFont(new Font("SansSerif", Font.BOLD, modeIconSize));
            g.setColor(modeColor);
            g.drawString(modeIcon, iconX, rowY + rowHeight - cellPadding - 1);
            int modeIconWidth = (int) g.context.measureText(modeIcon + " ").getWidth();
            g.setFont(valueFont);  // Restore to valueFont
            iconX += modeIconWidth;
        }
        
        // Draw row classification icon (cyclic rows only)
        String classification = table.getRowClassification(row);
        if ("cyclic".equals(classification)) {
            String classIcon = "⟳";
            Color classColor = new Color(200, 100, 0);  // Orange
            int iconSize = table.getOpsize() == 1 ? 10 : 12;
            g.setFont(new Font("SansSerif", 0, iconSize));  // 0 = plain (no bold)
            g.setColor(classColor);
            g.drawString(classIcon, iconX, rowY + rowHeight - cellPadding - 1);
            int classIconWidth = (int) g.context.measureText(classIcon + " ").getWidth();
            g.setFont(valueFont);  // Restore to valueFont
            iconX += classIconWidth;
        }
        
        // Draw row text
        g.setColor(getTextColor());
        g.drawString(rowText, textX, rowY + rowHeight - cellPadding - 2);
        
        // Draw current value on right side with voltage coloring
        double outputValue = table.getDisplayValue(row);
        String valueText = CircuitElm.getShortUnitText(outputValue, "");
        boolean showFlowUnits = (mode == RowOutputMode.FLOW_MODE);
        if (showFlowUnits) {
            valueText += " F";
        } else {
            valueText += " V";
        }
        int valueWidth = (int) g.context.measureText(valueText).getWidth();
        g.setColor(getVoltageColor(outputValue));
        g.drawString(valueText, tableX + tableWidth - valueWidth - cellPadding, rowY + rowHeight - cellPadding - 2);
        
        // Draw initial value indicator if present
        drawInitialValueIndicator(g, tableX, rowY, row, valueWidth);
        
        // Draw row separator (only if not using cached canvas)
        if (!usingCache && row < table.getRowCount() - 1) {
            g.setColor(getGridLineColor());
            int sepY = tableY + (row + 2) * rowHeight;
            g.drawLine(tableX, sepY, tableX + tableWidth, sepY);
        }
    }

    /**
     * Return true if text contains an assignment '=' that is not part of
     * comparison operators like ==, <=, >=, or !=.
     */
    private boolean containsStandaloneAssignment(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '=') {
                continue;
            }
            char prev = (i > 0) ? text.charAt(i - 1) : '\0';
            char next = (i + 1 < text.length()) ? text.charAt(i + 1) : '\0';
            if (prev == '=' || prev == '<' || prev == '>' || prev == '!' || next == '=') {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Reserve a fixed left gutter for row icons so equation text starts at the same x-position
     * regardless of which icons are present on a particular row.
     */
    private int getRowIconReservedWidth(Graphics g) {
        int adjustableSize = table.getOpsize() == 1 ? 12 : 16;
        int modeIconSize = table.getOpsize() == 1 ? 9 : 11;
        int classIconSize = table.getOpsize() == 1 ? 10 : 12;

        g.setFont(new Font("SansSerif", Font.BOLD, adjustableSize));
        int adjustableWidth = (int) g.context.measureText("↕ ").getWidth();

        g.setFont(new Font("SansSerif", Font.BOLD, modeIconSize));
        int flowWidth = (int) g.context.measureText("I→ ").getWidth();
        int paramWidth = (int) g.context.measureText("P ").getWidth();
        int modeWidth = Math.max(flowWidth, paramWidth);

        g.setFont(new Font("SansSerif", 0, classIconSize));
        int classWidth = (int) g.context.measureText("⟳ ").getWidth();

        g.setFont(valueFont);

        return adjustableWidth + modeWidth + classWidth + 2;
    }
    
    /**
     * Build the display equation string with slider variable substituted.
     */
    private String buildDisplayEquation(int row) {
        int rowCount = table.getRowCount();
        ensureRowTextCacheCapacity(rowCount);

        String equation = table.getEquation(row);
        if (equation == null) {
            equation = "";
        }
        String sliderVar = table.getSliderVarName(row);
        if (sliderVar == null) {
            sliderVar = "";
        }
        double sliderValue = sliderVar.isEmpty() ? Double.NaN : table.getSliderValue(row);

        boolean cacheValidForRow = equation.equals(cachedEquationSourceByRow[row])
            && sliderVar.equals(cachedSliderVarByRow[row])
            && (Double.isNaN(sliderValue) ? Double.isNaN(cachedSliderValueByRow[row]) : sliderValue == cachedSliderValueByRow[row])
            && cachedDisplayEquationByRow[row] != null;

        if (cacheValidForRow) {
            return cachedDisplayEquationByRow[row];
        }

        String displayEquation = Locale.convertGreekSymbols(equation);
        if (!sliderVar.isEmpty()) {
            String valueStr = CircuitElm.getShortUnitText(sliderValue, "");
            displayEquation = displayEquation.replaceAll("\\b" + sliderVar + "\\b", valueStr);
        }

        cachedEquationSourceByRow[row] = equation;
        cachedSliderVarByRow[row] = sliderVar;
        cachedSliderValueByRow[row] = sliderValue;
        cachedDisplayEquationByRow[row] = displayEquation;
        return displayEquation;
    }

    private String getCachedOutputName(int row) {
        int rowCount = table.getRowCount();
        ensureRowTextCacheCapacity(rowCount);

        String rawOutputName = table.getUIDisplayOutputName(row);
        if (rawOutputName == null) {
            rawOutputName = "";
        }

        String cachedOutputName = cachedOutputNameByRow[row];
        if (cachedOutputName != null && rawOutputName.equals(cachedRawOutputNameByRow[row])) {
            return cachedOutputName;
        }

        String converted = Locale.convertGreekSymbols(rawOutputName);
        cachedRawOutputNameByRow[row] = rawOutputName;
        cachedOutputNameByRow[row] = converted;
        return converted;
    }
    
    /**
     * Draw the initial value indicator for a row (shown in yellow brackets).
     */
    private void drawInitialValueIndicator(Graphics g, int tableX, int rowY, int row, int valueWidth) {
        String initEq = table.getInitialEquation(row);
        if (initEq == null || initEq.trim().isEmpty()) {
            return;
        }
        
        int opsize = table.getOpsize();
        Font smallFont = new Font("SansSerif", 0, opsize == 2 ? 8 : 7);
        g.setFont(smallFont);
        String initText = "[" + initEq + "]";
        int initWidth = (int) g.context.measureText(initText).getWidth();
        g.setColor(getTextColor());
        g.drawString(initText, tableX + table.getTableWidth() - valueWidth - initWidth - table.getCellPadding() * 2, 
                     rowY + table.getRowHeight() - table.getCellPadding() - 2);
        g.setFont(valueFont);
    }
}
