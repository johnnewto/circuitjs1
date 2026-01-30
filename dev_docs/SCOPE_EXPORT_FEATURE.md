# Scope Data Export Feature

## Overview

This feature allows you to export scope waveform data from CircuitJS1 in CSV or JSON formats for use with external plotting tools like Plotly.js, Python matplotlib, Excel, or other data analysis software.

## How to Use

### Exporting Data

1. **Right-click on a scope** in your circuit
2. Select **"Export Data..."** from the context menu
3. Choose your preferred format:
   - **CSV**: Simple comma-separated values, easy to import into Excel or Python
   - **JSON**: Structured data with metadata, ideal for web-based plotting tools

4. Choose data source:
   - **Visible Data (Circular Buffer)**: Exports only the data currently visible in the scope window
   - **Full History**: Available only in "Draw From Zero" mode, exports all recorded data from the start

5. Click **Export** and save the file

### Data Formats

#### CSV Format

Simple tabular format with headers:

```csv
Time (s),Voltage Min,Voltage Max,Current Min,Current Max
0.0000,0.0,0.0,0.0,0.0
0.0001,0.95,1.05,0.045,0.055
0.0002,1.85,1.95,0.085,0.095
...
```

#### JSON Format

Structured format with metadata:

```json
{
  "source": "CircuitJS1 Scope",
  "exportType": "circularBuffer",
  "simulationTime": 0.01,
  "timeStep": 0.0001,
  "plots": [
    {
      "name": "Voltage",
      "units": "V",
      "color": "#00FF00",
      "time": [0, 0.001, 0.002, ...],
      "minValues": [0, 0.95, 1.85, ...],
      "maxValues": [0, 1.05, 1.95, ...]
    }
  ]
}
```

## Using Exported Data

### With Plotly.js (Built-in Viewer)

CircuitJS1 has a built-in Plotly viewer. From the **Scopes** menu, select **View All in Plotly**.

Alternatively, you can use the exported JSON with any Plotly.js implementation:

1. Load the JSON data
2. Use Plotly.js `Plotly.newPlot()` with the traces
3. Interact with the plot: zoom, pan, export as PNG, hover for values

### With Python

```python
import json
import matplotlib.pyplot as plt

# Load JSON data
with open('scope-data.json', 'r') as f:
    data = json.load(f)

# Plot each channel
for plot in data['plots']:
    plt.plot(plot['time'], plot['maxValues'], 
             label=plot['name'], color=plot['color'])

plt.xlabel('Time (s)')
plt.ylabel('Value')
plt.title('CircuitJS1 Scope Data')
plt.legend()
plt.grid(True)
plt.show()
```

### With Python (CSV)

```python
import pandas as pd
import matplotlib.pyplot as plt

# Load CSV data
df = pd.read_csv('scope-data.csv')

# Plot
plt.plot(df['Time (s)'], df['Voltage Max'], label='Voltage')
plt.xlabel('Time (s)')
plt.ylabel('Voltage (V)')
plt.title('CircuitJS1 Scope Data')
plt.legend()
plt.grid(True)
plt.show()
```

### With Excel

1. Open Excel
2. Go to **Data > From Text/CSV**
3. Select your CSV file
4. Use Excel's charting features to visualize the data

## Data Structure

### Circular Buffer vs History

- **Circular Buffer**: Fixed-size buffer (typically 128-512 points) that continuously overwrites old data. This is what you see in the scope window during normal operation.

- **History Buffer**: Available in "Draw From Zero" mode, stores all data points from the start of the simulation. Can contain thousands of points and shows the complete timeline.

### Min/Max Values

Each sample point stores both minimum and maximum values captured during that time interval. This preserves signal details that might be missed with single-point sampling, especially for high-frequency signals.

## Implementation Details

### Files Added

- `ExportScopeDataDialog.java`: Dialog for choosing export format and data source

### Files Modified

- `Scope.java`: Added export methods:
  - `exportCircularBufferAsCSV()`
  - `exportCircularBufferAsJSON()`
  - `exportHistoryAsCSV()`
  - `exportHistoryAsJSON()`

- `ScopePopupMenu.java`: Added "Export Data..." menu item
- `CirSim.java`: Added handler for export menu command

## Benefits

1. **No external dependencies**: Pure Java implementation, no JSNI bridge needed
2. **Standard formats**: CSV and JSON are universally supported
3. **Flexible**: Users can choose their preferred analysis tools
4. **Preserves metadata**: JSON format includes units, colors, and timing information
5. **Full data access**: Can export either visible data or complete history

## Future Enhancements

Possible improvements:
- Export to other formats (MATLAB, HDF5)
- Batch export of multiple scopes
- Time range selection
- Data decimation for large datasets
- Direct clipboard copy
- Integration with online plotting services

## Technical Notes

- The export uses the same circular buffer and history data structures that drive the scope display
- Time values are calculated from simulation time and timestep parameters
- Colors are preserved from the scope's plot configuration
- Units (V, A, Ω, W) are included in the export metadata
