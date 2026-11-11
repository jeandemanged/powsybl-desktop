# Static Var Compensators

The **Static Var Compensators** table lists the static var compensators of the selected network. Open it with **Tables** > **Static Var Compensators**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Static Var Compensators table of the Four Substations Node Breaker sample network, after a load flow](../../../_generated/images/table-static-var-compensators.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Regulating** |  | Yes | Whether it regulates. Checking it sets the **Reactive Power** mode when none was set. |
| **Regulation Mode** |  | Yes, choice | **Voltage**, **Reactive Power** or **Voltage per Reactive Power**. |
| **Target V** |  | Yes | Voltage target (kV). |
| **Target Q** |  | Yes | Reactive power target (MVar). |
| **Q** | Solved Values |  | Reactive power (MVar). |
| **I** | Solved Values |  | Current (A). |
| **Regulated Bus Voltage** | Solved Values |  | Voltage of the regulated bus (kV). |

## Creation and removal

**New** > **Static Var Compensator…** creates a static var compensator, in the selected row's voltage level by default. The **Remove** button removes the selected row's static var compensator, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [static var compensator details](../details/static_var_compensator.md).
