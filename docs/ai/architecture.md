# Architecture

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`.

## Style

Not a textbook MVC/MVVM. The actual structure is:

- **One shared observable model** (`MainModel` + sub-models) holding JavaFX properties and observable lists.
- **FXML views with controllers** that read/mutate PowSyBl domain objects (`Network`, `Load`, ...) directly and
  react to model changes through property/list listeners.
- **A navigation history** that drives which view is in the main window's center.
- **No service layer, no DI framework.** Controllers call PowSyBl APIs themselves (off the FX thread when slow, see
  [threading.md](threading.md)). Logic worth testing without a stage is factored into plain classes outside `ui`
  (e.g. `SubstationDiagramRenderer`, `NetworkEdits` and the `network/edit` builders, `ContingencyListsIO`,
  `LoadFlowRun`, `LoadFlowConvergence`, `NetworkViolations`, `ApplicationParametersJson`, `MapNetworkData`).

## Package map

Code using JavaFX UI (`javafx.scene`, `stage`, `fxml`, `web`, ControlsFX, Ikonli) lives under `ui`, in packages named
after the features, with its FXML and scripts in the mirrored resource directory; the rest lives in feature-named
packages and is tested with plain JUnit. Outside `ui`, `javafx.beans`/`collections` (observable models) and
`javafx.concurrent`/`animation` (`AbstractNetworkTask`, `NetworkStudy`'s services, `LogsModel`'s flush timer) are
used, so tests of `core` start the toolkit with `Platform.startup`.

| Package | Responsibility |
|---|---|
| root (`…/`) | The shell: `Launcher`, `MainApplication`, `SplashScreen`, `MainController` |
| `core` | The shared state: `MainModel`, `NetworkStudy`, `AbstractNetworkTask`, `NetworkStructureChange`, `NetworkChanges` (the funnel of every creation and removal, see [editing.md](editing.md#structural-edits)) |
| `navigation` | `NavigationType`, `NavigationEvent` (record), the `NavigationState` interface and one implementation per restorable selection (`NetworkNavigationState` subclasses, `ReportNavigationState`), `NavigationHistory` |
| `network` | `NetworkIO` (import/export formats, format detection of a file, import and export with their reports), `NetworkAndReport`, `NetworkTieLines`, `VoltageRegulations` (IIDM voltage regulation helpers), sample networks (`MapTestNetworkFactory`) |
| `network/edit` | Changing the network structure: `NetworkEdits` (applying modifications, ids proposed), what each creation builds from its values (`Containers`, `Injections`, `Branches`, `Topologies`, `LineModifications`, `NetworkObjects`, each with spec records; `Connection` for where a feeder is connected), `Removals` (what a removal takes along, and the removal itself), `PositionEdits`, `FeederOrders`, `Kinds` |
| `search` | `NetworkSearch` (kinds, container resolution), `NetworkSearchIndex` (Lucene) |
| `diagram` | `BaseVoltages` (base voltage ranges and colors as single line diagrams use them, for whatever colors equipment outside diagrams), `SubstationDiagramRenderer`, `NetworkAreaDiagramRenderer` |
| `map` | `MapNetworkData` snapshot, `MapTileRenderer` |
| `contingency` | `ContingencyListsIO` (JSON I/O), `ContingencyNames`, `ContingencyListKind`, the editor's row models |
| `loadflow`, `security` | The runs (`LoadFlowRun`, `SecurityAnalysisRun`: report, cancellation, OpenLoadFlow's variant switch, parameter copy, pre-contingency monitor), result/report records, `LoadFlowConvergence` |
| `violations` | `NetworkViolations` (flow and voltage limit violations of the network's state), `FlowViolation`, `VoltageViolation` (also built from a security analysis' violations), `Loading`, `VoltageRange`, `ViolationTexts` (how violations read in the views) |
| `parameters` | `ParametersModel`, `ApplicationParameters` record, `GuiParameters` (with the map's `Basemap`, ...), `DesktopSldParameters`/`DesktopNadParameters` (PowSyBl's diagram parameters plus desktop-only fields), `ApplicationParametersJson`, `ParametersConfigFile` |
| `notification` | `Notification` record, `NotificationAction`, `NotificationStatus`, `NotificationsModel` |
| `logs` | `LogsModel` (Logback appender → observable list) |
| `memory` | `MaxHeapConfig` (the launcher `.cfg`'s `-Xmx` line) |
| `reports` | `PowsyblDesktopReportResourceBundle` registering `reports(_fr).properties` |
| `utils` | `Messages`, `Labels`, `ListenerManager`, `LanguagePreferences`, `Futures` (waiting on a PowSyBl future so that interruption cancels it), `UserGuide` (where the built user's guide is) |
| `ui` | `AbstractDisposableController`, `DisposableController` |
| `ui/window` | A view's per-window context: `SceneModel`, `SceneView`, and `SeparateWindows` (the separate windows and their tabs, see [windows.md](windows.md)) |
| `ui/controls` | Reusable controls and helpers: `CopyButtons`, `DateTimePicker`, `SearchableCheckComboBox`, `TableAutoFitLimiter`, `TreeItems`, `FileChooserPreferences` |
| `ui/form` | Editing building blocks shared by tables, detail views, creation dialogs and the contingency editor: `TableColumnSupport` (cell factories), `CellFields` (in-cell editors), `DetailForm` + `DetailPart` + `DetailTables` + `DetailSections` (form grids and their common sections), `CreationForm`, `TerminalPickerDialog`, `RemovalDialog` |
| `ui/network` | `NetworksController` (load/import/export/merge/detach networks), `SubstationsController` (tree + SLD/NAD tabs + embedded tables), `ContainerDiagram` (a container's SLD/NAD, its rendering and JS callbacks), `DropImportDialog` |
| `ui/network/tables` | One controller per equipment table and their bases (`AbstractEquipmentTableController`, `AbstractBusesController`, `AbstractNetworkObjectTableController`), `EmbeddableEquipmentTable`, `ViolationColumns` (columns of the Violations and Security Analysis Results views), `FlowViolationsDialog`, `ColumnVisibilityToolbarController`, `TableEditActions` (a table's context menu: details, creations, removal), edit dialogs (`GeographicalTagsDialog`, ...) |
| `ui/network/detail` | Equipment detail views, one `AbstractEquipmentDetailController` subclass per equipment type, opened by `EquipmentDetails.open`, and their reusable parts: `EquipmentSymbol`, `ReactiveLimitsPane` + `CapabilityChart`, `TapChangerPane`, `ExtensionsPane` + `ExtensionEditors`, `KeyValueTablePane` (properties, aliases), `CoordinatesPane`, `HvdcDetails` |
| `ui/network/edit` | Creation dialogs (`CreateDialog` + `ui/form/CreationForm`, `ConnectionSection`, `VoltageLevelChoice`) per family (`ContainerCreations`, `InjectionCreations`, `BranchCreations`, `NetworkObjectEdits`, `TopologyEdits`, `LineEdits`), and the menus offering them everywhere (`EditMenus`) |
| `ui/network/limits` | `OperationalLimitsPane`: an equipment's operational limits groups, read-only with violations highlighted (`FlowViolationsDialog`) or editable (detail views) |
| `ui/network/search` | `SearchBoxController` (reusable search box) |
| `ui/diagram` | `DiagramPaneController`: reusable WebView viewport (zoom, fit, PNG/SVG export); `MapBaseVoltages` (per base voltage filter checkboxes, in the map and the GUI parameters) |
| `ui/map` | Map view: `MapController`, `map.js` + Leaflet |
| `ui/contingency` | Contingency lists editor (`ContingenciesController` and its inline forms) |
| `ui/parameters` | Parameters view and its tabs (`AbstractDiagramParametersController`, `ParameterFormBuilder` for importer/exporter parameters) |
| `ui/notification` | Overlay popup and history panel (`NotificationOverlay`, `NotificationView`, `NotificationsController`) |
| `ui/report` | Report viewer (`ReportsController`, `ReportNodeViewController`, `ReportNodeTreeCell`) |
| `ui/logs`, `ui/memory`, `ui/about` | Logs view, small secondary windows |

`src/main/java-templates/…/util/PowsyblDesktopVersion.java` is filled in at build time by the
`templating-maven-plugin` (declared in `pom.xml`, configured by `powsybl-parent`).

## Enforced layering

`src/test/java/com/powsybl/powsybldesktop/ArchitectureTest.java` (ArchUnit) checks, by package only, that each layer
uses only the layers below it (from top to bottom):

| Layer | Packages |
|---|---|
| Shell | the root package: `Launcher`, `MainApplication`, `MainController`, `SplashScreen` |
| UI | `ui..` |
| State | `core`, `navigation`, `logs` |
| Domain | `network..` (`network.edit` included), `search`, `diagram`, `map`, `contingency`, `loadflow`, `security`, `violations`, `memory`, `parameters`, `notification` |
| Base | `utils`, `util`, `reports` |

Every class must be in a layer: a new top-level package fails the test until it is added to one. The test also checks
that:
- nothing outside the shell and `ui` uses JavaFX UI (`javafx.scene`, `javafx.stage`, `javafx.fxml`, ControlsFX,
  Ikonli);
- no packages depend on each other in a cycle;
- only `MainController` and `SceneModel` call `MainModel.getNavigationHistory()`;
- only the shell and `SeparateWindows` create a `Stage`.

The layering, JavaFX and cycle rules cover test classes too, so a test lives in the package of what it tests:
test helpers needing JavaFX go under `ui` (`ui/testutil`), and a test exercising a view of an upper package (e.g. a
network view in a `SeparateWindows` window) goes in that package, not the lower one. The last two rules cover
production code only.

Every rule is strict: fix the dependency, never weaken a rule or exempt a class to make a change pass. Views never
reference `MainApplication`: resources are loaded relative to the class next to them (or by absolute path for the
root's `styles.css` and logo), and the window title is `MainModel.APP_TITLE`.

## Entry point and lifecycle

- `…/Launcher.java` → `Application.launch(MainApplication.class)` (separate launcher class so the jar runs on the
  classpath without JavaFX modules).
- `…/MainApplication.java#start`: applies the persisted language, creates the single `MainModel`, restores the
  parameters config file, shows `SplashScreen`, then loads `main-view.fxml` on the FX thread with a controller
  factory that injects the `MainModel` into `MainController`. `styles.css` is added to the scene.
- `…/MainController.java` owns the shell (menu bar, toolbar, `BorderPane`) and is the only class that swaps the
  center view. It also hosts the toolbar actions for load flow / security analysis, violations computation and
  search index building.
- Language change (`onLanguageChange` → `reloadShell`) rebuilds `main-view.fxml` with a fresh bundle, keeps the
  same `MainModel`, disposes the old `MainController`, and re-fires the current `NavigationEvent` so the center view
  is reloaded too. Separate windows stay open and are relocalized (see [windows.md](windows.md#language-reload)).

## Shared state: `MainModel`, `NetworkStudy`, `SceneModel`

`…/core/MainModel.java` holds: loaded root networks (`getNetworks()`), the selected network (`networkProperty()`, may be
a subnetwork), an `updateProperty()` timestamp fired after any network mutation (`setUpdate()` /
`setUpdate(VoltageLevel)`), `networkBusyProperty()` (bound to the selected network's `NetworkStudy.busyProperty()`;
`markBusy(network)` returns an idempotent release action), search index state, reports, the main window's diagram
display settings, the network change guard, and the sub-models `NavigationHistory`, `ParametersModel`,
`NotificationsModel`, `LogsModel`.

`…/core/NetworkStudy.java` holds everything per loaded root network: LF/SA results, cached limit violations,
contingency lists and their enabled flags, running computation and index-build services, search indexes, Map views,
busy count. `MainModel.getStudy(n)` resolves a subnetwork to its root and throws if the network is not loaded. Studies
are created by `addNetwork`/`detachSubnetworks` and disposed (services cancelled, indexes closed) by `removeNetwork`.

`…/ui/window/SceneModel.java` is what a view controller receives (`SceneView.setSceneModel`): the shared `MainModel`
plus what depends on the window showing the view - its network (see [windows.md](windows.md#network-binding)), where
equipment detail views open (`getDetailWindows`), the navigation entry points (`navigate`: drives the main window;
`record`: main window only) and the diagram zoom/fit/depth (the main scene's are kept in `MainModel` across view swaps
and language reloads).

Rules:
- New per-network state goes in `NetworkStudy`, not in a `Map<Network, ...>` elsewhere.
- New state depending on the window showing a view goes in `SceneModel`.
- After mutating a network value from the UI, call `mainModel.setUpdate()` (or `setUpdate(voltageLevel)` when the
  change is confined to one voltage level, which lets the search index refresh only that level's buses); table and
  detail view editors already do (see [editing.md](editing.md#editing-cells)). Views refresh from `updateProperty()`.
- Collections exposed to views are unmodifiable views cached in a field (see the comment in
  `navigation/NavigationHistory.java` on why a fresh unmodifiable wrapper per call can silently stop forwarding).

## Navigation

- Views never load other views. They call `sceneModel.navigate(NavigationEvent.create(type, state))`.
- `NavigationHistory.navigate` drops an event equal to the current one, and `setGuard` can veto it: `MainController`
  asks `MainModel.confirmNetworkChange` for one selecting another network.
- Navigating sets `currentEventProperty()` to `null` before the new event (`NavigationHistory.dispatch`), so that
  re-firing an equal event is still seen: listeners must ignore `null`. `MainModel.setStructureChange` does the same.
- `sceneModel.record(event)` (`navigate(event, false)`) records history without switching view (used by tree/table
  selections, and by `AbstractEquipmentTableController.containerCell` to remember the current row before leaving); a
  no-op in a separate window.
- `MainController.onNavigationEvent` loads the view and calls its restore method (`navigateTo`, `goToLoad`,
  `selectReport`, ...); the view is reloaded only when the controller class changes:
  - `ensureSceneView(ControllerClass, "path/view.fxml", type)` for whole-network views keyed by `NavigationType`
    (tables, map, contingencies...), which also gives them the "open in new window" button;
  - `ensureController` for the others: substations (its own window buttons), and the single-instance views (logs,
    reports, parameters), which open in a separate window through their own `detachLogs`/`detachReports`/
    `detachParameters`;
  - the grounds, areas, overload management systems and voltage angle limits tables are listed in
    `NETWORK_OBJECT_TABLES` and dispatched by `onNetworkObjectTableEvent`, kept out of `onNavigationEvent` whose
    length checkstyle limits.
- `ContainerNavigationState` can carry an equipment, whose row the substations view selects.
- Back/forward history is capped at 30 entries. `removeIf` drops entries for removed networks, detached subnetworks
  and removed objects (`refersTo`).
- Equipment navigation states (e.g. `navigation/LoadNavigationState.java`) extend `NetworkNavigationState`, expose
  `create(equipment)` / `createNoX(network)` factories (e.g. `createNoLoad`), and override `equals`/`hashCode` with
  identity comparison of the equipment.

**Adding a center view**: add a `NavigationType` constant; add its label case to the exhaustive switch in
`NavigationEvent.describe()` (and a selection branch if it has a state); add a `NavigationState` implementation if the
view has restorable selection; add a branch in `MainController.onNavigationEvent` (or `onNetworkObjectTableEvent`),
choosing `ensureSceneView` or `ensureController` as above; add a public `onX()` action in `MainController` and a
button/menu item in `main-view.fxml`; add the label keys in both bundles (`main.toolbar.*`, or the existing
`desktop.common.equipment.<kind>.plural` for a table).

## Controller lifecycle

- Swappable controllers extend `…/ui/AbstractDisposableController.java` (implements `DisposableController`) and
  get a `ListenerManager`. Register model listeners with `listenerManager.listen(observable, listener)` so that
  `dispose()` detaches them when `MainController` swaps the view or a separate window's tab closes. Override
  `dispose()` to also stop services, dispose included sub-controllers (e.g. `searchBox().dispose()`), and call
  `super.dispose()`.
- Model injection is a setter called right after FXML load:
  - `setSceneModel(SceneModel)` for views implementing `SceneView` (tables, substations, map, contingencies,
    reports, ...);
  - `setMainModel(MainModel)` for `ParametersController` and the notification controllers;
  - `setModels(LogsModel, ParametersModel)` for `LogsViewController`;
  - `NetworksController` takes the main window's `setSceneModel(SceneModel)` without being a `SceneView`;
  - `MainController` itself gets `MainModel` through its constructor via a controller factory.
  Not being a `SceneView` doesn't keep a view out of separate windows: parameters and logs open there too.
- `@FXML private void initialize()` configures controls that don't need the model; work depending on the model
  happens in the model setter.
- `ListenerManager` cannot detach a single listener; when a controller needs to switch the list it observes, it
  refreshes explicitly instead (see the class javadoc of `ui/contingency/ContingenciesController.java`).

## Notifications

User-facing outcome of any background or failing operation is a `notification/Notification.java` record in
`mainModel.getNotificationsModel()`:

- Start: `Notification.createRunning("key", service::cancel)` → `add`.
- End: `replace(running, Notification.createSuccess|createPartialSuccess|createError(running.startTimestamp(), "key",
  actions...))`, or `createCancelled(running.startTimestamp(), "key")` (no actions).
- Immediate errors: `add(Notification.createError(Instant.now(), "key"))`.
- Messages are bundle keys resolved at render time; `{0}` is the elapsed time, extra args via `withMessageArgs`.
- Actions (`NotificationAction("key", event -> ...)`) typically navigate to `LOGS` (on failure) or to a report/results
  view.

Canonical example: `MainController.onLoadFlow` and `NetworksController.runImport`.

## Reports and logs

- PowSyBl `ReportNode`s are created by the toolkit-free runs (`LoadFlowRun`, `SecurityAnalysisRun`, `NetworkIO`) with
  `ReportNode.newRootReportNode().withAllResourceBundlesFromClasspath().withMessageTemplate("desktop.xxx").withTimestamp()`,
  templates defined in `reports.properties` and `reports_fr.properties` (registered by
  `reports/PowsyblDesktopReportResourceBundle.java` via `@AutoService`). Controllers pass completed reports to
  `mainModel.addReport(...)`; `ReportsController` shows them.
- The `reports` bundle is separate from the UI `i18n/messages` bundle; do not merge them. Report keys are global
  across all PowSyBl report bundles on the classpath, hence one prefix per library, as PowSyBl does (`core.*`,
  `olf.*`): ours is `desktop.<camelCaseName>`.
- Logging uses SLF4J (`LoggerFactory.getLogger(X.class)`), backed by Logback (`src/main/resources/logback.xml`).
  `LogsModel` captures root-logger events in a concurrent queue, flushed once per second by a `Timeline` into the
  observable list (capped at 20,000) shown by the Logs view.
