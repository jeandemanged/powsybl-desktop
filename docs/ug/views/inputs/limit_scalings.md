# Limit Scalings

The Limit Scalings view scales the operational limits of the selected network: for instance, check the lines against
90 % of their current limits, or allow 110 % of them after a contingency. Open it with **Inputs** > **Limit
Scalings** in the main toolbar.

A scaling multiplies the limits it applies to by a factor. It applies both to the limit violations of the network's
present state (the [Violations](../results/violations.md) view, the **Loading** column of the
[equipment tables](../equipment/tables/index.md), the [Map](../diagrams/map.md)) and to the [security analysis](../results/security_analysis_results.md).

## Content

The limit scalings belong to the network selected in the [Networks](../network/networks.md) view (to its root network, for a
subnetwork of a merged network). They are kept for the session only: export them to a file, or save them in a
[study](../../general/index.md#studies), to reuse them.

The view has three parts:

- top left, the ordered table of the scalings, with the columns **Enabled** (a checkbox), **#** (the order),
  **Limit Type**, **Factor (%)**, **Contingency Context**, **Equipment**, **Limits**, **Limits Groups**,
  **Monitoring Only** and **Open Load Flow Security Analysis** (see [below](#open-load-flow-support));
- bottom left, the form of the selected scaling;
- right, **Limits Scaled by the Selected Scaling**: the limit sets the selected scaling changes in the network, whatever
  its contingency context, with the columns **Equipment** (a link to its diagram), **Side**, **Limits Group**,
  **Permanent Limit**, **Scaled Permanent Limit** and **Scaled Temporary Limits** (how many of the temporary limits
  are scaled). A factor of 100 % still lists what the scaling matches.

![The Limit Scalings view](../../_generated/images/limit-scalings.png)

When several enabled scalings apply to the same limit, the last one in the table wins.

Unpaired boundary lines are not scaled: their violations are always checked against their own limits.

### Scaling form

- **Limit Type**: **Current**, **Active power** or **Apparent power** limits.
- **Factor (%)**: the percentage of the limits to check against, 0 or more. Below 100 % tightens the limits, above
  100 % relaxes them.
- **Monitoring Only**: the scaling applies only to the reported violations, not to the conditions for applying
  remedial actions. Both kinds scale the violations the application shows.
- **Contingency Context**: where the scaling applies: **All (base case and contingencies)**, **Base case only**,
  **Contingencies only**, or **Specific contingency**, chosen among the contingencies of the enabled
  [contingency lists](contingencies.md) or typed in. The violations of the network's present state are those of the
  base case.
- **Equipment**: the equipment the scaling applies to, matching any of the criteria of the list; all equipment when
  the list is empty. **Add** offers the kinds of criteria, the remove button removes the selected one, and the form of
  the selected criterion is shown below the list:
  - **Any Equipment**: **Country (at least one side)** and **Nominal Voltage (at least one side)**; at least one of
    them must be set. A new one matches any nominal voltage;
  - **Line**, **Tie Line**, **Two Windings Transformer**, **Three Windings Transformer**, **Boundary Line**: the
    countries and nominal voltages of their sides, as in the [contingency criterion lists](contingencies.md);
  - **Equipment Ids**: equipment ids, separated by spaces, commas or new lines.
- **Limits**: **All limits**, or the **Permanent limit** and the **Temporary limits**: **None**, **All temporary
  limits**, **Of duration** (in seconds), or **Of duration between** a **Min. Duration (s)** and a
  **Max. Duration (s)**, each with an **Inclusive** checkbox.
- **Limits Groups**: the operational limits groups the scaling applies to, among those of the network; **All selected
  groups** when none is checked.

A field is applied when you press Enter or leave it. An invalid entry (for instance a negative factor, or no limit at
all) is not applied: the scaling keeps its last valid value.

### Open Load Flow support

The Open Load Flow security analysis supports only some scalings. It skips the others, and ignores the countries of
**Any Equipment** criteria. Rather than letting this go unnoticed, the application shows it:

- the **Open Load Flow Security Analysis** column tells, for each scaling, **Applied**, **Partly applied**, **Not
  applied** or **Makes it fail**, the reasons being in its tooltip and at the top of the scaling's form;
- the toolbar tells how many enabled scalings are not, or only partly, applied.

Open Load Flow applies a scaling when it is on **Current** limits, in the **All** contingency context, not
**Monitoring Only**, with only **Any Equipment** criteria (each with a nominal voltage), at most two duration criteria
of different kinds, and no limits group selection. The violations of the network's present state, computed by the
application itself, apply every scaling.

## What you can do

### Create a scaling

1. Click **Add**: a scaling of the current limits at 100 % is added at the end of the table and selected.
2. Fill in its form. The limits it scales and the violations of the network update.

**Duplicate** adds a copy of the selected scaling right after it.

### Order the scalings

Select a scaling, then click **Move Up** or **Move Down**.

### Enable or disable scalings

Tick or untick the **Enabled** checkbox of a scaling, or click **Enable All** or **Disable All**. Only the enabled
scalings apply.

### Remove a scaling

Select it, then click the **Remove** button.

### Import and export scalings

- **Import**: reads a JSON file of PowSyBl limit scalings. When the network already has scalings, choose to
  **Append** the imported ones or **Replace** the existing ones. The imported scalings are enabled.
- **Export**: writes all the scalings of the network, enabled or not, in order, to a JSON file.

### See the effect of the scalings

- The limit violations of the network's present state are computed again as soon as the scalings change: the loading
  is then relative to the scaled permanent limit, so that a loading above 100 % is a violation. The **Violations of**
  dialog (the information button of a **Loading** cell) and the **Limits** tab of the
  [Equipment details](../equipment/details/index.md) show a **Scaled Value** column next to the limits that are scaled.
- A security analysis applies the scalings enabled when it is run: run it again after changing them. Its results tell
  when they are outdated, see [Security Analysis Results](../results/security_analysis_results.md).
