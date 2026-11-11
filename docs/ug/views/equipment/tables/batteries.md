# Batteries

The **Batteries** table lists the batteries of the selected network. Open it with **Tables** > **Equipment** > **Batteries**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Batteries table of the Battery sample network, after a load flow](../../../_generated/images/table-batteries.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Target P** |  | Yes | Active power target (MW). |
| **Target Q** |  | Yes | Reactive power target (MVar), used when not regulating voltage; for a remote reactive power regulation, its target. |
| **Min P** |  | Yes | MW. |
| **Max P** |  | Yes | MW. |
| **Regulating** |  | Yes | Whether the battery regulates. Checking it creates a voltage regulation if there is none. |
| **Regulation Mode** |  | Yes, choice | **Voltage** or **Reactive Power**. |
| **Target V** |  | Yes | Voltage target (kV). |
| **P** | Solved Values |  | Active power (MW). |
| **Q** | Solved Values |  | Reactive power (MVar). |
| **I** | Solved Values |  | Current (A). |
| **Regulated Bus Voltage** | Solved Values |  | Voltage of the bus of the regulated terminal, local or remote (kV). |

The columns are the same as in the [Generators](generators.md) table.

## Creation and removal

**New** > **Battery…** creates a battery, in the selected row's voltage level by default. The **Remove** button removes the selected row's battery, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [battery details](../details/battery.md).
