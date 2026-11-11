# Logs

The Logs view shows the messages logged by the application and the PowSyBl libraries while it runs: progress
information, warnings and errors. Open it with the **Logs** button of the main toolbar, or with **View logs** in the
notification of a failed operation. Like the reports and parameters views, it can also be opened in a separate window
(see [General usage](../../general/index.md)).

## Content

A table with one row per message:

- **Date/Time**: when the message was logged, with the time zone offset;
- **Severity**: INFO, WARN or ERROR;
- **Message**: the message text.

WARN rows and ERROR rows are highlighted in their own colors. A message spanning several lines (such as a table of
load flow parameters) keeps one table line per message line. When a message is too long for its
column, hover it to read it in full in a tooltip.

![The logs of a security analysis](../../_generated/images/logs.png)

New messages appear as they are logged, and the table scrolls to the newest one. Only the latest 20,000 messages are
kept. The logs are not saved between sessions, unless saved in a [study](../../general/index.md#studies).

For the detailed outcome of an import, export, load flow or security analysis, see the [Reports](reports.md) view
instead.

## What you can do

### Filter by severity

Click one of the severity buttons of the toolbar:

- **Show INFO, WARN and ERROR logs**
- **Show WARN and ERROR logs**
- **Show ERROR logs only**

### Choose the order

- **Newest logs on top**
- **Newest logs at the bottom**

Both choices are the same settings as the **Level filter** and **Sort order** of the **User Interface** tab of the
[Parameters](../parameters/index.md) view.

### Copy a message

Right-click a row, then **Copy message** to copy its text to the clipboard.

### Clear the logs

Click **Clear all** to empty the table.
