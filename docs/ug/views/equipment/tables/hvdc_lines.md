# HVDC Lines

The **HVDC Lines** table lists the HVDC lines of the selected network, each joining two converter stations. Open it with **Tables** > **HVDC Lines**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The HVDC Lines table of the Metrix Tutorial sample network, after a load flow](../../../_generated/images/table-hvdc-lines.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC**, one per side (the converter stations' terminals), are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Converter Station** |  |  | Links to the two converter stations, in the [VSC](vsc_converter_stations.md) or [LCC](lcc_converter_stations.md) converter stations table. |
| **Converters Mode** |  | Yes, choice | **Side 1 rectifier, side 2 inverter** or **Side 1 inverter, side 2 rectifier**. |
| **Active Power Setpoint** |  | Yes | MW. |
| **Max P** |  | Yes | MW. |
| **R** | Parameters | Yes | Resistance (Ω). |
| **Nominal Voltage** | Parameters | Yes | DC nominal voltage (kV). |
| **P** | Solved Values |  | Active power at each side (MW). |
| **Q** | Solved Values |  | Reactive power at each side (MVar). |
| **I** | Solved Values |  | Current at each side (A). |

The **Parameters** group is hidden by default. Only the **Name** column can be sorted.

## Creation and removal

**New** > **HVDC Line…** joins two converter stations that have no HVDC line yet. The **Remove** button removes the selected row's HVDC line, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [HVDC line details](../details/hvdc_line.md).
