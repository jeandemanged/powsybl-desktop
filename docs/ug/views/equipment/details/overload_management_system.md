# Overload Management System

The details of an overload management system. Open it from the info button of the [Overload Management Systems](../tables/overload_management_systems.md) table, or from the other places listed in [Equipment details](index.md).

![The overload management system view](../../../_generated/images/detail-overload-management-system.png)

## Header

A link to the single line diagram of its substation. No symbol.

## Main tab

### Characteristics

| Field | Editor | Meaning |
|---|---|---|
| **Enabled** | Check box | Whether it is active. |
| **Monitored element** | Read-only | Id of the monitored element. |
| **Monitored side** | Read-only | `ONE`, `TWO` or `THREE`. |

## Other tabs

### Trippings

One row per tripping: **Key**, **Name** (editable), **Tripping type**, **Element to operate** (a switch, or a branch or transformer with its side), **Current limit** (editable) and **Opens (else closes)** (check box). Trippings can't be added to or removed from an existing system.

![The Trippings tab](../../../_generated/images/detail-overload-management-system-trippings.png)

### Extensions

The extensions that can be added: Dynamic Model.

### Properties and Aliases

See [Equipment details](index.md#edit-extensions-properties-and-aliases).
