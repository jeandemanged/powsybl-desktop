# Overload Management Systems

The **Overload Management Systems** table lists the overload management systems of the selected network: automatons that monitor the current in an element and trip switches or branches when it exceeds a threshold. Open it with **Tables** > **Overload Management Systems**.

![The Overload Management Systems table, with a system added to the smallNodeBreakerWithVoltageLimits sample network](../../../_generated/images/table-overload-management-systems.png)

## Columns

| Column | Group | Editable | Meaning |
|---|---|---|---|
| **Name** |  |  | The name, or the id, after an info button and a copy button. |
| **Substation** |  |  | The substation holding it. |
| **Enabled** |  | Yes | Whether it is active. |
| **Monitored element** |  |  | Id of the monitored element. |
| **Monitored side** |  |  | `ONE`, `TWO` or `THREE`. |
| **Trippings** |  |  | Number of trippings, detailed in the [Equipment details](../details/index.md). |

The table has no search box.

## Creation and removal

**New** > **Overload Management System…** creates one in the selected row's substation, or in the network's first substation. The **Remove** button removes the selected row's overload management system, after a confirmation listing what is removed with it.

## Details

The info button of the **Name** column opens the [overload management system details](../details/overload_management_system.md).
