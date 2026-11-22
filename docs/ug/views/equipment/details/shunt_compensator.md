# Shunt Compensator

The details of a shunt compensator. Open it from the info button of the [Shunt Compensators](../tables/shunt_compensators.md) table, or from the other places listed in [Equipment details](index.md).

![The shunt compensator view](../../../_generated/images/detail-shunt-compensator.png)

## Header

A link to the voltage level, and the capacitor (or inductor, for a reactor) symbol with the solved values and the connection check box.

## Main tab

### Connection

See [Connection](index.md#connection-section).

### Sections

| Field | Editor | Meaning |
|---|---|---|
| **Type** | Read-only | **Capacitor** or **Reactor**; `?` for a non-linear model mixing positive and negative susceptances. |
| **Model Type** | Read-only | **Linear** or **Non-Linear**. |
| **Section Count** | Text field | Number of sections in service. |
| **Maximum Section Count** | Text field (linear model) | Read-only for a non-linear model. |
| **B per Section**, **G per Section** | Text field | Linear model only: susceptance and conductance of one section (S). |
| **Current B**, **Current G** | Read-only | Susceptance and conductance of the sections in service (S). |

### Local Targets

**Local Target V**, see [Local Targets](index.md#local-targets-section).

### Voltage Regulation

See [Voltage Regulation](index.md#voltage-regulation-section). Mode **Voltage** only.

### Solved Values

| Field | Editor | Meaning |
|---|---|---|
| **Solved Section Count** | Read-only | Section count computed by the load flow. |

## Other tabs

### Sections

One row per section count, from 1 to the maximum: **Section**, **B** and **G** (S, editable for a non-linear model), and the **Q at nominal V** and **P at nominal V** drawn with that many sections.

![The Sections tab](../../../_generated/images/detail-shunt-compensator-sections.png)

### Extensions

The extensions that can be added: Discrete Measurements, Connectable Position, Reference Priorities, Observability, Measurements, Dynamic Model.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
