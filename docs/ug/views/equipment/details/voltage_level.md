# Voltage Level

The details of a voltage level. Open it from the info button of the [Voltage Levels](../tables/voltage_levels.md) table, or from the other places listed in [Equipment details](index.md).

![The voltage level view](../../../_generated/images/detail-voltage-level.png)

## Header

A link to the voltage level's single line diagram. No symbol.

## Main tab

### General

| Field | Editor | Meaning |
|---|---|---|
| **Substation** | Link | To its [substation's details](substation.md), `-` when none. |
| **Topology Kind** | Read-only | `NODE_BREAKER` or `BUS_BREAKER`. |
| **Nominal Voltage** | Text field | kV. |
| **Low Voltage Limit**, **High Voltage Limit** | Text field | kV, checked for the voltage [violations](../../analysis/violations.md). |

## Other tabs

### Extensions

The extensions that can be added: Short Circuit, Slack Terminal, Observability Areas, Dynamic Model.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
