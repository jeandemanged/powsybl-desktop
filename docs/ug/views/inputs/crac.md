# CRAC

The CRAC view shows the CRAC of the selected network: the contingencies, the CNECs (critical network elements, with
the contingency and instant they are monitored at) and the remedial actions that the
[remedial action optimization](../results/rao_results.md) (RAO) works with. Open it with **Inputs** > **CRAC**. Like
the other inputs, the CRAC belongs to the whole network a RAO runs on, for a subnetwork its root network.

## Content

A toolbar with **New**, **Import**, **Generate**, **Export**, an **Add** menu, a button to remove the selected contingency,
CNEC or remedial action and a button to remove the CRAC, then a line telling the CRAC's name, its time stamp if it has one, and its instants
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
- a flow CNEC: its form, to edit it (see below);
- an angle or voltage CNEC: its type, network elements, state, role (optimized, monitored), thresholds with their unit,
  reliability margin and operator;
- a network action or a range action: its form, to edit it (see below).

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

The form of the selected instant shows its kind and edits its **Remedial Action Usage Limits**, the most the RAO may
use at that instant: **Max Remedial Actions** in all (applied on Enter or when leaving the field), and **Per TSO**, a
row per TSO (the operator of the remedial actions) with its max topological actions, PSTs, remedial actions and
elementary actions. A blank limit is none. A limit can't be negative, and a TSO's max remedial actions can't be fewer
than its max topological actions or PSTs. The outage instant has no usage limits: no remedial action is used at it.

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

### Add and edit flow CNECs

**Add** > **Flow CNECs…** first picks branches, then asks the states to monitor them at (the preventive state, and each
contingency at each instant after it: all checked at first) and the sides with thresholds (those of the
[CRAC Creation](../parameters/crac_creation.md) tab's default monitored side at first). A flow CNEC is added on each
branch at each state, as **Generate** makes one: named after the branch and the state, optimized, with the nominal
voltages of the branch's ends and, on each side, a threshold of ± its permanent current limit (A), or else of ± its
permanent active power limit (MW). Those whose id the CRAC already has, or with no limit on the sides, are skipped:
**View skipped CNECs** in the notification tells which and why.

The form of the selected flow CNEC edits it, each change applied at once (text fields on Enter or when leaving them):
**Id**, **Name**, **Branch** (**Change…** picks another), **State**, **Role** (**Optimized**, **Monitored**),
**Reliability Margin**, **Operator**, **Border**, the **Nominal Voltages** and **iMax** of both sides (needed by thresholds
in A and % of iMax), and the **Thresholds**: a row per threshold with its side (1 or 2), unit (A, MW or %), min and/or
max. A change OpenRAO refuses (no threshold, a threshold in % without iMax...) is told below the fields and undone. When a
remedial action is conditioned on the CNEC (an on-constraint usage rule), only its role can be changed, and it can't be
removed. Angle and voltage CNECs, only monitored by the RAO, can be removed but not edited.

The button after the **Add** menu also removes the selected CNEC.

### Add and edit network actions

**Add** > **Network Action…** picks network elements (switches, branches, three-winding transformers, injections,
boundary lines, HVDC lines) and adds a network action, named "Network action 1" (or the next free number), with an
elementary action on each, from its current state: a switch or a branch's terminals toggled (opened if closed, closed
if open), a PST at its current tap, a generator, load or boundary line at its current active power set point, a shunt
compensator's sections toggled (all or none), an HVDC line's AC emulation deactivated. It has no usage rule yet: the
RAO doesn't use it.

The form of the selected network action edits it, each change applied at once (text fields on Enter or when leaving
them): **Id**, **Name**, **Operator**, **Speed**, **Activation Cost**, and the **Elementary Actions**, a row each with
its kind, element and value:

| Kind | Element | Value |
|------|---------|-------|
| `switch` | a switch | `open` or `close` |
| `terminals` | a branch, transformer or injection | `open` or `close` |
| `pstTap` | a phase-shifting transformer | the tap, within its range |
| `generator`, `load`, `boundaryLine` | a generator, a load, a boundary line | the active power set point, in MW |
| `shunt` | a shunt compensator | the section count, at most its maximum |
| `switchPair` | the switch to open | the switch to close |
| `acEmulationOff` | an HVDC line | none |

**Add Actions…** adds an elementary action on more elements picked, as above. An element can be acted on only once,
and must be of the kind's type. A refused change is told below the fields and undone. The **Usage Rules** are edited
[as below](#edit-usage-rules). An imported action with an elementary action of another kind (a relative set point...)
is read-only.

The button after the **Add** menu also removes the selected remedial action, network or range action.

### Add and edit range actions

The **Add** menu adds range actions from the network, with no usage rule yet (the RAO doesn't use them), named "PST
range action 1" and so on (or the next free number):

- **PST Range Action…** picks phase-shifting transformers and adds a range action on each: its initial tap and
  tap-to-angle conversion are the network's, its range the PST's taps.
- **HVDC Range Action…** picks HVDC lines and adds a range action on each: its initial set point is the line's (negative
  from side 2 to side 1), its range ± its maximum active power.
- **Injection Range Action…** picks generators and loads and adds one range action on all of them, as the CRAC
  generator does: each injection's distribution key is its share of the total set point (generation minus
  consumption), or 1 when alone; its range is a single generator's active power limits, else ± 100 MW around the
  initial set point.
- **Counter-Trading Range Action…** asks a country of the network and adds a counter-trade from it to its neighbours in
  the network, from 0 by up to ± 100 MW.

The form of the selected range action edits it, each change applied at once (text fields on Enter or when leaving them):
**Id**, **Name**, its PST or **HVDC Line** (**Change…** picks another), its **Initial Set Point** (a PST's
**Initial Tap** is shown, taken from the network), **Operator**, **Speed**, **Activation Cost**, **Up Variation Cost**
and **Down Variation Cost** (per MW or tap of variation), **Group** (range actions of a group move together), and its
**Ranges**, a row each with its type, min and max:

| Type | Range |
|------|-------|
| `absolute` | the set points (or taps) allowed |
| `relativeToInitialNetwork` | the variation allowed from the initial set point |
| `relativeToPreviousInstant` | the variation allowed from the previous instant's set point |
| `relativeToPreviousTimeStep` | the variation allowed from the previous time step's |

A blank min or max is no limit (but for an absolute range of an HVDC, injection or counter-trading range action). A PST
range action may have no range: the PST's taps apply. OpenRAO ignores a range relative to the previous instant of a PST
range action without usage rules or only available at the preventive instant: it is refused.

An injection range action also has its **Injections and Distribution Keys**, a row each with a generator or load and
its key (the share of the set point it takes, a load's counted negatively); **Add Injections…** adds more, of key 1.
A counter-trading range action has its **Area** and **Connected Areas**: a row per range of the exchange on the border
with an area, a row with a blank range for an area without any. Areas are country codes (`FR`, `BE`...). OpenRAO 7.5.1
can't remove a counter-trading range action from a CRAC: once added or imported, it is read-only and can't be removed.

A refused change is told below the fields and undone. The **Usage Rules** are edited [as below](#edit-usage-rules),
but for a counter-trading range action's.

### Edit usage rules

The usage rules of a remedial action tell when the RAO may use it; an action without any is never used. The
**Usage Rules** list of a network or range action's form shows them; the **+** button asks a new one, the **-** button
removes the selected ones. The new rule's **Kind** tells what else it needs:

- **At an instant**: the action is available at the **Instant**, in every state (after any contingency for an auto or
  curative instant).
- **After a contingency**: available at the auto or curative **Instant** after the **Contingency** only.
- **When a CNEC is constrained**: available at the **Instant** when the **CNEC** is overloaded; only the CNECs at that
  instant or after it are offered.
- **On a flow constraint in a country**: available at the **Instant** when a CNEC of the **Country** is overloaded,
  after the **Contingency** or after any.

No rule can be at the outage instant. A rule the action already has, or one OpenRAO would refuse, is told below the
list, the action unchanged. A PST range action with a range relative to the previous instant must keep a rule at an
instant other than the preventive one.

### Export the CRAC

**Export** writes the CRAC in OpenRAO's JSON format, which **Import** reads back.

### Remove the CRAC

The button after **Export** removes the CRAC of the network, after confirmation. The result of the last RAO is kept.
