# UI conventions (JavaFX, FXML, i18n, tables, dialogs, styling)

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`; FXML files sit in the mirrored
resources package.

## Screens: FXML + controller

- One FXML file per view, named `kebab-case-view.fxml`, in the resources package matching the controller's Java
  package (`network/tables/LoadsController.java` ↔ `network/tables/loads-view.fxml`).
- The root element declares `fx:controller="fully.qualified.Controller"`. Every `fx:id` has a matching `@FXML`
  field of the same name and compatible type; every `onAction="#name"` has a matching method. Renaming one side
  without the other breaks loading at runtime (and the controller's tests).
- Controller fields that tests read are package-private or public (e.g. `LoadsController.loadsTableView`,
  `nameColumn`); keep that visibility when tests depend on it.
- Reusable sub-views are embedded with `<fx:include fx:id="searchBox" source="..."/>`; the nested controller is
  injected as a field named `<fx:id>Controller` (e.g. `searchBoxController`, `columnVisibilityToolbarController`,
  `sldPaneController`). Existing reusable pieces:
  - `network/search/search-box.fxml` → `SearchBoxController` (bind with `searchBox().bind(mainModel, kinds, onMatch)`).
  - `network/tables/column-visibility-toolbar.fxml` → `ColumnVisibilityToolbarController.configure(List<ColumnGroup>)`.
  - `diagram/diagram-pane.fxml` → `DiagramPaneController` (WebView diagram viewport).
  - Equipment table views themselves, included in `network/substations-view.fxml` as `*Embedded` tabs.
- Load views with `new FXMLLoader(url, Messages.bundle())`. For center views, `MainController.loadView/ensureController`
  already does it.
- Canonical simple screen: `…/network/tables/LoadsController.java` + `loads-view.fxml`. Canonical complex screens:
  `…/network/SubstationsController.java`, `…/network/NetworksController.java`.

## Internationalization

- All user-visible text is in `i18n/messages.properties` (English) **and** `i18n/messages_fr.properties` (French);
  both have the same key set. Add every new key to both.
- English text uses **American English** spelling (`canceled`, `color`, `toward`, `-ize`, `analyze`, `center`),
  not British (`cancelled`, `colour`, `towards`, `-ise`, `analyse`, `centre`). Key names may keep PowSyBl/JavaFX
  spellings (e.g. `*.cancelled` mirrors `Worker.State.CANCELLED`).
- In FXML: `text="%key"`. In Java: `Messages.get("key")` / `Messages.get("key", args...)` (`MessageFormat`
  syntax — escape single quotes as `''`). For a `Label: value` line use `Messages.labelValue(labelKey, value)`
  rather than a dedicated `"Xxx: {0}"` key.
- One concept, one key: a term means the same thing in every view, so it has a single key in `desktop.common.*`
  instead of one copy per view. Before adding a key, search the bundle for the same English text and reuse it.
  - `desktop.common.equipment.<kind>` / `<kind>.plural` — equipment type names (Title Case in English), used for
    toolbar buttons, info panels, search, column headers and dialog headers alike.
  - `desktop.common.column.*` — column headers shared by several tables.
  - `desktop.common.action.*` — generic buttons/menu items (add, remove, import, export, clear all, cancel...).
  - `desktop.common.computationStatus.*`, `desktop.common.regulationMode.*`, `desktop.common.columnGroup.*`,
    and flat `desktop.common.<term>` for other shared terms (`loadFlow`, `parameters`, `singleLineDiagram`...).
  - Shared NAD/SLD parameter labels live in `parameters.diagram.*` (`sharedLabel`/`sharedTooltip` in
    `AbstractDiagramParametersController`).
- Other keys: `<area>.<sub>.<name>`, area = the view or feature (`main.*`, `networks.*`, `loads.column.*`,
  `parameters.*`, `loadflow.param.*`, `securityAnalysis.param.*`, ...), only for text specific to that area.
- FXML `%key` text is resolved once at load; language switching works by reloading the shell (see
  [architecture.md](architecture.md#entry-point-and-lifecycle)). Strings built in Java at runtime should be
  re-resolved when rendered (see how `Notification` stores a key, not a string).
- Enum display text is provided by overriding `toString()` with `Messages.get(...)` (e.g. `GroupingMode` in
  `SubstationsController`).

## Equipment tables

Pattern for "one row per equipment of a kind" tables (Loads, Generators, Lines, Transformers, ...):

1. Controller extends `…/network/tables/AbstractEquipmentTableController.java` (`<T extends Identifiable<?>>`).
   It provides sorted data loading, refresh on `networkProperty()`/`updateProperty()`, search integration,
   embedded-in-substations filtering (`setContainer`), container link cells and row selection.
2. `@FXML private void initialize()` calls `initializeTable()` first, then configures each column with
   `TableColumnSupport.configureXxxColumn(...)` and `setCellValueFactory`.
3. Implement the hooks: `tableView()`, `searchBox()`, `searchKinds()`, `networkItems(network)`,
   `voltageLevelsOf(item)`, `asOwnEntity(match)`, `nameColumn()`, `substationColumn()`, `voltageLevelColumn()`,
   `ownNavigationEvent(item)`; override `disableContainerLinks()` only for equipment spanning two containers.
4. Add a public `goToX(X)` that calls `goToItem(x)` for navigation restore.
5. FXML: `VBox` root, optional `column-visibility-toolbar.fxml` include, `search-box.fxml` include, a `TableView`
   with `editable="true"` if any column is editable, `tableMenuButtonVisible="true"`, read-only columns marked
   `editable="false"`, numeric columns `styleClass="right-aligned-column"`, check-box columns `centered-column`.
6. Wire navigation (see [architecture.md](architecture.md#navigation)) and, if it should appear in the substations
   view, an embedded tab in `substations-view.fxml` / `SubstationsController`.
7. Tests: `XControllerTest`, `XControllerEmptyStateTest`, and add the FXML to `EditLockTest.VIEWS`.

Not every table uses this base: bus views extend `AbstractBusesController` (itself an
`AbstractEquipmentTableController<Bus>`); `SubstationsTableController`, `VoltageLevelsController`,
`ComponentsController`, `SecurityAnalysisResultsController` extend `AbstractDisposableController` directly;
`SwitchesController` does too and implements `EmbeddableEquipmentTable` (embedded only, no standalone view). Read the
closest one before adding a similar view.

Large tables: `initializeTable()` installs `TableAutoFitLimiter`; do the same for any new large `TableView` (the Logs
table uses it too).

## Editing cells

- Editing writes straight to the PowSyBl object in the commit handler, e.g.
  `p0Column.setOnEditCommit(e -> e.getRowValue().setP0(e.getNewValue()))`, then the view relies on
  `mainModel.setUpdate()` (connected-checkbox toggles call `mainModel.setUpdate(terminal.getVoltageLevel())`).
  There is no form object/DTO layer.
- Use the factories in `…/network/tables/TableColumnSupport.java` (`configureDoubleColumn`,
  `configureEditableBooleanColumn`, `configureChoiceColumn`, `configureNullableEditable*`, `configureMultiSided*`,
  `configureInfoButtonColumn`, `configureSpinnerIntColumn`, ...). They handle:
  - validation errors: a `PowsyblException` from the setter is caught, the old value restored, and
    `notifyEditError` shows a ControlsFX popup + red flash;
  - success feedback: green flash (`flashEditSuccess` / `flashKeyed`). Cells whose setter triggers
    `mainModel.setUpdate` must key the flash by row (`flashKeyed`/`applyKeyedFlash`), because the refresh rebuilds cells;
  - the busy lock: custom editor cells are disabled when the table isn't editable (`disableUnlessTableEditable`).
- A new kind of editor goes into `TableColumnSupport` (or at least calls `disableUnlessTableEditable`), never as an
  ad-hoc cell factory in a controller.

## Dialogs and secondary windows

Two established forms:

- **Code-built modal dialogs**: a package-private `final` class with a private constructor and a
  `static show(Window owner, ...)` method building a `javafx.scene.control.Dialog` in code (no FXML):
  `initOwner(owner)`, `initModality(Modality.WINDOW_MODAL)`, title from `Messages`, content set on the `DialogPane`,
  `ButtonType.CLOSE` (or a result converter), `showAndWait()`. Examples: `…/network/tables/GeographicalTagsDialog.java`
  (simplest), `TapChangerStepsDialog`, `ShuntCompensatorSectionsDialog` (editable cells), `…/network/DropImportDialog.java`
  (returns a result via `setResultConverter`). Edits inside these dialogs write to the IIDM object directly and use the
  same error/flash helpers as tables.
- **FXML secondary windows** opened by `MainController`: parameters (`onParameters`, non-modal `Stage`, single
  instance, brought to front if already open) and memory (`onMemory`); about (`onAbout`) is an FXML content inside a
  `Dialog`. Pattern: load with `Messages.bundle()`, add `styles.css` to the root, `initOwner(main window)`, set the
  app icon (`logo.png`), dispose the controller in `setOnHidden`, and close it in `MainController.dispose()`.
- Confirmations and error details use `javafx.scene.control.Alert` directly (e.g. `NetworksController`,
  `ParametersController.onReset`).
- Inline forms inside a view (contingency list forms) are FXML sub-controllers swapped in by the parent controller
  (`contingency/CriterionListFormController`, `DefaultContingencyListFormController`).

## WebView-based views (diagrams, map)

- Diagrams are SVG produced off the FX thread by `SubstationDiagramRenderer` / `NetworkAreaDiagramRenderer` and
  injected by `DiagramPaneController` into an HTML shell in a `WebView`.
- JS → Java callbacks go through `window.controller` (`JSObject.setMember("controller", this)` once the page has
  loaded); callback methods are public and annotated `@SuppressWarnings("unused") // used by JS`
  (`SubstationsController.onSwitchClick`, `onFeederTopBottomClick`). Keep JS (`network/sld.js`, `map/map.js`) and
  these method names in sync.
- The Map view does not draw the network in JavaScript: tiles are rendered in Java (`MapTileRenderer`) on a thread
  pool from an immutable `MapNetworkData` snapshot; Leaflet only displays tiles. Leaflet files are unpacked from the
  `org.webjars:leaflet` dependency at build time — not checked in.

## Styling and icons

- One global stylesheet, `src/main/resources/com/powsybl/powsybldesktop/styles.css`, added to the main scene, to
  each secondary window's root, to the notification overlay popup and to the splash screen. Dialogs owned by the main
  window (`dialog.initOwner(...)`) and popups (tooltips, context menus) inherit it from their owner's scene; a window
  with no owner must add it itself.
- No inline styles (`setStyle(...)` / FXML `style="..."`): add a style class to `styles.css`, in the matching section
  (generic, tables, status markers, then one section per view), with a short comment saying where it is used and why,
  and apply it with `getStyleClass().add(...)` / `styleClass="..."`. Padding that varies per instance of a shared class stays a layout
  property (`<padding><Insets/></padding>`, as for the `map-overlay` panels).
- Reusable classes: `section-title`, `right-aligned-column`, `centered-column`, `editable-cell`,
  `edit-error`/`edit-success`, `icon-button`, `container-link`, `row-warn`/`row-error`, `alert-label`/`alert-icon`,
  `map-overlay`, `notification-*`.
- Icons are Ikonli Material Design 2 (`ikonli-materialdesign2-pack`): `<FontIcon iconLiteral="mdi2a-arrow-left" iconSize="16"/>`
  in FXML or `new FontIcon("mdi2p-pencil")` in Java. Do not add another icon library.
- ControlsFX is available and used (e.g. `Notifications` popups for edit errors).
