# Generators

The **Generators** table lists the generators of the selected network. Open it with **Tables** > **Equipment** > **Generators**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Generators table of the smallNodeBreakerWithVoltageLimits sample network, after a load flow](../../../_generated/images/table-generators.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Target P** |  | Yes | Active power target (MW). |
| **Target Q** |  | Yes | Reactive power target (MVar), used when not regulating voltage; for a remote reactive power regulation, its target. |
| **Min P** |  | Yes | MW. |
| **Max P** |  | Yes | MW. |
| **Regulating** |  | Yes | Whether the generator regulates. Checking it creates a voltage regulation if there is none. |
| **Regulation Mode** |  | Yes, choice | **Voltage** or **Reactive Power**. |
| **Target V** |  | Yes | Voltage target (kV). |
| **P** | Solved Values |  | Active power (MW). |
| **Q** | Solved Values |  | Reactive power (MVar). |
| **I** | Solved Values |  | Current (A). |
| **Regulated Bus Voltage** | Solved Values |  | Voltage of the bus of the regulated terminal, local or remote (kV). |

## Creation and removal

**New** > **Generator…** creates a generator, in the selected row's voltage level by default. The **Remove** button removes the selected row's generator, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [generator details](../details/generator.md).
