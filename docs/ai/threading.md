# Threading and background work

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`.

## Rules

1. **Never run slow PowSyBl work on the JavaFX Application Thread** (network import/export, load flow, security
   analysis, diagram rendering, search index build, map snapshot). Use a JavaFX `Service` creating a `Task`.
2. **UI and model updates happen on the FX thread** — in `setOnSucceeded` / `setOnFailed` / `setOnCancelled`
   handlers, or via `Platform.runLater` from non-`Task` threads (only `MapController`'s tile pool and
   `AbstractNetworkTask` do this). `MainModel`, `NetworkStudy` and the sub-models are not thread-safe and are FX-thread only.
3. **IIDM is not thread-safe.** Any background job that reads or writes a *loaded* network is an
   `…/AbstractNetworkTask.java`. It marks the root network busy from task creation until `compute()` actually
   returns (even after a cancel, since most PowSyBl calls ignore interruption). It must be created on the FX thread,
   i.e. inside `Service.createTask()`.
4. **Capture inputs on the FX thread** in `createTask()` (current container, parameter copies) and pass them into the
   task. Parameter objects edited in place by the parameters window must be copied first:
   `LoadFlowParameters.copy()`, `MainController.copyParameters(SecurityAnalysisParameters)`,
   `DesktopParametersJson.copy(...)` for SLD/NAD.
5. Network import (`NetworksController.runImport`) uses a plain `Task` because the network isn't loaded (no study
   to mark busy) yet. Format detection on dropped files does too.

## The busy lock

- `MainModel.markBusy(network)` / `NetworkStudy.busyProperty()` count running jobs per root network;
  `MainModel.networkBusyProperty()` mirrors the selected network's study.
- While busy, the UI must not edit the network:
  - Tables with editable columns bind `TableView.editable` to `networkBusyProperty().not()`
    (`AbstractEquipmentTableController.setMainModel`); custom editor cells follow via
    `TableColumnSupport.disableUnlessTableEditable`.
  - Diagram switch clicks, merge/detach, renaming the network, and starting a LF/SA are refused
    (`MainController.refuseIfBusy` adds a `main.networkBusy` error notification).
- `EditLockTest` verifies every table view's editors are disabled while busy and enabled after.
- The search box's background query only touches the Lucene index (`NetworkSearchIndex.searchCandidates`); resolving
  candidates to network objects happens back on the FX thread.

## Canonical patterns

**Computation with notification** — `MainController.onLoadFlow`:

```text
check selection → use root network (never a subnetwork) → refuseIfBusy → copy parameters
Service { createTask() → new AbstractNetworkTask<>(mainModel, network) { compute() { ... } } }
add RUNNING notification (cancel = service::cancel)
setOnSucceeded → store result in NetworkStudy, mainModel.setUpdate(), addReport, replace notification
setOnFailed    → LOGGER.error(...), replace with error notification + "view logs" action
setOnCancelled → replace with cancelled notification
study.trackComputation(service) → service.start()
```

Cancellable PowSyBl calls: `LoadFlow.run()`/`SecurityAnalysis.run()` block on `CompletableFuture.join()`, which
ignores interruption, so the code uses `runAsync(...)` + `future.get()` and cancels the future on
`InterruptedException`. Follow the same approach for other `runAsync`-capable PowSyBl APIs.

**Latest-wins rendering** — `SubstationsController.sldRenderService` / `nadRenderService`: a long-lived `Service`
field restarted on each change (`restart()` drops the previous result); rendering is lazy (only while the tab is
shown). `MapController.networkDataService` follows the same shape.

**Tracked services**: services that must survive a language reload or be cancelled when a network is removed are
registered on the `NetworkStudy` (`trackComputation`, `trackSearchIndexBuild`), not held only by a controller.
Controller-owned services are cancelled in the controller's `dispose()`.

**Results arriving after removal**: success handlers check the network is still loaded before storing anything
(see `MainController.ensureSearchIndex`, which closes an index whose network was removed meanwhile).

## Testing background code

`…/NetworkTaskTest` (plain JUnit, starts the toolkit with `Platform.startup`) covers the busy semantics of
`AbstractNetworkTask`. UI tests wait for FX events with `WaitForAsyncUtils.waitForFxEvents()` and mutate state inside
`interact(...)`.
