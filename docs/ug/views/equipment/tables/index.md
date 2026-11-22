# Equipment tables

The equipment tables list every object of one kind in the selected network, one row per object, with its main
attributes and its solved values. Open one from the main toolbar: **Tables**, then the kind of object.
Most of them also appear as tabs of the [Substations](../../diagrams/substations.md) view, limited there to the selected substation or
voltage level.

## Content

![The Lines table after a load flow](../../../_generated/images/tables-lines.png)

### Rows and columns

- When a subnetwork is selected in the [Networks](../../network/networks.md) view, a table lists the objects of that subnetwork only.
- Rows are sorted by name (or id) at opening, except areas (by area type, then name) and voltage angle limits (by
  id). Click a column header to sort by that column, click again to reverse the
  order; missing values (shown as `-`) always come last.
- Numbers are shown with the decimal places set in the [Parameters](../../parameters/index.md) view.
- Values that change with a load flow (**P**, **Q**, **I**, voltages, angles, **Loading**, ...) are the values held by
  the network: they are empty (`-`) until a load flow has been run, and are updated after each run.
- Two-sided (or three-sided) objects, such as lines, HVDC lines and transformers, show one value per side, stacked in
  the cell (side 1 on top).

Common columns:

| Column | Meaning |
|---|---|
| **Country** | Country code of the object's substation, `-` when none. |
| **Substation**, **Voltage Level** | Where the object is connected, as links (one per side for multi-sided objects). |
| **Name** | The name, or the id when the object has no name, after an info button and a copy button. |
| **Connected** | Whether each terminal is connected: a check box per side. |
| **CC**, **SC** | Number of the connected component and of the synchronous component of the bus the terminal is connected to. |
| **P**, **Q**, **I** | Solved active power, reactive power and current at each terminal. |
| **Loading** | Loading of a branch, see [Loading column](#loading-column). |
| **Voltage Range** | Solved voltage of a bus within its voltage level limits, see [Voltage Range column](#voltage-range-column). |

### Column groups

Tables with many columns have check boxes above the table that show or hide a group of columns at once: **Parameters**
(electrical parameters such as R, X, G, B, hidden by default), **Solved Values** (shown by default), and, where
relevant, **Solved Values at Boundary**, **Ratio Tap Changers** and **Phase Tap Changers** (hidden by default).

![The column groups of the Lines table](../../../_generated/images/tables-column-groups.png)

Individual columns can also be shown or hidden from the **+** menu button at the right end of the column headers.

### Loading column

The **Loading** column (lines, tie lines, boundary lines, transformers) shows the loading of the object as a
percentage of its permanent limit, with a bar:

- the bar goes from 0 to 200 %, with a black mark at 100 % in the middle; an arrow at its right end means the
  loading is above 200 %;
- all green when the object has no limit violation;
- when it has a violation, orange up to 100 % and red beyond;
- for a violation of a temporary limit, its acceptable duration follows the percentage, in minutes (e.g. `120.0 % 10'`);
- hovering over a violated loading tells the limit type, the limit name, the side and the acceptable duration.

![The Loading column in different situations, for a 1000 A permanent limit](../../../_generated/images/tables-loading-bars.png)

When an object has several violations, the most severe one is shown (shortest acceptable duration first, then
highest percentage); otherwise, the highest loading over its sides and limits. The info button before the bar opens
the list of all the object's violations and limits, as in the [Violations](../../results/violations.md) view.

Loadings and violations are computed when the network is loaded, after each load flow and when the limit
scalings change, not after edits: see
[Violations](../../results/violations.md).

### Voltage Range column

The **Voltage Range** column (buses, busbar sections) shows the solved voltage on a scale from 80 % to 120 % of the
nominal voltage, with the nominal voltage in the middle, the allowed band between the voltage level's low and high
voltage limits, and the part beyond a limit in red. The percentage of the nominal voltage follows the bar; hovering
over it gives the voltage, the nominal voltage and the limits in kV. An arrow at an end of the scale means the voltage
is beyond it; on a side without limit, the allowed band goes up to the end of the scale.

![The Voltage Range column in different situations, for a 400 kV bus with limits at 380 and 420 kV](../../../_generated/images/tables-voltage-bars.png)

## What you can do

### Find an object

Type in the **Search** box at the top of the table: the best match is selected and scrolled to, and the arrows next
to the box go through the other matches. Searching for a substation or a voltage level selects the first row
connected to it. See [Search](../../network/search.md).

### Edit values

Columns whose cells show a field, check box or choice box can be edited in place; the others are read-only.

1. Click into the field and type the new value (a click selects the whole value, a second click places the cursor).
2. Press **Enter**, or leave the field, to apply it. **Escape** reverts what was typed; **Up** and **Down** apply it
   and move to the row above or below.

The value is checked by PowSyBl when applied:

- accepted, the cell flashes green and every other view (diagrams, details, other tables) shows the change;
- refused, the cell flashes red, the previous value comes back and a message tells why.

An empty field or `-` means no value, which is refused where PowSyBl requires one.

While a computation (load flow, security analysis, ...) runs on the network, all editors are disabled, as well as
creations and removals; they come back once it ends. The info and copy buttons stay available.

### Open an object's details or diagram

- Click the info button in the **Name** column, or right-click the row and choose **Details**, to open the
  object's [Equipment details](../details/index.md).
- Click a **Substation** or **Voltage Level** link to show it in the [Substations](../../diagrams/substations.md) view. Back in the
  history, the table comes back with the same row selected (see [General usage](../../../general/index.md)).

### Copy an id or a name

Click the copy button in the **Name** column: it copies the id, or, when the object has a name, offers **Copy ID** and
**Copy name**.

### Create or remove objects

- The **+** button of the table toolbar (**New**) lists what can be created from this table. The new object is placed
  in the selected row's voltage level by default.
- The delete button (**Remove**) removes the selected row's object, after a confirmation listing what is removed with
  it.
- Right-clicking a row offers the same actions, and the edit menus of the object (its **New** and **Topology**
  submenus, its removal).

### Open the table in another window

The **Open in new window** button of the table toolbar opens the table in a separate window, or, from its menu, as a
tab of an existing one. See [General usage](../../../general/index.md).

## Tables

One page per table lists its columns, what can be edited and what can be created from it. The screenshots are taken
on small networks of **Sample Networks**, in the import menu (see [Networks](../../network/networks.md)), after a load
flow.

```{toctree}
---
maxdepth: 1
---
substations
voltage_levels
busbar_sections
buses
switches
generators
batteries
loads
lines
tie_lines
boundary_lines
transformers
shunt_compensators
static_var_compensators
hvdc_lines
vsc_converter_stations
lcc_converter_stations
grounds
areas
overload_management_systems
voltage_angle_limits
```
