# Transformers

The **Transformers** table lists the two- and three-winding transformers of the selected network, in one table. Open it with **Tables** > **Transformers**. It is also a tab of the [Substations](../../diagrams/substations.md) view, for the selected substation or voltage level.

![The Transformers table of the microGridBaseCaseAssembled sample network, after a load flow, with its three-winding transformer](../../../_generated/images/table-transformers.png)

## Columns

**Substation**, **Name**, **Voltage Level**, **Connected**, **CC** and **SC** are the [common columns](index.md#rows-and-columns).

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Rated U** |  | Yes | Rated voltage of each side (kV). |
| **Rated S** |  | Yes | Rated apparent power (MVA): one for a two-winding transformer, one per leg for a three-winding one. |
| **R**, **X**, **G**, **B** | Parameters | Yes | Series resistance and reactance (Ω), magnetizing conductance and susceptance (S). |
| **Ratio Tap Changer** | Ratio Tap Changers | Yes, stepper | Tap position, between the low and high taps. Empty without ratio tap changer. |
| **Regulating** | Ratio Tap Changers | Yes | Whether the ratio tap changer regulates. |
| **Regulation Mode** | Ratio Tap Changers | Yes, choice | **Voltage** or **Reactive Power**. |
| **Regulation Value** | Ratio Tap Changers | Yes | Target, in kV or MVar depending on the mode. |
| **Target Deadband** | Ratio Tap Changers | Yes | In the unit of the regulation value. |
| **Solved Tap** | Ratio Tap Changers |  | Tap position computed by the load flow. |
| **Phase Tap Changer** | Phase Tap Changers | Yes, stepper | Tap position. Empty without phase tap changer. |
| **Regulating** | Phase Tap Changers | Yes | Whether the phase tap changer regulates. |
| **Regulation Mode** | Phase Tap Changers | Yes, choice | **Current Limiter** or **Active Power Control**. |
| **Regulation Value** | Phase Tap Changers | Yes | Target, in A or MW depending on the mode. |
| **Target Deadband** | Phase Tap Changers | Yes | In the unit of the regulation value. |
| **Solved Tap** | Phase Tap Changers |  | Tap position computed by the load flow. |
| **P** | Solved Values |  | Active power at each side (MW). |
| **Q** | Solved Values |  | Reactive power at each side (MVar). |
| **I** | Solved Values |  | Current at each side (A). |
| **Loading** | Solved Values |  | Loading against the limits, see [Loading column](index.md#loading-column). |

Side-dependent cells hold one value per side: two for a two-winding transformer, three for a three-winding one. Parameters, **Rated S** and tap changers hold one value for a two-winding transformer, one per leg for a three-winding one. The regulation columns are empty for a tap changer without load tap changing capabilities.

The **Parameters**, **Ratio Tap Changers** and **Phase Tap Changers** groups are hidden by default. The tap changers are fully described, steps included, in the [Equipment details](../details/index.md). Only the **Substation**, **Name** and **Loading** columns can be sorted. In the Substations view tab, the **Substation** and **Voltage Level** links stay, as another side can be in another voltage level.

## Creation and removal

**New** > **Two Windings Transformer…** or **Three Windings Transformer…** creates a transformer in the selected row's substation (in the Substations view, in the selected substation). The **Remove** button removes the selected row's transformer, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [two-winding transformer details](../details/two_winding_transformer.md) or [three-winding transformer details](../details/three_winding_transformer.md).
