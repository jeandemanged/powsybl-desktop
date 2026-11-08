# Load flow

This tutorial runs an AC load flow on SmallGrid and goes through its results: convergence, flows and voltages in a
single-line diagram, and the limit violations, in a table and on the map. It starts from the network loaded in the
[previous tutorial](load_network.md).

## Run the load flow

1. In the main toolbar, click **Run**.
2. Click **Load Flow**.

![The Run menu](../_generated/images/quickstart-loadflow-run.png)

The load flow runs with the parameters of the [Parameters](../views/parameters.md) view; the defaults are fine for
this tutorial. The computation itself is done by PowSyBl OpenLoadFlow.

When it ends, a notification gives the outcome and two links: **View results** and **View report**.

![The load flow completed notification](../_generated/images/quickstart-loadflow-results.png)

## Check the convergence

Click **View results**, or open **Tables** > **Load Flow Results**. The table has one row per network component
computed: the status of the computation, the number of iterations, the reference and slack buses, and the active
power mismatch distributed over the network.

![Load flow results](../_generated/images/quickstart-loadflow-components.png)

On SmallGrid, the single component converges in a few iterations. See [Load flow results](../views/loadflow_results.md)
for the meaning of each column, and [Reports](../views/reports.md) for the detailed log of the computation.

## Look at flows and voltages in a diagram

1. Click **Substations** in the main toolbar.
2. In the tree on the left, select a voltage level, e.g. **RÓM132KV**: its single-line diagram opens.

![Single-line diagram after the load flow](../_generated/images/quickstart-loadflow-sld.png)

Each feeder shows its active and reactive power flows, its current and its loading, and each bus shows its voltage
magnitude and angle. Clicking a feeder at the other end of a line opens the voltage level on the other side. See
[Substations](../views/substations.md) for everything diagrams can do.

The computed values are also in the equipment tables: **Tables** > **Equipment** > **Lines** for flows,
**Buses (Bus/Branch View)** for voltages.

## Look at the limit violations

Open **Tables** > **Violations**. The **Flows** tab lists the branches loaded beyond their limits, and the
**Voltages** tab the buses out of their voltage limits.

![Limit violations after the load flow](../_generated/images/quickstart-loadflow-violations.png)

On SmallGrid, two lines are overloaded. The **Loading** column shows how far beyond its permanent limit each one is,
and for how long it may stay so (here 1 and 15 minutes). The info button next to the bar opens the details of the
line's violations, and its limits with the crossed ones highlighted. See [Violations](../views/violations.md).

The **Voltages** tab is empty: all bus voltages are within the limits of their voltage level.

## See the violations on the map

Click **Map** in the main toolbar. A red warning bubble now marks each overloaded line, above its middle. With **Show flows** checked,
overloaded lines are also highlighted in red all along, and each end of a line gets chevrons pointing the way the
active power flows there, one to four depending on the loading of that side (see [Map](../views/map.md)).

![Limit violations on the map](../_generated/images/quickstart-loadflow-map.png)

Zoom in to see the chevrons, here around the substation with the most lines:

![Flow chevrons around a substation](../_generated/images/map-flows-close-up.png)

Hover a bubble to list the line's violations; click it to open the line's details.

Next, check what happens when lines trip with a [security analysis](security_analysis.md).
