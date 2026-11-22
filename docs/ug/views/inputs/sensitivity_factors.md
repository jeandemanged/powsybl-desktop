# Sensitivity Factors

The Sensitivity Factors view defines what a [sensitivity analysis](../results/sensitivity_analysis_results.md) computes: the
sensitivity factors of the selected network, grouped in factor matrices, and the variable sets some of them use. Open
it with **Inputs** > **Sensitivity Factors** in the main toolbar.

## Content

The factors belong to the network selected in the [Networks](../network/networks.md) view (to its root network, for a
subnetwork of a merged network). They are kept for the session; save them in a
[study](../../general/index.md#studies), or export them to files.

The view has two parts:

- left, two tabs: **Factor Matrices**, each shown with its number of factors, and **Variable Sets**, each shown with its
  number of variables. A warning icon marks an item Open Load Flow would fail on; its tooltip tells why;
- right, the form of the selected item, the reasons of its warning first.

![A factor matrix](../../_generated/images/quickstart-sensi-matrix.png)

### Factor matrices

A sensitivity factor is the sensitivity of a function (a flow, a current, a voltage...) to a variable (an injection,
a phase shift, a target voltage...). A factor matrix computes the sensitivity of each of its functions to each of its
variables: its number of factors is the number of functions times the number of variables.

- **Name** of the matrix.
- **Function type**, and the **Functions**: branch active power, current or reactive power on side 1, 2 or 3 (lines,
  tie lines, transformers, unpaired boundary lines; side 3 for three-winding transformers), bus voltage (buses of the
  bus view) or bus reactive power.
- **Variable type**, and the **Variables**: injection active or reactive power (generators, loads, batteries,
  converter stations, unpaired boundary lines; shunt compensators for reactive power), phase shift of a two-winding
  phase shifter or of the leg of a three-winding one, target voltage of a regulating equipment, HVDC line active power,
  shunt compensator susceptance, branch resistance, reactance or admittance, pilot point target voltage (secondary
  voltage control zones).
- **Variable sets**: checked, the variables are [variable sets](#variable-sets) rather than equipment; only for
  injection active power variables, with branch active power or current functions.
- **Computed for**: **Pre-contingency and every contingency**, **Pre-contingency only**, **Every contingency only**
  or **One contingency**, chosen among the contingencies of the enabled
  [contingency lists](contingencies.md).

Open Load Flow doesn't compute every pair of function and variable types, nor every type in DC: the form tells when
the matrix holds such a pair, for the AC or DC mode the sensitivity analysis would run in (see
[Sensitivity Analysis parameters](../parameters/sensitivity_analysis.md)). It also tells which functions and variables
are not found in the network or are not of the expected kind, which variable sets don't exist, and when the chosen
contingency is not among those of the enabled lists.

### Variable sets

A variable set spreads one MW over several injections, each by its weight: for instance a generation shift key
(GLSK), the injections of a zone weighted by their share. A factor matrix with **Variable sets** checked computes the
sensitivity to each of its variable sets.

- **Id** of the set, unique; renaming it renames it in the matrices using it.
- **Variables**: the injections and their **Weight** (double-click a weight to edit it).

The form tells which variables are not found in the network or are not injections.

## What you can do

### Create a factor matrix or a variable set

1. Click **Add**, then **Factor matrix** or **Variable set**.
2. Fill in its form. A field is applied when you press Enter or leave it.

### Pick the functions and variables

Click **Add…** under a list: a dialog lists the network's equipment the type applies to, without the ones already in
the list. Type in the search field to filter them by id, name or kind; checked elements stay checked while the search
changes. **Select shown** and **Unselect shown** check or uncheck all the listed ones. Click **OK** to add the checked
elements.

![Picking equipment](../../_generated/images/quickstart-sensi-pick-generators.png)

To remove elements, select them in the list (Ctrl or Shift for several) and click **Remove**.

### Remove a matrix or a variable set

Select it and click the **-** button of the toolbar.

### Import and export

The **Import** and **Export** menus of the toolbar read and write JSON files:

- **Factor matrices…**: the matrices, in the application's own format, which keeps how factors are grouped;
- **Factors (PowSyBl format)…**: the factors one by one, in the PowSyBl format other tools read; imported factors are
  grouped back into matrices named after the file;
- **Variable sets (PowSyBl format)…**: the variable sets; an imported set replaces the one of the same id.

An import or export that fails is reported by an error notification.
