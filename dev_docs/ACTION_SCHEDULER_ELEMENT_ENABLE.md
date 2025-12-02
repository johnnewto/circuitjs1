# ActionTimeElm Element Enable/Disable Feature

## Overview
Added an "Enabled" checkbox to the ActionTimeElm element that controls whether the entire action scheduler is active. When disabled, all scheduled actions are prevented from executing, and the element's visual rendering is grayed out.

## Implementation Details

### 1. ActionTimeElm.java - Element Enable State

#### Added Fields
```java
boolean enabled;  // Default: true
```

#### Constructor Updates
```java
public ActionTimeElm(int xx, int yy) {
    super(xx, yy);
    enabled = true; // Enabled by default
}

public ActionTimeElm(int xa, int ya, int xb, int yb, int f, StringTokenizer st) {
    super(xa, ya, xb, yb, f);
    enabled = true; // Default to enabled for backward compatibility
    // ... consume old format tokens ...
    // Try to read enabled flag for this element
    try {
        enabled = Boolean.parseBoolean(st.nextToken());
    } catch (Exception e) {
        // No enabled flag in saved file, use default
    }
}
```

#### Persistence
```java
String dump() { 
    return super.dump() + " " + enabled; 
}
```

#### Visual Rendering
When disabled, the element shows:
- **Global alpha transparency**: 0.5 opacity applied to entire element
- **Gray header**: Header background changes from cornflower blue to gray
- **Status text**: Header shows "(DISABLED)" suffix
- **Pattern**: Follows StopTimeElm's disabled rendering pattern

```java
void draw(Graphics g) {
    g.save();
    // ...
    
    // Apply gray filter if disabled
    if (!enabled) {
        g.context.setGlobalAlpha(0.5);
    }
    
    // Draw header with state indication
    Color headerColor = enabled ? new Color(100, 149, 237) : Color.gray;
    g.setColor(headerColor);
    g.fillRect(cx - width/2, cy - height/2, width, headerHeight);
    
    String headerText = enabled ? "Action Schedule" : "Action Schedule (DISABLED)";
    // ...
}
```

#### Edit Dialog
```java
public EditInfo getEditInfo(int n) {
    if (n == 0) {
        EditInfo ei = new EditInfo("", 0, -1, -1);
        ei.checkbox = new Checkbox("Enabled", enabled);
        return ei;
    }
    if (n == 1) {
        EditInfo ei = new EditInfo("", 0, -1, -1);
        ei.text = "This element displays scheduled actions";
        ei.text += "\n\nDouble-click to open Action Time Dialog";
        ei.text += "\nfor full action management.";
        ei.text += "\n\nWhen disabled, the action scheduler is inactive";
        ei.text += "\nand no actions will execute.";
        return ei;
    }
    return null;
}

public void setEditValue(int n, EditInfo ei) {
    if (n == 0) {
        enabled = ei.checkbox.getState();
    }
}
```

#### Info Display
```java
void getInfo(String arr[]) {
    arr[0] = "Action Schedule Display";
    arr[1] = "element enabled = " + (enabled ? "yes" : "no");
    arr[2] = "current time = " + getUnitText(sim.t, "s");
    // ... more info ...
}
```

### 2. ActionScheduler.java - Execution Control

#### step() Method Enhancement
The scheduler now checks if ANY ActionTimeElm in the circuit is enabled before executing actions:

```java
public void step(double currentTime) {
    // Check if any ActionTimeElm exists and if all are enabled
    boolean anyElementEnabled = false;
    for (int i = 0; i != sim.elmList.size(); i++) {
        CircuitElm ce = sim.getElm(i);
        if (ce instanceof ActionTimeElm) {
            ActionTimeElm ate = (ActionTimeElm) ce;
            if (ate.enabled) {
                anyElementEnabled = true;
                break;
            }
        }
    }
    
    // If no ActionTimeElm is enabled, skip action execution
    if (!anyElementEnabled) {
        return;
    }
    
    // ... execute actions as normal ...
}
```

**Behavior**:
- If **no ActionTimeElm exists** in circuit: Actions will **NOT** execute
- If **at least one ActionTimeElm is enabled**: Actions **WILL** execute
- If **all ActionTimeElm elements are disabled**: Actions will **NOT** execute

This allows multiple ActionTimeElm display elements with at least one needing to be enabled for the scheduler to work.

## User Workflow

### Enabling/Disabling the Action Scheduler

#### Via Right-Click Edit
1. Right-click on the ActionTimeElm element
2. Select "Edit" from context menu
3. Check/uncheck the "Enabled" checkbox
4. Click "OK"

#### Via Element Properties
1. Click on ActionTimeElm to select it
2. Open properties panel (if not already open)
3. Toggle the "Enabled" checkbox in properties

### Visual Feedback

#### When Enabled
- **Header**: Blue background with "Action Schedule" text
- **Opacity**: 100% (fully visible)
- **Actions**: Execute at scheduled times
- **Console**: Action execution messages appear

#### When Disabled
- **Header**: Gray background with "Action Schedule (DISABLED)" text
- **Opacity**: 50% (semi-transparent, grayed out)
- **Actions**: No actions execute (scheduler is inactive)
- **Console**: No action execution messages

### File Format

#### Old Format (backward compatible)
```
o 432 x1 y1 x2 y2 flags
```

#### New Format
```
o 432 x1 y1 x2 y2 flags enabled
```

Example:
```
o 432 100 200 200 200 0 true
```

When loading old circuits without the enabled flag, the element defaults to `enabled = true`.

## Design Pattern: Following StopTimeElm

This implementation follows the established pattern from `StopTimeElm.java`:

### Similarities
1. **Boolean enabled field**: Default `true`
2. **Checkbox in EditInfo**: Uses `ei.checkbox = new Checkbox("Enabled", enabled);`
3. **Gray rendering when disabled**: Uses `g.context.setGlobalAlpha(0.5)`
4. **Header color change**: Blue → Gray when disabled
5. **Status text**: Appends "(DISABLED)" to header
6. **Backward compatibility**: Defaults to enabled if flag missing
7. **Persistence**: Saves enabled state in dump()

### Key Difference
- **StopTimeElm**: Controls only itself (one element stops sim at specific time)
- **ActionTimeElm**: Controls global scheduler (affects all scheduled actions)

## Use Cases

### Multiple Display Elements
A circuit can have multiple ActionTimeElm elements for different viewing locations:
- Place one near sliders being controlled
- Place one in overview area
- At least ONE must be enabled for actions to execute

### Temporary Disabling
Disable the scheduler to:
- Test circuit behavior without automated actions
- Debug circuit issues
- Compare manual vs. automated control
- Prevent actions during development/testing

### Circuit Modes
Create circuits with different operating modes:
- **Normal mode**: ActionTimeElm enabled, automated sequence runs
- **Manual mode**: ActionTimeElm disabled, user controls everything
- **Debug mode**: ActionTimeElm disabled, observe raw circuit behavior

## Technical Notes

### Global Scheduler Control
The enabled state is element-specific but affects the global ActionScheduler:
- Actions are stored centrally in ActionScheduler singleton
- ActionScheduler.step() checks for enabled elements before executing
- This allows the display element to control execution behavior

### Performance
Minimal performance impact:
- Element check happens once per simulation step
- Iteration stops on first enabled element found (short-circuit)
- No action execution overhead when disabled

### Integration
- **ActionScheduler**: Modified step() method checks element states
- **ActionTimeElm**: Provides enable/disable UI and visual feedback
- **CirSim**: No changes needed (calls scheduler.step() as before)

## Testing Checklist
- [x] Compilation successful
- [ ] Create ActionTimeElm and verify "Enabled" checkbox appears in edit dialog
- [ ] Disable element and verify gray rendering with "(DISABLED)" text
- [ ] Verify actions don't execute when element is disabled
- [ ] Enable element and verify actions execute normally
- [ ] Test with multiple ActionTimeElm elements (mixed enabled/disabled)
- [ ] Save circuit with disabled element and verify persistence
- [ ] Load old circuit without enabled flag (should default to enabled)
- [ ] Verify info display shows "element enabled = yes/no"
- [ ] Test drag-and-drop with disabled elements
- [ ] Verify ActionTimeDialog still opens when element is disabled

## Future Enhancements (Not Implemented)
- Per-element action filtering (each element shows/controls subset of actions)
- Element-specific enable/disable of individual actions
- Visual indication in ActionTimeDialog when element is disabled
- Automatic element creation when first action is added
- Multiple scheduler instances (one per element)
