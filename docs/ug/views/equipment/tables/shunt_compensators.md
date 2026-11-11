# Shunt Compensators

The **Shunt Compensators** table lists the shunt compensators (capacitor and reactor banks) of the selected network. Open it with **Tables** > **Equipment** > **Shunt Compensators**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Shunt Compensators table of the smallNodeBreakerWithVoltageLimits sample network, after a load flow](../../../_generated/images/table-shunt-compensators.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Type** |  |  | **Capacitor** or **Reactor**, from the sign of the susceptance. |
| **Model Type** |  |  | **Linear** or **Non-Linear**. |
| **Section** |  | Yes, stepper | Number of sections in service, from 0 to **Maximum Section**. |
| **Maximum Section** |  |  |  |
| **Voltage Regulation** |  | Yes | Whether it regulates voltage. |
| **Target V** |  | Yes | Voltage target (kV). |
| **Target Deadband** |  | Yes | kV. |
| **Regulated Bus Voltage** | Solved Values |  | Voltage of the regulated bus (kV). |
| **Solved Section** |  |  | Section count computed by the load flow. |
| **P** | Solved Values |  | Active power (MW). |
| **Q** | Solved Values |  | Reactive power (MVar). |
| **I** | Solved Values |  | Current (A). |

The sections are detailed in the [Equipment details](../details/index.md).

## Creation and removal

**New** > **Shunt Compensator…** creates a shunt compensator, in the selected row's voltage level by default. The **Remove** button removes the selected row's shunt compensator, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [shunt compensator details](../details/shunt_compensator.md).
