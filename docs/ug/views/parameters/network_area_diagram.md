# Network Area Diagram

The **Network Area Diagram** tab sets how the network area diagrams of the [Substations](../diagrams/substations.md)
view are laid out and drawn. Choices show the PowSyBl names of the values. Any change redraws the open network area diagrams; number fields refuse values that aren't numbers. These are the parameters of PowSyBl's
[network area diagram](https://powsybl.readthedocs.io/projects/powsybl-diagram/latest/network_area_diagrams/index.html).

![The Network Area Diagram tab](../../_generated/images/parameters-network-area-diagram.png)

## Style & Labels

| Field | Editor | Description |
|---|---|---|
| **Style** | Choice: `TOPOLOGICAL`, `NOMINAL_VOLTAGE` | TOPOLOGICAL colors each electrical node distinctly, NOMINAL_VOLTAGE colors by nominal voltage. |
| **Bus Legend** | Check box | Display each bus voltage (and angle) in the voltage level legends. |
| **Voltage Level Details** | Check box | Display production and consumption totals in the voltage level legends. |
| **Substation In Legend** | Check box | Add the substation name to the voltage level legends. |
| **Display Ids** | Check box | Show ids instead of names in the voltage level legends. |
| **Double Arrows** | Check box | Display two values (and arrows) per edge info instead of one. |
| **Edge Info (External)** | Choice: `ACTIVE_POWER`, `REACTIVE_POWER`, `CURRENT`, `NAME`, `VALUE_PERMANENT_LIMIT_PERCENTAGE`, `EMPTY` | Value displayed at each edge end, on the external side. |
| **Edge Middle Info (Side 1)** | Same choices | Value displayed at the middle of edges, for side 1. |
| **Edge Middle Info (Side 2)** | Same choices | Value displayed at the middle of edges, for side 2 (with double arrows). |
| **Edge Info (Internal)** | Same choices | Value displayed at each edge end, on the internal side (with double arrows). |

## Diagram Sizing

| Field | Editor | Description |
|---|---|---|
| **Diagram Padding** | Numbers: Left, Top, Right, Bottom | Padding around the whole diagram. |
| **Size Constraint** | Choice: `NONE`, `FIXED_SCALE`, `FIXED_WIDTH`, `FIXED_HEIGHT`, and numbers Width, Height, Scale | How the diagram's size is constrained: none, a fixed width, a fixed height, or a fixed scale. Entering a width, height or scale selects the matching mode. |

## Text & Metadata

| Field | Editor | Description |
|---|---|---|
| **Insert Name/Description** | Check box | Insert a title/description element with the network's name. |
| **SVG Prefix** | Text field | Prefix prepended to generated SVG element ids, to combine several diagrams on one page. |

## SVG Output

| Field | Editor | Description |
|---|---|---|
| **CSS Location** | Choice: `INSERTED_IN_SVG`, `EXTERNAL_IMPORTED`, `EXTERNAL_NO_IMPORT` | Where the diagram's CSS styles are placed in the generated SVG. |

## Arrows

| Field | Editor | Description |
|---|---|---|
| **Arrow Shift** | Number | Distance of the power-flow arrow from the edge midpoint. |
| **Arrow Label Shift** | Number | Distance of the arrow's value label from the arrow. |
| **Arrow Path In** | Text field | SVG path drawn for an incoming power-flow arrow. |
| **Arrow Path Out** | Text field | SVG path drawn for an outgoing power-flow arrow. |
| **PST Arrow Head Size** | Number | Arrow head size for phase-shifting transformers. |
| **Double Arrow Shift Factor (Arrows)** | Number | Spacing factor between the two arrows of a double-circuit line. |
| **Double Arrow Shift Factor (Text)** | Number | Spacing factor between the two arrows' value labels. |

## Node & Shape Geometry

| Field | Editor | Description |
|---|---|---|
| **Converter Station Width** | Number | Width of an HVDC converter station symbol. |
| **Voltage Level Circle Radius** | Number | Radius of a voltage level's circle. |
| **Fictitious Voltage Level Circle Radius** | Number | Radius of a fictitious voltage level's circle. |
| **Transformer Circle Radius** | Number | Radius of a transformer symbol's circle. |
| **Node Hollow Width** | Number | Width of the hollow ring drawn around a node. |
| **Unknown Bus Node Extra Radius** | Number | Extra radius added to a node with an unknown bus count. |
| **Inter-Annulus Space** | Number | Spacing between the concentric rings of a multi-busbar node. |

## Edges

| Field | Editor | Description |
|---|---|---|
| **Edges Fork Length** | Number | Length of the fork where parallel edges split. |
| **Edges Fork Aperture** | Number | Angle, in degrees, of the fork where parallel edges split. |
| **Edge Start Shift** | Number | Distance an edge starts away from its node. |
| **Edge Info Along Edge** | Check box | Draw power-flow values along the edge instead of beside it. |
| **Edge Infos Included** | Check box | Include power-flow value labels on edges. |

## Loops

| Field | Editor | Description |
|---|---|---|
| **Loop Distance** | Number | Distance of a self-loop from its node. |
| **Loop Edges Aperture** | Number | Angle, in degrees, between the two ends of a self-loop. |
| **Loop Control Distance** | Number | Control-point distance shaping a self-loop's curve. |

## Injections

| Field | Editor | Description |
|---|---|---|
| **Injection Aperture** | Number | Angle, in degrees, between multiple injections at a node. |
| **Injection Edge Length** | Number | Length of an injection's connecting edge. |
| **Injection Circle Radius** | Number | Radius of an injection's (load/generator) circle. |

## Legends

| Field | Editor | Description |
|---|---|---|
| **Voltage Level Legends Included** | Check box | Show voltage level legends. |

## Localization & Units

| Field | Editor | Description |
|---|---|---|
| **Language Tag** | Text field | Locale used to format numbers in the diagram. |
| **Undefined Value Symbol** | Text field | Text shown in place of a value that can't be computed. |

## Debug

| Field | Editor | Description |
|---|---|---|
| **Highlight Graph** | Check box | Highlight the graph structure (debug). |

## Layout

| Field | Editor | Description |
|---|---|---|
| **Layout Algorithm** | Choice: `ATLAS2_FORCE`, `BASIC_FORCE`, `GEOGRAPHICAL` | ATLAS2_FORCE and BASIC_FORCE are force-directed layouts; GEOGRAPHICAL places voltage levels from substation positions, the missing ones by force layout. |
| **Text Nodes Force Layout** | Check box | Include text label nodes in the force-directed layout computation. |
| **Text Node Fixed Shift** | Numbers: X, Y | Fixed offset applied to a text label from its node when not force-laid-out. |
| **Timeout (Seconds)** | Number | Maximum time allowed for the layout algorithm to run. |
| **Text Node Edge Connection Y Shift** | Number | Vertical shift of the line connecting a text label to its node. |
| **Injections Added** | Check box | Include load/generator injections in the diagram. |
| **Edges To Invisible Nodes Displayed** | Check box | Draw the half of a branch whose other end is a voltage level outside the diagram. |
| **Scale Factor** | Number | Overall scale factor applied to the computed layout. |

## Force Layout Tuning

| Field | Editor | Description |
|---|---|---|
| **Atlas2 Max Steps** | Number | Maximum number of iterations of the Atlas2 algorithm (ATLAS2_FORCE). |
| **Atlas2 Repulsion Intensity** | Number | Repulsion between nodes; higher spreads nodes further apart (ATLAS2_FORCE). |
| **Atlas2 Edge Attraction Intensity** | Number | Attraction between connected nodes; higher emphasizes clusters and shrinks the diagram (ATLAS2_FORCE). |
| **Atlas2 Attract To Center** | Check box | Pull nodes toward the center, preventing unconnected parts from drifting away (ATLAS2_FORCE). |
| **Atlas2 Attract To Center Intensity** | Number | Strength of the pull toward the center; lower gives a less dense diagram (ATLAS2_FORCE). |
| **Atlas2 Speed Factor** | Number | Node speed relative to the global speed; lower is slower but more stable (ATLAS2_FORCE). |
| **Atlas2 Max Speed Factor** | Number | Maximum node speed relative to the global speed; lower reduces swinging but slows convergence (ATLAS2_FORCE). |
| **Atlas2 Swing Tolerance** | Number | How much swinging contributes to the global speed; must be strictly positive (ATLAS2_FORCE). |
| **Atlas2 Max Global Speed Increase** | Number | Maximum global speed increase ratio between two steps; higher converges faster but less smoothly (ATLAS2_FORCE). |
| **Atlas2 Barnes-Hut Theta** | Number | Barnes-Hut approximation strength; higher is faster but less accurate, 0 disables it (faster on small networks) (ATLAS2_FORCE). |
| **Atlas2 Quadtree Increment** | Number | Number of steps between Barnes-Hut quadtree recalculations; higher is faster but less accurate (ATLAS2_FORCE). |
| **Overlap Point Size Scale** | Number | Scale of the node size used to detect overlaps (ATLAS2_FORCE overlap prevention post-processing). |
| **Overlap Point Size Offset** | Number | Offset of the node size used to detect overlaps (ATLAS2_FORCE overlap prevention post-processing). |
| **Overlap Edge Attraction Intensity** | Number | Attraction between connected nodes while removing overlaps (ATLAS2_FORCE overlap prevention post-processing). |
| **Overlap Repulsion (No Overlap)** | Number | Repulsion between nodes that don't overlap (ATLAS2_FORCE overlap prevention post-processing). |
| **Overlap Repulsion (Overlapping)** | Number | Repulsion between overlapping nodes (ATLAS2_FORCE overlap prevention post-processing). |
| **Overlap Repulsion Zone Ratio** | Number | Size of the zone around a node in which repulsion applies (ATLAS2_FORCE overlap prevention post-processing). |
| **Overlap Attract To Center Intensity** | Number | Pull toward the center while removing overlaps (ATLAS2_FORCE overlap prevention post-processing). |
| **Basic Force Max Steps** | Number | Maximum number of iterations (BASIC_FORCE, which also uses the Layout category's timeout). |
