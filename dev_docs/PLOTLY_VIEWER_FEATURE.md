# Interactive Plotly.js Scope Viewer

## Overview

This feature provides an **instant interactive visualization** of scope data using Plotly.js in a new browser window. Unlike the basic export feature, this opens a live, interactive graph viewer where you can zoom, pan, and explore all your scope data without leaving CircuitJS1.

## Features

✅ **One-Click Viewing** - Open all scopes or individual scopes instantly  
✅ **Interactive Graphs** - Zoom, pan, hover for values, export as PNG  
✅ **Range Slider** - Navigate through entire time series with ease  
✅ **No File Downloads** - Data embedded directly in the viewer  
✅ **Multiple Scopes** - View all scopes at once, each in its own panel  
✅ **Download Options** - Export all data as JSON or CSV from the viewer  
✅ **Automatic History Detection** - Uses full history when available (Draw From Zero mode)  

## How to Use

### View All Scopes

**Method 1: From Menu**
1. Click **Scopes** menu in the top menu bar
2. Select **"View All Scopes in Plotly..."**
3. Click **"Open Viewer"** in the dialog
4. A new browser window/tab opens with interactive graphs

**Method 2: Keyboard Shortcut** (if available)
- Press the assigned keyboard shortcut

### View Single Scope

1. **Right-click** on any scope in your circuit
2. Select **"View in Plotly..."** from the context menu
3. Click **"Open Viewer"** in the dialog
4. A new browser window opens showing just that scope

## Interactive Features

### In the Plotly Viewer

**Range Slider:**
- Use the slider below each graph to navigate the full time series
- Drag the handles to select a specific time range
- Great for long simulations with lots of data
- Click "Toggle Range Sliders" to show/hide all sliders at once

**Zooming:**
- Click and drag to zoom into a region
- Double-click to reset zoom

**Panning:**
- Shift + drag to pan across the graph
- Use scrollbar if available

**Hover:**
- Hover over traces to see exact values
- Tooltip shows time and value

**Legend:**
- Click legend items to show/hide individual traces
- Double-click to isolate one trace

**Export Images:**
- Use camera icon to export current view as PNG
- Configurable resolution and filename

**Download Data:**
- Click **"Download All Data (JSON)"** for structured format
- Click **"Download All Data (CSV)"** for spreadsheet format

## Viewer Layout

Each scope appears in its own panel with:
- **Scope Name/Title** at the top
- **Metadata** showing export type, sample count, timing info
- **Interactive Plot** with all traces
- **Legend** on the right side
- **Plotly Controls** in the top-right corner

### Color Preservation

- Plot colors match those in CircuitJS1 scopes
- Min/max traces shown (if different)
- Dotted lines for min values
- Solid lines for max values

## Data Included

### For Each Scope:
- Scope name (from title or custom label)
- All visible plots
- Time arrays (calculated from simulation time)
- Min and max value arrays
- Units (V, A, Ω, W)
- Plot colors

### Automatic Mode Selection:
- **Draw From Zero Mode ON + History Available**: Exports full history from t=0
- **Otherwise**: Exports circular buffer (visible data)

## Technical Details

### Generated HTML Structure

The viewer generates a complete, self-contained HTML page with:
- Embedded Plotly.js library (loaded from CDN)
- All scope data embedded as JSON
- Styling for responsive layout
- Download buttons for data export
- JavaScript plotting code

### Browser Compatibility

Works in all modern browsers:
- Chrome/Edge (recommended)
- Firefox
- Safari
- Opera

**Requirements:**
- JavaScript enabled
- Pop-ups allowed (for new window)
- Internet connection (for Plotly.js CDN)

### Performance

- Handles thousands of data points efficiently
- Smooth zooming and panning
- Responsive interface
- Lightweight (data only, not full CircuitJS1)

## Comparison with Other Methods

| Feature | Interactive Viewer | Export Dialog |
|---------|-------------------|---------------|
| **Speed** | Instant | Manual file saving |
| **Data Source** | All/Single scope | Single scope |
| **Interaction** | Built-in | None |
| **Downloads** | Optional | Primary purpose |
| **Setup** | None | None |
| **Offline** | CDN required | Yes |

## Use Cases

### Quick Analysis
- Check waveform shapes quickly
- Verify signal timing
- Compare multiple signals

### Detailed Investigation
- Zoom into specific regions
- Measure exact values
- Identify anomalies

### Presentation
- Export high-quality images
- Share interactive viewers
- Document results

### Data Collection
- Download all scope data at once
- CSV format for spreadsheets
- JSON format for processing

## Troubleshooting

### Pop-up Blocked
**Problem:** New window doesn't open  
**Solution:** Allow pop-ups for CircuitJS1 in your browser settings

### Plotly Not Loading
**Problem:** Graphs don't appear  
**Solution:** Check internet connection (Plotly.js loads from CDN)

### No Data Shown
**Problem:** Viewer opens but shows no graphs  
**Solution:** 
- Ensure scopes have visible plots
- Check that simulation has run
- Verify scope isn't empty

### Download Buttons Not Working
**Problem:** Can't download JSON/CSV  
**Solution:** Check browser download settings and permissions

## Implementation Files

### New Files Created
- `ScopeViewerDialog.java` - Dialog and HTML generation

### Modified Files
- `CirSim.java` - Menu items and command handlers
- `ScopePopupMenu.java` - Context menu items
- `Scope.java` - Export methods (from previous feature)

### Related Files
- `ExportScopeDataDialog.java` - Manual export dialog

## Code Architecture

### Dialog Flow
1. User selects menu item
2. `ScopeViewerDialog` constructor called
3. User clicks "Open Viewer"
4. `openViewer()` collects all scope data
5. `exportScope()` converts each scope to JSON
6. `generatePlotlyHTML()` creates complete HTML
7. `openWindowWithHTML()` opens new browser window (JSNI)

### Data Flow
```
CircuitJS1 Scopes
    ↓
Scope.exportCircularBufferAsJSON() / exportHistoryAsJSON()
    ↓
ScopeViewerDialog.exportScope() (adds metadata)
    ↓
JSON array of all scopes
    ↓
generatePlotlyHTML() (embeds in HTML template)
    ↓
New browser window with Plotly.js
```

### Key Methods

**ScopeViewerDialog:**
- `openViewer()` - Orchestrates data collection
- `exportScope()` - Exports individual scope
- `generatePlotlyHTML()` - Creates HTML document
- `openWindowWithHTML()` - Native JavaScript window.open()

**Integration Points:**
- `CirSim.menuPerformed()` - Menu command handler
- Main menu "Scopes" → "View All Scopes in Plotly..."
- Scope popup menu → "View in Plotly..."

## Future Enhancements

Possible improvements:
- **Real-time updates** - Live streaming to open viewer
- **Comparison mode** - Overlay multiple simulation runs
- **Annotation tools** - Add markers and notes to graphs
- **Custom layouts** - Arrange multiple scopes in grid
- **Offline mode** - Bundle Plotly.js locally
- **Dark theme** - Match CircuitJS1 dark mode
- **Filtering options** - Select which plots to show
- **Time range selection** - Export specific time windows

## Benefits Over Manual Export

1. **Instant gratification** - No file saving/loading
2. **Context preserved** - All scopes together
3. **Professional output** - Plotly.js quality graphs
4. **No external tools** - Everything in browser
5. **Flexible downloads** - Get data if needed later

## Example Workflow

```
User simulates RC circuit with 2 scopes:
  - Scope 1: Input voltage
  - Scope 2: Capacitor voltage & current

1. Click Scopes → View All Scopes in Plotly...
2. New window opens showing both scopes
3. Zoom into charging phase
4. Hover to read exact values
5. Export high-res PNG for documentation
6. Download CSV for further analysis
```

All without leaving CircuitJS1 or manually managing files!

## Advantages

### For Students
- Quick verification of expected behavior
- Easy zoom to see details
- Professional-looking results

### For Engineers
- Fast iteration during design
- Export-ready visualizations
- Data extraction when needed

### For Educators
- Demonstrate waveforms interactively
- Create clear documentation
- Share interactive viewers

### For Researchers
- Rapid analysis of results
- Publication-quality graphics
- Data preservation options
