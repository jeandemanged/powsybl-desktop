# VSC Converter Station

The details of a voltage source converter station. Open it from the info button of the [VSC Converter Stations](../tables/vsc_converter_stations.md) table, or from the other places listed in [Equipment details](index.md).

![The vsc converter station view](../../../_generated/images/detail-vsc-converter-station.png)

## Header

A link to the voltage level, and the VSC converter station symbol with the solved values and the connection check box. On the right of the **Main** tab, the P/Q capability chart: the reactive limits, the target operating point and the solved one. The chart's active power range goes from minus to plus the HVDC line's **Max P**.

## Main tab

### General

Followed by the [converter station rows](index.md#converter-station-rows).

### Connection

See [Connection](index.md#connection-section).

### Local Targets

**Local Target V** and **Local Target Q**, see [Local Targets](index.md#local-targets-section).

### Voltage Regulation

See [Voltage Regulation](index.md#voltage-regulation-section). Modes **Voltage** and **Reactive Power**; **Reactive Power** needs a remote regulated terminal.

## Other tabs

### Reactive Limits

As for a generator, over the active power range of the chart; the target P is the HVDC line's setpoint. See [Edit reactive limits](index.md#edit-reactive-limits).

### Extensions

The extensions that can be added: Connectable Position, Reference Priorities, Observability, Measurements, Dynamic Model.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
