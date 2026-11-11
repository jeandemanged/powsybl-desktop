# Two-Winding Transformer

The details of a two-winding transformer, titled **Transformer** followed by its name. Open it from the info button of the [Transformers](../tables/transformers.md) table, or from the other places listed in [Equipment details](index.md).

![The two-winding transformer view](../../../_generated/images/detail-two-winding-transformer.png)

## Header

Links to the voltage levels of both sides, and the transformer symbol (a phase-shifting transformer when it has a phase tap changer), with the solved values and a connection check box at each end.

## Main tab

### Characteristics

| Field | Editor | Meaning |
|---|---|---|
| **R**, **X** | Text field | Series resistance and reactance (Ω). |
| **G**, **B** | Text field | Magnetizing conductance and susceptance (S). |
| **Rated U1**, **Rated U2** | Text field | Rated voltages (kV). |
| **Rated S** | Text field | Rated apparent power (MVA). |

### Side 1, Side 2

The [Connection](index.md#connection-section) rows of each side.

## Other tabs

### Tap Changers

A **Ratio Tap Changer** and a **Phase Tap Changer** pane. Without tap changer, a pane shows **None** and an **Add** button. With one:

- **Load Tap Changing Capabilities** (check box), **Low Tap Position** (text field), **High Tap Position** (read-only), **Tap Position** (text field) and **Solved Tap** (read-only);
- **Regulation**: for a ratio tap changer, as in [Voltage Regulation](index.md#voltage-regulation-section) (modes **Voltage** and **Reactive Power**); for a phase tap changer, **Regulating**, **Regulation Mode**, **Regulation Value**, **Target Deadband** and **Regulated Terminal**;
- **Steps**: a table of the steps, **Tap Position**, **Alpha (°)** (phase tap changer only), **Rho**, **R (%)**, **X (%)**, **G (%)** and **B (%)**, all editable but the position;
- **Add step** appends a copy of the highest step, **Remove step** removes the selected one, **Remove** deletes the tap changer.

![The Tap Changers tab](../../../_generated/images/detail-two-winding-transformer-tap-changers.png)

### Limits

The operational limits groups of each side. See [Edit operational limits](index.md#edit-operational-limits).

### Extensions

The extensions that can be added: Phase Angle Clock, To Be Estimated, Fortescue, CGMES Tap Changers, Discrete Measurements, Connectable Position, Measurements, Operating Status, Observability, Dynamic Model.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
