# Area

The details of an area. Open it from the info button of the [Areas](../tables/areas.md) table, or from the other places listed in [Equipment details](index.md).

![The area view](../../../_generated/images/detail-area.png)

## Header

No link nor symbol: the voltage levels are listed in their own tab.

## Main tab

### Characteristics

| Field | Editor | Meaning |
|---|---|---|
| **Area type** | Read-only | For instance control area or bidding zone. |
| **Interchange target** | Text field | MW. |

### Solved Values

| Field | Editor | Meaning |
|---|---|---|
| **AC Interchange**, **DC Interchange**, **Interchange** | Read-only | MW. |

## Other tabs

### Voltage Levels

The voltage levels of the area (**Name**, **Id**). Choose one of the other voltage levels in the box and click **Add** to add it; **Remove** removes the selected one.

### Boundaries

The boundaries of the area: **Boundary** (the equipment, with its side), **AC** (AC or DC), **P** and **Q**. **Add** opens a terminal picker and adds an AC boundary, or a DC one when **AC** is unchecked; **Remove** removes the selected one.

![The Boundaries tab](../../../_generated/images/detail-area-boundaries.png)

### Extensions

The extensions that can be added: Dynamic Model.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
