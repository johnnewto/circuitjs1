# Reverse Lookup Cache Implementation for getNameByNode()

## Changes Made

### 1. Added Reverse Lookup Cache
- Added `nodeToLabelCache` HashMap to cache node number → label name mappings
- Added `ensureNodeToLabelCache()` method to build cache lazily when needed
- Updated `invalidateCache()` to also clear the reverse lookup cache

### 2. Updated getNameByNode() Method
- **Before**: O(n) linear search through all labeled nodes for each call
- **After**: O(1) HashMap lookup after initial O(n) cache building

### 3. Added Cache Invalidation Points
- `invalidateCache()`: Clears both sorted names and reverse lookup caches
- `setNode()`: Invalidates reverse cache when node assignments change
- `resetNodeList()`: Clears all caches when circuit resets
- `getConnectedPost()`: Invalidates when new labeled nodes added
- `setComputedValue()`: Invalidates when new entries added

## Performance Benefits

### Before Optimization
```java
static String getNameByNode(int nodeNumber) {
    if (labelList == null) return null;
    for (String labelName : labelList.keySet()) {        // O(n) iteration
        LabelEntry entry = labelList.get(labelName);     // O(1) HashMap lookup
        if (entry != null && entry.node == nodeNumber) {
            return labelName;                            // Found after avg n/2 checks
        }
    }
    return null;                                         // Not found after n checks
}
```
**Time Complexity**: O(n) per call where n = number of labeled nodes

### After Optimization  
```java
static String getNameByNode(int nodeNumber) {
    if (labelList == null) return null;
    
    ensureNodeToLabelCache();                           // O(n) once, then O(1)
    if (nodeToLabelCache != null) {
        return nodeToLabelCache.get(nodeNumber);        // O(1) HashMap lookup
    }
    // ... fallback code for safety
}
```
**Time Complexity**: O(n) first call to build cache, then O(1) for all subsequent calls

## Usage Scenarios Where This Helps

1. **Circuit Info Display**: `getInfo()` methods showing node assignments
2. **Debug Output**: Displaying node-to-label mappings 
3. **Scope Labels**: Converting node numbers back to readable names
4. **Circuit Analysis**: Tools that need to map between node numbers and labels

## Cache Behavior

### Cache Building
- **Lazy Loading**: Cache built only when `getNameByNode()` is first called
- **Filtered Entries**: Only caches actual circuit nodes (entry.node >= 0)
- **Excludes Computed Values**: Computed-only entries (node = -1) not cached

### Cache Invalidation
- **Node Assignment Changes**: When `setNode()` updates node assignments
- **New Labeled Nodes**: When new LabeledNodeElm elements are added
- **Circuit Reset**: When `resetNodeList()` is called
- **Computed Values**: When `setComputedValue()` adds new entries

### Memory Usage
- **Minimal Overhead**: One additional HashMap with Integer keys
- **Automatic Cleanup**: Cache cleared and rebuilt as needed
- **Shared Benefit**: All callers of `getNameByNode()` benefit from same cache

## Fallback Safety
- **Graceful Degradation**: Falls back to linear search if cache building fails
- **Null Handling**: Proper null checks throughout
- **Error Resilience**: Cache errors don't break functionality

This optimization particularly benefits circuits with many labeled nodes, where `getNameByNode()` might be called frequently during debugging, info display, or analysis operations.