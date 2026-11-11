# Invariants and known inconsistencies

Each invariant is stated briefly; the linked document holds the detail. Change a rule there and here together.

## Invariants (preserve in every change)

State and communication
1. `MainModel` (one instance per app, survives language reloads) is the only shared state. Views communicate only
   through it and `NavigationHistory`; no static mutable state, event bus, or controller-to-controller references
   (except a parent controller driving its `fx:include`d children). [architecture](architecture.md#shared-state-mainmodel-networkstudy-scenemodel)
2. Per-root-network state lives in `NetworkStudy` (`MainModel.getStudy(network)`); per-window state in `SceneModel`.
3. Creating or removing network objects goes through `NetworkChanges.apply`/`remove`, which fires the structure change
   and the update. Every other mutation from the UI is followed by `mainModel.setUpdate()` / `setUpdate(voltageLevel)`
   (table editors through `TableColumnSupport.onEditApplied`, detail views through `onEdit`).
   [editing](editing.md#editing-cells)
4. Only `MainController` swaps the center view, in response to `NavigationHistory.currentEventProperty()`. There is a
   single history, the main window's: views navigate through `SceneModel.navigate` / `record`, never through
   `getNavigationHistory()` directly. A user-initiated change of the selected network goes through
   `MainModel.confirmNetworkChange` (the history guard does it for navigation). [architecture](architecture.md#navigation)

Lifecycle
5. Swappable controllers extend `AbstractDisposableController`; model listeners go through
   `listenerManager.listen(...)`; `dispose()` releases listeners, services and sub-controllers and calls
   `super.dispose()`. [architecture](architecture.md#controller-lifecycle)
6. Every window other than the main window, the splash screen, modal dialogs and the memory window goes through
   `ui/window/SeparateWindows`; never create a `Stage` for a view elsewhere, never raise a separate window from a focus
   listener. Network-bound views close when the selection changes to another root network; windows stay open across
   a language reload. [windows](windows.md)

Threading
7. No slow PowSyBl call on the FX thread. Background network reads/writes are `AbstractNetworkTask`s created in
   `Service.createTask()`; model/UI updates happen in the service's FX-thread handlers. [threading](threading.md)
8. Inputs to background work are captured (and parameters copied) on the FX thread. [threading](threading.md#rules)
9. The UI does not edit a busy network: every editor is disabled or refused while busy.
   [threading](threading.md#the-busy-lock)
10. Load flow, security and sensitivity analysis run on the root network, never a subnetwork.
    [domain](domain-and-persistence.md#networks-and-subnetworks)
11. Violation indications read the study's cached violations, never check limits on the fly.
    [domain](domain-and-persistence.md#networks-and-subnetworks)

UI resources
12. `fx:id` ↔ `@FXML` field, `onAction="#m"` ↔ method, `fx:controller` ↔ class stay consistent; `fx:include` controllers
    are injected as `<fx:id>Controller`. [ui-conventions](ui-conventions.md#screens-fxml--controller)
13. Every `FXMLLoader` receives `Messages.bundle()`; every UI string exists in both `messages.properties` and
    `messages_fr.properties`, worded as PowSyBl's own translations; `reports*.properties` stay a separate bundle.
    [ui-conventions](ui-conventions.md#internationalization)
14. No inline styles: no `setStyle(...)` in Java nor `style="..."` in FXML; styling goes through classes in
    `styles.css`. [ui-conventions](ui-conventions.md#styling-and-icons)
15. Editors go through `CellFields`/`TableColumnSupport` (tables) and `DetailForm`/`DetailPart` (detail views), never
    ad-hoc cell factories. [editing](editing.md#editing-cells)
16. Long-running and failed operations are reported through `NotificationsModel` (RUNNING → `replace` with outcome),
    failures also logged with SLF4J. [architecture](architecture.md#notifications)

Build
17. Checkstyle must pass — it fails the build. [build-and-testing](build-and-testing.md#checkstyle-fails-the-build)
18. Do not add dependencies, re-add Monocle, set `testfx.headless`, or remove the `org.openjfx` exclusion from
    `copy-dependencies`; a JavaFX module used by the code is listed in the packaging scripts.
    [build-and-testing](build-and-testing.md#packaging)
19. `ArchitectureTest` passes: a new package goes into a layer, code using JavaFX UI goes under `ui`, and a forbidden
    dependency is fixed, never exempted. [architecture](architecture.md#enforced-layering)
20. New table views, read-only results views included, are added to `EditLockTest`; new views get a controller test
    (and an `*EmptyStateTest` for table views other than the read-only results views).
    [build-and-testing](build-and-testing.md#conventions)
21. `README.md` stays in sync with the features: a user-visible feature added, removed or changed updates "What it
    can do" (and drops the matching item from "What it cannot do yet"); new logic of the app's own rather than
    PowSyBl's is added to the intro's list of exceptions; a new import/export format is listed only if its converter
    is on the classpath (`mvn -o dependency:list`).
22. `docs/ai` stays in sync with the code: a change to documented behavior updates its document in the same change.

## Review checklist

- Does the change follow the closest existing implementation rather than a new pattern?
- Any new listener on `MainModel` registered through `listenerManager`? Any new service cancelled on dispose or tracked
  in `NetworkStudy`?
- Any PowSyBl call that can be slow on the FX thread? Any background job missing `AbstractNetworkTask`?
- Any new editor not going through `TableColumnSupport`/`DetailForm`? Busy lock respected? Creations/removals through
  `NetworkChanges`?
- New strings in both bundles, keys sorted? FXML ids/handlers matching? Styling through `styles.css` classes, no inline style?
- New SLD/NAD/GUI parameter field added to `ApplicationParametersJson`'s `Binder`? Form calls its `onChange` hook?
- New navigation type handled in `NavigationEvent.describe()` and `MainController.onNavigationEvent` (or
  `onNetworkObjectTableEvent`)?
- User-visible feature change reflected in `README.md`? Documented behavior change reflected in `docs/ai`?
- New package added to a layer of `ArchitectureTest`? New logic outside `ui`, with plain JUnit tests?
- Tests added/updated and `mvn clean install` (or affected tests + `mvn checkstyle:check@default`) run?

## Known inconsistencies (documented, not to be "fixed" silently)

- **Navigation dispatch is a long `if/else` chain** in `MainController.onNavigationEvent` (plus
  `onNetworkObjectTableEvent`), while `NavigationEvent.describe()` is an exhaustive `switch`. Both must be extended for
  a new view; only the switch is compiler-checked.
- **Table controllers have several shapes**: `AbstractEquipmentTableController` (and `AbstractBusesController`),
  `AbstractNetworkObjectTableController`, and the controllers extending `AbstractDisposableController` directly that
  re-implement parts (e.g. the busy-lock binding) - see [editing](editing.md#equipment-tables). Prefer the abstract base
  for a new flat equipment table.
- **Model injection** differs per controller (`setSceneModel`, `setMainModel`, `setModels`, constructor) - see
  [architecture](architecture.md#controller-lifecycle).
- **Dialogs**: modal dialogs are built in code (mostly static `show` helpers; `RemovalDialog.confirmThenRemove` and the
  `ElementPickerDialog` subclass differ), secondary windows are FXML; there is no single dialog abstraction. Follow the form
  matching your need (see ui-conventions).
- **File I/O on the FX thread**: contingency list, limit scalings, sensitivity factors and parameters JSON
  import/export run synchronously in event handlers (small files), unlike network import/export which run in services.
- **Network import and study loading use a plain `Task`** rather than `AbstractNetworkTask` — intentional, since the
  networks are not loaded yet; **study saving** marks its networks busy with `markBusy` instead, writing several at
  once (see [threading](threading.md#rules), rule 5).
- **One inline style remains**: `ui/about/about-view.fxml` (monospace license text), against invariant 14.
