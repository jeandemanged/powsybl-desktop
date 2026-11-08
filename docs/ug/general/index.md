# General usage

This section describes the application's window, menus and toolbar, and how to arrange views in several windows. The
views themselves are described in [Views and associated tasks](../views/index.md).

## The main window

The main window has, from top to bottom:

- the **menu bar**;
- the **main toolbar**, giving access to every view and computation;
- the **current view**, which fills the rest of the window. Most views have their own toolbar at their top.

![The main window](../_generated/images/quickstart-network-loaded.png)

Only one view at a time is shown in the main window. Choosing another view in the toolbar replaces it; to see several
views side by side, open them in [separate windows](#separate-windows-and-tabs).

## Menu bar

| Menu         | Items                                                                                              |
|--------------|----------------------------------------------------------------------------------------------------|
| **File**     | **Load Study…** and **Save Study…** load and save a [study](#studies). **Exit** closes the application, with all its windows. With networks loaded, it first asks for confirmation, as their unsaved changes are lost; closing the main window does the same. Turn this off with **Confirm before exiting** in the parameters' **User Interface** tab. |
| **Language** | **English** or **Français**. See [Language](#language).                                            |
| **Windows**  | One submenu per separate window. See [Separate windows and tabs](#separate-windows-and-tabs).     |
| **Help**     | **User's Guide (PDF)** and **User's Guide (HTML)** open this guide with the system's PDF viewer or web browser, **Memory** opens the memory window, **About** shows links to PowSyBl, the versions of the PowSyBl modules and the third-party licenses. |

## Main toolbar

![The main toolbar](../_generated/images/general-main-toolbar.png)

From left to right:

| Button                                                       | Action                                                                                                         |
|--------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| Back and forward arrows                                      | Go back to the previous view, or forward again. See [Navigation history](#navigation-history).                 |
| **Networks**                                                 | Open the [Networks](../views/networks.md) view.                                                                |
| **Substations**                                              | Open the [Substations](../views/substations.md) view.                                                          |
| **Map**                                                      | Open the [Map](../views/map.md).                                                                               |
| **Contingencies**                                            | Open the [Contingencies](../views/contingencies.md) view.                                                      |
| **Tables**                                                   | Open an [equipment table](../views/tables.md) (**Equipment** submenu), or a results table.                     |
| **Run**                                                      | Run a **Load Flow** or a **Security Analysis** on the selected network.                                        |
| **Parameters**                                               | Open the [Parameters](../views/parameters.md) view.                                                            |
| **Logs**                                                     | Open the [Logs](../views/logs.md).                                                                             |
| **Reports**                                                  | Open the [Reports](../views/reports.md).                                                                       |
| Speech bubble, at the right end                              | Show or hide the [notifications panel](#notifications).                                                        |

The views showing network content all show the network selected in the [Networks](../views/networks.md) view.

## Navigation history

Every view change is recorded, like in a web browser: opening a view from the toolbar, clicking an equipment link in a
table, selecting a voltage level in the substations tree... The back and forward arrows of the main toolbar move
through this history, restoring the view and its selection. The history keeps the last 30 steps. Steps referring to
something that no longer exists (a closed network, a removed equipment) are dropped.

## Separate windows and tabs

Any view can also be opened in a separate window, to keep it visible while working in the main window, for instance a
single-line diagram next to a table, or on a second screen.

### Opening a view in a separate window

- In the toolbar of a view, click the **Open in new window** button (the square with an arrow, at the right end):
  the view opens in a new window. The small arrow next to it offers **New window** or the existing windows, to open
  the view as a new tab of one of them instead.
- The **Map**, **Contingencies**, **Parameters**, **Logs** and **Reports** buttons of the main toolbar can also be
  dragged: release the mouse over a separate window to add the view to it as a tab, anywhere else to open it in a new
  window at that place.
- Some views open in a separate window by themselves: the equipment detail views, and the diagrams opened from the
  map, open as a tab of the most recently created window.

A view can be open only once: opening it again brings its window to the front and selects its tab.

A new window opens centered over the window it is opened from, on the same screen.

![A separate window with two equipment detail tabs](../_generated/images/general-separate-window.png)

### Windows

Separate windows are named "Window 1", "Window 2"... To rename one, double-click its name, left of its tabs, or use
**Windows** > *window name* > **Rename…** in the main window. The **Windows** menu also brings any window, or any of
its tabs, to the front.

Separate windows are independent of the main window: they don't stay in front of it, and the main window can be
brought in front of them. Closing the main window closes all of them, and closes the application.

### Tabs

Each separate window holds one or more views as tabs:

- drag a tab to reorder it, or to move it to another window's tabs;
- drop a tab outside of any window to give it a window of its own;
- close a tab with its close button. A window closes with its last tab.

A separate window has no menu, toolbar nor history of its own. Navigating from it, e.g. clicking an equipment link in
a table, opens the target in the main window and brings the main window to the front. The exception is a single-line
diagram: following a line to the voltage level at its other end stays in the same tab.

### Separate windows and networks

A separate window showing network content keeps showing the network that was selected when it was opened. Selecting
another network in the [Networks](../views/networks.md) view closes these tabs, after asking for confirmation. The map
is the exception: it always follows the selected network.

## Notifications

Every operation that takes time or may fail reports its outcome in a notification: imports, load flows, security
analyses, exports... A notification pops up at the bottom right of the main window, with:

- a colored status: running, succeeded, partially succeeded, failed or cancelled;
- the elapsed time of the operation;
- links to follow up, such as **View results** and **View report**, or the logs when something failed;
- while the operation runs, a **Cancel** button.

The popup hides by itself a few seconds after the operation ends. The speech bubble button at the right end of the main
toolbar opens the notifications panel, which keeps all of them; **Clear all** empties it.

![The notifications panel](../_generated/images/general-notifications.png)

## Studies

A study saves the application's work in a single `.psbx` file, to resume it later or hand it to someone else:
**File** > **Save Study…** writes it, **File** > **Load Study…** reads it back.

Saving first asks what the study holds, in two groups of check boxes, each with **All** and **None** buttons:

- **Data**: **Parameters**, **Contingencies** (the contingency lists, with whether each one is enabled), **Load Flow
  Results**, **Security Analysis Results**, **Reports** and **Logs**;
- **Networks**: the loaded networks to save, each with its subnetworks. A network is saved with its current values,
  including the ones a load flow computed.

Loading a study replaces the loaded networks by the study's. With networks loaded, it first asks for confirmation, and
offers **Save Study First…** to keep them in a study before. The parameters, reports and logs are replaced by the
study's when it holds them, and kept otherwise. The network selected when the study was saved is selected again.
Limit violations and the search index are computed again once the networks are loaded.

Saving and loading can take a while for large networks: a progress bar shows each step, and the application can't be
used meanwhile. A notification tells the outcome. A network busy with another operation, such as a running load flow,
can't be saved until it is over.

## Language

The **Language** menu switches the user interface between English and French. The change applies immediately: loaded
networks, open windows and tabs, history, notifications and reports are kept, only the views are rebuilt. The choice
is remembered for the next start.

## Memory

**Help** > **Memory** opens a window showing how much memory the application uses, out of its maximum. In the packaged
application, the maximum can be changed there; it applies at the next start. A larger network needs more memory.
