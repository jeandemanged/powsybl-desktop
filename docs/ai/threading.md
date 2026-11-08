# Threading and background work

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`.

## Rules

1. **Never run slow PowSyBl work on the JavaFX Application Thread** (network import/export, load flow, security
   analysis, violations computation, diagram rendering, search index build, map snapshot). Use a JavaFX `Service`
   creating a `Task`.
2. **UI and model updates happen on the FX thread** — in `setOnSucceeded` / `setOnFailed` / `setOnCancelled`
   handlers, or via `Platform.runLater` from threads that aren't a `Task` (`MapController`'s tile pool,
   `AbstractNetworkTask`'s release, the security analysis' cancel path in `MainController.onSecurityAnalysis`).
   `MainModel`, `NetworkStudy` and the sub-models are not thread-safe and are FX-thread only. `Platform.runLater` is
   also used on the FX thread to defer work until after the current event or layout pass.
3. **IIDM is not thread-safe.** Any background job that reads or writes a *loaded* network is an
   `…/core/AbstractNetworkTask.java`. It marks the root network busy from task creation until `compute()` actually
   returns (even after a cancel, since most PowSyBl calls ignore interruption), released in the FX event reporting
   its result (before `setOnSucceeded`/`setOnFailed` handlers), so no edit can slip in between the end of the work and
   the handler storing its result (e.g. a search index missing a rename). It must be created on the FX thread,
   i.e. inside `Service.createTask()`.
4. **Capture inputs on the FX thread** in `createTask()` (current container, parameter copies) and pass them into the
   task. Parameter objects edited in place by the parameters view must be copied first:
   `LoadFlowParameters.copy()`, `SecurityAnalysisRun.copyParameters(SecurityAnalysisParameters)`,
   `ApplicationParametersJson.copy(...)` for SLD/NAD.
5. Plain `Task`s are only for work not touching a loaded network: network import (`NetworksController.runImport`,
   `runImports`, calling `NetworkIO.importNetwork`) and sample networks (`loadSample`), whose network isn't loaded yet
   (no study to mark busy); format detection of dropped files; the search box's Lucene query.

## The busy lock

- `MainModel.markBusy(network)` (returning an idempotent release action) / `NetworkStudy.busyProperty()` count running
  jobs per root network; `MainModel.networkBusyProperty()` mirrors the selected network's study.
- While busy, the UI must not edit the network:
  - Tables with editable columns bind `TableView.editable` to `networkBusyProperty().not()`
    (`AbstractEquipmentTableController.setSceneModel`, `AbstractNetworkObjectTableController`, and the tables
    extending `AbstractDisposableController` directly); custom editor cells follow via
    `TableColumnSupport.disableUnlessTableEditable`.
  - Detail views bind every `DetailPart`'s `editableProperty()` (`AbstractEquipmentDetailController`).
  - Structural edits (creations, removals, position edits) are refused by `NetworkChanges.apply` with a
    `main.networkBusy` error notification; the menus offering them (`EditMenus`, `TableEditActions`, the map's) are
    disabled.
  - Starting a LF/SA is refused by `MainController.refuseIfBusy` (`main.networkBusy`); diagram switch clicks by
    `ContainerDiagram.onSwitchClick`.
  - The networks view disables merge/detach and its network fields (id, name, case date, forecast).
- `EditLockTest` verifies every table view's editors are disabled while busy and enabled after.
- The search box's background query only touches the Lucene index (`NetworkSearchIndex.searchCandidates`, which
  returns objects through the index's own id map); the filtering that reads the network (`Candidates.resolve()`, the
  containers having matching equipment) happens back on the FX thread.

## Canonical patterns

**Computation with notification** — `MainController.onLoadFlow`:

```text
check selection → use root network (never a subnetwork) → refuseIfBusy → copy parameters
Service { createTask() → new AbstractNetworkTask<>(mainModel, network) { compute() { LoadFlowRun.run(...) + violations } } }
add RUNNING notification (cancel = service::cancel)
setOnSucceeded → store result and violations in NetworkStudy, mainModel.setUpdate(), addReport, replace notification
setOnFailed    → LOGGER.error(...), replace with error notification + "view logs" action
setOnCancelled → replace with cancelled notification
study.trackComputation(service) → service.start()
```

Cancellable PowSyBl calls: `LoadFlow.run()`/`SecurityAnalysis.run()` block on `CompletableFuture.join()`, which
ignores interruption, so the code uses `runAsync(...)` + `Futures.await(future)`, which cancels the future on
`InterruptedException`. Follow the same approach for other `runAsync`-capable PowSyBl APIs. Keep the computation
itself in a toolkit-free class (`LoadFlowRun`, `SecurityAnalysisRun`, `NetworkIO`) tested with plain JUnit; the
controller only wires it to the `Service`, the notifications and the study.

**Latest-wins rendering** — `ContainerDiagram.renderService`: a long-lived `Service` field restarted on each change
(`restart()` drops the previous result); rendering is lazy (only while the diagram is shown).
`MapController.networkDataService` follows the same shape.

**Tracked services**: services that must survive a language reload or be cancelled when a network is removed are
registered on the `NetworkStudy` (`trackComputation`, `trackSearchIndexBuild`), not held only by a controller.
Controller-owned services are cancelled in the controller's `dispose()`.

**Results arriving after removal**: success handlers check the network is still loaded before storing anything
(see `MainController.ensureSearchIndex`, which closes an index whose network was removed meanwhile).

## Testing background code

`core/NetworkTaskTest` (plain JUnit, starts the toolkit with `Platform.startup`) covers the busy semantics of
`AbstractNetworkTask`. UI tests wait for FX events with `WaitForAsyncUtils.waitForFxEvents()` and mutate state inside
`interact(...)`.
