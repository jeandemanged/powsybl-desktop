# Reports

The Reports view shows the detailed reports of the operations run in the application: network imports and exports,
load flows, security analyses and sensitivity analyses. Open it with **Diagnostics** > **Reports** in the main toolbar, or with **View report** in
the notification of a finished operation, which opens that operation's report. Like the logs and parameters views, it
can also be opened in a separate window (see [General usage](../../general/index.md)).

## Content

- Left, the list of reports, oldest first, each with its date and time and its title: **Network Import**,
  **Network Export**, **Load Flow Report**, **Security Analysis Report** or **Sensitivity Analysis Report**. "No reports yet" is shown while the list
  is empty. When the view is opened, the latest report is selected.
- Right, the selected report as a tree of messages, fully expanded. A colored marker in front of each message gives
  its severity (TRACE, DEBUG, INFO, DETAIL, WARN or ERROR).
- The toolbar: **Clear all**, **Export**, and the severity filter buttons.

![The report of a security analysis](../../_generated/images/reports.png)

The messages come from PowSyBl (importers, exporters, Open Load Flow...) and their level of detail depends on it. The
reports are not saved between sessions, unless saved in a [study](../../general/index.md#studies).

## What you can do

### Read a report

1. Select a report in the left list.
2. Browse its tree; expand or collapse a node with its arrow.

### Filter by severity

Click one of the severity buttons of the toolbar:

- **Show all severities**
- **Show DEBUG and higher severities**
- **Show INFO and higher severities**
- **Show DETAIL, WARN and ERROR**
- **Show WARN and ERROR**
- **Show ERROR only**

A message below the chosen severity is hidden, unless one of its sub-messages is shown, so that the path to every
shown message stays visible. This is the same setting as the **Severity filter** of the **User Interface** tab of the
[Parameters](../parameters/index.md) view.

### Copy a message or a value

Right-click a message:

- **Copy message** copies its text to the clipboard;
- **Copy *key* = *value*** copies one of the values attached to the message (an equipment id, a number...).

### Export a report

1. Select a report in the left list.
2. Click **Export** and choose a file: a `.json` file gets the PowSyBl JSON format of the report, a `.txt` file the
   report as plain text.

### Clear the reports

Click **Clear all** to remove all the reports from the list.
