# Buses

Two tables list the buses of the selected network, each in one topology view:

- **Buses (Bus/Branch View)**: the electrical nodes, everything connected by closed switches merged into one bus. They are the buses a load flow computes.
- **Buses (Bus/Breaker View)**: the buses of bus/breaker voltage levels as defined, and the buses computed from the nodes of node/breaker voltage levels.

Open them with **Tables** > **Equipment** > **Buses (Bus/Branch View)** or **Buses (Bus/Breaker View)**. Both are also tabs of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Buses (Bus/Branch View) table of the smallNodeBreakerWithVoltageLimits sample network, after a load flow](../../../_generated/images/table-buses-bus-branch-view.png)

![The Buses (Bus/Breaker View) table of the same network](../../../_generated/images/table-buses-bus-breaker-view.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **CC** and **SC** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Fictitious P0** |  | Yes | Fictitious active power injection at the bus (MW). |
| **Fictitious Q0** |  | Yes | Fictitious reactive power injection at the bus (MVar). |
| **V Magnitude** | Solved Values |  | Voltage (kV). |
| **V Angle** | Solved Values |  | Voltage angle (°). |
| **Voltage Range** | Solved Values |  | See [Voltage Range column](index.md#voltage-range-column). |
| **Bus in Bus/Branch View** |  |  | Bus/Breaker View only: a link to the bus it is part of in the Bus/Branch View. |

Buses have no detail view: the **Name** column only has the copy button. The rows are rebuilt after every change, as the buses depend on the topology.

## Creation and removal

- Buses (Bus/Branch View): computed from the topology, they can't be created or removed.
- Buses (Bus/Breaker View): **New** > **Bus…** creates a bus in a bus/breaker voltage level; **Remove** removes the selected bus, if it belongs to a bus/breaker voltage level.
