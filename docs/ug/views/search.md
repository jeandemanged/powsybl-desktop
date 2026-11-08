# Search

The search finds equipment of the selected network by id or name, tolerating typing errors. It is not a view of its
own but a search box found at the top of several views: the [Substations](substations.md) tree, the [Map](map.md)
and most [Equipment tables](tables.md).

## Content

A search box is a text field followed by two buttons, ◀ (previous match) and ▶ (next match), and a status line:

- the field prompt tells what the box searches, for instance **Search Substations / Voltage Levels / Generators...**
  in the generators table;
- the status line shows the current match as **position/count — kind: name**, for instance
  **1/3 — Generator: GEN_1**, followed by **(Approximate match)** when the matches are approximate;
- **Not found** is shown, and the field highlighted, when nothing matches;
- **Indexing…** is shown, and the field disabled, while the network's search index is being built, just after the
  network is loaded or selected.

![A match](../_generated/images/search-box-match.png)

![An approximate match, for a typing error](../_generated/images/search-box-approximate.png)

![Nothing found](../_generated/images/search-box-not-found.png)

What a box searches depends on its view:

| Search box of | Finds |
|---|---|
| Substations view, Map, Substations and Voltage Levels tables | every kind of equipment |
| An equipment table | substations, voltage levels and the table's own kind of equipment (both bus tables: buses of both views) |

The equipment searched are substations, voltage levels, buses (bus/branch and bus/breaker views), generators, batteries, shunt and static var
compensators, VSC and LCC converter stations, loads, lines, transformers, tie lines, boundary lines, HVDC lines,
busbar sections and grounds. In an equipment table, a substation or voltage level is only found if it holds equipment
of the table's kind.

The equipment tables shown inside the [Substations](substations.md) view have no search box.

## What you can do

### Search

1. Type part of an id or a name in the search box. Several words must all match.
2. The search runs as you type and selects the best match: ids or names starting with what you typed rank before
   those merely containing it.
3. Press Enter or click ▶ to go to the next match, ◀ to the previous one; both wrap around.

When nothing contains what you typed, the search falls back to approximate matching, tolerating typing errors, and
the status line says **(Approximate match)**.

The search is kept up to date with the network: created and removed equipment, ids and names changed in the
[Equipment details](details.md), and buses changed by a topology change, are taken into account as soon as the change
is made.

### What selecting a match does

- **Substations view**: the tree selects the match's substation or voltage level (the voltage level of an equipment),
  showing its diagrams and equipment tabs.
- **Map**: the map zooms to the match and marks it: a line, tie line or boundary line is fitted whole and marked in
  its middle, any other equipment is shown at its substation, with some neighbouring substations around. A match
  without position on the map leaves the view unchanged.
- **Equipment table**: the match's row is selected and scrolled to. For a substation or voltage level, the first row
  of that container is selected instead, with its substation or voltage level cell.
- **Substations** table: the row of the match's substation is selected.
- **Voltage Levels** table: the row of the match's voltage level is selected, or for a substation its first voltage
  level.

A search does not record a navigation history entry.
