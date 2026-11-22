# Lines

The **Lines** table lists the AC lines of the selected network. Open it with **Tables** > **Lines**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Lines table of the smallNodeBreakerWithVoltageLimits sample network, after a load flow](../../../_generated/images/table-lines.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC**, one per side, are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **R** | Parameters | Yes | Series resistance (Ω). |
| **X** | Parameters | Yes | Series reactance (Ω). |
| **G1**, **B1** | Parameters | Yes | Shunt conductance and susceptance at side 1 (S). |
| **G2**, **B2** | Parameters | Yes | Shunt conductance and susceptance at side 2 (S). |
| **P** | Solved Values |  | Active power at each side (MW). |
| **Q** | Solved Values |  | Reactive power at each side (MVar). |
| **I** | Solved Values |  | Current at each side (A). |
| **Loading** | Solved Values |  | Loading against the limits, see [Loading column](index.md#loading-column). |

The **Parameters** group is hidden by default. Only the **Name** and **Loading** columns can be sorted. In the Substations view tab, the **Substation** and **Voltage Level** links stay, as the other side can be elsewhere.

## Creation and removal

**New** > **Line…** creates a line, from the selected row's voltage level by default. The **Remove** button removes the selected row's line, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [line details](../details/line.md).
