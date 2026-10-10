# Invariants and known inconsistencies

## Invariants (preserve in every change)

State and communication
1. `MainModel` (one instance per app, survives language reloads) is the only shared state. Views communicate only
   through it and `NavigationHistory`; no static mutable state, event bus, or controller-to-controller references
   (except a parent controller driving its `fx:include`d children).
2. Per-root-network state lives in `NetworkStudy`; access it with `MainModel.getStudy(network)`.
3. After mutating a network from the UI, fire `mainModel.setUpdate()` / `setUpdate(voltageLevel)`.
4. Only `MainController` swaps the center view, in response to `NavigationHistory.currentEventProperty()`.

Lifecycle
5. Center-view controllers extend `AbstractDisposableController`; model listeners go through
   `listenerManager.listen(...)`; `dispose()` releases listeners, services and sub-controllers and calls `super.dispose()`.
6. Secondary windows are single-instance, owned by the main window, dispose their controller on hide, and are closed by
   `MainController.dispose()`.

Threading
7. No slow PowSyBl call on the FX thread. Background network reads/writes are `AbstractNetworkTask`s created in
   `Service.createTask()`; model/UI updates happen in the service's FX-thread handlers.
8. Inputs to background work are captured (and parameters copied) on the FX thread.
9. The UI does not edit a busy network: editable tables bind `editable` to `networkBusyProperty().not()`, custom editors
   use `TableColumnSupport.disableUnlessTableEditable`, computations go through `refuseIfBusy`.
10. LF/SA run on the root network, never a subnetwork.

UI resources
11. `fx:id` ↔ `@FXML` field, `onAction="#m"` ↔ method, `fx:controller` ↔ class stay consistent; `fx:include` controllers
    are injected as `<fx:id>Controller`.
12. Every `FXMLLoader` receives `Messages.bundle()`; every UI string exists in both `messages.properties` and
    `messages_fr.properties`; `reports*.properties` stay a separate bundle.
13. Long-running and failed operations are reported through `NotificationsModel` (RUNNING → `replace` with outcome),
    failures also logged with SLF4J.

Build
14. Checkstyle must pass (sorted property keys, headers, formatting) — it fails the build.
15. Do not add dependencies, re-add Monocle, set `testfx.headless`, or remove the `org.openjfx` exclusion from
    `copy-dependencies`.
16. New table views are added to `EditLockTest`; new views get a controller test (and an `*EmptyStateTest` for
    table/detail views).

## Review checklist

- Does the change follow the closest existing implementation rather than a new pattern?
- Any new listener on `MainModel` registered through `listenerManager`? Any new service cancelled on dispose or tracked
  in `NetworkStudy`?
- Any PowSyBl call that can be slow on the FX thread? Any background job missing `AbstractNetworkTask`?
- Any new editor not going through `TableColumnSupport`? Busy lock respected?
- New strings in both bundles, keys sorted? FXML ids/handlers matching?
- New SLD/NAD parameter field added to `DesktopParametersJson`'s `Binder`? Form calls its `onChange` hook?
- New navigation type handled in `NavigationEvent.describe()` and `MainController.onNavigationEvent`?
- Tests added/updated and `mvn clean install` (or affected tests + `mvn checkstyle:check@default`) run?

## Known inconsistencies (documented, not to be "fixed" silently)

- **Navigation dispatch is a long `if/else` chain** in `MainController.onNavigationEvent`, while
  `NavigationEvent.describe()` is an exhaustive `switch`. Both must be extended for a new view; only the switch is
  compiler-checked.
- **Table controllers have two shapes**: most extend `AbstractEquipmentTableController`, but
  `SubstationsTableController`, `VoltageLevelsController`, `ComponentsController`, `SecurityAnalysisResultsController`
  and `SwitchesController` extend `AbstractDisposableController` directly and re-implement parts (e.g. the busy-lock
  binding). Prefer the abstract base for a new flat equipment table.
- **Model injection**: most controllers use `setMainModel(MainModel)`; `LogsViewController` uses
  `setLogsModel(LogsModel)`; `MainController` uses constructor injection through a controller factory.
- **Dialogs**: modal dialogs are built in code (static `show` helpers), secondary windows are FXML; there is no
  single dialog abstraction. Follow the form matching your need (see ui-conventions).
- **Inline styles** exist in a few places (`SplashScreen`, `DropImportDialog`, `LogsViewController`) although
  `styles.css` classes are the dominant pattern.
- **Contingency list refresh** is explicit (`refreshInstantiatedTable()`) rather than listener-based, because
  `ListenerManager` cannot detach a single listener.
- **File I/O on the FX thread**: contingency list and parameters JSON import/export run synchronously in event
  handlers (small files), unlike network import/export which run in services.
- **Network import** uses a plain `Task` rather than `AbstractNetworkTask` — intentional, since the network is not loaded
  yet.
