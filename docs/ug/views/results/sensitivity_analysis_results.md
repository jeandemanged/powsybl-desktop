# Sensitivity Analysis Results

The Sensitivity Analysis Results view shows the sensitivities computed by the last sensitivity analysis of the
selected network, before and after each contingency. Open it with **Results** > **Sensitivity Analysis Results**, or
with **View results** in the notification of a finished sensitivity analysis.

## Content

The view is empty until a sensitivity analysis has been run on the network, with the
[sensitivity factors](../inputs/sensitivity_factors.md) and the enabled [contingency lists](../inputs/contingencies.md). A message above
the table tells when the factor matrices have changed since: run the analysis again to update the results.

Above the table:

- **State**: **Pre-contingency**, then each contingency, named after the equipment it loses when it is a single one.
  A contingency whose computation failed is marked "(failed)" and has no values; one that changes nothing is marked
  "(no impact)".
- **Matrix**: **All**, or a single factor matrix.
- Whether the analysis was computed in AC or DC.

![The sensitivities of a state](../../_generated/images/quickstart-sensi-results.png)

The table has one row per factor of the state: a factor computed for the pre-contingency state only is not listed
after contingencies, one computed for a single contingency only after it.

| Column | Content |
|---|---|
| **Matrix** | The factor's matrix. |
| **Function Type**, **Function** | The function, and its equipment. |
| **Variable Type**, **Variable** | The variable, and its equipment or variable set. |
| **Function Reference** | The value of the function in that state (MW, MVar, A or kV). |
| **Sensitivity** | The change of the function per unit of the variable: per MW for an injection, per degree for a phase shift, per kV for a target voltage... |

Two kinds of missing values are told apart, both in gray:

- **Below threshold**: Open Load Flow doesn't report a sensitivity smaller than the thresholds of the
  [Sensitivity Analysis parameters](../parameters/sensitivity_analysis.md), nor its function reference (then "-");
- **Not computed**: Open Load Flow could not compute it, for instance for disconnected equipment, or equipment out of
  the main connected component.

Click a column header to sort the rows. The decimal places of the sensitivities are set by **Sensitivity** in the
[User Interface](../parameters/user_interface.md) parameters.

## What you can do

### Run a sensitivity analysis

1. Define the factor matrices in the [Sensitivity Factors](../inputs/sensitivity_factors.md) view.
2. Optionally, define and enable contingency lists in the [Contingencies](../inputs/contingencies.md) view.
3. Set the parameters in the **Sensitivity Analysis** and **Load Flow** tabs of the [Parameters](../parameters/index.md) view.
4. Click **Run** > **Sensitivity Analysis** in the main toolbar.

The analysis runs on the selected network (the whole merged network, for a subnetwork), with the factor matrices and
the contingencies of all the enabled lists. It is refused when no factors are defined ("No sensitivity factors are
defined for this network"), and when Open Load Flow would fail on some factors: a dialog lists them, as "The Open Load
Flow AC sensitivity analysis would fail on these factors" (or DC); fix or remove them first. A notification follows
its progress and can cancel it; at the end it offers **View results** and **View report** (the report opens in the
[Reports](../diagnostics/reports.md) view). When the computation failed for some contingencies, the notification says
"Sensitivity analysis completed, failed for N contingencies", and those contingencies are marked "(failed)". Each run
replaces the previous results of the network. See the [Sensitivity analysis tutorial](../../quickstart/sensitivity_analysis.md).
