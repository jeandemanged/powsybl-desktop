# CRAC

The CRAC view shows the CRAC of the selected network: the contingencies, the CNECs (critical network elements, with
the contingency and instant they are monitored at) and the remedial actions that the
[remedial action optimization](../results/rao_results.md) (RAO) works with. Open it with **Inputs** > **CRAC**. Like
the other inputs, the CRAC belongs to the whole network a RAO runs on, for a subnetwork its root network.

## Content

A toolbar with **Import**, **Export** and a button to remove the CRAC, then a line telling the CRAC's name, its time
stamp if it has one, and its instants (preventive, outage, then any auto and curative ones), or "No CRAC for this
network: import one".

On the left, four tabs:

- **Contingencies**: each contingency, by name.
- **CNECs**: each CNEC, with its state: **Preventive**, or a contingency and an instant.
- **Remedial Actions**: each remedial action, with its type (network action, PST, HVDC, injection,
  counter-trading).
- **Import Report**: what the import ignored or altered, and why.

An object whose network elements are no longer in the network (removed since the import) is marked with a warning
icon; its tooltip gives the missing elements. A RAO with such a CRAC is refused.

On the right, the details of the object selected on the left:

- a contingency: its elements;
- a CNEC: its type (flow, angle or voltage), network elements, state, role (optimized, monitored), thresholds by side
  with their unit, reliability margin and operator;
- a remedial action: its type, network elements, usage rules (when it is available: at an instant, after a
  contingency, when a CNEC is constrained, on a flow constraint in a country), operator, activation cost and speed.

## What you can do

### Import a CRAC

1. Click **Import** and choose a CRAC file: OpenRAO's JSON, a CSE CRAC, a CIM CRAC, network code (NC) profiles or a
   flow-based constraint document. Its format is recognized from its content; a file of no supported format is
   refused ("Not a CRAC file of a supported format").
2. The CRAC is read with the parameters of the [CRAC Creation](../parameters/crac_creation.md) tab of the
   Parameters view (default monitored line side, usage limits, the formats' own settings). For a CIM CRAC, NC
   profiles or a flow-based constraint document, a dialog first asks the **Time Stamp**, in UTC: these files can hold
   several situations, the one of this date and time is imported; for NC profiles, also their **Capacity Calculation
   Region**, pre-filled from the tab. **OK** is disabled while one is missing. The dialog offers the time stamp of the
   last import of the same format again.
3. A notification follows the import. Once done, the imported CRAC replaces the network's; **View import report** in
   the notification shows what was ignored or altered. When the CRAC can't be created (for instance a JSON CRAC whose
   CNECs are on lines without current limits), the notification says so and the CRAC in place is kept: its import
   report tells why.

The import reads the network: it is refused while a computation runs on it.

### Export the CRAC

**Export** writes the CRAC in OpenRAO's JSON format, which **Import** reads back.

### Remove the CRAC

The button after **Export** removes the CRAC of the network, after confirmation. The result of the last RAO is kept.
