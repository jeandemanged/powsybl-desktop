# Contingencies

The Contingencies view defines the contingency lists of the selected network: the losses of equipment simulated by a
[security analysis](../results/security_analysis_results.md) or a
[sensitivity analysis](../results/sensitivity_analysis_results.md). Open it with **Inputs** > **Contingencies** in the main toolbar.

## Content

The contingency lists belong to the network selected in the [Networks](../network/networks.md) view (to its root network, for a
subnetwork of a merged network). They are kept for the session only: export them to a file, or save them in a
[study](../../general/index.md#studies), to reuse them.

The view has three parts:

- top left, the contingency lists, each shown as its kind and its name ("(unnamed)" when it has none), with a
  checkbox telling whether it is enabled;
- bottom left, the form of the selected list;
- right, **Instantiated Contingencies** (with their count): the contingencies that the enabled lists create on the
  network, duplicates removed, with the columns **Name**, **Elements** (the equipment lost) and **Valid** (checked
  when every element exists in the network).

![A line criterion list and its contingencies](../../_generated/images/quickstart-sa-contingencies.png)

### Kinds of lists

| Kind | Creates |
|---|---|
| **Explicit List** | The contingencies typed in, each with its own elements. |
| **Line Criterion** | One contingency per line matching the criteria. |
| **Tie Line Criterion** | One contingency per tie line matching the criteria. |
| **Injection Criterion** | One contingency per injection of the chosen **Equipment Type** (generator, load, battery, shunt compensator, static var compensator, busbar section, boundary line, HVDC converter station, bus) matching the criteria. |
| **Two Windings Transformer Criterion** | One contingency per two-winding transformer matching the criteria. |
| **Three Windings Transformer Criterion** | One contingency per three-winding transformer matching the criteria. |
| **HVDC Line Criterion** | One contingency per HVDC line matching the criteria. |

### Explicit list form

- **Name** of the list.
- A table of contingencies (**Id**, **Elements**), with buttons to add and remove a contingency.
- The elements of the selected contingency (**Type**, **Id**), with **Add Element** and a remove button.

### Criterion list form

- **Name** of the list.
- Countries: **Country** for the equipment with a single country criterion (injections, transformers), or
  **Country (Side 1)** and **Country (Side 2)** for lines, tie lines and HVDC lines.
- Nominal voltages: **Nominal Voltage** for injections and tie lines, or **Nominal Voltage (Side 1)**,
  **(Side 2)** (and **(Side 3)** for three-winding transformers) for the others. Each is an interval with a
  **Low Bound (kV)** and a **High Bound (kV)**, each with an **Inclusive** checkbox.
- **Equipment Type**, for injection criterion lists only.
- **Property Criteria**: a table of conditions on equipment properties, with the columns **Key**, **Values**
  (comma-separated), **Equipment To Check** and **Side To Check**.
- **Regex**: a regular expression the equipment id must match.

An empty criterion doesn't filter: a new criterion list, with all criteria empty, selects every equipment of its kind.

## What you can do

### Create a list

1. Click **Add**, then the kind of list.
2. Fill in its form. A field is applied when you press Enter or leave it; the instantiated contingencies update.

An invalid entry (for instance a low bound above the high bound, or an invalid regular expression) is not applied:
the list keeps its last valid criteria.

### Enable or disable a list

Tick or untick the checkbox in front of the list. Only the enabled lists appear in **Instantiated Contingencies** and
are used by **Run** > **Security Analysis** and **Run** > **Sensitivity Analysis**.

### Remove a list

Select it, then click the **Remove** button.

### Import and export lists

- **Import**: reads a JSON file of PowSyBl contingency lists and adds its lists to the existing ones. A list of a
  kind this view cannot edit is still listed (under its PowSyBl type name) and used, but its form only says that
  editing is not supported.
- **Export**: writes all the lists of the network, enabled or not, to a JSON file.

### Run a security analysis

See [Security Analysis Results](../results/security_analysis_results.md) and the
[Security analysis tutorial](../../quickstart/security_analysis.md).
