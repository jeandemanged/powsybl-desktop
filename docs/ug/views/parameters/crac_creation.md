# CRAC Creation

The **CRAC Creation** tab sets how a CRAC file is read when it is imported in the [CRAC](../inputs/crac.md) view: the
parameters of [PowSyBl OpenRAO](https://powsybl.readthedocs.io/projects/openrao/en/stable/)'s CRAC importers, by
format. The time stamp of a CIM, NC or flow-based constraint file, which holds several situations, is not set here:
it is asked at the import, as is the capacity calculation region of NC profiles if none is set here.

![The CRAC Creation tab](../../_generated/images/parameters-crac-creation.png)

Lists, maps and nested settings are tables: double-click a cell to edit it (Enter or clicking elsewhere confirms,
Escape cancels), **+** adds a row at the end, **-** removes the selected rows. A blank cell is an absent value. When a
row is invalid (a required cell blank, a number that isn't one), the reason is shown below the table, naming the row
and the column, and the parameters keep their previous value until it is fixed.

## All Formats

| Field | Editor | Description |
|---|---|---|
| **Default Monitored Line Side** | Choice: `Both sides`, `Side 1`, `Side 2` | The side of the lines the CNECs monitor when the CRAC file doesn't tell. |
| **Usage Limits per Instant** | Table: **Instant**, **Max Remedial Actions** | The maximum number of remedial actions at each instant (`preventive`, `curative`...); blank for no limit. |
| **Usage Limits per Instant and TSO** | Table: **Instant**, **TSO**, **Max Topological Actions**, **Max PSTs**, **Max Remedial Actions**, **Max Elementary Actions** | The maximum numbers of each TSO at each instant; blank for no limit. A limit on elementary actions needs the PSTs approximated as integers in the [RAO parameters](remedial_action_optimization.md). |

## CSE CRAC

| Field | Editor | Description |
|---|---|---|
| **Aligned Range Actions** | Table: **Range Actions (separated by +)** | Groups of range actions always set to the same set point, such as `pst1 + pst2`. |
| **Busbar Changes** | Table: **Remedial Action**, **Switch to Open**, **Switch to Close** | For each busbar change remedial action, the pairs of switches it opens and closes, a row per pair. |

## CIM CRAC

| Field | Editor | Description |
|---|---|---|
| **Aligned Range Actions** | Table: **Range Actions (separated by +)** | As for CSE. |
| **Time Series** | Table: **mRID** | The time series to import; all when empty. |
| **Range Action Speeds** | Table: **Range Action**, **Speed** | The speed of each automatic range action, the smallest acting first. |
| **Voltage Monitored Elements** | Table: **Network Element** | Voltage CNECs: the voltage levels whose voltage is monitored. |
| **Voltage Monitored States** | Table: **Instant**, **Contingencies (comma-separated)** | Voltage CNECs: the instants the voltage is monitored at, after the contingencies given (none for `preventive`). |
| **Voltage Thresholds** | Table: **Instant**, **Nominal Voltage (kV)**, **Min (kV)**, **Max (kV)** | Voltage CNECs: the thresholds at each monitored instant, by nominal voltage. |

The three voltage tables together define the voltage CNECs: all empty for none, otherwise they need monitored
elements and thresholds for each monitored state.

## NC Profiles

| Field | Editor | Description |
|---|---|---|
| **Capacity Calculation Region** | Choice | The capacity calculation region of the profiles, required to import them; **None** to choose it at each import. |
| **Curative Instants** | Table: **Instant**, **Application Time (s)** | The curative instants to create, with the time after the contingency at which their remedial actions apply. |
| **TSOs Not Using PATL in Final State** | Table: **TSO** | SWE region: the TSOs whose final state CNECs don't use the permanent limits. |

## Flow-Based Constraint Document

| Field | Editor | Description |
|---|---|---|
| **ICS Cost Up**, **ICS Cost Down** | Number | Activation costs of the upward and downward ICS (intraday congestion management) remedial actions. |
| **Internal HVDC Converters** | Table: **HVDC EIC**, **HVDC Code**, **Pole**, **Converter Node**, **Converter Station** | The converters of the internal HVDCs' poles, a row per converter. |
| **Internal HVDC Lines** | Table: **HVDC EIC**, **HVDC Code**, **Pole**, **Line**, **From**, **To** | The lines of the internal HVDCs' poles, a row per line. |

The rows of both HVDC tables sharing an HVDC (EIC and code) and a pole make one pole of one internal HVDC.

They are documented in the
[OpenRAO documentation](https://powsybl.readthedocs.io/projects/openrao/en/stable/input-data/crac/creation-parameters.html).

## Import and export

The **Import** and **Export** buttons of the tab read and write these parameters only, in OpenRAO's CRAC creation
parameters JSON format.
