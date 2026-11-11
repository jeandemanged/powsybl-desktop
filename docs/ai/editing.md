# Editing the network: tables, editors, creations, removals, detail views

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`; FXML files sit in the mirrored
resources package. The busy lock every editor respects is described in [threading.md](threading.md#the-busy-lock).

## Structural edits

- Every creation or removal goes through `core/NetworkChanges.apply(mainModel, network, change)` (removals:
  `NetworkChanges.remove(mainModel, identifiable)`): refused (`main.networkBusy` error notification) while the network
  is busy, run on the FX thread (a few objects, like value edits). A temporary IIDM `NetworkListener` on the root
  network collects what `change` created and removed - modifications create and remove more than they are given (a
  bay's switches, a voltage level's contents, a converter station's HVDC line) - and publishes it as a
  `NetworkStructureChange` through `MainModel.setStructureChange`, then `setUpdate()` (only the latter when nothing was
  created nor removed, e.g. an internal connection).
- `setStructureChange` purges the navigation history of entries whose state `refersTo` a removed object (every
  equipment `NavigationState` implements it, by identity). Listeners then: `MainController` updates every search index
  of the root network in place (`NetworkSearchIndex.applyStructureChange`) and closes the separate-window views of
  removed objects (`SeparateWindows.closeRemoved`: detail views, container diagrams and tabs); `SubstationsController`
  rebuilds its tree keeping expansion and selection (or the closest remaining ancestor); `NetworksController` refreshes
  the statistics; editable tables reload their rows (see [Equipment tables](#equipment-tables)). Diagrams re-query on
  `updateProperty()`.
- powsybl-iidm-modification modifications are applied with `NetworkEdits.apply(network, modification)`, which throws
  on error (they only log by default). Node breaker feeders get their bay (`CreateFeederBay`,
  `CreateBranchFeederBays`, and leg by leg for three-winding transformers) and a `ConnectablePosition` where the voltage
  level uses positions.
- Position edits (map) go through `NetworkChanges.apply` too, see
  [diagrams-and-map.md](diagrams-and-map.md#position-edits).

## Equipment tables

Pattern for "one row per equipment of a kind" tables (Loads, Generators, Lines, Transformers, ...):

1. Controller extends `…/ui/network/tables/AbstractEquipmentTableController.java` (`<T extends Identifiable<?>>`,
   implements `EmbeddableEquipmentTable`). It provides sorted data loading, refresh on
   `networkProperty()`/`structureChangeProperty()`/`updateProperty()` (see below), search integration, embedded-in-substations filtering (`setContainer`),
   container link cells, row selection, the busy-lock binding and `goTo(Identifiable)`.
2. `@FXML private void initialize()` calls `initializeTable()` first, then configures each column with a
   `TableColumnSupport.configure*Column(...)` factory and `setCellValueFactory`.
3. Implement the hooks: `tableView()`, `searchBox()`, `searchKinds()`, `networkItems(network)`,
   `voltageLevelsOf(item)`, `asOwnEntity(match)`, `nameColumn()`, `substationColumn()`, `voltageLevelColumn()`,
   `ownNavigationEvent(item)`; override `disableContainerLinks()` only for equipment spanning two containers, and
   `creationItems(Container)` for the table's creations (see [Creation dialogs](#creation-dialogs-and-edit-menus)).
4. Add a public `goToX(X)` that calls `goToItem(x)` for navigation restore.
5. FXML: `VBox` root, optional `column-visibility-toolbar.fxml` include, `search-box.fxml` include, a `TableView`
   with `editable="true"` if any column is editable, `tableMenuButtonVisible="true"`, read-only columns marked
   `editable="false"`, numeric columns `styleClass="right-aligned-column"`, check-box columns `centered-column`.
6. Wire navigation (see [architecture.md](architecture.md#navigation)) and, if it should appear in the substations
   view, an embedded tab in `substations-view.fxml` / `SubstationsController`.
7. Tests: `XControllerTest`, `XControllerEmptyStateTest`, and add the FXML to `EditLockTest.VIEWS`.

Other table shapes - read the closest one before adding a similar view:
- bus views extend `AbstractBusesController` (itself an `AbstractEquipmentTableController<Bus>`);
- tables of objects outside voltage levels (areas, overload management systems, voltage angle limits) extend
  `AbstractNetworkObjectTableController`: no search box, no embedding;
- `SubstationsTableController`, `VoltageLevelsController`, `ComponentsController`, `SecurityAnalysisResultsController`,
  `SensitivityAnalysisResultsController`, `ViolationsController` extend `AbstractDisposableController` directly and re-implement what they need (e.g. the
  busy-lock binding); `SwitchesController` does too and implements `EmbeddableEquipmentTable` (embedded only, no
  standalone view).

Large tables: `initializeTable()` installs `TableAutoFitLimiter`; do the same for any new large `TableView` (the Logs
table uses it too).

Refreshing (editable tables): the rows (`data.setAll`) are reloaded only when they can be other objects - the selected
network changes, or a structure change (`MainModel.structureChangeProperty()`, non-null) is published. A plain
`updateProperty()` change only re-reads the visible cells (`TableView.refresh()`): its cost is the visible rows whatever
the table's size, the order is kept (an edited row sorted by its column stays by its field) and a field being typed in
keeps its focus and text, uncommitted. Bus tables reload on every update (`rowsChangeOnUpdate`): IIDM computes their
buses anew after a topology change. Hence objects created or removed outside `NetworkChanges` don't show in the tables.

Every violation indication (the Violations view, the "Loading" and "Voltage Range" columns, `ViolationColumns`) reads
the study's cached violations, never checks limits in a cell: see
[domain-and-persistence.md](domain-and-persistence.md#networks-and-subnetworks).

## Editing cells

- Editing writes straight to the PowSyBl object in the commit handler, e.g.
  `p0Column.setOnEditCommit(e -> e.getRowValue().setP0(e.getNewValue()))`. There is no form object/DTO layer. Setters
  only write to IIDM: once an edit applied, its editor fires an event bubbling up to the table
  (`TableColumnSupport.fireEditApplied`), whose controller follows it with the network update
  (`TableColumnSupport.onEditApplied`, installed by every editable table: `setUpdate(voltageLevel)` when the row lies in
  a single voltage level, else `setUpdate()`), as detail views' `onEdit` does. Connected checkboxes call their
  `onToggle` (`setUpdate(terminal.getVoltageLevel())`) instead. A new editable table installs `onEditApplied`.
- Editable cells are always-visible fields, edited like the parameters and detail views' fields - never a
  double-click-to-edit cell (no `TextFieldTableCell`/`ChoiceBoxTableCell`). The fields live in
  `…/ui/form/CellFields.java` (`CellTextField`, `CellChoiceBox`, `CellSearchableComboBox`, `IntStepper`):
  `textColumn`/`choiceColumn`/`searchableChoiceColumn` commit through the column's `onEditCommit`, and `sidedColumn`
  stacks one editor per side (one side per row for single-value editors). A text field commits on Enter or focus
  loss, Escape reverts what is being typed, Up/Down commit then move to the field above/below in the same column;
  focusing a field selects its cell. A click into an unfocused field selects its whole value (typing overwrites it),
  a further click places the caret.
- Country choices (PowSyBl `Country`) are searchable and shown by `Labels.countryFormat()` (code and name): a single
  choice is a ControlsFX `SearchableComboBox` (`CellFields.searchableChoiceColumn`, `DetailForm.searchableChoiceField`,
  `CreationForm.searchableChoice`), a multiple one a `…/ui/controls/SearchableCheckComboBox.java` (ControlsFX's
  `CheckComboBox` has no search).
- Use the factories in `…/ui/form/TableColumnSupport.java` (`configureDoubleColumn`,
  `configureEditableBooleanColumn`, `configureChoiceColumn`, `configureSingleSlotChoiceColumn`,
  `configureNullableEditable*`, `configureMultiSided*`, `configureDetailNameColumn`, `configureSpinnerIntColumn`, ...)
  and, in detail views, `DetailTables.editableColumn`. They handle:
  - validation errors: a `PowsyblException` from the setter (for field editors, `CellFields.apply`, also an
    `IllegalArgumentException`, e.g. unparsable text; a `NumberFormatException` gets
    `desktop.common.editError.invalidNumber`) is caught, the value shown again, with a ControlsFX popup + red flash;
  - success feedback: green flash, then the network update (above);
  - both flashes keyed by row (`CellFields.KeyedFlash`), not held on a cell: a table rebuilds its cells when its items
    change, or recreates them (`TableView.refresh()`, e.g. after `mainModel.setUpdate`), which can rebind a cell to
    another row before the flash fades. For the same reason the focus of a text field (`CellTextField`, `IntStepper`),
    with what is being typed, follows its row through a rebuild; choice boxes don't keep it. Never call
    `TableView.refresh()` after `getItems().setAll(...)`: the latter already rebuilds the cells, the former recreates
    them. Structural edits are the one exception: `MainModel.setStructureChange` publishes the change (tables
    `setAll`) then calls `setUpdate()` (editable tables `refresh()`);
  - the busy lock: editor cells are disabled when the table isn't editable (`disableUnlessTableEditable`).
- Buttons in cells that stay usable while the network is busy (info, copy) carry the
  `CopyButtons.NON_EDITING_BUTTON` style class; `EditLockTest` treats any other enabled control in a cell as an editor.
- A new kind of editor goes into `CellFields`/`TableColumnSupport`, never as an ad-hoc cell factory in a controller.
- In tables with two-sided (or three-sided) rows (Lines, HVDC lines, Transformers), a single editable value goes
  through a one-slot multi-sided factory (`configureMultiSidedEditableDoubleColumn` with `List.of(value)`,
  `configureSingleSlotChoiceColumn`), a field per side: a row's several values are stacked at a fixed per-side height
  (aligned across columns), a single value is centered in the row.

## Creation dialogs and edit menus

- A creation dialog is a `ui/form/CreationForm` (the `DetailForm` grid, but fields holding values read on OK:
  `text`, `id` (unique among the network's objects), `number`, `integer`, `check`, `choice`, `searchableChoice`,
  `dateTime`, `custom`,
  rows shown or hidden with `visibleWhen`) shown by `CreateDialog.show` / `showThenDetails` (which then opens the
  created object's detail view, where everything the form doesn't ask for is set, unless unchecked: the choice is
  remembered, `GuiParameters.openDetailsAfterCreation`). The dialog widens to the form's minimum width, so an editor
  wider than the value column's 260 px isn't cut. OK applies it and only closes once applied: a
  `CreationForm.FieldException` is shown on its editor, an IIDM error on the form. Forms ask for what IIDM requires,
  with defaults; a form with many fields is a small class with a `create` method (checkstyle limits lambdas to 20
  lines).
- The dialog only reads its fields into a spec record (checking what must point at an editor, with `FieldException`)
  and applies the toolkit-free builder of `network/edit` (e.g. `Injections.add(network, id, name, spec, connection)`)
  inside `NetworkChanges.apply`: what gets created is tested there with plain JUnit, the dialog test only covers the
  wiring.
- Where a connectable is connected is a `ConnectionSection` (read as a `network/edit/Connection`): voltage level,
  busbar section (node breaker: position order, suggested at the end of the section, and direction) or bus (bus
  breaker). Orders are never refused: `FeederOrders` brings one out of the section's range back to its nearest end and,
  when it is taken, moves every following feeder of the voltage level up (moved back if the creation fails). A voltage
  level with busbar sections both with and without a `BusbarSectionPosition` is taken as unsectioned: powsybl-core
  (7.4.2) throws an NPE ranging orders by section there, so `FeederOrders.apply` hides the target busbar section's
  position while the modification runs (no open disconnectors to its parallel busbars then).
- A voltage level is chosen with a `VoltageLevelChoice` (connection sections; busbar section, switch and bus dialogs
  opened from tables): a searchable choice, narrowed by a searchable substation choice above it ("no substation"
  first) when the offered voltage levels span several substations - many voltage levels are named alike. A
  `SearchableComboBox` driving another: its value is briefly null as its popup hides (ignore it), and the driven one's
  items are replaced with `setItems(new list)`, never `getItems().setAll`, which fails in its cells once its popup has
  been shown. Test such chains by clicking through the popups: `setValue` alone doesn't reproduce either.
- Dates with time are a `…/ui/controls/DateTimePicker.java` (JavaFX has no date-time picker: a `DatePicker`, hour and
  minute spinners, a whole-hour UTC offset choice; dates shown as `31 Oct 2026` whatever the locale), reporting user edits only
  through `setOnEdit` - `setValue` shows a value without reporting it (the networks view's case date, the new network
  dialog).
- New lines default their second side to `Branches.otherEnd`: another substation's voltage level of the
  closest nominal voltage, nearest substation first (from their positions).
- What can be created or removed for an object comes from `EditMenus.items(sceneModel, owner, network, context)` (a
  "New" submenu, a "Topology" submenu, the removal), the same in the substations tree (its "+" button and context
  menu), the SLD (its toolbar "+" button for the shown container, and its context menu on equipment, or on the
  background for the shown container), the NAD context menu (on a voltage level, branch or injection, or on the
  background for the shown container), the map context menu (after, on a substation, its and its voltage levels'
  single line diagrams, "new substation here", and its position edits) and the table row context menus. Menu edits are disabled while the
  network is busy.
- A context menu whose items depend on the selection is installed with `EditMenus.installContextMenu(control, items)`,
  which builds them on the right-click: a `ContextMenu` doesn't show while empty, so filling it in its `onShowing`
  leaves it never shown. Tables get add and remove buttons with `TableEditActions.install`
  (`AbstractEquipmentTableController.creationItems` for the base's tables); in a row's context menu, the table's
  creations are loose items, labeled "New …" (`edit.menu.newKind`). A table's busbar section, switch and bus
  creations let the voltage level be chosen (any of the right topology kind). Detail views have a remove button.
- Removals: `ui/form/RemovalDialog.confirmThenRemove` confirms, listing what goes with the object (computed by the
  toolkit-free `network/edit/Removals`), then calls `NetworkChanges.remove`, which runs `Removals.remove`.

## Equipment detail views

Non-modal, all-fields view of one equipment (`…/ui/network/detail/`), one `AbstractEquipmentDetailController`
subclass per equipment type (generators, batteries, loads, shunt and static var compensators, VSC/LCC converter
stations, HVDC lines, lines, tie lines, boundary lines, two- and three-winding transformers, busbar sections, switches,
substations, voltage levels, grounds, areas, overload management systems), all on the shared
`equipment-detail-view.fxml` (no `fx:controller`: `EquipmentDetails.load` sets it). Canonical examples:
`GeneratorDetailController` (injection), `TwoWindingsTransformerDetailController` (branch). Tested together by
`EquipmentDetailViewsTest`.

**Opening.** `EquipmentDetails.open(sceneModel, equipment)` (a no-op for an unsupported type) opens it as a tab of the
latest separate window created, keyed by equipment id (see [windows.md](windows.md)); works from separate windows too,
through `SceneModel.getDetailWindows()`; `EquipmentDetails.equipmentLink` links one to another. Add a type with a
subclass and a `Kind` entry in `EquipmentDetails`. Entry points:
- every equipment table's name column info button (`TableColumnSupport.configureDetailNameColumn`, or
  `withDetailAndCopyButtons` around a container link for the substations and voltage levels tables);
- the SLD/NAD toolbar info button for the shown substation or voltage level (`ContainerDiagram`);
- in the SLD: a feeder click (unless the feeder leads to another voltage level, which the click keeps navigating to),
  a click on a busbar section or on a transformer drawn inside the diagram, and a right-click on any equipment
  (details, HVDC line details for a converter station, go to / show in table);
- in the NAD: a click on a branch (unless drawn as a half line, which centers beyond it) or an injection, and a
  right-click on a voltage level, branch or injection (details, single line diagrams, centering);
- on the map, a click on a line, tie line or boundary line (a click on a substation opens its single line diagram in
  the "Diagrams" window).

**What the base builds.**
- The header (title, voltage level links).
- The main tab's general section: id (editable through IIDM's `setId`, the separate window's tab re-keyed with
  `SeparateWindows.retarget`; nothing else referring to the old id is updated, e.g. contingencies), name, fictitious.
  Id and name have a copy button (`DetailForm.copyButton`).
- The extensions and properties/aliases tabs, and the remove button.

**What a subclass adds**: its main rows (`buildMain`), an `EquipmentSymbol` above the tabs (`symbol()`, from
`newSymbol()`), a main side node (`mainSide`, e.g. `capabilityChart(...)`) and tabs (`extraTabs`:
`reactiveLimitsTab(...)`, `limitsTab()`, `TapChangerPane`s, ...). Every part is a `DetailPart` passed to
`register(...)`: the base re-reads it after every network update and binds its `editableProperty()` to the busy lock.
Edits run `onEdit()` (`mainModel.setUpdate`).

**Shared pieces - reuse, never duplicate:**
- `DetailForm`: label/editor/unit rows; commits on Enter or focus loss, Escape reverts; same error/flash feedback as
  tables.
- `EquipmentSymbol`: every connectable, tie line and HVDC line drawn with the SLD parameters' component library and
  its CSS, transformed and anchored as SLD does, colored by `diagram/BaseVoltages`, disconnected grey. Sides left to
  right (a tie line's boundary point in the middle, a three-winding transformer's side 1 on top). Each end shows its
  solved P, Q, I as SLD feeders do (unnamed, P and Q unsigned, the library's `ARROW_ACTIVE`/`ARROW_REACTIVE` giving the
  direction: toward the equipment when positive), the bus V and angle, and its connected checkbox (none for a busbar
  section). The only place these are shown, not in the form.
- `DetailSections`:
  - terminal connection: the node (editable, node/breaker topology), else the bus/breaker view bus (editable), then
    the bus/branch view bus, both labelled with their view; a switch's nodes or buses are read-only (IIDM can't move a
    switch);
  - sides, local targets;
  - a `VoltageRegulationHolder`'s voltage regulation as IIDM holds it (also used by ratio tap changers), the regulated
    terminal row with `TerminalPickerDialog`;
  - links to other equipment's details.
- `DetailTables`: editable row table columns, fields keyed by row index.
- `ReactiveLimitsPane`: staged edits + Apply/Revert, IIDM reactive limits being replaced as a whole.
- `TapChangerPane`, `OperationalLimitsPane.editable(...)` (also the violations popup's read-only pane),
  `ExtensionsPane` with editors from `ExtensionEditors` (per-type lists composed of shared generic ones),
  `KeyValueTablePane.properties/aliases`, `CoordinatesPane`, `HvdcDetails`.
- `ui/controls/CopyButtons`: the copy buttons used everywhere (`DetailForm.copyButton`; table name cells through
  `configureDetailNameColumn`, `configureNameColumn`, `withDetailAndCopyButtons`: an icon after the info one copying
  the id, or offering id or name when there is a name; the networks details pane).

Show IIDM attributes as IIDM holds them, not the tables' simplified columns, and only the values IIDM accepts for the
type (e.g. regulation modes per `VoltageRegulationUtils`).

The selected network's details are not a separate view but the networks view's details pane (`NetworksController`):
its fields (id and name editable, the id through IIDM's `setId`, for subnetworks too), then tabs reusing
`ExtensionsPane` and `KeyValueTablePane` after the statistics.

## Adding an equipment kind

Grounds are the most recent complete example: `git grep -n -E 'Ground\b|GROUNDS?\b'` lists every touchpoint. A kind
usually needs:

- **Wording**: `desktop.common.equipment.<kind>` and `.plural` in both bundles; `network/edit/Kinds`.
- **Search**: a `NetworkSearch.Kind`, its indexing in `NetworkSearchIndex`, and the views' kind sets
  (`SearchBoxController`).
- **Table**: controller + FXML ([Equipment tables](#equipment-tables)), `EditLockTest.VIEWS`, an embedded tab in the
  substations view (`SubstationsController`, `ContainerNavigationState.ContainerTab`) and the SLD's "show in table"
  (`ContainerDiagram`).
- **Navigation**: `NavigationType`, `NavigationEvent.describe()`, a `NetworkNavigationState`, `MainController`
  dispatch and `onX()`, the `main-view.fxml` menu item ([architecture.md](architecture.md#navigation)).
- **Details**: an `AbstractEquipmentDetailController` subclass and its `EquipmentDetails.Kind`
  ([Equipment detail views](#equipment-detail-views)).
- **Creation and removal**: a `network/edit` builder with plain JUnit tests, its dialog in `ui/network/edit`, its
  `EditMenus` entry; check what `Removals` takes along.
- **Elsewhere**: terminal choices (`TerminalPickerDialog`), contingency element types
  (`DefaultContingencyListFormController`), sensitivity function and variable candidates (`SensitivityElements`, checked
  by `SensitivityElementsTest`), `README.md`'s feature list.
