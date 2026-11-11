# Boundary Line

The details of a boundary line. Open it from the info button of the [Boundary Lines](../tables/boundary_lines.md) table, or from the other places listed in [Equipment details](index.md).

![The boundary line view](../../../_generated/images/detail-boundary-line.png)

## Header

A link to the voltage level, and a symbol going from the network bus to the boundary point, with the solved values of both ends.

## Main tab

### General

| Field | Editor | Meaning |
|---|---|---|
| **Pairing Key** | Text field | The key used to pair boundary lines into tie lines. |
| **Tie Line** | Link | To the [tie line's details](tie_line.md), `-` when not paired. |

### Connection

See [Connection](index.md#connection-section).

### Characteristics

| Field | Editor | Meaning |
|---|---|---|
| **R**, **X** | Text field | Series resistance and reactance (Ω). |
| **G**, **B** | Text field | Shunt conductance and susceptance (S). |

### Setpoints

| Field | Editor | Meaning |
|---|---|---|
| **P0** | Text field | Active power consumed at the boundary point (MW). |
| **Q0** | Text field | Reactive power consumed at the boundary point (MVar). |

### Generation

| Field | Editor | Meaning |
|---|---|---|
| **Min P**, **Max P**, **Target P** | Text field | MW. |
| **Target Q** | Text field | MVar. |
| **Voltage Regulation** | Check box | Whether the generation regulates voltage. |
| **Target V** | Text field | kV. |

## Other tabs

### Reactive Limits

Only for a boundary line with generation. See [Edit reactive limits](index.md#edit-reactive-limits).

### Limits

The operational limits groups of its side. See [Edit operational limits](index.md#edit-operational-limits).

### Extensions

The extensions that can be added: Operating Status, Line Position, Connectable Position, Reference Priorities, Observability, Measurements, Dynamic Model. CGMES Boundary Node is shown when present but can't be added.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
