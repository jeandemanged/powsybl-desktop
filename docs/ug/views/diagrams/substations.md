# Substations

The Substations view browses the selected network container by container: a tree of its substations and voltage
levels, and for the selected one its single line diagram, its network area diagram and tables of its equipment. Open
it with the **Substations** button of the main toolbar, or by clicking a substation or voltage level link in a table,
a detail view header or a diagram.

## Content

### Substation tree

The left part shows a toolbar, a [search box](../network/search.md) and the tree:

- top-level items group the substations, by default **By subnetwork** when the network has subnetworks and
  **By country** otherwise; placeholder groups such as **\<No country\>** or **\<No subnetwork\>** collect what has
  none;
- under each substation, its voltage levels with their nominal voltage in kV, highest first;
- voltage levels without substation are listed under a **\<No substation\>** item of their group.

### Diagram and equipment tabs

The right part shows tabs for the selected substation or voltage level:

- **Single Line Diagram** and **Network Area Diagram**, always present (**No selection** while nothing is selected);
- one tab per kind of equipment the container holds, only when it holds some: **Switches**, **Generators**,
  **Batteries**, **Shunt Compensators**, **Static Var Compensators**, **VSC Converter Stations**,
  **LCC Converter Stations**, **Loads**, **Lines**, **Transformers**, **Tie Lines**, **Boundary Lines**,
  **HVDC Lines**, **Grounds**, **Busbar Sections**, **Buses (Bus/Branch View)** and **Buses (Bus/Breaker View)**.

The equipment tabs are the [Equipment tables](../equipment/tables/index.md), restricted to the selected container and without their
search box. The selected tab is kept when another container is selected, if that container has it.

Diagrams show the values held by the network (for instance after a load flow) and are redrawn after every change of
the network. Their look is set in the **Single Line Diagram** and **Network Area Diagram** tabs of the
[Parameters](../parameters/index.md).

![The single line diagram of a voltage level](../../_generated/images/quickstart-loadflow-sld.png)

![The Lines tab of a voltage level](../../_generated/images/substations-lines-tab.png)

## What you can do

### Browse the tree

- Choose the grouping with the grouping menu button: **By country**, **By subnetwork**, or **By area** followed by an
  area type of the network (a substation appears under every area one of its voltage levels belongs to).
- **Expand all** and **Collapse all** expand or collapse every group and substation.
- Click a substation or voltage level to show its diagrams and equipment. Each selection is recorded in the
  navigation history (see [General usage](../../general/index.md)).
- Type in the search box to find any equipment: the tree selects its substation or voltage level, and its single line
  diagram briefly outlines the equipment.

### Create and remove network objects

- Click the **+** button of the tree toolbar (tooltip **Create or remove network objects**), or right-click a tree
  item. The menu offers, for the selected item:
  - **New**: what can be created in it (substations, voltage levels, equipment...);
  - **Topology**: topology modifications;
  - **Remove ...**: its removal, after a confirmation listing what is removed with it.
- These menus are disabled while a computation runs on the network.

### Use the single line diagram

- Click a switch to open or close it. The diagram is redrawn with the new topology.
- Click a feeder:
  - leading to another voltage level (line, transformer...): the diagram of that voltage level is shown, with the
    feeder you came through briefly outlined in green;
  - otherwise: the [Equipment details](../equipment/details/index.md) of the equipment open or, for an equipment
    without detail view, its row in the tab listing its kind.
- Click a busbar section, or a transformer drawn inside the diagram, to open its details.
- Right-click an equipment for **Details**, **HVDC line details** (converter stations), **Go to** the voltage level at
  the other end or **Show in table**, then what can be created on it and its removal.
- Right-click the background, or use the **+** button of the diagram toolbar, to create objects in the shown
  container.
- The information button (tooltip **Details of the substation or voltage level**) opens the details of the shown
  substation or voltage level.

### Use the network area diagram

- The network area diagram shows the voltage levels of the selected container and their neighbours.
- Set the **Depth** slider (1 to 6): how many steps of neighbouring voltage levels are included.
- Click a voltage level to center the diagram on it. Click a line drawn only halfway, leading to a voltage level
  beyond the depth, to center the diagram on that voltage level. Click another line, a transformer, an HVDC line or an
  injection to open its [Equipment details](../equipment/details/index.md). The pointer turns into a hand over what
  can be clicked.
- Once the diagram is centered on another voltage level, a green ring moves from the previous center to the new one,
  the view scrolling along when zoomed in, and outlines it for a few seconds. With the force layouts, the voltage
  levels shown before and after stay where they were, unless **Keep positions when centering** is unticked in the
  [User Interface](../parameters/user_interface.md) parameters.
- Right-click a voltage level (its circle or its label) for **Details**, **Open single line diagram**, **Center the
  diagram on** it and, in the main window, **Open in new window**, then what can be created in it and its removal.
- Right-click a line, transformer, HVDC line or, when drawn, an injection (or its values) for **Details**, **Show in
  table**, **Go to** the single line diagram of each voltage level it connects, **Center the diagram on** each of
  them, then its removal.
- Right-click the background to create objects in the shown container.
- Centering the diagram on a voltage level shows its network area diagram at the same depth: **Back** returns to the
  previous one. In a separate window, the window shows it instead.

![The network area diagram, depth 2, fitted to the screen](../../_generated/images/substations-nad.png)

### Zoom and export a diagram

Both diagram tabs share the same toolbar:

- **Zoom out**, the zoom percentage list (25 % to 200 %) and **Zoom in**; the mouse wheel zooms around the pointer;
- **Fit to screen** keeps the whole diagram fitted to the pane, also when it is resized;
- drag with the middle mouse button to pan;
- **Export diagram**: **Copy to clipboard (PNG)**, **Copy to clipboard (SVG)**, **Save to file (PNG)**,
  **Save to file (SVG)**; the file name proposed is the container name.

### Open in a new window

- The **Open in new window** button of each diagram toolbar and of each equipment tab opens it, for the selected
  container, in a separate window. Its arrow offers **New window** or a new tab in an existing window.
- In a separate window, a single line diagram feeder click leading to another voltage level shows that voltage level in
  the same window tab. See [General usage](../../general/index.md) for separate windows.
