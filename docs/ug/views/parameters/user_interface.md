# User Interface

The **User Interface** tab sets the application itself: how numbers are displayed, what the map shows, how network
area diagrams behave, how logs and reports are filtered, and a few behaviors.

![The User Interface tab](../../_generated/images/parameters-user-interface.png)

## Decimal Places

The number of decimal places of each displayed quantity, from 0 to 10, in tables, detail views, diagrams and
results. Fields: **Voltage (kV)**, **Angle (°)** (phase shifts included), **Power (MW, MVAr, MVA)**, **Power factor**,
**Current (A)**, **Percentage (%)** (such as voltage violations and tap step deviations), **Resistance, reactance (Ω)**,
**Conductance, susceptance (S)**, **Ratio (rho)**, **Limit scaling**, **Sensitivity** (sensitivity analysis values) and
**Coordinate** (latitudes and longitudes).

## Map

The defaults of the [Map](../diagrams/map.md) boxes, kept in sync with them.

| Field | Editor | Description |
|---|---|---|
| **Basemap** | Choice: `Offline`, `OpenStreetMap` | The background of the map. |
| **Voltage ranges** | Check boxes | The voltage ranges drawn on the map, one check box per range, with **Check all** and **Check none**. |
| **Base case flow violations** | Check box | Show the base case flow violations. |
| **Base case voltage violations** | Check box | Show the base case voltage violations. |
| **Contingency flow violations** | Check box | Show the flow violations after contingencies. |
| **Contingency voltage violations** | Check box | Show the voltage violations after contingencies. |
| **Show flows** | Check box | Draw flow chevrons on the lines and highlight the overloaded ones. |

## Network Area Diagram

| Field | Editor | Description |
|---|---|---|
| **Keep positions when centering** | Check box | When a [network area diagram](../diagrams/substations.md#use-the-network-area-diagram) is centered on another voltage level, the voltage levels shown before and after stay where they were; only the others are laid out. Force layouts only. |

## Logs

The filter and the order of the [Logs](../diagnostics/logs.md) view, kept in sync with its buttons.

| Field | Editor | Description |
|---|---|---|
| **Level filter** | Choice: `Show INFO, WARN and ERROR logs`, `Show WARN and ERROR logs`, `Show ERROR logs only` |  |
| **Sort order** | Choice: `Newest logs on top`, `Newest logs at the bottom` |  |

## Reports

The filter of the [Reports](../diagnostics/reports.md) view, kept in sync with its button.

| Field | Editor | Description |
|---|---|---|
| **Severity filter** | Choice: `Show all severities`, `Show DEBUG and higher severities`, `Show INFO and higher severities`, `Show DETAIL, WARN and ERROR`, `Show WARN and ERROR`, `Show ERROR only` |  |

## Network Editing

| Field | Editor | Description |
|---|---|---|
| **Open the details after creation** | Check box | The default of the check box of the creation dialogs: open the [details](../equipment/details/index.md) of a created equipment. |

## General

| Field | Editor | Description |
|---|---|---|
| **Confirm before exiting** | Check box | Ask for confirmation before closing the application with networks loaded. |
