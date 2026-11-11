# Networks

The Networks view lists the loaded networks and shows the details of the selected one. It is where networks are
imported, created, exported, merged, detached and closed. Open it with the **Networks** button of the main toolbar.

## Content

The view is split in two parts.

On the left, a toolbar and the tree of loaded networks:

- each loaded network is a top-level item, its subnetworks (if any) are listed under it;
- an item shows the network name (or id if it has no name) followed by its countries, or by the number of countries
  when there are more than five;
- when no network is loaded, the tree shows **No network loaded** and a hint to drag and drop network files or
  folders there.

Selecting a network in the tree makes it the network shown by every other view (substations, tables, map, results).
Several networks can be selected at once (Ctrl or Shift click) for closing, merging or detaching them.

On the right, the details of the selected network:

- **ID:** and **Name:**, both editable, each with a copy button;
- **Source format:** the format the network was imported from;
- **Countries:** the countries of its substations (hidden when there is none);
- **Case date:** editable date and time, with its time zone;
- **Forecast distance (min):** editable, in minutes;
- **Validation level:** the IIDM validation level of the network.

Below them, three tabs:

- **Statistics**: the number of buses in the bus/breaker and bus/branch views (with how many are in the main connected
  component), subnetworks, substations, voltage levels, lines, tie lines, transformers, generators, batteries, loads,
  compensators, switches, busbar sections, boundary lines (paired and unpaired), HVDC lines and converter stations,
  grounds and overload management systems. Only non-zero counts are listed.
- **Extensions**: the IIDM extensions of the network, as in the [Equipment details](../equipment/details/index.md).
- **Properties and Aliases**: the network properties and aliases, which can be added and removed.

**No network selected** or **Multiple networks selected** is shown instead of the details when relevant.

![The Networks view](../../_generated/images/quickstart-network-loaded.png)

## What you can do

### Import a network

- Click the import menu button of the toolbar (tooltip **Import**) and choose the format: **IIDM** (XML, JSON or
  binary), CGMES, UCTE, MATPOWER, PSS®E, IEEE CDF, PowerFactory... The list shows every format available in the
  application.
- For **CGMES**, choose **From folder...** to import a folder of CIM files (EQ, TP, SSH, SV...), or **From file...**
  to import a single file or an archive.
- The file chooser offers a **Supported files** filter with the extensions of the chosen format, and **All files**.
  It reopens in the last folder used.

  ![The import menu](../../_generated/images/networks-import-menu.png)

- The import runs in the background; a notification tells when it completes, with a **View report** link to its
  report, or **View logs** if it fails. The import parameters of each format are set in the **Network Import** tab
  of the [Parameters](../parameters/index.md).

### Import by drag and drop

- Drag one or more network files, archives or folders from your file manager and drop them on the networks tree.
- The format of each dropped item is detected. When every item matches exactly one format, they are imported right
  away, one after the other.
- When an item matches several formats, or none, the **Import networks** dialog lists every dropped item: choose the
  format in **Import as** where several are possible; items marked **Unsupported – will not be tried** are skipped.
- At most 100 networks can be imported in one drop.

### Load a sample network

- In the import menu, open **Sample Networks** and pick a network: IEEE test cases (9 to 300 buses), CGMES conformity
  and ReliCap grids, the Metrix tutorial, SmallGrid with voltage limits (**smallNodeBreakerWithVoltageLimits**, used by
  the [quick start tutorials](../../quickstart/index.md)), and under **Miscellaneous Testing** the PowSyBl test networks.
- Sample networks labelled **(Geographical Layout)** come with substation and line positions, which the [Map](../diagrams/map.md)
  displays.

### Create a new network

1. Click **New network** (the first toolbar button).
2. Enter its **Id** (a unique one is proposed), an optional **Name** and its **Case date**.
3. Click **OK**: the empty network is added and selected. Create its substations from the
   [Substations](../diagrams/substations.md) view or the [Map](../diagrams/map.md).

### Export a network

1. Select a loaded network (not a subnetwork).
2. Open the export menu button (tooltip **Export**) and choose the format; the IIDM formats (XIIDM, BIIDM, JIIDM) come
   first.
3. Choose the file. The export runs in the background with the parameters of the **Network Export** tab of the
   [Parameters](../parameters/index.md); its notification links to its report.

### Edit the network attributes

- Type in **ID:**, **Name:** or **Forecast distance (min):** and press Enter (or leave the field) to apply. An id
  already used in the network is refused.
- Change the **Case date:** with its date picker, hour and minute fields and time zone.
- Edit extensions, properties and aliases in their tabs.

These fields are disabled while a computation runs on the network.

### Merge and detach

- **Merge**: select two or more loaded networks that have no subnetworks, then click **Merge**. They are replaced by
  a single network holding each of them as a subnetwork.
- **Detach**: select one or more subnetworks and click **Detach** to turn them back into separate networks. Selecting
  a merged network and clicking **Detach** detaches all its subnetworks.

### Close networks

- **Close** closes the selected networks, **Close all** every loaded network, after confirmation. Unsaved changes are
  lost: export a network first to keep them.
