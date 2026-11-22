# CRAC

The CRAC view shows the CRAC of the selected network: the contingencies, the CNECs (critical network elements, with
the contingency and instant they are monitored at) and the remedial actions that the
[remedial action optimization](../results/rao_results.md) (RAO) works with. Open it with **Inputs** > **CRAC**. Like
the other inputs, the CRAC belongs to the whole network a RAO runs on, for a subnetwork its root network.

## Content

A toolbar with **New**, **Import**, **Generate**, **Export**, an **Add** menu, a button to remove the selected contingency and
a button to remove the CRAC, then a line telling the CRAC's name, its time stamp if it has one, and its instants
(preventive, outage, then any auto and curative ones), or "No CRAC for this network: create, import or generate one".

On the left, five tabs:

- **Contingencies**: each contingency, by name. One with no elements, or with elements no longer in the network, is
  marked.
- **CNECs**: each CNEC, with its state: **Preventive**, or a contingency and an instant.
- **Remedial Actions**: each remedial action, with its type (network action, PST, HVDC, injection,
  counter-trading).
- **Instants**: the CRAC's instants, in their order, with their kind.
- **Import Report**: what the import ignored or altered, and why.

An object whose network elements are no longer in the network (removed since the import) is marked with a warning
icon; its tooltip gives the missing elements. A RAO with such a CRAC is refused.

On the right, the details of the object selected on the left:

- a contingency: its form, to edit it (see below);
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

### Generate a CRAC from the network

**Generate** creates a CRAC from the network itself, with no file, as the [CRAC Generator](../parameters/crac_generator.md)
and [CRAC Creation](../parameters/crac_creation.md) tabs of the Parameters view say: a contingency for the loss of each
branch, a flow CNEC on each branch at each state, range actions on the PSTs and, if set, redispatching,
counter-trading and balancing. When the network already has a CRAC, a confirmation is asked first. When the generator
would refuse the parameters (a post-contingency instant without acceptable duration...), a dialog lists every reason
instead. A notification follows the generation; **View generation report** in it shows what was skipped and why. The
generated CRAC is named after the network and time stamped with its date.

### Create an empty CRAC

**New** asks the **Id** of the CRAC (the network's by default), an optional **Auto Instant** and the **Curative Instants**,
separated by commas (one, "curative", by default). The CRAC also has a preventive and an outage instant. On **OK**, the
new CRAC, with no contingency, CNEC or remedial action yet, replaces the network's; when OpenRAO refuses the instants
(two of the same id...), the dialog stays open and tells why.

### Add an instant

**Add** > **Instant…** asks the kind and id of a new instant, added after the existing ones: an auto instant only while
the CRAC has neither auto nor curative instant, curative ones always. Instants can't be removed.

### Add and edit contingencies

- **Add** > **Contingency** adds a contingency with no elements, named "Contingency 1" (or the next free number), and
  selects it.
- **Add** > **Contingencies of the Contingency Lists** adds the contingencies of the enabled lists of the
  [Contingencies](contingencies.md) view, as a security analysis resolves them. Those whose id the CRAC already has, and
  those with elements not in the network, are skipped; a notification tells how many were added and skipped.

The form of the selected contingency edits it, each change applied at once:

- **Id** and **Name**, applied on Enter or when leaving the field. They can be changed only while no CNEC or remedial
  action refers to the contingency: the form tells what refers to it. An empty name is the id.
- **Network Elements**: **Add…** picks network objects (branches, injections, HVDC lines, busbar sections, switches)
  among those not in the contingency yet; **Remove** removes the selected ones. The elements can be changed even when
  CNECs or remedial actions refer to the contingency.

The button after the **Add** menu removes the selected contingency, unless a CNEC or a remedial action refers to it (a
notification then says which). Each edit marks the last [RAO result](../results/rao_results.md) as computed with
another CRAC.

### Export the CRAC

**Export** writes the CRAC in OpenRAO's JSON format, which **Import** reads back.

### Remove the CRAC

The button after **Export** removes the CRAC of the network, after confirmation. The result of the last RAO is kept.
