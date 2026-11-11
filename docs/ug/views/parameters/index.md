# Parameters

The Parameters view gathers every setting of the application: computations, diagrams, network import and export,
and the user interface itself. Open it with the **Parameters** button of the main toolbar, or at a given tab from the button's small arrow; like the logs and reports
views, it can also be opened in a separate window (see [General usage](../../general/index.md)).

## Content

A toolbar with four buttons (described by their tooltips), then one tab per family of parameters, each described in
its own page:

| Tab | What it sets |
|---|---|
| [User Interface](user_interface.md) | The application itself: decimal places, map, logs, reports, network editing and exit confirmation. |
| [Network Import](network_import.md) | The parameters of each network import format. |
| [Network Export](network_export.md) | The parameters of each network export format. |
| [Single Line Diagram](single_line_diagram.md) | How single line diagrams are laid out and drawn. |
| [Network Area Diagram](network_area_diagram.md) | How network area diagrams are laid out and drawn. |
| [Load Flow](load_flow.md) | The load flow parameters (PowSyBl Open Load Flow). |
| [Security Analysis](security_analysis.md) | The security analysis parameters. |
| [Sensitivity Analysis](sensitivity_analysis.md) | The sensitivity analysis parameters, and whether it runs in AC or DC. |

Every tab has a list of categories (or of formats, for **Network Import** and **Network Export**) on the left, and
the fields of the selected one on the right. Hover a field label to read what the parameter does.

```{toctree}
---
maxdepth: 1
hidden:
---
user_interface
network_import
network_export
single_line_diagram
network_area_diagram
load_flow
security_analysis
sensitivity_analysis
```

## What you can do

### Edit parameters

Change a field: the new value applies at once, there is no **OK** or **Apply** button. A displayed single-line or
network-area diagram is redrawn with the new diagram parameters; the next load flow, security or sensitivity
analysis, import or export uses the new computation or format parameters.

### Save the parameters for the next sessions

Edits are kept only for the current session until saved. Click the first button of the toolbar, whose tooltip
**Save all parameters to ..., restored on next startup** gives the file location. The parameters are saved in
`config.json`, in:

- Windows: `%APPDATA%\powsybl-desktop`
- macOS: `~/Library/Application Support/powsybl-desktop`
- Linux: `~/.powsybl-desktop`

and restored at the next start. The button is disabled when there is nothing new to save. If the saved file
cannot be read at startup, the defaults are used, the unreadable file is set aside (renamed with a `.bak`
extension) and a notification tells it.

### Reset to the default values

Click **Reset all parameters to their default values** and confirm. Save afterwards to make the reset permanent.

### Exchange parameters with files

- **Export all parameters to a JSON file** / **Import all parameters from a JSON file** (toolbar): all the
  parameters of every tab at once.
- **Import** / **Export** buttons of the [Load Flow](load_flow.md#import-and-export),
  [Security Analysis](security_analysis.md#import-and-export) and
  [Sensitivity Analysis](sensitivity_analysis.md#import-and-export) tabs: only that tab's parameters.

An import or export that fails is reported by an error notification.
