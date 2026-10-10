# Architecture

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`.

## Style

Not a textbook MVC/MVVM. The actual structure is:

- **One shared observable model** (`MainModel` + sub-models) holding JavaFX properties and observable lists.
- **FXML views with controllers** that read/mutate PowSyBl domain objects (`Network`, `Load`, ...) directly and
  react to model changes through property/list listeners.
- **A navigation history** that drives which view is in the main window's center.
- **No service layer, no DI framework.** Controllers call PowSyBl APIs themselves (off the FX thread when slow, see
  [threading.md](threading.md)). Logic that must be unit-testable without a JavaFX toolkit is factored into plain
  classes (e.g. `SubstationDiagramRenderer`, `NetworkAreaDiagramRenderer`, `ContingencyListsIO`,
  `LoadFlowConvergence`, `ApplicationParametersJson`, `MapNetworkData`).

## Package map

| Package | Responsibility |
|---|---|
| root (`…/`) | `Launcher`, `MainApplication`, `SplashScreen`, `MainController` (shell), `MainModel`, `NetworkStudy`, `AbstractNetworkTask` |
| `navigation` | `NavigationType`, `NavigationEvent` (record), `NavigationState` + one subclass per restorable selection, `NavigationHistory` |
| `network` | `NetworksController` (load/import/export/merge/detach networks), `SubstationsController` (tree + SLD/NAD tabs + embedded tables), diagram renderers, `DropImportDialog` |
| `network/tables` | One controller per equipment table, `AbstractEquipmentTableController`, `EmbeddableEquipmentTable`, `TableColumnSupport` (cell factories), `ColumnVisibilityToolbarController`, edit dialogs |
| `network/search` | `NetworkSearch` (kinds, container resolution), `NetworkSearchIndex` (Lucene), `SearchBoxController` (reusable search box) |
| `diagram` | `DiagramPaneController`: reusable WebView viewport (zoom, fit, PNG/SVG export) |
| `map` | Map view: `MapController`, `MapNetworkData` snapshot, `MapTileRenderer`, `map.js` + Leaflet |
| `contingency` | Contingency lists editor and JSON I/O |
| `loadflow`, `security` | Result/report records and helpers (`LoadFlowConvergence`) |
| `parameters` | Parameters window (tabs), `ParametersModel`, `ApplicationParameters` record, `ApplicationParametersJson`, `ParametersConfigFile` |
| `notification` | `Notification` record, `NotificationsModel`, overlay popup and history panel |
| `report`, `reports` | Report viewer; `PowsyblDesktopReportResourceBundle` registering `reports(_fr).properties` |
| `logs` | `LogsModel` (Logback appender → observable list), `LogsViewController` |
| `memory`, `about` | Small secondary windows |
| `utils` | `AbstractDisposableController`, `ListenerManager`, `Messages`, preferences helpers, `Labels`, `TreeItems`, `TableAutoFitLimiter` |

`src/main/java-templates/…/util/PowsyblDesktopVersion.java` is filled in at build time by the
`templating-maven-plugin` (inherited from `powsybl-parent`).

## Entry point and lifecycle

- `…/Launcher.java` → `Application.launch(MainApplication.class)` (separate launcher class so the jar runs on the
  classpath without JavaFX modules).
- `…/MainApplication.java#start`: applies the persisted language, creates the single `MainModel`, restores the
  parameters config file, shows `SplashScreen`, then loads `main-view.fxml` on the FX thread with a controller
  factory that injects the `MainModel` into `MainController`. `styles.css` is added to the scene.
- `…/MainController.java` owns the shell (menu bar, toolbar, `BorderPane`) and is the only class that swaps the
  center view. It also hosts the toolbar actions for load flow / security analysis and search index building.
- Language change (`onLanguageChange` → `reloadShell`) rebuilds `main-view.fxml` with a fresh bundle, keeps the
  same `MainModel`, disposes the old `MainController`, and re-fires the current `NavigationEvent` so the center view
  is reloaded too.

## Shared state: `MainModel` and `NetworkStudy`

`…/MainModel.java` holds: loaded root networks (`getNetworks()`), the selected network (`networkProperty()`, may be
a subnetwork), an `updateProperty()` timestamp fired after any network mutation (`setUpdate()` /
`setUpdate(VoltageLevel)`), `networkBusyProperty()`, search index state, reports, the main window's diagram display
settings, the network change guard, and the sub-models `NavigationHistory`, `ParametersModel`, `NotificationsModel`,
`LogsModel`.

`…/NetworkStudy.java` holds everything per loaded root network: LF/SA results, contingency lists and their enabled
flags, running computation and index-build services, search indexes, Map views, busy count. `MainModel.getStudy(n)`
resolves a subnetwork to its root and throws if the network is not loaded. Studies are created by
`addNetwork`/`detachSubnetworks` and disposed (services cancelled, indexes closed) by `removeNetwork`.

`…/SceneModel.java` is what a view controller receives (`SceneView.setSceneModel`): the shared `MainModel` plus what
depends on the window showing the view - the network (the main window's follows the selection; a separate window's is
fixed to the network selected when it was opened, and closed before the selection changes, so the selection-derived
`MainModel` state - busy, search index - applies to it too), the navigation entry points (`navigate`: drives the main
window; `record`: main window only) and the diagram zoom/fit/depth (the main scene's are kept in `MainModel` across
view swaps and language reloads).

Rules:
- New per-network state goes in `NetworkStudy`, not in a `Map<Network, ...>` elsewhere.
- New state depending on the window showing a view goes in `SceneModel`.
- After mutating a network from the UI, call `mainModel.setUpdate()` (or `setUpdate(voltageLevel)` when the change is
  confined to one voltage level, which lets the search index refresh only that level's buses). Views refresh
  from `updateProperty()`.
- Collections exposed to views are unmodifiable views cached in a field (see the comment in
  `navigation/NavigationHistory.java` on why a fresh unmodifiable wrapper per call can silently stop forwarding).

## Navigation

- Views never load other views. They call `sceneModel.navigate(NavigationEvent.create(type, state))`.
- `MainController.initialize()` listens to `currentEventProperty()` and `onNavigationEvent` uses
  `ensureSceneView(ControllerClass, "path/view.fxml", type)` (or `ensureController` for main-window-only views) — the
  view is reloaded only when the controller class changes — then calls a restore method (`navigateTo`, `goToLoad`,
  `selectReport`, ...). `ContainerNavigationState` can carry an equipment, whose row the substations view selects.
- `sceneModel.record(event)` records history without switching view (used by tree/table selections, and by
  `AbstractEquipmentTableController.containerCell` to remember the current row before leaving); a no-op in a separate
  window.
- `NavigationHistory.setGuard` vetoes a navigation: `MainController` asks `MainModel.confirmNetworkChange` for one
  selecting another network.
- Back/forward history is capped at 30 entries; `removeIf` drops entries for removed networks.
- `NavigationState` subclasses (e.g. `navigation/LoadNavigationState.java`) extend `NetworkNavigationState`, expose
  `create(equipment)` / `createNoX(network)` factories, and override `equals`/`hashCode` with identity comparison of
  the equipment.

**Adding a center view**: add a `NavigationType` constant; add its label case to the exhaustive switch in
`NavigationEvent.describe()` (and a selection branch if it has a state); add a `NavigationState` subclass if the view
has restorable selection; add a branch in `MainController.onNavigationEvent`; add a public `onX()` action in
`MainController` and a button/menu item in `main-view.fxml`; add `main.toolbar.*` keys in both bundles.

## Separate windows

`…/window/SeparateWindows.java` (owned by `MainController`, reached by views through
`SceneModel.getSeparateWindows()`) shows one view per window in a fixed context, with no menu, toolbar nor history:
- an "open in new window" button (`SeparateWindows.addOpenButton`) is added to the view's own toolbar (or its
  `openInNewWindowHost` Pane): by `MainController.ensureSceneView` for whole views (tables, map, contingencies), by
  `SubstationsController` for its diagram panes (`network/ContainerDiagram`, shared with its own tabs) and equipment
  tabs (fixed to the selected container), and for logs/reports (single-instance, not network-bound);
- the window's view gets a `SceneModel.separate` scene: its navigation drives the main window and brings it to front;
- network-bound windows close when the selected network changes; `confirmNetworkChange` asks first.
- one window per key: whole-network views (tables, map, contingencies) are keyed by `NavigationType`, container
  diagrams and equipment tabs by kind and container id, logs/reports/parameters (not network-bound) by name; opening
  an open key brings its window to front instead;
- parameters are a center view (`NavigationType.PARAMETERS`) moved, not copied, to their window (the main window goes
  back in history), since two parameter forms wouldn't follow each other's edits; the toolbar button focuses that
  window while open, and going back in history to parameters moves them back to the main window;
- the main window's Windows menu lists them (`getOpenWindows`) to focus one;
- they are independent stages, not owned by the main window, which can thus come in front of them.

## Controller lifecycle

- Swappable controllers extend `…/utils/AbstractDisposableController.java` (implements `DisposableController`) and
  get a `ListenerManager`. Register model listeners with `listenerManager.listen(observable, listener)` so that
  `dispose()` detaches them when `MainController` swaps the view. Override `dispose()` to also stop services,
  dispose included sub-controllers (e.g. `searchBox().dispose()`), and call `super.dispose()`.
- Model injection is a setter called right after FXML load: `setSceneModel(SceneModel)` for views
  (`SceneView`), `setMainModel(MainModel)` for main-window-only controllers (`LogsViewController` takes
  `setModels(LogsModel, ParametersModel)` instead). `MainController` itself gets `MainModel` through its constructor
  via a controller factory.
- `@FXML private void initialize()` configures controls that don't need the model; work depending on the model
  happens in the model setter.
- `ListenerManager` cannot detach a single listener; when a controller needs to switch the list it observes, it
  refreshes explicitly instead (see the class javadoc of `contingency/ContingenciesController.java`).

## Notifications

User-facing outcome of any background or failing operation is a `notification/Notification.java` record in
`mainModel.getNotificationsModel()`:

- Start: `Notification.createRunning("key", service::cancel)` → `add`.
- End: `replace(running, Notification.createSuccess|createPartialSuccess|createError|createCancelled(running.startTimestamp(), "key", actions...))`.
- Immediate errors: `add(Notification.createError(Instant.now(), "key"))`.
- Messages are bundle keys resolved at render time; `{0}` is the elapsed time, extra args via `withMessageArgs`.
- Actions (`NotificationAction("key", handler)`) typically navigate to `LOGS` (on failure) or to a report/results view.

Canonical example: `MainController.onLoadFlow` (lines ~175–286) and `NetworksController.runImport`.

## Reports and logs

- PowSyBl `ReportNode`s are created with `ReportNode.newRootReportNode().withAllResourceBundlesFromClasspath().withMessageTemplate("desktop.xxx")`,
  templates defined in `reports.properties` and `reports_fr.properties` (registered by
  `reports/PowsyblDesktopReportResourceBundle.java` via `@AutoService`). Completed reports go to
  `mainModel.addReport(...)` and are shown by `ReportsController`.
- The `reports` bundle is separate from the UI `i18n/messages` bundle; do not merge them. Report keys are global
  across all PowSyBl report bundles on the classpath, hence one prefix per library, as PowSyBl does (`core.*`,
  `olf.*`): ours is `desktop.<camelCaseName>`.
- Logging uses SLF4J (`LoggerFactory.getLogger(X.class)`), backed by Logback (`src/main/resources/logback.xml`).
  `LogsModel` captures root-logger events (capped at 20,000) for the Logs view.
