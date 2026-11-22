# Single Line Diagram

The **Single Line Diagram** tab sets how the single line diagrams of the [Substations](../diagrams/substations.md) view
are laid out and drawn. Choices show the PowSyBl names of the values. Any change redraws the open single line diagrams; number fields refuse values that aren't numbers. These are the parameters of PowSyBl's
[single line diagram](https://powsybl.readthedocs.io/projects/powsybl-diagram/latest/single_line_diagrams/index.html).

![The Single Line Diagram tab](../../_generated/images/parameters-single-line-diagram.png)

## Layout Algorithms

| Field | Editor | Description |
|---|---|---|
| **Substation Layout** | Choice: `HORIZONTAL`, `VERTICAL` | Arrange the voltage levels of a substation diagram horizontally or vertically. |
| **Voltage Level Layout** | Choice: `SMART`, `POSITION_FROM_EXTENSIONS`, `POSITION_BY_CLUSTERING` | Algorithm placing busbars and feeders: SMART uses position extensions when present (or for bus-breaker topology) and clustering otherwise; the other two force one of these. |
| **Stack Feeders** | Check box | Draw feeders connected to several busbars with stacked disconnectors (ignored by SMART). |
| **Remove Unnecessary Fictitious Nodes** | Check box | Remove fictitious nodes that aren't needed to draw the topology (ignored by SMART). |
| **Substitute Singular Fictitious Nodes** | Check box | Replace fictitious nodes with a single connection by feeder nodes (ignored by SMART). |
| **Internal Transformers As Equipment** | Check box | Draw two-winding transformers internal to a voltage level as equipment nodes (ignored by SMART). |
| **Handle Shunts** | Check box | Detect and draw shunt cells (ignored by SMART). |
| **Fail On Unhandled Pattern** | Check box | Fail the rendering instead of approximating when a topology pattern isn't supported (ignored by SMART). |

## Components & Style

| Field | Editor | Description |
|---|---|---|
| **Component Library** | Choice: `CONVERGENCE`, `FLAT_DESIGN` | Set of symbols used to draw equipment. |
| **Style** | Choice: `TOPOLOGICAL`, `BUS_HIGHLIGHT`, `NOMINAL_VOLTAGE` | TOPOLOGICAL colors each electrical node distinctly, BUS_HIGHLIGHT also highlights a bus on hover, NOMINAL_VOLTAGE colors by nominal voltage. |

## Identification

| Field | Editor | Description |
|---|---|---|
| **SVG Prefix** | Text field | Prefix prepended to generated SVG element ids, to combine several diagrams on one page. |

## Localization & Units

| Field | Editor | Description |
|---|---|---|
| **Language Tag** | Text field | Locale used to format numbers in the diagram. |
| **Undefined Value Symbol** | Text field | Text shown in place of a value that can't be computed. |
| **Active Power Unit** | Text field | Unit label shown next to active power values. |
| **Reactive Power Unit** | Text field | Unit label shown next to reactive power values. |
| **Current Unit** | Text field | Unit label shown next to current values. |

## Bus & Feeder Info

| Field | Editor | Description |
|---|---|---|
| **Bus Info Margin** | Number | Margin around bus information text. |
| **Feeder Infos Intra-Margin** | Number | Margin between adjacent feeder info elements. |
| **Feeder Infos Outer Margin** | Number | Margin outside the feeder info elements. |
| **Feeder Info Symmetry** | Check box | Mirror the feeder info layout symmetrically. |
| **Display Current** | Check box | Show the current (I) feeder info alongside active and reactive power. |
| **Display Arrow For Current** | Check box | Draw a direction arrow next to the current feeder info. |
| **Display Permanent Limit Percentage** | Check box | Show the loading of branches and three-winding transformers as a percentage of their permanent current limit. |
| **Display Absolute Values** | Check box | Show active and reactive power as absolute values, the arrows giving the flow direction. |
| **Three Windings Transformer Feeder Info Mode** | Choice: `ONLY_INSIDE_VOLTAGE_LEVEL`, `ONLY_OUTSIDE_VOLTAGE_LEVEL`, `FULL_3WT` | Which feeders of a three windings transformer carry the power and current arrows: the inner one, the outer ones, or all three. |
| **Buses Legend** | Check box | Show a legend for bus colors. |

## Labels

| Field | Editor | Description |
|---|---|---|
| **Use Name** | Check box | Label equipment by name instead of id. |
| **Angle Label Shift** | Number | Shift applied to angle value labels. |
| **Label Centered** | Check box | Center labels on their equipment. |
| **Label Diagonal** | Check box | Draw labels diagonally. |
| **Bus Label Diagonal** | Check box | Draw bus labels diagonally. |

## Interaction

| Field | Editor | Description |
|---|---|---|
| **Tooltips Enabled** | Check box | Show tooltips when hovering diagram elements. |

## SVG Output

| Field | Editor | Description |
|---|---|---|
| **CSS Location** | Choice: `INSERTED_IN_SVG`, `EXTERNAL_IMPORTED`, `EXTERNAL_NO_IMPORT` | Where the diagram's CSS styles are placed in the generated SVG. |
| **Avoid SVG Components Duplication** | Check box | Reuse shared component shapes instead of duplicating them. |

## Drawing Style

| Field | Editor | Description |
|---|---|---|
| **Draw Straight Wires** | Check box | Draw connections as straight lines instead of orthogonal. |
| **Show Grid** | Check box | Overlay a debug grid on the diagram. |
| **Show Internal Nodes** | Check box | Show internal topology nodes (debug). |
| **Display Equipment Nodes Label** | Check box | Show equipment node ids (debug). |
| **Display Connectivity Nodes Id** | Check box | Show connectivity node ids (debug). |
| **Unify Voltage Level Colors** | Check box | Use a single color for all voltage levels. |

## Bus Spacing

| Field | Editor | Description |
|---|---|---|
| **Vertical Space Bus** | Number | Vertical spacing between busbars. |
| **Horizontal Bus Padding** | Number | Horizontal padding around busbars. |

## Cell Dimensions

| Field | Editor | Description |
|---|---|---|
| **Cell Width** | Number | Width of a cell. |
| **Extern Cell Height** | Number | Height of an extern cell. |
| **Intern Cell Height** | Number | Height of an intern cell. |
| **Stack Height** | Number | Height of a stacked cell. |
| **Min Extern Cell Height** | Number | Minimum height of an extern cell. |
| **Adapt Cell Height To Content** | Check box | Adapt cell height to its content. |

## Snake Lines

| Field | Editor | Description |
|---|---|---|
| **Horizontal Snake Line Padding** | Number | Horizontal padding for connections between voltage levels. |
| **Vertical Snake Line Padding** | Number | Vertical padding for connections between voltage levels. |
| **Space For Feeder Infos** | Number | Space reserved for feeder info labels. |
| **Zone Layout Snake Line Padding** | Number | Padding for connections in a multi-substation zone layout. |

## Component Sizing

| Field | Editor | Description |
|---|---|---|
| **Max Component Height** | Number | Maximum height of a component's icon. |
| **Min Space Between Components** | Number | Minimum spacing between components. |

## Padding

| Field | Editor | Description |
|---|---|---|
| **Voltage Level Padding** | Numbers: Left, Top, Right, Bottom | Padding around a voltage level's diagram. |
| **Diagram Padding** | Numbers: Left, Top, Right, Bottom | Padding around the whole diagram. |

## Alignment & Topology

| Field | Editor | Description |
|---|---|---|
| **Busbars Alignment** | Choice: `FIRST`, `LAST`, `MIDDLE`, `NONE` | How busbars of different lengths are aligned. |
| **Display disconnectors on busbar** | Check box | Draw disconnectors directly on busbars instead of in the feeder cells. |
| **Remove Fictitious Switch Nodes** | Check box | Remove fictitious switch nodes from the diagram. |
| **Display Tee Points In Voltage Levels** | Check box | Display tee points within voltage levels. |
