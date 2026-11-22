# Remedial Action Optimization Results

The Remedial Action Optimization Results view shows the result of the last remedial action optimization (RAO) of the
selected network: the flows on its CNECs before and after optimization, the remedial actions it activated and its
costs. Open it with **Results** > **Remedial Action Optimization Results**, or with **View results** in the
notification of a finished RAO.

## Content

The view is empty until a RAO has been run on the network (**Run** > **Remedial Action Optimization**, with the
parameters of the [Remedial Action Optimization](../parameters/remedial_action_optimization.md) tab). A message at the
top tells when the network's CRAC has been replaced since: run the RAO again to update the result.

Below it, a summary: whether the RAO completed or some of its computations failed, whether the flows are secure after
optimization, whether it was computed in AC or DC, and the steps it went through (as told by OpenRAO).

Then three tabs.

### CNECs

The flow CNECs (critical network elements, with the contingency and instant they are monitored at), most limiting
first. Above the table, **State** shows **All states**, or the CNECs of one state only (**Preventive**, or a
contingency and an instant); **Unit** shows the flows, thresholds and margins in **A** or **MW**.

| Column | Content |
|---|---|
| **CNEC** | The CNEC's name. |
| **Element** | The branch it monitors. |
| **State** | **Preventive**, or the contingency and the instant it is monitored at. |
| **Side** | The side of the branch whose flow is shown, the first the CNEC monitors. |
| **Role** | **Optimized** (its margin is maximized or made positive), **Monitored** (its margin must not decrease too much: MNEC), or both. |
| **Min Threshold**, **Max Threshold** | Its thresholds on that side, "-" when it has none. |
| **Initial Flow**, **Initial Margin** | Before any remedial action. |
| **Flow After Preventive**, **Margin After Preventive** | After the preventive remedial actions, for a CNEC monitored after a contingency. |
| **Flow**, **Margin** | After the remedial actions of its own instant. |

The margin is the distance to the nearest threshold, over all the sides the CNEC monitors; a negative margin, in red,
is a constraint. Angle and voltage CNECs are not shown: the RAO doesn't compute them.

### Remedial Actions

The remedial actions the RAO activated, state by state: the **Type** of action (network action, PST, HVDC, injection,
counter-trading) and, for a range action, its **Initial Set Point** and **Set Point** (an angle for a PST, MW
otherwise), plus the **Initial Tap** and **Tap** of a PST.

### Costs

The cost of the situation, which the RAO minimizes, before optimization (**Initial**) and after the optimization of
each instant: its **Functional Cost** (the objective function: the opposite of the worst margin, or the cost of the
remedial actions), **Virtual Cost** (penalties, such as MNEC violations or failed sensitivity computations) and their
sum, then each virtual cost.

## What you can do

### Run a remedial action optimization

1. Give the network a CRAC (contingencies, CNECs and remedial actions).
2. Set the parameters in the **Remedial Action Optimization** and **Load Flow** tabs of the
   [Parameters](../parameters/index.md) view.
3. Click **Run** > **Remedial Action Optimization** in the main toolbar.

The RAO runs on the selected network (the whole merged network, for a subnetwork) and its CRAC; the network itself is
never changed, the optimal remedial actions being only shown here. It is refused when the network has no CRAC ("No
CRAC is defined for this network"), when the CRAC no longer matches the network, edited since the CRAC was imported,
and when OpenRAO would fail on, or misread, the CRAC with the parameters: a dialog lists every reason (elements missing
from the network, an objective function or loop-flow constraints needing GLSKs, several curative instants with
FastRAO...); fix them first. A notification follows its progress and can cancel it; at the end it offers **View
results** and **View report** (the report opens in the [Reports](../diagnostics/reports.md) view, with OpenRAO's
log of the optimization). It says "Remedial action optimization completed, flows still not secure" when some CNEC
margins remain negative, and "Remedial action optimization failed: ..." with OpenRAO's reason when it failed. Each run
replaces the previous result of the network.

### Read the result

- Sort a column by clicking its header: the CNECs are sorted by margin first, most limiting first.
- Show or hide columns from the menu at the right end of the table header.
