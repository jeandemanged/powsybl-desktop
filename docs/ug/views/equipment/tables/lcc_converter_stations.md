# LCC Converter Stations

The **LCC Converter Stations** table lists the line commutated converter stations of the selected network. Open it with **Tables** > **LCC Converter Stations**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The LCC Converter Stations table of the Four Substations Node Breaker sample network, after a load flow](../../../_generated/images/table-lcc-converter-stations.png)

## Columns

**Substation**, **Voltage Level**, **Name**, **Connected**, **CC** and **SC** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **HVDC Line** |  |  | A link to its [HVDC line](hvdc_lines.md). |
| **Loss Factor (%)** |  | Yes |  |
| **Power Factor** |  | Yes |  |
| **P** | Solved Values |  | Active power (MW). |
| **Q** | Solved Values |  | Reactive power (MVar). |
| **I** | Solved Values |  | Current (A). |

## Creation and removal

**New** > **LCC Converter Station…** creates a LCC converter station, in the selected row's voltage level by default. The **Remove** button removes the selected row's LCC converter station, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [LCC converter station details](../details/lcc_converter_station.md).
