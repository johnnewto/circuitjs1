# Scope Refactoring - Quick Reference Guide

## Key Changes Overview

### 1. New Constants (Scope.java)

```java
// Display constants
static final int SETTINGS_WHEEL_SIZE = 36;
static final int SETTINGS_WHEEL_MARGIN = 100;
static final int SHADOW_OFFSET = 4;
static final int SHADOW_BLUR = 8;
static final int MIN_PIXEL_SPACING = 20;

// ScopePlot constants
private static final double DEFAULT_MAN_SCALE = 1.0;
private static final double DEFAULT_AC_ALPHA = 0.9999;
private static final double AC_ALPHA_TIME_CONSTANT = 1.15;
```

### 2. Key Method Changes

#### ScopePlot
- `timeStep()` - Added comprehensive documentation and improved formatting
- `assignColor()` - Simplified logic with better comments
- Constructors - Added JavaDoc with parameter descriptions

#### Scope
- `initialize()` - Added method documentation
- `resetGraph()` - Overloaded with clear documentation
- `calcVisiblePlots()` - Improved variable names (voltCount, currentCount, otherCount)
- `captureToHistory()` - Extracted helper method `areHistoryBuffersAllocated()`
- `calc2dGridPx()` - Improved readability with descriptive variable names
- `drawSettingsWheel()` - Used local constants for wheel dimensions

#### ScopeElm
- `draw()` - Extracted to `drawNormal()` and `drawForExport()`
- `drawShadowBox()` - New helper method for shadow rendering
- `setShadowColor()` - New helper method for color management
- `setBackgroundColor()` - New helper method for background

### 3. Variable Organization

Variables now organized into logical sections:
- Data (scopePointCount, fft, position, etc.)
- Display Settings (manualScale, showI, showV, etc.)
- Working Data (plots, visiblePlots, imageCanvas, etc.)
- Static Variables (cursorTime, cursorUnits, cursorScope)

### 4. Documentation Standards

All public methods now have JavaDoc:
```java
/**
 * Brief description of what the method does.
 * @param paramName Description of parameter
 * @return Description of return value
 */
```

### 5. Code Style Improvements

**Before:**
```java
if (v < minValues[ptr])
	minValues[ptr] = v;
if (v > maxValues[ptr])
	maxValues[ptr] = v;
```

**After:**
```java
// Update min/max for current sample point
if (v < minValues[ptr])
    minValues[ptr] = v;
if (v > maxValues[ptr])
    maxValues[ptr] = v;
```

### 6. Performance Tips

- Batch drawing still uses `g.startBatch()` / `g.endBatch()`
- Circular buffer logic unchanged for performance
- No additional object allocations in hot paths

### 7. Testing Checklist

- [ ] Test normal scope display
- [ ] Test 2D plotting
- [ ] Test FFT display
- [ ] Test draw-from-zero mode
- [ ] Test AC coupling
- [ ] Test manual scaling
- [ ] Test export functionality
- [ ] Test multiple scopes
- [ ] Test print mode rendering

### 8. Common Patterns

#### Adding a New Display Mode
1. Add constant in appropriate section
2. Add boolean field in Display Settings section
3. Create helper method with JavaDoc
4. Update `getFlags()` and `setFlags()` if persistence needed

#### Adding a New Helper Method
1. Make it private if only used internally
2. Add JavaDoc with `@param` and `@return`
3. Use descriptive method name (verb + noun)
4. Keep it focused on single responsibility

#### Modifying Draw Logic
1. Check if change affects `drawNormal()`, `drawForExport()`, or both
2. Test both normal and export rendering
3. Consider adding constants for magic numbers
4. Update documentation if behavior changes

## Backward Compatibility

✅ All serialization/deserialization unchanged
✅ All public API methods unchanged
✅ All rendering behavior unchanged
✅ File format compatibility maintained

## Migration Notes

No migration needed! This is a pure refactoring that maintains 100% compatibility.

## Build & Test

```bash
# Build (from project root)
./gradlew compileGwt

# Test manually
# Open circuitjs.html in browser
# Test various scope configurations
```

## Questions?

Refer to:
- `SCOPE_REFACTORING_SUMMARY.md` for detailed changes
- JavaDoc in source files for method documentation
- Original git commit for line-by-line changes
