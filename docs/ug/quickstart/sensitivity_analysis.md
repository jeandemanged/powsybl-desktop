# Sensitivity analysis

A sensitivity analysis computes how much a quantity of the network, the function, changes when another one, the
variable, changes by one unit. This tutorial computes the sensitivities of the active power flow on the 220 kV line
8-30 of SmallGrid to the output of four generators: by how many MW the flow changes when each generator produces one
more MW, before and after each contingency.
It starts from the network loaded in the [first tutorial](load_network.md), with the contingency list of the
[security analysis tutorial](security_analysis.md): without it, only the pre-contingency sensitivities are computed.

## Define the sensitivity factors

1. Click **Sensitivity Factors** in the main toolbar.
2. Click **Add**, then **Factor matrix**.

![Adding a factor matrix](../_generated/images/quickstart-sensi-add-matrix.png)

A factor matrix computes the sensitivity of each of its functions to each of its variables. Its form opens on the right
of the view. In **Name**, type `Line 8-30` and press Enter.

### The function: the flow on line 8-30

**Function type** is already **Branch active power (side 1)**, the active power flow at side 1 of a branch. Under
**Functions**, click **Add…**: a dialog lists the equipment the function type applies to, here the lines and
transformers. Type `8-30` in its search field, check the line, then click **OK**.

![Picking the line](../_generated/images/quickstart-sensi-pick-line.png)

### The variables: four generators

**Variable type** is already **Injection active power**, the active power a generator or load injects. Under
**Variables**, click **Add…**, then check the generators one search at a time: type `Breed` and check
**Breed SM**, then do the same for `Sporn`, `Glen Lyn` and `ClinchRv`. A checked element stays checked while you
search for the next one. Click **OK**.

![Picking the generators](../_generated/images/quickstart-sensi-pick-generators.png)

Leave **Computed for** on **Pre-contingency and every contingency**. The matrix holds four factors: the flow on line
8-30 against each generator.

![The factor matrix](../_generated/images/quickstart-sensi-matrix.png)

Had the matrix held anything Open Load Flow can't compute, a warning icon would mark it in the list, and the form would
tell why. See [Sensitivity factors](../views/analysis/sensitivity_factors.md).

## Run the sensitivity analysis

1. In the main toolbar, click **Run**.
2. Click **Sensitivity Analysis**.

The analysis runs on the contingencies of the enabled contingency lists, with the parameters of the
[Sensitivity Analysis](../views/parameters/sensitivity_analysis.md) tab of the Parameters view: in AC, as the load
flow, unless its **Calculation Mode** says otherwise. When it ends, a notification offers **View results** and
**View report**.

## Read the results

Click **View results**, or open **Tables** > **Sensitivity Analysis Results**. The view shows one state at a time,
**Pre-contingency** first, with one row per factor.

![The pre-contingency sensitivities](../_generated/images/quickstart-sensi-results.png)

- **Function Reference** is the value of the function, here the flow on line 8-30: 71.8 MW.
- **Sensitivity** is the change of that flow, in MW, per MW more produced by the generator.

One more MW from Breed SM, at side 1 of the line, raises its flow by 0.63 MW; one more MW from any of the three
others lowers it by about 0.08 MW. A generator can't produce one more MW alone: the load flow balances it, as set by
the **Slack Distribution** category of the [Load Flow](../views/parameters/load_flow.md) parameters (by default, all
generators lower their output in proportion to their maximum power). A sensitivity is thus the effect of shifting one MW from all the generators to that one.

## After a contingency

The **State** list holds the pre-contingency state, then the contingencies of the enabled lists, each named after the
line it loses. Choose **26-30**:

![The sensitivities after the loss of line 26-30](../_generated/images/quickstart-sensi-results-contingency.png)

Without line 26-30, line 8-30 carries 92.7 MW, and its sensitivities to the generators change slightly. A contingency
whose computation failed shows "(failed)" in the list and has no values. See
[Sensitivity analysis results](../views/analysis/sensitivity_analysis_results.md).

The factor matrices are kept for the session; save them in a [study](../general/index.md#studies), with the results,
to resume later.
