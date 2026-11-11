# Substations

The **Substations** table lists the substations of the selected network. Open it with **Tables** > **Substations**.

![The Substations table of the smallNodeBreakerWithVoltageLimits sample network](../../../_generated/images/table-substations.png)

## Columns

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Name** |  |  | The name, or the id, after an info button and a copy button, as a link to the substation in the [Substations](../../diagrams/substations.md) view. |
| **Country** |  | Yes, searchable choice | Country code and name, `-` when none. |
| **TSO** |  | Yes | Transmission system operator. An empty field or `-` removes it. |
| **Geographical Tags** |  | Add only | The tags, separated by `;`. The pencil button (**Edit geographical tags**) opens a dialog to add tags; tags can't be removed. |

The search box finds substations, and any other object of the network: a match selects its substation.

## Creation and removal

**New** > **Substation…** creates a substation, in the network. The **Remove** button removes the selected row's substation, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [substation details](../details/substation.md).
