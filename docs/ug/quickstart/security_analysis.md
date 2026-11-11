# Security analysis

A security analysis simulates a list of contingencies (losses of equipment) one at a time, and reports the limit
violations each one causes. This tutorial checks SmallGrid against the loss of any single line (an "N-1" analysis), and
shows the violations found on the map.
It starts from the network loaded in the [first tutorial](load_network.md).

## Define the contingencies

1. In the main toolbar, click the small arrow of **Inputs**, then **Contingencies**.
2. Click **Add**, then **Line Criterion**.

![Adding a contingency list](../_generated/images/quickstart-sa-add-contingencies.png)

A line criterion list creates one contingency per line matching its criteria: countries, nominal voltages,
properties, identifier pattern. Leave them all empty to select every line. The right part of the view shows the
contingencies the list creates, here one per line of the network.

![The contingencies of the list](../_generated/images/quickstart-sa-contingencies.png)

The checkbox in front of a list tells whether the security analysis uses it. See
[Contingencies](../views/inputs/contingencies.md) for the other kinds of lists and for saving lists to files.

## Run the security analysis

1. In the main toolbar, click **Run**.
2. Click **Security Analysis**.

The analysis runs with the parameters of the [Parameters](../views/parameters/index.md) view. When it ends, a
notification offers **View results** and **View report**.

![After the security analysis](../_generated/images/quickstart-sa-done.png)

## Read the results

Click **View results**, or open **Results** > **Security Analysis Results**. The **Contingencies** tab has one row per
contingency, plus the pre-contingency situation: its status, the number of flow and voltage violations, and the worst
one.

![Security analysis results per contingency](../_generated/images/quickstart-sa-results.png)

The other tabs list the violations themselves: **Flows by contingency**, **Flows by equipment** and **Voltages**.

![Flow violations by contingency](../_generated/images/quickstart-sa-flows-by-contingency.png)

On SmallGrid, a few line losses overload other lines: the **Pre-contingency Value** column shows the flow before the
contingency, the **Value** column the flow after it. Some also bring bus voltages out of their limits, listed in the
**Voltages** tab: the loss of line 8-9 alone causes 35 voltage violations. Click an equipment to open its voltage
level in the **Substations** view. See
[Security analysis results](../views/results/security_analysis_results.md).

## See the violations on the map

Click **Map** in the main toolbar. Besides the red bubbles of the pre-contingency overloads, orange bubbles mark the
elements with violations after some contingencies only: the lines they overload, and the substations whose voltage
levels get out of their voltage limits.

![Security analysis violations on the map](../_generated/images/quickstart-sa-map.png)

Hovering a bubble lists the violations of the element, after each contingency. The voltage violations hide the
overloads here: in the **Violations** box at the top right, uncheck **Contingency voltage violations**. Only the
overloads remain, those of the pre-contingency situation in red and those after contingencies in orange.

![The map without contingency voltage violations](../_generated/images/quickstart-sa-map-no-contingency-voltage.png)

The checkboxes are saved with the **User Interface** parameters. See [Map](../views/diagrams/map.md).
