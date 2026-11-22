# CRAC Generator

The **CRAC Generator** tab sets how **Generate** in the [CRAC](../inputs/crac.md) view creates a CRAC from the
network itself, with no file: [PowSyBl OpenRAO](https://powsybl.readthedocs.io/projects/openrao/en/stable/)'s CRAC
generator creates a contingency for the loss of each branch, a flow CNEC on each branch at each state, and range
actions on the PSTs and, as set here, redispatching, counter-trading and balancing. The default monitored line side
and the usage limits come from the [CRAC Creation](crac_creation.md) tab.

![The CRAC Generator tab](../../_generated/images/parameters-crac-generator.png)

The tables are edited as in the CRAC Creation tab. A line at the top of the **Instants and Contingencies** category
tells what the generator would refuse (a post-contingency instant without acceptable duration, a table naming an
unknown instant...); **Generate** is refused with the same reasons.

## Instants and Contingencies

| Field | Editor | Description |
|---|---|---|
| **Auto Instants**, **Curative Instants** | Table: **Instant** | The instants of the CRAC, in order, after `preventive` and `outage` (reserved names). One curative instant, `curative`, by default. |
| **Aligned Range Actions** | Table: **Range Actions (separated by +)** | Groups of PST range actions always set to the same tap. |
| **Contingency Countries** | Country choice | The countries of the branches whose loss is a contingency; all when none is chosen. |
| **Contingency Min Voltage (kV)**, **Contingency Max Voltage (kV)** | Number | The nominal voltage range of these branches; blank for no limit. |
| **Branches Without Contingency** | Table: **Branch** | Branches whose loss is not a contingency. |

## CNECs

| Field | Editor | Description |
|---|---|---|
| **CNEC Countries** | Country choice | The countries of the branches with CNECs; all when none is chosen. |
| **Optimized Min Voltage (kV)**, **Optimized Max Voltage (kV)** | Number | The nominal voltage range of the branches with optimized CNECs; blank for no limit. |
| **Monitored Elements (MNEC)** | Check box | Also create monitored, not optimized, CNECs on the branches in the range below. |
| **Monitored Min Voltage (kV)**, **Monitored Max Voltage (kV)** | Number | The nominal voltage range of the monitored branches; blank for no limit. |
| **Thresholds** | Choice | **From the operational limits**: each instant's CNECs use the limit of its acceptable duration below; **From the permanent limits**. |
| **Limit Multipliers per Instant** | Table: **Instant**, **Multiplier** | The limits of each instant's CNECs are multiplied by this; 1 for an instant not listed. |
| **Limit Multipliers per Nominal Voltage** | Table: **Instant**, **Nominal Voltage (kV)**, **Multiplier** | Multipliers for the branches of a nominal voltage, overriding the instant's. |
| **Acceptable Durations per Instant** | Table: **Instant**, **Acceptable Duration (s, blank: permanent)** | For each post-contingency instant (`outage`, auto and curative ones), the acceptable duration of the operational limit its CNECs use; required for all of them with thresholds from the operational limits. 60 s at `outage` and the permanent limit at `curative` by default. |
| **Acceptable Durations per Nominal Voltage** | Table: **Instant**, **Nominal Voltage (kV)**, **Acceptable Duration** | Durations for the branches of a nominal voltage, overriding the instant's. |

## Range Actions

| Field | Editor | Description |
|---|---|---|
| **PST Range Actions** | Check box | Create a range action on each PST at each instant. |
| **PST Countries** | Country choice | The countries of these PSTs; all when none is chosen. |
| **Excluded PSTs** | Table: **PST** | PSTs without range action. |
| **PST Tap Ranges per Instant** | Table: **Instant**, **Min Tap**, **Max Tap**, **Range Type** | The taps the PST range actions may use at an instant (`ABSOLUTE`, `RELATIVE_TO_INITIAL_NETWORK`, `RELATIVE_TO_PREVIOUS_INSTANT`); their whole range for an instant not listed. |
| **Redispatching** | Check box | Create a redispatching range action on each generator at each instant. Off by default: OpenRAO's default, a range action on every generator, gives huge CRACs on real networks. |
| **Redispatching Countries** | Country choice | The countries of these generators; all when none is chosen. |
| **Excluded Generators** | Table: **Generator** | Generators without redispatching. |
| **Redispatching Ranges and Costs** | Table: **Instant**, **Min (MW)**, **Max (MW)**, **Activation Cost**, **Up Variation Cost**, **Down Variation Cost** | The range and costs of the redispatching at an instant; the generators' physical range and no cost for an instant not listed. |
| **Injection Combinations** | Table: **Combination**, **Injections (separated by +)** | Redispatching range actions on groups of injections, keyed by their initial production or consumption; an injection in one group at most. Created even with **Redispatching** off. |
| **Counter-Trading** | Table: **Country (code)**, **Instant**, **Min (MW)**, **Max (MW)**, costs | A counter-trading range action with a country (its code, such as `FR`) at an instant, with a proportional GLSK. None by default. |
| **Balancing** | Table: **Instant**, **Min (MW)**, **Max (MW)**, costs | A balancing range action at an instant, on the injections in neither redispatching nor counter-trading. None by default. |

They are documented in the
[OpenRAO documentation](https://powsybl.readthedocs.io/projects/openrao/en/stable/input-data/crac/crac-generator.html).
The tab has no import or export: OpenRAO has no file format for these settings. They are saved with all the
parameters.
