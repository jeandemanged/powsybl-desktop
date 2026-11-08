# Parameters

The Parameters view gathers every setting of the application: computations, diagrams, network import and export,
and the user interface itself. Open it with the **Parameters** button of the main toolbar; like the logs and reports
views, it can also be opened in a separate window (see [General usage](../general/index.md)).

## Content

A toolbar with four buttons (described by their tooltips), then one tab per family of parameters:

| Tab | What it sets |
|---|---|
| **User Interface** | Settings of the application itself, in the categories **Decimal Places** (number of decimals of each displayed quantity: voltage, angle, power, current, percentage...), **Map** (**Basemap**, **Voltage ranges** shown, base case and contingency flow and voltage violations shown, **Show flows**), **Logs** (**Level filter**, **Sort order**), **Reports** (**Severity filter**), **Network Editing** (**Open the details after creation**) and **General** (**Confirm before exiting**). |
| **Network Import** | The parameters of each network import format. |
| **Network Export** | The parameters of each network export format. |
| **Single Line Diagram** | How single-line diagrams are laid out and drawn (layout algorithms, labels, feeder information, sizes, style...). |
| **Network Area Diagram** | How network-area diagrams are laid out and drawn (layout algorithm, edges, arrows, legends, sizes...). |
| **Load Flow** | The load flow parameters (PowSyBl Open Load Flow). |
| **Security Analysis** | The security analysis parameters, in the categories **General**, **Increased Violations**, **Modified Monitored Elements** and **Miscellaneous**. |

Every tab has a list of categories (or of formats, for **Network Import** and **Network Export**) on the left, and
the fields of the selected one on the right. Hover a field label to read what the parameter does. A format without
parameters shows "This format has no parameters."

![The User Interface tab](../_generated/images/parameters-user-interface.png)

![The Load Flow tab](../_generated/images/parameters-load-flow.png)

The **Logs** and **Reports** settings of the **User Interface** tab are the same as the filter buttons of the
[Logs](logs.md) and [Reports](reports.md) views: changing one side updates the other.

The load flow and security analysis parameters are those of PowSyBl. They are documented in the PowSyBl
documentation:

- [generic load flow parameters](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/simulation/loadflow/configuration.html);
- [Open Load Flow specific load flow parameters](https://powsybl.readthedocs.io/projects/powsybl-open-loadflow/en/latest/loadflow/parameters.html);
- [generic security analysis parameters](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/simulation/security/configuration.html);
- [Open Load Flow specific security analysis parameters](https://powsybl.readthedocs.io/projects/powsybl-open-loadflow/en/latest/security/parameters.html).

The security analysis always runs with the load flow parameters of the **Load Flow** tab.

## What you can do

### Edit parameters

Change a field: the new value applies at once, there is no **OK** or **Apply** button. A displayed single-line or
network-area diagram is redrawn with the new diagram parameters; the next load flow, security analysis, import or
export uses the new computation or format parameters.

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
- **Import** / **Export** buttons of the **Load Flow** and **Security Analysis** tabs: only that tab's parameters,
  in the PowSyBl JSON format of load flow or security analysis parameters. Importing security analysis parameters
  doesn't change the load flow parameters.

An import or export that fails is reported by an error notification.
