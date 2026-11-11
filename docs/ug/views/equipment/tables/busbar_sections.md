# Busbar Sections

The **Busbar Sections** table lists the busbar sections of the node/breaker voltage levels of the selected network. Open it with **Tables** > **Equipment** > **Busbar Sections**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Busbar Sections table of the smallNodeBreakerWithVoltageLimits sample network, after a load flow](../../../_generated/images/table-busbar-sections.png)

## Columns

**Country**, **Substation**, **Voltage Level** and **Name** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **V Magnitude** | Solved Values |  | Voltage (kV). |
| **V Angle** | Solved Values |  | Voltage angle (°). |
| **Voltage Range** | Solved Values |  | See [Voltage Range column](index.md#voltage-range-column). |

The table is read-only.

## Creation and removal

**New** > **Busbar Section…** creates a busbar section, in a node/breaker voltage level, the selected row's by default. The **Remove** button removes the selected row's busbar section, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [busbar section details](../details/busbar_section.md).
