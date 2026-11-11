# VSC Converter Stations

The **VSC Converter Stations** table lists the voltage source converter stations of the selected network. Open it with **Tables** > **Equipment** > **VSC Converter Stations**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The VSC Converter Stations table of the Metrix Tutorial sample network, after a load flow](../../../_generated/images/table-vsc-converter-stations.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **HVDC Line** |  |  | A link to its [HVDC line](hvdc_lines.md). |
| **Loss Factor (%)** |  | Yes |  |
| **Regulating** |  | Yes | Whether it regulates. |
| **Regulation Mode** |  | Yes, choice | **Voltage** or **Reactive Power**. |
| **Target V** |  | Yes | Voltage target (kV). |
| **Target Q** |  | Yes | Reactive power target (MVar). |
| **P** | Solved Values |  | Active power (MW). |
| **Q** | Solved Values |  | Reactive power (MVar). |
| **I** | Solved Values |  | Current (A). |
| **Regulated Bus Voltage** | Solved Values |  | Voltage of the regulated bus (kV). |

## Creation and removal

**New** > **VSC Converter Station…** creates a VSC converter station, in the selected row's voltage level by default. The **Remove** button removes the selected row's VSC converter station, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [VSC converter station details](../details/vsc_converter_station.md).
