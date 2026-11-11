# HVDC Line

The details of an HVDC line. Open it from the info button of the [HVDC Lines](../tables/hvdc_lines.md) table, or from the other places listed in [Equipment details](index.md).

![The hvdc line view](../../../_generated/images/detail-hvdc-line.png)

## Header

Links to the voltage levels of both converter stations, and a symbol drawing the two converter stations and the DC line between them.

## Main tab

### Characteristics

| Field | Editor | Meaning |
|---|---|---|
| **R** | Text field | Resistance (Ω). |
| **Nominal Voltage** | Text field | DC nominal voltage (kV). |
| **Max P** | Text field | Maximum active power (MW). |

### Setpoints

| Field | Editor | Meaning |
|---|---|---|
| **Active Power Setpoint** | Text field | MW. |
| **Converters Mode** | Choice box | `SIDE_1_RECTIFIER_SIDE_2_INVERTER` or `SIDE_1_INVERTER_SIDE_2_RECTIFIER`. |

### Side 1, Side 2

For each side, a **Converter Station** link to its details, then the [Connection](index.md#connection-section) rows of the converter station.

## Other tabs

### Extensions

The extensions that can be added: Angle Droop Active Power Control, Operator Active Power Range, Operating Status, Dynamic Model.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
