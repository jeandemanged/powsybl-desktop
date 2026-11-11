# Three-Winding Transformer

The details of a three-winding transformer, titled **Transformer** followed by its name. Open it from the info button of the [Transformers](../tables/transformers.md) table, or from the other places listed in [Equipment details](index.md).

![The three-winding transformer view](../../../_generated/images/detail-three-winding-transformer.png)

## Header

Links to the voltage levels of the legs, and the three-winding transformer symbol, side 1 on top.

## Main tab

### General

| Field | Editor | Meaning |
|---|---|---|
| **Rated U0 (Star Bus)** | Text field | Rated voltage of the star bus (kV). |

### Leg 1, Leg 2, Leg 3

| Field | Editor | Meaning |
|---|---|---|
| **R**, **X** | Text field | Series resistance and reactance of the leg (Ω). |
| **G**, **B** | Text field | Magnetizing conductance and susceptance (S). |
| **Rated U** | Text field | kV. |
| **Rated S** | Text field | MVA. |
| Connection rows |  | See [Connection](index.md#connection-section). |

## Other tabs

### Tap Changers

For each leg, the same panes as for a [two-winding transformer](two_winding_transformer.md#tap-changers).

### Limits

The operational limits groups of each side. See [Edit operational limits](index.md#edit-operational-limits).

### Extensions

The extensions that can be added: Phase Angle Clock, To Be Estimated, Fortescue, Connectable Position, Measurements, Operating Status, Discrete Measurements, Dynamic Model. CGMES Tap Changers is shown when present but can't be added.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
