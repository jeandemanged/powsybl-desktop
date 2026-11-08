# Load Flow Results

The Load Flow Results table shows, for each connected and synchronous component of the network, the outcome of the
last load flow. Open it from the main toolbar with **Tables** > **Load Flow Results**, or with **View results** in the
notification shown at the end of a load flow.

## Content

One row per (connected component, synchronous component) pair found among the buses of the bus/branch view, sorted by
component numbers. The rows are listed even before any load flow has been run; the result columns are then empty.

| Column | Meaning |
|---|---|
| **CC** | Number of the connected component. Component 0 is the main one, the largest. |
| **SC** | Number of the synchronous component. A connected component whose parts are linked only by HVDC lines has several synchronous components. |
| **Buses** | Number of buses of the bus/branch view in the component. |
| **Status** | Outcome of the load flow for the component, with a colored marker: **Converged** (green), **Max Iteration Reached** or **Failed** (red), **No Calculation** (gray). |
| **Status Text** | Additional text given by the load flow engine about the status. |
| **Iterations** | Number of iterations of the load flow on the component. |
| **Reference Bus** | Bus used as angle reference. |
| **Slack Buses** | Each slack bus followed by its remaining active power mismatch (MW). |
| **Distributed P** | Active power (MW) distributed over the component by the slack distribution. |

The bus ids of **Reference Bus** and **Slack Buses** are links when the bus exists in the bus/branch view.

![Load flow results](../_generated/images/quickstart-loadflow-components.png)

### Convergence verdict

At the end of a load flow, a notification gives an overall verdict, decided by the application from the components'
statuses (components with **No Calculation** are ignored):

- **Load flow completed**: every computed component converged.
- **Load flow partially converged**: every synchronous component of the main connected component (CC 0) converged,
  but some smaller component did not.
- **Load flow not converged**: at least one synchronous component of the main connected component did not converge,
  whatever the smaller ones did.

A load flow that ends without error is therefore not necessarily converged: check the verdict and this table. If the
computation itself fails, the notification says **Load flow failed** and offers **View logs** (see [Logs](logs.md)).

## What you can do

- Sort the rows by clicking a column header (all columns but **Status** and **Slack Buses**).
- Show or hide columns from the **+** menu button at the right end of the column headers.
- Click a bus link in **Reference Bus** or **Slack Buses** to show its voltage level in the
  [Substations](substations.md) view.
- Read the detailed log of the computation: click **View report** in the load flow notification, or open the
  [Reports](reports.md) view, where each run adds its report.
- Rerun the load flow with **Run** > **Load Flow** after changing the network or the [Parameters](parameters.md): the
  table is updated with the new results.
- Open the table in a separate window with **Open in new window** (see [General usage](../general/index.md)).

See the [Load flow tutorial](../quickstart/loadflow.md) for a complete example.
