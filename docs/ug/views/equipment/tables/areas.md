# Areas

The **Areas** table lists the areas of the selected network: groups of voltage levels, such as control areas or bidding zones, with their interchange. Open it with **Tables** > **Equipment** > **Areas**.

![The Areas table of the EurostagTutorialExample1Factory createWithTieLinesAndAreas sample network, after a load flow](../../../_generated/images/table-areas.png)

## Columns

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Name** |  |  | The name, or the id, after an info button and a copy button. |
| **Area type** |  |  | For instance control area or bidding zone. |
| **Interchange target** |  | Where set | MW. `-`, and not editable, when the area has none. |
| **AC Interchange** |  |  | Solved AC interchange through the area's boundaries (MW). |
| **DC Interchange** |  |  | Solved DC interchange (MW). |
| **Interchange** |  |  | Total solved interchange (MW). |
| **Voltage Levels** |  |  | Number of voltage levels of the area. |
| **Boundaries** |  |  | Number of boundaries of the area. |

Areas aren't tied to a voltage level: the table has no search box. Rows are sorted by area type, then name.

## Creation and removal

**New** > **Area…** creates an area. The **Remove** button removes the selected row's area, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [area details](../details/area.md).
