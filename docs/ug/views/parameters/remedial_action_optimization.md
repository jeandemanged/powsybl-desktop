# Remedial Action Optimization

The **Remedial Action Optimization** tab sets the parameters of the remedial action optimization (RAO), run by **Run** >
**Remedial Action Optimization** and computed by
[PowSyBl OpenRAO](https://powsybl.readthedocs.io/projects/openrao/en/stable/): its search tree RAO, directly or through
FastRAO (see [FastRAO](#fastrao)). The RAO runs with the load flow parameters of the [Load Flow](load_flow.md) tab,
AC or DC as its **Calculation Mode** says.

![The Remedial Action Optimization tab](../../_generated/images/parameters-rao.png)

## General

| Field | Editor | Description |
|---|---|---|
| **Calculation Mode** | Choice: `Same as load flow`, `AC`, `DC` | AC or DC, for the RAO only: **Same as load flow** follows the **DC** parameter of the Load Flow tab, and tells which it is. |
| **Available CPUs** | Number | Number of threads the RAO runs contingency scenarios and search tree leaves on (at least 1). |
| **Sensitivity Failure Overcost** | Number | Cost added to the objective function when a sensitivity computation fails. |

## Objective Function

| Field | Editor | Description |
|---|---|---|
| **Objective Function** | Choice | `SECURE_FLOW` stops once every CNEC is secure, `MAX_MIN_MARGIN` maximizes the smallest margin, `MIN_COST` minimizes the cost of the remedial actions. `MAX_MIN_RELATIVE_MARGIN` needs GLSKs, which the application doesn't provide: a RAO with it is refused. |
| **Enforce Curative Security** | Check box | Keep optimizing curative states until they are secure, even when the preventive state is not. |
| **Curative Min Objective Improvement** | Number | Minimum improvement of the objective function by a curative optimization for its remedial actions to be kept. |
| **Skip Curative CNECs of TSOs Without CRAs** | Check box | Don't optimize the curative CNECs of operators that have no curative remedial action, only keep their margins from decreasing. |
| **Costly Min Margin Parameters** | Check box | Whether the parameters below are set: the `MIN_COST` objective function needs them. Changing one of their values ticks it. |
| **Shifted Violation Penalty** | Number | Cost per MW of margin below the shifted violation threshold, for the `MIN_COST` objective function. |
| **Shifted Violation Threshold** | Number | Margin, in MW, below which the shifted violation penalty applies. |

## Range Actions

| Field | Editor | Description |
|---|---|---|
| **PST Model** | Choice | `CONTINUOUS` optimizes PST angles, then rounds them to taps; `APPROXIMATED_INTEGERS` optimizes taps directly, more precisely but slower. A CRAC limiting elementary actions per TSO needs `APPROXIMATED_INTEGERS`. |
| **PST**, **HVDC**, **Injection Min Impact Threshold** | Number | Penalty per degree (PST) or MW (HVDC, injection) of set point change, so that only range actions improving the objective function enough are used. |
| **PST**, **HVDC**, **Injection Sensitivity Threshold** | Number | Sensitivities of CNECs to these range actions below this value are ignored. |
| **Range Shrinking** | Choice | Shrink the range actions' ranges between MIP iterations, to converge faster. |
| **Max MIP Iterations** | Number | Maximum number of iterations between the linear optimization of range actions and the sensitivity computation (at least 1). |
| **Solver** | Choice | Linear optimization solver: `CBC` and `SCIP` come with the application, `XPRESS` needs its own installation and license. |
| **Relative MIP Gap** | Number | Relative gap at which the solver stops optimizing. |
| **Solver Specific Parameters** | Text field | Parameters passed to the solver as they are, in its own syntax; empty for none. |

## Topological Actions

| Field | Editor | Description |
|---|---|---|
| **Max Preventive Search Tree Depth**, **Max Curative Search Tree Depth** | Number | Maximum number of consecutive preventive, curative topological actions. |
| **Topological Action Relative Min Impact** | Number | Minimum relative improvement of the objective function (0 to 1) for a topological action to be kept. |
| **Topological Action Absolute Min Impact** | Number | Minimum improvement of the objective function, in its unit, for a topological action to be kept. |
| **Skip Actions Far From Most Limiting Element** | Check box | Ignore the topological actions too many country boundaries away from the most limiting element. |
| **Max Boundaries for Skipping Actions** | Number | Number of country boundaries beyond which a topological action is too far. |
| **Allow Electrical Islands** | Check box | Whether topological actions may split the network into electrical islands. |

## Second Preventive RAO

| Field | Editor | Description |
|---|---|---|
| **Execution Condition** | Choice | When to run a second preventive optimization, taking the curative results into account: never (`DISABLED`), when curative results can be improved, or only if the RAO's cost increased too. |
| **Hint From First Preventive RAO** | Check box | Start the second preventive optimization from the first one's remedial actions. |

## Monitored Elements (MNEC)

| Field | Editor | Description |
|---|---|---|
| **MNEC Constraints** | Check box | Keep the margins of the monitored, not optimized, CNECs from decreasing by more than the acceptable margin decrease. Changing one of the values below ticks it. |
| **Acceptable Margin Decrease** | Number | How much, in MW, the margin of a monitored CNEC may decrease from its initial value. |
| **MNEC Violation Cost** | Number | Cost per MW of violation of the MNEC constraint, added to the objective function. |
| **MNEC Constraint Adjustment Coefficient** | Number | Margin, in MW, taken off the MNEC constraint in the linear optimization to make up for its approximations. |

## FastRAO

FastRAO runs the search tree RAO on the worst CNECs only, then adds CNECs until the result holds for all of them:
faster on large CRACs. It is not another optimizer: every other category applies to the search tree RAO it runs. It
handles at most one curative instant: a RAO through FastRAO of a CRAC with more is refused.

| Field | Editor | Description |
|---|---|---|
| **Use FastRAO** | Check box | Run the RAO through FastRAO. |
| **CNECs Added per Iteration** | Number | Number of worst CNECs added to the optimized ones at each iteration (at least 1). |
| **Add Unsecure CNECs** | Check box | Also add, at each iteration, every CNEC whose margin is below the margin limit. |
| **Margin Limit** | Number | Margin below which a CNEC is unsecure, for **Add Unsecure CNECs**. |

They are documented in the
[OpenRAO documentation](https://powsybl.readthedocs.io/projects/openrao/en/stable/parameters.html). Loop-flow and
relative margin parameters need GLSKs, which the application doesn't provide: they are not shown, and a RAO with them
is refused. Predefined combinations of topological actions can only be set by importing a file.

## Import and export

The **Import** and **Export** buttons of the tab read and write the RAO parameters only, in the OpenRAO JSON format of
RAO parameters. Load flow parameters held by an imported file are ignored: those of the **Load Flow** tab are kept.
The **Calculation Mode** and **Use FastRAO** are the application's own: they are neither imported nor exported, but
saved with all the parameters.
