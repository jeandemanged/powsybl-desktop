# Loads

The **Loads** table lists the loads of the selected network. Open it with **Tables** > **Loads**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Loads table of the smallNodeBreakerWithVoltageLimits sample network, after a load flow](../../../_generated/images/table-loads.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **P0** |  | Yes | Active power consumption (MW). |
| **Q0** |  | Yes | Reactive power consumption (MVar). |
| **P** | Solved Values |  | Active power (MW). |
| **Q** | Solved Values |  | Reactive power (MVar). |
| **I** | Solved Values |  | Current (A). |

## Creation and removal

**New** > **Load…** creates a load, in the selected row's voltage level by default. The **Remove** button removes the selected row's load, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [load details](../details/load.md).
