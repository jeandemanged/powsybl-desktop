# Loading the SmallGrid sample network

SmallGrid is one of the test networks of the ENTSO-E CGMES conformity assessment: a fictitious transmission network
of about a hundred substations at 132 kV and 220 kV. The application bundles it, in a node/breaker version: each
voltage level is described down to its busbar sections and switches.

## Start the application

When the application starts, it shows the **Networks** view, empty: no network is loaded yet.

![The application at startup](../_generated/images/quickstart-start.png)

The main toolbar, at the top, gives access to all the views and computations. See
[General usage](../general/index.md) for a tour of the main window.

## Load the sample network

1. In the toolbar of the **Networks** view, click the **Import** button (the arrow pointing up).
2. Open **Sample Networks**, then **CgmesConformity1Catalog**.
3. Click **smallNodeBreaker (Geographical Layout)**. The *Geographical Layout* variant also contains the positions of
   the substations, used by the [Map](../views/map.md).

![The sample networks menu](../_generated/images/quickstart-sample-menu.png)

Loading takes a second. A notification at the bottom right of the window tells when the network is loaded.

## Look at the network

The network now appears in the list on the left, and is selected. The right part of the view shows its identity
(ID, name, source format, countries, case date) and, in the **Statistics** tab, how many substations, voltage levels,
lines, generators, loads... it contains.

![SmallGrid loaded](../_generated/images/quickstart-network-loaded.png)

Every other view now shows this network. From here you can, for instance:

- browse the substations and their single-line diagrams with **Substations**;
- see the network on a map with **Map**;
- list the equipment of a given type from **Tables** > **Equipment**.

Next, [run a load flow](loadflow.md) on this network.
