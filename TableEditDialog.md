# Dynamic Table Implementation in GWT Using Grid, FlexTable, and CellTable

## Overview

This document provides instructions to apply to TableEditDialog for creating or editing a table  that supports adding or removing individual rows and columns. Using widgets from the [GWT Widget Gallery](https://www.gwtproject.org/doc/latest/RefWidgetGallery.html):  **Grid**


If the Table is new  then creating a new table with rows and columns shown below and 

## TableEditDialog 
This button will open a new dialog box that will allow the user to add or remove rows and columns from the table based on the below structure. If the table is new then it will create a table with the structure shown below.

Don't worry for now about If the table already exists . Later we will show the existing table with the ability to add or remove rows and columns. All the adding and removing of cells will be done in this dialog.

## Table when creating from new 

| Buttons  |                    | Assets | Liabilities | Equity | A-L-E |
|----------|--------------------|--------|-------------|--------|-------|
|          |                    | ⧾ ⧿ →  |⧾ ⧿ ← →      |⧾ ⧿ ←   |       |
|          |Flows↓/Stock Vars → |        |             |        |       |
|----------|--------------------|--------|-------------|--------|-------|
|          |InitialConditions   |        |             |        |       |
| ⧾        |                    |        |             |        |       |


⧾ ⧿ ← → ↑ ↓ indicate buttons for adding, deleting or moving rows or columns. the cells are created with empty text boxes for user to enter text.




## Table edit view when extra rows or cells are created. 

| Buttons  |                    | Assets | Assets | Liabilities | Equity | A-L-E |
|----------|--------------------|--------|--------|-------------|--------|-------|
|          |                    | ⧾ ⧿ →  | ⧾ ⧿ →  |   ⧾ ⧿ ←     |        |       |
|          |Flows↓/Stock Vars → |        |        |             |        |       |
|----------|--------------------|--------|--------|-------------|--------|-------|
|          |InitialConditions   |        |        |             |        |       |
| ⧾        |                    |        |        |             |        |       |
| ⧾ ⧿ ↓    |                    |        |        |             |        |       |
| ⧾ ⧿ ↑ ↓  |                    |        |        |             |        |       |
| ⧾ ⧿ ↑    |                    |        |        |             |        |       |

⧾ ⧿ ← → ↑ ↓ indicate buttons for adding, deleting or moving rows or columns. The cells are created with empty text boxes for user to enter text.

## Column Properties and Financial Accounting Structure

### Column Type System

The table implements a strict financial accounting structure where each column is classified into one of three fundamental accounting types:

1. **Asset** - Represents resources owned by the entity
2. **Liability** - Represents obligations owed by the entity  
3. **Equity** - Represents owner's stake in the entity (Assets - Liabilities)

### Column Positioning Rules

The table maintains a strict left-to-right ordering of column types to preserve accounting structure:

- **Assets**: Always positioned on the **leftmost** side of the table
- **Liabilities**: Positioned in the **center** between Assets and Equity
- **Equity**: Always positioned on the **rightmost** side (before the computed A-L-E column)
- **Computed (A-L-E)**: Always the final column

**Visual Representation:**
```
[Asset1] [Asset2] ... [AssetN] | [Liab1] [Liab2] ... [LiabN] | [Equity] [A-L-E]
<------ Left Most ------------> <------- Center ------------>   <--- Right Most -->
```

This ordering ensures the accounting equation (Assets = Liabilities + Equity) is visually represented in the table structure.

### Initial Table Configuration

When a new table is created, it contains exactly **one column of each type** in the following order:

| Column Position | Type       | Default Name | Can Add More? | Can Delete? | Can Move? |
|----------------|------------|--------------|---------------|-------------|-----------|
| 1              | Asset      | "Asset"      | Yes (→)       | Yes*        | Yes (→)   |
| 2              | Liability  | "Liability"  | Yes (→)       | Yes*        | Yes (←)   |
| 3              | Equity     | "Equity"     | **No**        | **No**      | **No**    |
| 4              | Computed   | "A-L-E"      | N/A           | No          | No        |

*Can delete if there are multiple columns of that type.

### Equity Column Rules (Special Constraints)

The **Equity** column has unique restrictions to maintain accounting integrity:

- **Exactly One**: There can only be one Equity column in the table
- **Immutable Position**: Cannot be moved left or right
- **Cannot Be Added**: No add button (⧾) appears for creating additional Equity columns
- **Cannot Be Deleted**: No delete button (⧿) appears on the Equity column
- **Protected Structure**: Ensures the accounting equation (Assets = Liabilities + Equity) remains valid

### Dynamic Type Assignment Through Movement

The column type system is **position-dependent** and changes dynamically based on column movements:

#### Moving Assets to the Right (Asset → Liability Conversion)

When you move the **rightmost Asset column** to the right:
- The column **crosses the Asset/Liability boundary**
- Its type automatically changes from **Asset** to **Liability**
- The column header retains its name but now represents a liability account
- **Automatic Column Creation**: If moving this column leaves **zero Asset columns**, a new Asset column is automatically created to maintain the minimum requirement of at least one Asset
- Example: Moving "Cash" (Asset) → "Cash" (Liability) when it moves past the last Asset position

**Visual Representation:**
```
Before:  [Asset1] [Asset2] | [Liab1] [Equity]
Action:  Move Asset2 →
After:   [Asset1] | [Asset2] [Liab1] [Equity]
         Asset2 is now a Liability!

Special Case - Last Asset:
Before:  [Asset1] | [Liab1] [Equity]
Action:  Move Asset1 →
After:   [NewAsset] | [Asset1] [Liab1] [Equity]
         Asset1 became a Liability, NewAsset auto-created!
```

#### Moving Liabilities to the Left (Liability → Asset Conversion)

When you move the **leftmost Liability column** to the left:
- The column **crosses the Liability/Asset boundary**
- Its type automatically changes from **Liability** to **Asset**
- The column header retains its name but now represents an asset account
- **Automatic Column Creation**: If moving this column leaves **zero Liability columns**, a new Liability column is automatically created to maintain the minimum requirement of at least one Liability
- Example: Moving "Accounts Payable" (Liability) → "Accounts Payable" (Asset) when it moves before the first Liability position

**Visual Representation:**
```
Before:  [Asset1] | [Liab1] [Liab2] [Equity]
Action:  Move Liab1 ←
After:   [Asset1] [Liab1] | [Liab2] [Equity]
         Liab1 is now an Asset!

Special Case - Last Liability:
Before:  [Asset1] | [Liab1] [Equity]
Action:  Move Liab1 ←
After:   [Asset1] [Liab1] | [NewLiab] [Equity]
         Liab1 became an Asset, NewLiab auto-created!
```

### Boundary Rules and Constraints

#### The Asset-Liability Boundary
- A virtual boundary exists between the rightmost Asset column and the leftmost Liability column
- Columns can **cross this boundary** through movement operations
- **Crossing the boundary triggers automatic type conversion**
- The Equity column acts as an **immovable anchor** on the rightmost side preventing boundary confusion
- **Automatic Balance Maintenance**: When a column crosses the boundary and would leave a type with zero columns, a new column of that type is automatically created to maintain the minimum requirement

#### Positional Invariants
1. **Assets always occupy the leftmost positions** in the table
2. **Liabilities always occupy the center positions** between Assets and Equity
3. **Equity always occupies the rightmost position** (before the A-L-E computed column)
4. **At least one Asset column must exist** - enforced by auto-creation if needed
5. **At least one Liability column must exist** - enforced by auto-creation if needed
6. **Exactly one Equity column must exist** - cannot be added, deleted, or moved

#### Movement Restrictions
1. **Assets can only move within Asset region or cross to Liability region** (moving right)
2. **Liabilities can only move within Liability region or cross to Asset region** (moving left)
3. **Equity cannot move** and serves as a fixed reference point on the right
4. **No column can move past or swap with Equity**
5. **Column order within a type region can be freely rearranged** without triggering type conversion

### Adding New Columns

When adding columns using the **⧾** button:

#### Adding After an Asset Column
```
Result: New column is created as an Asset
Position: Inserted immediately after the clicked Asset column
Type: Asset (automatically assigned)
```

#### Adding After a Liability Column  
```
Result: New column is created as a Liability
Position: Inserted immediately after the clicked Liability column  
Type: Liability (automatically assigned)
```

#### Adding After Equity Column
```
Result: Not allowed - no ⧾ button appears
Reason: Equity must remain the last editable column before A-L-E
```

### Deleting Columns

Column deletion follows these rules:

1. **Can delete Assets** if there is more than one Asset column
2. **Can delete Liabilities** if there is more than one Liability column  
3. **Cannot delete the last Asset** (must have at least one)
4. **Cannot delete the last Liability** (must have at least one)
5. **Cannot delete Equity** (only one exists and is protected)
6. **Cannot delete A-L-E** (computed column, structural element)

### Type Indicator Visual Design

To help users understand column types, the interface should provide visual indicators:

- **Asset columns**: Could have a distinctive background color or icon (e.g., light blue, 💰)
- **Liability columns**: Different background color or icon (e.g., light red, 📄)
- **Equity column**: Unique highlighting to show its special status (e.g., light green, 🏦)
- **A-L-E column**: Clearly marked as computed/read-only (e.g., gray background, 🧮)

### Implementation Considerations

The column property system requires:

1. **Type Tracking**: Internal array/map tracking each column's current type (Asset/Liability/Equity)
2. **Boundary Detection**: Logic to determine when a column crosses the Asset-Liability boundary
3. **Automatic Conversion**: Handler that updates column type when boundary crossing is detected
4. **Auto-Creation Logic**: When a boundary cross would leave a type with zero columns, automatically insert a new column of that type
5. **Positional Enforcement**: Maintain left-to-right ordering (Assets → Liabilities → Equity → Computed)
6. **Button State Management**: Dynamic enabling/disabling of movement buttons based on column position and type
7. **Validation Rules**: Enforcement of minimum column counts per type (at least 1 Asset, 1 Liability, exactly 1 Equity)

### Data Structure Representation

Suggested internal representation:
```java
enum ColumnType {
    ASSET,
    LIABILITY, 
    EQUITY,
    COMPUTED  // For A-L-E column
}

class ColumnDefinition {
    String name;           // User-defined column header
    ColumnType type;       // Current column type
    int position;          // Current position in table
    boolean canMove;       // Movement allowed?
    boolean canDelete;     // Deletion allowed?
}
```

### Accounting Equation Integrity

The column structure ensures the fundamental accounting equation is always maintained:

**Assets = Liabilities + Equity**

This is enforced through:
- Required minimum of 1 Asset column (auto-created if boundary crossing would eliminate all Assets)
- Required minimum of 1 Liability column (auto-created if boundary crossing would eliminate all Liabilities)
- Exactly 1 Equity column (immovable, rightmost position)
- The A-L-E computed column that validates the equation
- Strict left-to-right positional ordering (Assets left, Liabilities center, Equity right)

The type conversion system allows flexible modeling while maintaining this mathematical relationship. The auto-creation feature ensures users cannot accidentally create an invalid accounting structure by moving columns across boundaries.

## Design Principles for Table Editor

The labeled cells above are fixed and not editable. The buttons (⧾ ⧿ ← → ↑ ↓) are used to add, delete or move rows and columns. The design principles for this table editor are as follows:

the css for elements  is at `src/com/lushprojects/circuitjs1/public/style.css`

### 1. Progressive Disclosure Principle
The table editor follows a layered approach to complexity management:

**Initial State**: Present users with a minimal, standard table structure containing the essential columns (Buttons, Flow Description, Assets, Liabilities, Equity, A-L-E). This prevents cognitive overload while establishing the fundamental framework.

**Expansion on Demand**: Control buttons (⧾ ⧿ ← → ↑ ↓) appear contextually only where additions, deletions, or movements are logically possible. This reduces visual clutter while maintaining full functionality accessibility.

**Contextual Availability**: Row and column manipulation controls are positioned adjacent to their target elements, following the principle of spatial relationship in interface design.

### 2. Affordance and Signification
The button symbols provide clear visual affordances:

- **⧾ (Add)**: Visually suggests expansion or growth
- **⧿ (Delete)**: Indicates removal or reduction
- **← → ↑ ↓ (Direction arrows)**: Clearly communicate movement direction and insertion points

These symbols transcend language barriers and provide immediate understanding of available actions without requiring textual labels.

### 3. Consistency and Predictability
The interface maintains consistent behavior patterns:

**Positional Logic**: Add buttons always appear at insertion points, delete buttons appear on existing elements, and movement controls appear where repositioning is contextually appropriate.

**State Preservation**: When users add or remove elements, the existing data remains intact and positioned logically, preventing accidental data loss.

**Uniform Interaction Model**: All table modifications follow the same interaction pattern - select target location, choose action, confirm result.

### 4. Error Prevention and Recovery
The design incorporates several safety mechanisms:

**Visual Feedback**: Changes are immediately visible, allowing users to assess modifications before committing them permanently.

**Logical Constraints**: The system maintains table integrity by preventing invalid operations (such as deleting required header columns or creating malformed structures).

**Reversible Actions**: Each operation can be undone through corresponding inverse actions, supporting user confidence in experimentation.

### 5. Cognitive Load Management
The interface minimizes mental effort through:

**Spatial Organization**: Related controls are grouped logically - column controls with columns, row controls with rows.

**Visual Hierarchy**: Different types of controls (structural vs. content) are visually distinguished through positioning and styling.

**Incremental Complexity**: Users can start with simple operations and progressively access more advanced features as needed.

### 6. Accessibility and Universal Design
The design considerations include:

**Clear Visual Indicators**: Sufficient contrast and sizing for button recognition across different vision capabilities.

**Logical Tab Order**: Keyboard navigation follows predictable patterns through the interface elements.

**Semantic Structure**: The table maintains proper semantic relationships for screen readers and assistive technologies.

### 7. Data Integrity and Validation
The system ensures reliable operation through:

**Structure Validation**: Maintains proper table formatting and prevents creation of invalid configurations.

**Content Preservation**: User-entered data is protected during structural modifications.

**Relationship Maintenance**: Column and row relationships remain consistent during reorganization operations.

### 8. User Experience Flow
The editing process follows a natural progression:

**Discovery Phase**: Users first encounter the basic table structure and understand the domain concepts (Assets, Liabilities, Equity flows).

**Exploration Phase**: Control buttons become available as users interact with the table, revealing additional capabilities organically.

**Customization Phase**: Users can modify the structure to match their specific analytical needs while maintaining the logical relationships between financial elements.

**Validation Phase**: The system provides immediate feedback on the validity and completeness of the table configuration before allowing users to proceed.

### 9. Mental Model Alignment
The interface design aligns with users' existing mental models:

**Financial Accounting Concepts**: The default column structure (Assets, Liabilities, Equity) matches standard accounting frameworks, reducing learning overhead.

**Spreadsheet Familiarity**: The grid-based layout and manipulation controls mirror familiar spreadsheet interactions.

**Logical Flow Representation**: The table structure supports the mental model of stocks and flows in system dynamics modeling.

### 10. Scalability and Performance
The design considerations for larger tables include:

**Efficient Rendering**: Only visible elements are fully rendered, with optimization for tables that exceed viewport boundaries.

**Memory Management**: Data structures are designed to handle expansion and contraction without memory fragmentation.

**Responsive Behavior**: The interface adapts gracefully to different screen sizes while maintaining functionality. 
