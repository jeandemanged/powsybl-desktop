# Equipment details

An equipment detail view shows, and lets you edit, every attribute of one equipment as PowSyBl holds it, including
those the [Equipment tables](tables.md) don't show. Detail views open in a separate window, as a tab of the latest
window opened (or of a new window if none is open), from:

- the **Details** button (information icon) next to the name in every equipment table;
- the single line diagram: a click on a feeder that doesn't lead to another voltage level, on a busbar section or on a
  transformer drawn inside the diagram, or **Details** / **HVDC line details** in an equipment's right-click menu (see
  [Substations](substations.md));
- the **Details of the substation or voltage level** button of the diagram toolbars;
- the [Map](map.md): a click on a line, tie line or boundary line;
- a creation dialog, when **Open the details after creation** is checked;
- links inside another detail view (a voltage level's substation, a converter station's HVDC line...).

Opening the details of an equipment already shown brings its tab to the front.

Detail views exist for generators, batteries, loads, shunt and static var compensators, VSC and LCC converter
stations, HVDC lines, lines, tie lines, boundary lines, two- and three-winding transformers, busbar sections,
switches, substations, voltage levels, grounds, areas and overload management systems.

## Content

### Header and symbol

- The title gives the equipment kind and name, for instance **Generator GEN_1**.
- Links to the equipment's voltage levels (to the substation for a substation) open it in the
  [Substations](substations.md) view, on the tab listing this kind of equipment with its row selected.
- **Remove** removes the equipment, after a confirmation listing what is removed with it.
- For connectable equipment, a symbol drawn as in the single line diagrams, colored by base voltage (grey when
  disconnected). Each end shows its solved P, Q and I, the bus voltage and angle, and a checkbox to connect or
  disconnect it.

### Main tab

- **General**: **Id** and **Name** (each with a copy button) and **Fictitious**.
- Then the sections of the equipment kind, for instance:
  - **Connection**: the node (node/breaker topology) or the bus (bus/breaker topology), and the bus of the bus/branch
    view;
  - **Characteristics**, **Setpoints**, **Active Power**, **Sections**, **Susceptance**, voltage regulation with the
    regulated terminal;
  - a substation's **Country**, **TSO**, **Geographical Tags** and voltage levels; a voltage level's substation,
    topology kind, nominal voltage and voltage limits;
  - links to related equipment: a tie line's **Boundary Line 1** and **Boundary Line 2**, a converter station's
    **Other Converter Station**...
- Generators, batteries and VSC converter stations also show a P/Q capability chart: the reactive limits, the target
  operating point and the solved one.

![The details of a generator](../_generated/images/details-generator.png)

### Other tabs

Depending on the equipment kind:

- **Reactive Limits** (generators, batteries, VSC converter stations, boundary lines with generation): the **Kind**
  of limits, **Min/max** (**Min Q**, **Max Q**) or **Capability curve** (a table of points P, **Min Q**, **Max Q**).
  A **Capability shape** is shown but can't be edited here.
- **Tap Changers** (two- and three-winding transformers): for each winding, its **Ratio Tap Changer** and
  **Phase Tap Changer**: positions, **Regulation** and the table of **Steps**.
- **Limits** (lines, tie lines, transformers, boundary lines): for each side, the operational limits groups, and in
  each group the current, active power and apparent power limits: permanent limit and temporary limits (**Name**,
  **Acceptable Duration**, **Value**, **Fictitious**), with the side's present value. When enabled
  [limit scalings](limit_scalings.md) scale a selected group's limits in the base case, a read-only **Scaled Value**
  column shows the scaled limits next to the original ones.

  ![The Limits tab of a line](../_generated/images/details-line-limits.png)

- **Sections** (shunt compensators): the susceptance and conductance of each section count, with the reactive and
  active power drawn at nominal voltage.
- **Voltage Levels** and **Boundaries** (areas), **Trippings** (overload management systems).
- **Extensions**: one section per IIDM extension of the equipment. Most are editable; the others show their name and
  class with **Not editable here**.
- **Properties and Aliases**: the **Properties** (name and value) and **Aliases** tables.

## What you can do

### Edit values

- Type a new value and press Enter, or leave the field, to apply it; Escape restores the current value.
- A value refused by PowSyBl is shown again with an error message and a red flash; an applied one flashes green.
  Every other view is updated.
- Editing is disabled while a computation (load flow, security analysis, export...) runs on the network.
- Changing the **Id** keeps the view open on the renamed equipment. Other references to the old id, such as
  [Contingencies](contingencies.md), are not updated.

### Edit reactive limits

1. Choose the **Kind**: **Min/max** or **Capability curve**.
2. Enter **Min Q** and **Max Q**, or edit the curve points, adding and removing them with **Add point** and
   **Remove point**.
3. Click **Apply** to replace the reactive limits, or **Revert** to discard the changes.

### Edit tap changers

- **Add** creates a ratio or phase tap changer on a winding that has none, **Remove** deletes it.
- Edit **Load Tap Changing Capabilities**, **Low Tap Position**, the tap position, the regulation and the steps in
  place; **Add step** and **Remove step** (for the selected step) change the step list.

### Edit operational limits

- Add a group with **Group id** and **Add group**; remove one with **Remove group**.
- Check **Selected** to make a group the one in use for its side.
- **Add limits** adds current, active power or apparent power limits to a group; **Remove limits** removes them.
- Edit the limits in place: the permanent limit value, and each temporary limit's name, value, acceptable duration
  and fictitious flag; **Add temporary limit** and **Remove temporary limit** change the temporary limits. The permanent limit can't be removed alone: remove the limits instead.

These limits are the ones checked for the [Violations](violations.md) and by the security analysis.

### Edit extensions, properties and aliases

- **Add extension** lists the extensions that can be added to the equipment; **Remove** in a section removes it.
- Add a property or an alias by filling the fields under its table and clicking **Add**; select a row and click
  **Remove** to delete it.
