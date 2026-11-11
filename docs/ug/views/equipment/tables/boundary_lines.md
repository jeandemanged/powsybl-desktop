# Boundary Lines

The **Boundary Lines** table lists the boundary lines of the selected network: lines connected at one end only, the other end being a boundary point standing for the rest of the grid. Two of them can be paired into a [tie line](tie_lines.md). Open it with **Tables** > **Equipment** > **Boundary Lines**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Boundary Lines table of the microGridBaseCaseAssembled sample network, after a load flow](../../../_generated/images/table-boundary-lines.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Pairing Key** |  |  | The key used to pair boundary lines into tie lines. |
| **Tie Line** |  |  | A link to its tie line, when paired. |
| **R**, **X**, **G**, **B** | Parameters | Yes | Series resistance and reactance (Ω), shunt conductance and susceptance (S). |
| **P0** |  | Yes | Active power consumed at the boundary point (MW). |
| **Q0** |  | Yes | Reactive power consumed at the boundary point (MVar). |
| **Min P**, **Max P** |  | Yes | Generation part: MW. |
| **Voltage Regulation** |  | Yes | Generation part: whether it regulates voltage. |
| **Target V** |  | Yes | Generation part: voltage target (kV). |
| **Target Q** |  | Yes | Generation part: reactive power target (MVar). |
| **P (Network Side)** | Solved Values |  | Active power at the network side (MW). |
| **Q (Network Side)** | Solved Values |  | Reactive power at the network side (MVar). |
| **I (Network Side)** | Solved Values |  | Current at the network side (A). |
| **P (Boundary)** | Solved Values at Boundary |  | Active power at the boundary point (MW). |
| **Q (Boundary)** | Solved Values at Boundary |  | Reactive power at the boundary point (MVar). |
| **I (Boundary)** | Solved Values at Boundary |  | Current at the boundary point (A). |
| **V Magnitude (Boundary)** | Solved Values at Boundary |  | Voltage at the boundary point (kV). |
| **V Angle (Boundary)** | Solved Values at Boundary |  | Voltage angle at the boundary point (°). |
| **Loading** | Solved Values |  | See [Loading column](index.md#loading-column). For a paired boundary line, the loading of its tie line on its side. |

The **Parameters** and **Solved Values at Boundary** groups are hidden by default. The generation part columns show `-`, and can't be edited, for a boundary line without generation.

## Creation and removal

**New** > **Boundary Line…** creates a boundary line, in the selected row's voltage level by default. The **Remove** button removes the selected row's boundary line, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [boundary line details](../details/boundary_line.md).
