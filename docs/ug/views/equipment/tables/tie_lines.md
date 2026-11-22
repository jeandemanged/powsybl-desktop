# Tie Lines

The **Tie Lines** table lists the tie lines of the selected network: lines made of two boundary lines joined at their boundary point, typically between two subnetworks. Open it with **Tables** > **Tie Lines**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Tie Lines table of the microGridBaseCaseAssembled sample network, after a load flow](../../../_generated/images/table-tie-lines.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC**, one per side, are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Pairing Key** |  |  | The key that paired the two boundary lines. |
| **Boundary Line** |  |  | Links to the two [boundary lines](boundary_lines.md). |
| **R**, **X**, **G1**, **B1**, **G2**, **B2** | Parameters |  | Computed from the boundary lines' parameters: edit them there. |
| **P** | Solved Values |  | Active power at each side (MW). |
| **Q** | Solved Values |  | Reactive power at each side (MVar). |
| **I** | Solved Values |  | Current at each side (A). |
| **Loading** | Solved Values |  | Loading against the limits, see [Loading column](index.md#loading-column). |
| **P (Boundary)** | Solved Values at Boundary |  | Active power at the boundary point (MW). |
| **Q (Boundary)** | Solved Values at Boundary |  | Reactive power at the boundary point (MVar). |
| **I (Boundary)** | Solved Values at Boundary |  | Current at the boundary point (A). |
| **V Magnitude (Boundary)** | Solved Values at Boundary |  | Voltage at the boundary point (kV). |
| **V Angle (Boundary)** | Solved Values at Boundary |  | Voltage angle at the boundary point (°). |

The **Parameters** and **Solved Values at Boundary** groups are hidden by default. The boundary values are read on the boundary line of side 1. Columns with one value per side can't be sorted. In the Substations view tab, the **Substation** and **Voltage Level** links stay, as the other side can be elsewhere.

## Creation and removal

**New** > **Tie Line…** pairs two unpaired boundary lines into a tie line. The **Remove** button removes the selected row's tie line, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [tie line details](../details/tie_line.md).
