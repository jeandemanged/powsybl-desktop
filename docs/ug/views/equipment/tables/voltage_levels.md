# Voltage Levels

The **Voltage Levels** table lists the voltage levels of the selected network. Open it with **Tables** > **Voltage Levels**.

![The Voltage Levels table of the smallNodeBreakerWithVoltageLimits sample network](../../../_generated/images/table-voltage-levels.png)

## Columns

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Country** |  |  | Country code of the substation. |
| **Substation** |  |  | A link to the substation. |
| **Name** |  |  | The name, or the id, after an info button and a copy button, as a link to the voltage level in the [Substations](../../diagrams/substations.md) view. |
| **Topology Kind** |  |  | `NODE_BREAKER` or `BUS_BREAKER`. |
| **Nominal Voltage** |  | Yes | kV. |
| **Low Voltage Limit** |  | Yes | kV. Checked for the voltage [violations](../../results/violations.md). |
| **High Voltage Limit** |  | Yes | kV. Checked for the voltage violations. |

## Creation and removal

**New** > **Voltage Level…** creates a voltage level, in the selected row's substation by default. The **Remove** button removes the selected row's voltage level, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [voltage level details](../details/voltage_level.md).
