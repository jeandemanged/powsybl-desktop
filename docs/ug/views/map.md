# Map

The Map view draws the substations and lines of the selected network on a geographical map, from the substation and
line positions held by the network. Open it with the **Map** button of the main toolbar; drag that button out of the
main window to open the map in a separate window.

## Content

![The map after a security analysis](../_generated/images/quickstart-sa-map.png)

- The map shows the whole network the selected network belongs to, with all its subnetworks. Substations and lines
  are colored by base voltage, as in the single line diagrams. A line without route of its own is drawn straight
  between its substations.
- Hovering a substation or a line shows its name (or id) in a tooltip.
- Top left: the zoom buttons, the **Fit to network** button and a [search box](search.md).
- Below the search box, when some substations have no position or some lines no route: the
  **Positions: N substations without position, M lines without route** panel, collapsed by default.
- Top right: the basemap choice, **Offline** (country borders drawn by the application, no internet access needed)
  or **OpenStreetMap** (needs internet access; **No internet connection: OpenStreetMap tiles cannot be loaded** is
  shown otherwise), and the **Open in new window** button.
- Below it, the **Voltage ranges** box: one checkbox per base voltage range of the single line diagrams (a range in
  kV, the highest one reading "and above"), with **Check all** and **Check none**.
- Below it, the **Violations** box: **Base case flow violations**, **Base case voltage violations**,
  **Contingency flow violations** and **Contingency voltage violations** checkboxes.
- Below it, the **Show flows** checkbox, checked by default.

  ![The Show flows checkbox](../_generated/images/map-show-flows-box.png)

  ![The Voltage ranges box](../_generated/images/map-voltage-ranges-box.png)
  ![The Violations box](../_generated/images/map-violations-box.png)

- Limit violations are marked with warning bubbles: those of the network's present state (see
  [Violations](violations.md)), and those after each contingency of the last security analysis (see
  [Security Analysis Results](security_analysis_results.md); none for a contingency whose computation failed). A
  line's flow violations are marked above its middle, a transformer's flow violations and a voltage level's voltage
  violations above their substation. A bubble is red when the element has a shown violation of the present state,
  orange when all its shown violations follow contingencies.
- Hovering a marked element or its bubble lists its violations under its name: limit type, value and crossed limit,
  loading, side and acceptable duration for a flow violation; bus, voltage, limit and percentage of nominal voltage
  for a voltage violation. Present state violations come first, then **After** each contingency, at most 10 of them,
  the most severe first, followed by **... and N more after contingencies** when there are more.
- With **Show flows** checked, flows of the network's present state are drawn on lines, tie lines and boundary lines:
  - a line overloaded (with a flow violation, see [Violations](violations.md)) is highlighted in red all along, whatever
    the **Violations** checkboxes;
  - each end of a line with a computed flow gets chevrons near it, pointing the way the active power flows at that
    end: away from the end when it enters the line there, towards it when it leaves the line. A tie line or boundary
    line drawn up to its boundary point gets chevrons there too, from the flow at the boundary point. Their
    number follows the loading of that side of the line (at a boundary point, of its substation's side): one chevron
    under 25 %, two under 50 %, three under 75 %, four from 75 %, overloaded lines included, and a single one for a side
    without limits. The loading is the one of the
    [Violations](violations.md) view: relative to the permanent limit, of whichever current, active power or apparent
    power limit is the most loaded on that side.
  - a line gets no chevrons when its ends can't be told apart on the map: when one of its substations has no position,
    or both are at the same position. Lines too short on screen get no chevrons either; zoom in to see them.

    ![Flow chevrons around a substation after a load flow](../_generated/images/map-flows-close-up.png)

The map view (center and zoom) is remembered per network and restored when coming back to the map. In a separate
window, the map follows the network selected in the main window.

## What you can do

### Navigate

- Drag to pan; use the mouse wheel, double-click or the zoom buttons to zoom.
- **Fit to network** zooms to show the whole network.
- Type in the search box: the map zooms to the match and marks it (a line in its middle, any other equipment at its
  substation). Equipment drawn nowhere leaves the view unchanged.

### Filter and change the basemap

- Uncheck a voltage range to hide its substations and lines; hidden elements can't be hovered nor clicked.
- Uncheck a **Violations** checkbox to hide those violations' warning bubbles and tooltip lines. For instance, without
  the contingency voltage violations, only the overloads remain:

  ![The map without the contingency voltage violations](../_generated/images/quickstart-sa-map-no-contingency-voltage.png)

- Choose the basemap in the list at the top right.

The basemap, the hidden voltage ranges, the shown violations and **Show flows** are saved with the **User Interface** parameters (see
[Parameters](parameters.md)).

### Open diagrams and details

- Click a substation to open its single line diagram in a separate window.
- Click a line, tie line or boundary line to open its [Equipment details](details.md).
- Click a warning bubble to open what it marks, as a click on it does.
- Right-click a substation for **Single line diagram of** the substation or of each of its voltage levels.

### Create and remove network objects

- Right-click anywhere and choose **New substation here…**: the substation is created at that place, its country
  proposed from the basemap.
- Right-click a substation or a line for the same **New**, **Topology** and removal entries as in the
  [Substations](substations.md) view.

### Place substations without position

1. Expand the **Positions** panel. It lists the substations without position; type in **Filter** to narrow the list.
2. Select a substation, then click on the map where it goes. The next substation of the list is selected, so that
   several can be placed in a row.
3. Press Escape to stop placing.

**Straight routes for N lines without route…** gives every line without route a straight route between its
substations, after confirmation.

### Move a substation

1. Right-click it and choose **Move…**.
2. Drag the substation, or click where it goes. The ends of the routes at its position follow.
3. Click **Apply** (or press Enter) to keep the new position, **Cancel** (or Escape) to discard it.

**Remove position** removes the substation position.

### Edit a line route

1. Right-click a line and choose **Edit route…**, or **Define route…** for a line without route of its own. For a
   tie line, choose the side to edit.
2. Edit the points: click on the map to add points to a new route; drag a point to move it, drag a midpoint to add
   one, double-click a point (or right-click it and choose **Delete point**) to delete it. The ends snap to their
   substations.
3. **Straight route** replaces the points by a straight route.
4. Click **Apply** (or press Enter), or **Cancel** (or Escape).

The line context menu also offers **Straight route** and **Remove route** directly.

Position edits are disabled while a computation runs on the network.
