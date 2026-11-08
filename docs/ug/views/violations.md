# Violations

The Violations view lists the limit violations of the selected network: branches loaded beyond their limits and buses
whose voltage is outside their voltage level's limits. Open it from the main toolbar with **Tables** > **Violations**.

## Content

### When violations are computed

Violations are checked against the network's present values (the solved values written by the last load flow):

- once when a network is loaded (imported, created, merged, ...);
- again after each load flow.

They are **not** recomputed after an edit (of a value, of a limit, a creation or a removal): run a load flow to
update them. When a subnetwork is selected, only the violations of its own equipment are listed.

What is checked:

- **Flows**: the current, active power and apparent power limits (permanent and temporary) of the selected
  operational limits group of each side of the lines, tie lines, two- and three-winding transformers, and of the
  boundary lines not paired in a tie line. Limits are edited in the limits tab of the [Equipment details](details.md).
- **Voltages**: each bus of the bus/branch view against the **Low Voltage Limit** and **High Voltage Limit** of its
  voltage level.

The same results feed the **Loading** and **Voltage Range** columns of the [equipment tables](tables.md).

### Flows tab

The tab title gives the number of rows, e.g. **Flows (3)**. One row per equipment, showing its most severe violation
(shortest acceptable duration first, then highest loading); the rows are sorted the same way.

![The Flows tab](../_generated/images/quickstart-loadflow-violations.png)

| Column | Meaning |
|---|---|
| **Subject** | The equipment, as a link to its substation or voltage level. |
| **Type** | **Line**, **Tie Line**, **Transformer** or **Boundary Line**. |
| **Side** | The side where the limit is crossed. |
| **Limit Type** | **Current**, **Active power** or **Apparent power**. |
| **Limit Name** | Name of the crossed limit. |
| **Acceptable Duration** | Acceptable duration of the crossed temporary limit, in seconds; `-` for the permanent limit. |
| **Value** | Present value: current in A, or power in MW or MVA. |
| **Permanent Limit** | Permanent limit of the same side and limit type. |
| **Limit Scaling** | Hidden by default. |
| **Loading** | See below. |

The **Loading** column shows the value as a percentage of the permanent limit, with a bar:

- the bar goes from 0 to 200 %, with a black mark at 100 % in the middle; an arrow at its right end means the
  loading is above 200 %;
- for a violation, the bar is orange up to 100 % and red beyond (it is all green for a loading without violation, as
  in the equipment tables);
- for a temporary limit, the acceptable duration follows the percentage, in minutes (e.g. `120.0 % 10'`);
- hovering over it tells the limit type, the limit name, the side and the acceptable duration.

![The Loading column in different situations, for a 1000 A permanent limit](../_generated/images/tables-loading-bars.png)

### Voltages tab

The tab title gives the number of violations, e.g. **Voltages (2)**. One row per bus, the farthest beyond its limit
(relative to the nominal voltage) first.

| Column | Meaning |
|---|---|
| **Voltage Level** | The voltage level, as a link. |
| **Bus** | The bus of the bus/branch view. |
| **Limit Type** | **Low voltage** or **High voltage**. |
| **Value** | Bus voltage (kV). |
| **Limit** | The crossed limit (kV). |
| **Nominal Voltage** | Nominal voltage of the voltage level (kV). |
| **% of Nominal** | Bus voltage as a percentage of the nominal voltage. |
| **Voltage Range** | The voltage on a scale from 80 % to 120 % of the nominal voltage, the allowed band between the limits, the part beyond a limit in red; hovering over it gives the values in kV. |

![The Voltage Range column in different situations, for a 400 kV bus with limits at 380 and 420 kV](../_generated/images/tables-voltage-bars.png)

## What you can do

- See all the violations and limits of an equipment: click the info button (**Show all violations and limits**) in
  its **Loading** cell. The **Violations of** dialog lists every violation of the equipment, the one shown in the
  table marked with a star, with its side, limit type, operational limits group, limit name, acceptable duration,
  value, permanent limit and loading. Below the list, the equipment's operational limits are shown with the present
  values.

  ![The violations and limits of a line](../_generated/images/violations-dialog.png)

- Click a **Subject** or **Voltage Level** link to show it in the [Substations](substations.md) view.
- Sort a tab by clicking a column header; show or hide columns, e.g. **Limit Scaling**, from the **+** menu button at
  the right end of the column headers.
- Update the list after changing the network: run a load flow (**Run** > **Load Flow**).
- Open the view in a separate window with **Open in new window** (see [General usage](../general/index.md)).

The violations found by a security analysis, before and after each contingency, are shown in the
[Security analysis results](security_analysis_results.md) view.
