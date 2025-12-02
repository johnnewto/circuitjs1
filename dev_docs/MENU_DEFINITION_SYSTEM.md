# Menu Definition System - Implementation Summary

## Overview

The CircuitJS1 menu system can now be configured using a text file (`menulist.txt`), similar to how circuit examples are configured using `setuplist.txt`. This makes it much easier to customize, reorganize, or extend the component menu without modifying Java code.

## Architecture

### File Location
- **Menu Definition**: `/src/com/lushprojects/circuitjs1/public/menulist.txt`
- **Loading**: Asynchronously loaded via HTTP request during initialization
- **Fallback**: If the file fails to load, the system falls back to the original hardcoded menu

### Key Components

1. **Menu Definition Storage**:
   - `menuDefinition` (String): Stores the loaded menu file content
   - `menuDefinitionLoaded` (boolean): Tracks whether the file loaded successfully

2. **Loading Method**:
   - `loadMenuDefinition()`: Asynchronously loads `menulist.txt` during initialization
   - Called at the end of `init()`

3. **Menu Composition**:
   - `composeMainMenu(MenuBar mainMenuBar, int num)`: Main entry point (unchanged API)
   - `composeMainMenuFromFile(MenuBar mainMenuBar, int num)`: Parses menu from text file
   - `composeMainMenuHardcoded(MenuBar mainMenuBar, int num)`: Original hardcoded menu (fallback)

## Menu Definition File Format

### Syntax

```
# Comment lines start with #
# Empty lines are ignored

# Top-level menu items
ClassName|Display Name|Shortcut (optional)

# Submenus
+Submenu Title
ClassName|Display Name|Shortcut
-

# Special handling for Subcircuits menu (populated dynamically)
+Subcircuits
-
```

### Example

```
# Top-level items
WireElm|Add Wire
ResistorElm|Add Resistor

# Submenu
+Passive Components
CapacitorElm|Add Capacitor
InductorElm|Add Inductor
-

# With shortcuts
+Drag
DragAll|Drag All|(Alt-drag)
DragPost|Drag Post|(Ctrl-drag)
-
```

## Features

### 1. **Dynamic Menu Loading**
- Menu structure loaded from text file at runtime
- No need to recompile for menu changes

### 2. **Hierarchical Structure**
- Support for nested submenus using `+` and `-` markers
- Unlimited nesting depth (stack size: 10 levels)

### 3. **Shortcut Support**
- Optional shortcuts specified after display name
- Platform-specific shortcuts handled automatically:
  - `(A-M-drag)` becomes `(A-Cmd-drag)` on Mac
  - `(Ctrl-drag)` uses `ctrlMetaKey` variable

### 4. **Special Handling**
- **Subcircuits menu**: Automatically detected and populated dynamically
- **Localization**: All display names wrapped with `Locale.LS()`

### 5. **Graceful Fallback**
- If file loading fails, original hardcoded menu is used
- Console warning logged for debugging

## Benefits

### For Developers
- **Easy Customization**: Modify menu structure without touching Java code
- **Rapid Prototyping**: Test new menu organizations quickly
- **Version Control**: Menu structure tracked separately from code
- **Reduced Complexity**: Menu definition separated from implementation

### For Users
- **Customizable**: Users can create custom menu layouts
- **Theme Support**: Different menu organizations for different contexts
- **Localization**: Easier to create language-specific menu files

## Implementation Details

### Menu Stack Processing

The parser maintains a stack of `MenuBar` objects to handle nested submenus:

```java
MenuBar[] menuStack = new MenuBar[10];
int stackPtr = 0;
menuStack[stackPtr++] = mainMenuBar;
```

When encountering `+`, a new submenu is created and pushed onto the stack.
When encountering `-`, the stack is popped to return to the parent menu.

### Line Parsing

```java
String[] parts = line.split("\\|");
String className = parts[0].trim();      // Element class name
String displayName = parts[1].trim();    // User-visible text
String shortcut = parts[2].trim();       // Optional shortcut
```

### Integration Points

1. **Menu Creation**: `composeMainMenu()` called twice:
   - Once for the Draw menu
   - Once for the right-click context menu

2. **Subcircuit Handling**: 
   - Array index `num` (0 or 1) identifies which menu instance
   - `subcircuitMenuBar[num]` populated when "Subcircuits" submenu detected

## Testing

### Manual Testing Steps

1. **Normal Operation**:
   - Build and run application
   - Verify menu loads correctly
   - Check all submenus open properly
   - Test component creation from menu

2. **File Not Found**:
   - Delete or rename `menulist.txt`
   - Verify fallback to hardcoded menu
   - Check console for warning message

3. **Custom Menu**:
   - Modify `menulist.txt` (add/remove items)
   - Rebuild application
   - Verify changes appear in menu

### Build Commands

```bash
# Compile GWT (includes copying public resources)
gradle compileGwt

# Development server (for testing)
gradle gwtSuperDev
```

## Future Enhancements

### Potential Features
1. **Multiple menu files**: Support for different profiles/themes
2. **Runtime reloading**: Hot-reload menu without page refresh
3. **User preferences**: Remember custom menu layouts
4. **Menu editor**: Visual editor for creating menu files
5. **Validation**: Syntax checking and error reporting

### Configuration Options
```
# Possible future extensions
@icon: wire.png        # Custom icons
@group: BasicComponents # Logical grouping
@visible: true         # Conditional visibility
@order: 10             # Explicit ordering
```

## Comparison with setuplist.txt

| Feature | setuplist.txt | menulist.txt |
|---------|--------------|--------------|
| Purpose | Circuit examples | Component menu |
| Format | `+`/`-` for sections | `+`/`-` for submenus |
| Item syntax | `filename title` | `ClassName\|Display\|Shortcut` |
| Localization | Titles localized | All text localized |
| Dynamic content | Circuit loading | Menu creation |

## Files Modified

1. **CirSim.java**:
   - Added `menuDefinition` and `menuDefinitionLoaded` fields
   - Added `loadMenuDefinition()` method
   - Added `composeMainMenuFromFile()` method
   - Renamed original method to `composeMainMenuHardcoded()`
   - Modified `composeMainMenu()` to dispatch to appropriate method

2. **menulist.txt** (new file):
   - Complete menu structure definition
   - 200+ lines defining all components and submenus

## Backward Compatibility

✅ **Fully backward compatible**: The system automatically falls back to the original hardcoded menu if the text file is unavailable.

## Notes

- Menu file is loaded asynchronously, but menu composition happens synchronously
- File must be present in the compiled WAR for production deployment
- Changes to menu file require rebuild (GWT copies static resources during compilation)
- Console debugging available via `console()` native method

## Example Usage

### Adding a New Component

1. Open `menulist.txt`
2. Add line: `NewElm|Add New Component`
3. Rebuild application
4. New item appears in menu

### Reorganizing Menu

1. Cut/paste lines in `menulist.txt`
2. Adjust submenu markers (`+`/`-`)
3. Rebuild application
4. Menu reflects new structure

### Creating Custom Layout

1. Copy `menulist.txt` to `menulist-custom.txt`
2. Modify copy as desired
3. Update `loadMenuDefinition()` to load custom file
4. Rebuild and test
