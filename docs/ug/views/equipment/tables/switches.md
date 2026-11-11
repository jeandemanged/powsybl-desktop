# Switches

The **Switches** table lists the switches of a substation or voltage level. It exists only as a tab of the [Substations](../../diagrams/substations.md) view: select a substation or a voltage level there, then the **Switches** tab.

![The Switches tab of a voltage level of the smallNodeBreakerWithVoltageLimits sample network](../../../_generated/images/table-switches.png)

## Columns

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Name** |  |  | The name, or the id, after an info button and a copy button. |
| **Kind** |  |  | `BREAKER`, `DISCONNECTOR` or `LOAD_BREAK_SWITCH`. |
| **Open** |  | Yes | Whether the switch is open. |
| **Retained** |  | Node/breaker only | Whether the switch is kept in the bus/breaker view. |

The table has no search box.

## Creation and removal

**New** > **Switch…** creates a switch, in a node/breaker voltage level. The **Remove** button removes the selected row's switch, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [switch details](../details/switch.md).
