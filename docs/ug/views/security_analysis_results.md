# Security Analysis Results

The Security Analysis Results view shows the limit violations found by the last security analysis of the selected
network, before and after each contingency. Open it with **Tables** > **Security Analysis Results**, or with
**View results** in the notification of a finished security analysis.

## Content

The view is empty until a security analysis has been run on the network. When a subnetwork is selected, only the
violations of its own equipment are shown. Only flow (current, active power, apparent power) and voltage violations
are shown.

Four tabs, each giving its number of rows. In every tab the pre-contingency rows (named **Pre-contingency**) come
first, then the others, most severe first.

### Contingencies

One row per state: the pre-contingency situation and each contingency.

- **Contingency**, and **Status** of its computation: **Converged**, **No Impact**, **Max Iteration Reached**,
  **Solver Failed**, **Failed** or **No Calculation**. A state that didn't converge is marked, has no violations and
  is listed first. When the pre-contingency state doesn't converge, no contingency is simulated.
- **Flow Violations** (number of equipment with a flow violation) and **Voltage Violations** (number of buses with a
  voltage violation).
- **Worst Flow Violation** (the most loaded equipment) and **Worst Loading**.

![The Contingencies tab](../_generated/images/quickstart-sa-results.png)

### Flows by contingency

One row per state and equipment with a flow violation, showing its most severe one: **Contingency**, **Subject**,
**Type**, **Side**, **Limit Type**, **Limit Name**, **Acceptable Duration**, **Pre-contingency Value**, **Value**,
**Permanent Limit**, **Pre-contingency Loading**, **Loading**. A **Limit Scaling** column is hidden by default.

![The Flows by contingency tab](../_generated/images/quickstart-sa-flows-by-contingency.png)

### Flows by equipment

One row per equipment with a flow violation, showing its most severe one over all the states: the same columns, plus
**Contingencies** (the number of contingencies violating it), with **Worst Contingency** instead of **Contingency**.

### Voltages

One row per state and bus with a voltage violation: **Contingency**, **Voltage Level**, **Bus**, **Limit Type**,
**Pre-contingency Value**, **Value**, **Limit**, **Nominal Voltage**, **% of Nominal**, **Pre-contingency Voltage
Range**, **Voltage Range**.

![The Voltages tab](../_generated/images/sa-results-voltages.png)

### Loading and voltage range

The loading columns draw the flow as a percentage of the permanent limit, on a bar from 0 to 200 % marked at 100 %,
the part beyond 100 % in the alert color, with the acceptable duration of the violated limit. Hover it to read the
violation. The voltage range columns place the voltage within the limits of the voltage level. The pre-contingency
columns of a contingency row give the value and loading of the same equipment or bus before the contingency, for
comparison. See [Violations](violations.md), which uses the same columns for the current state of the network.

## What you can do

### Run a security analysis

1. Define and enable contingency lists in the [Contingencies](contingencies.md) view.
2. Set the parameters in the **Security Analysis** and **Load Flow** tabs of the [Parameters](parameters.md) view.
3. Click **Run** > **Security Analysis** in the main toolbar.

The analysis runs on the selected network (the whole merged network, for a subnetwork), with the contingencies of
all the enabled lists. A notification follows its progress and can cancel it; at the end it offers **View results**
and **View report** (the report opens in the [Reports](reports.md) view). Each run replaces the previous results of
the network. See the [Security analysis tutorial](../quickstart/security_analysis.md).

### Explore the results

- Sort by any column; the pre-contingency rows stay first.
- Show or hide columns with the menu button at the right of the column headers.
- Click a **Subject** (or **Worst Flow Violation**) link to open the equipment's voltage level in the
  [Substations](substations.md) view.
- Click the information button of a loading cell (tooltip **Show all violations and limits**) to see all the
  violations of the equipment, in that state and before the contingency (in all states, from **Flows by
  equipment**), along with its limits.
