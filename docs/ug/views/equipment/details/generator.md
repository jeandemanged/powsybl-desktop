# Generator

The details of a generator. Open it from the info button of the [Generators](../tables/generators.md) table, or from the other places listed in [Equipment details](index.md).

![The generator view](../../../_generated/images/detail-generator.png)

## Header

A link to the voltage level, and the generator symbol with the solved values and the connection check box. On the right of the **Main** tab, the P/Q capability chart: the reactive limits, the target operating point and the solved one.

## Main tab

### General

| Field | Editor | Meaning |
|---|---|---|
| **Energy Source** | Choice box | `HYDRO`, `NUCLEAR`, `WIND`, `THERMAL`, `SOLAR` or `OTHER`. |
| **Condenser** | Read-only | Whether the generator is a synchronous condenser. |
| **Rated S** | Text field | Rated apparent power (MVA). |

### Connection

See [Connection](index.md#connection-section).

### Active Power

| Field | Editor | Meaning |
|---|---|---|
| **Min P** | Text field | MW. |
| **Max P** | Text field | MW. |
| **Target P** | Text field | Active power target (MW). |

### Local Targets

**Local Target V** and **Local Target Q**, see [Local Targets](index.md#local-targets-section).

### Voltage Regulation

See [Voltage Regulation](index.md#voltage-regulation-section). Modes **Voltage** and **Reactive Power**; **Reactive Power** needs a remote regulated terminal.

## Other tabs

### Reactive Limits

The **Kind** of reactive limits, **Min/max** or **Capability curve**, and their values. See [Edit reactive limits](index.md#edit-reactive-limits).

![The Reactive Limits tab](../../../_generated/images/detail-generator-reactive-limits.png)

### Extensions

The extensions that can be added: Active Power Control, Coordinated Reactive Control, Short Circuit, Startup, ENTSO-E Category, Fortescue, Manual Frequency Restoration Reserve, Connectable Position, Reference Priorities, Observability, Measurements, Dynamic Model.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
