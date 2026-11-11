# Load

The details of a load. Open it from the info button of the [Loads](../tables/loads.md) table, or from the other places listed in [Equipment details](index.md).

![The load view](../../../_generated/images/detail-load.png)

## Header

A link to the voltage level, and the load symbol with the solved values and the connection check box.

## Main tab

### General

| Field | Editor | Meaning |
|---|---|---|
| **Type** | Choice box | `UNDEFINED`, `AUXILIARY` or `FICTITIOUS`. |
| **Load Model** | Read-only | The type of the load model, `-` when none. |

### Connection

See [Connection](index.md#connection-section).

### Setpoints

| Field | Editor | Meaning |
|---|---|---|
| **P0** | Text field | Active power consumption (MW). |
| **Q0** | Text field | Reactive power consumption (MVar). |

## Other tabs

### Extensions

The extensions that can be added: Load Detail, Asymmetrical Load, Connectable Position, Reference Priorities, Observability, Measurements, Dynamic Model.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
