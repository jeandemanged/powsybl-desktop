# Domain logic and persistence

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`.

## The domain is PowSyBl

The app has almost no domain model of its own: it displays and edits PowSyBl IIDM objects (`Network`, `Substation`,
`VoltageLevel`, `Load`, `Generator`, ...) directly, and runs PowSyBl computations. Business rules (validation of
values, topology, convergence) come from PowSyBl; the app surfaces them (e.g. a `PowsyblException` thrown by a setter
becomes an edit-error popup, see [editing.md](editing.md#editing-cells)).

**Check PowSyBl's actual behavior before relying on it.** Module versions are not written in `pom.xml`: every PowSyBl
module used (directly, or at runtime through ServiceLoader such as the format converters, `powsybl-iidm-impl`,
`powsybl-config-classic` and the RDF4J triple store) is declared without a version and managed by the
`com.powsybl:powsybl-dependencies` BOM imported in `dependencyManagement` at `powsybl-dependencies.version`, under the
`powsybl-parent` parent. The BOM manages Logback as `runtime`, so the Logback dependencies state `compile` explicitly.
One exception: OpenRAO (`open-rao-*`) is newer than the BOM's, at `powsybl-open-rao.version`, through its own BOM
imported first; that BOM imports an older powsybl-core, so the powsybl-core BOM is imported before it at
`powsybl-core.version`, which must stay the version `powsybl-dependencies` manages (imports are resolved in order, the
first managing an artifact winning). Check with `mvn dependency:tree` that no other version moves on an upgrade.
Read the BOM version in `pom.xml`'s properties, and resolve the in-use ones with `mvn -o dependency:tree` before reading a PowSyBl sources jar; the local
Maven cache may contain unrelated versions.

## Networks and subnetworks

- `MainModel.getNetworks()` contains **root** networks only. The selected network (`MainModel.getNetwork()`) may be a
  subnetwork; `network.getNetwork()` gives its root. `MainModel.getStudy(n)` accepts either.
- Navigation states hold the equipment's own network (`getParentNetwork()`). `MainController.networkToSelect` keeps
  the user's selection within a root network: a selected subnetwork stays selected for its own equipment, a root network
  selected by the user stays selected for its subnetworks' equipment. Only equipment held by the root network (e.g. a
  tie line between subnetworks, which a subnetwork's views don't list) selects the root network, left again for the
  next subnetwork equipment navigated to; another subnetwork's equipment selects that subnetwork.
- Merging goes through `MainModel.mergeNetworks`, which merges only if `NetworkMerges.refusal` finds nothing powsybl
  would refuse (several variants, shared ids/aliases/voltage angle limit ids) and returns the refusal otherwise:
  powsybl checks each network only as it merges it, so a failure leaves the networks merged before it emptied and
  unusable (a later merge of them throws an NPE in `detachDcTopologyModel`).
- Detaching is done in `NetworksController` and goes through `MainModel.detachSubnetworks`, which
  drops navigation entries, search indexes and map views referring to the detached subnetworks (the subnetwork object is
  left empty by `Subnetwork.detach()`).
- Load flow, security and sensitivity analysis always run on the **root** network (a subnetwork-scoped run would ignore tie lines
  to the rest of the merged network) — see the comment in `MainController.onLoadFlow`.
- Results are stored per root network in `NetworkStudy` (`setLoadFlowResult`, `setSecurityAnalysisResult`,
  `setSensitivityAnalysisResult` with the matrices and AC/DC it was computed with, which the result doesn't hold). The
  network itself carries the solved values (P/Q/I...) written by the load flow; views re-read them on
  `updateProperty()`.
- Limit violations (`violations/NetworkViolations`: current, active and apparent power limits of branches,
  three-winding transformers and unpaired boundary lines, plus bus voltages) are computed off the FX thread once per
  loaded root network (`MainController.computeViolations`), after each load flow run (in its task) and on every change
  of the enabled limit scalings, then cached in `NetworkStudy.setViolations`, with every equipment's loading
  (percentage of its scaled permanent limits, violated or not). Edits don't recompute them. Every violation indication
  (Violations view, the equipment tables' "Loading" and "Voltage Range" columns) reads this cache; never check limits
  on the fly in a cell. Detection takes a `LimitsComputer`: `LimitScalings.limitsComputer(enabled scalings)` (see
  [Limit scalings](#limit-scalings)); `FlowViolation.permanentLimit` is the scaled one.
  The Security Analysis Results view shows the security analysis' own violations the same way, converted by
  `FlowViolation.of`/`VoltageViolation.of` (permanent limit, scaled by the scalings the analysis ran with for the
  state's contingency, and bus-view bus resolved against the network). Its
  post-contingency rows also show the pre-contingency values: the security analysis runs with a pre-contingency only
  `StateMonitor` of all equipment (`SecurityAnalysisRun.preContingencyMonitor`), whose bus results are
  mapped to bus-view buses whatever their id kind (bus breaker view bus, busbar section, bus view bus).

## Computations

- Load flow: `…/loadflow/LoadFlowRun.java` (`LoadFlow.runAsync(network, LoadFlowRunParameters...)`) with Open Load Flow
  parameters (`OpenLoadFlowParameters` extension edited by `LoadFlowParametersController`). Outcome classified by
  `…/loadflow/LoadFlowConvergence.java` (converged / partially / not converged) — completing without exception does
  not mean converged.
- Security analysis: `…/security/SecurityAnalysisRun.java` (`SecurityAnalysis.runAsync`) with the enabled contingency
  lists of the study, aggregated in a `ListOfContingencyLists` and de-duplicated by `ContingencyNames.deduplicate`. A
  non-converged pre-contingency result is reported as an error. OpenLoadFlow's multi-threaded analysis switches the
  network to thread-local variants while running; `SecurityAnalysisRun.prepare` makes that switch itself beforehand on
  the FX thread (`…/network/VariantMultiThreadAccess.java`), setting the FX thread's and the task's working variant,
  and `restoreVariantAccess` undoes it once the computation is over (also after a cancel, once the future completes),
  so that the FX thread can still read the network meanwhile. The desktop code otherwise never uses variants.
- Sensitivity analysis: `…/sensitivity/SensitivityAnalysisRun.java` (`SensitivityAnalysis.runAsync`), the same way:
  contingencies resolved like the security analysis', variant switch through `VariantMultiThreadAccess` (Open Load
  Flow's AC sensitivity analysis is multi-threaded when its thread count is above 1). Users define factors as
  `SensitivityFactorMatrix`es (functions x variables, one contingency context), expanded by
  `SensitivityFactor.createMatrix`; PowSyBl's flat factor list can't keep that grouping, so
  `SensitivityFactorsIO` writes the app's own JSON for them, PowSyBl's factors only for import/export (read back
  grouped exactly: the functions of one kind of factor sharing the same variables make a matrix) and PowSyBl's
  format for variable sets. A single factor of a kind Open Load Flow doesn't compute fails the whole analysis:
  `OlfSensitivityFactorSupport` mirrors its rules (Open Load Flow 2.4.0, AC and DC, variable sets), and
  `OlfSensitivityFactorSupportTest` runs Open Load Flow on every kind of factor: update both on an upgrade.
  The matrices and variable sets live in `NetworkStudy.getSensitivityFactorMatrices()` /
  `getSensitivityVariableSets()`, edited by `ui/sensitivity/SensitivityFactorsController` (**Inputs** >
  **Sensitivity Factors** in the toolbar): elements are picked among `SensitivityElements.candidates`, and a matrix or variable set
  `SensitivityAnalysisRun.problems` finds fault with is marked and tells why. `SensitivityElements.isEligible`
  mirrors Open Load Flow 2.4.0's lookups and PowSyBl's `SensitivityFactor.resolveBusId` (a bus voltage function's id
  is a bus view bus or what `IdBasedBusRef` resolves to one); `SensitivityElementsTest` runs Open Load Flow on every
  kind of element of three networks to check it. Unknown branch parameter and pilot point variables, merely skipped
  by Open Load Flow, are refused too. `MainController.onSensitivityAnalysis` runs the matrices against the enabled
  contingency lists, after refusing when the study has no matrices, or with every reason
  (`SensitivityAnalysisRun.problems`: unsupported kinds,
  ineligible elements, unknown variable sets or contingency, invalid variable set members) rather than letting Open
  Load Flow fail on the first one. A failed base case throws; failed contingencies
  only have a `FAILURE` state status (partial success notification).
- The Sensitivity Analysis Results view (`ui/network/tables/SensitivityAnalysisResultsController`) shows one state at
  a time, optionally one matrix, through `SensitivityResultRows`: a state's factors as their contingency context
  says, mapped back to their matrix by the run's matrices (the result's factors are their expansion, in order). Open
  Load Flow doesn't report a value (nor its function reference) below the thresholds: shown as "Below threshold",
  told apart from NaN ("Not computed"). It tells when the study's matrices differ from the run's. The result holds
  contingency ids only: `ContingencyNames.displayName(String, Network)` names them after the element of the same id.
  `SensitivityTexts` names the function and variable types.
- Remedial action optimization: `…/rao/RaoRun.java`, OpenRAO's search tree RAO (`Rao.find("SearchTreeRao")`), or FastRAO
  (`"FastRao"`, an app setting) which runs it on the worst CNECs, adding some until the result holds for all: not an
  alternative optimizer, every search tree parameter applies to it too (its inner RAO is hard-coded in OpenRAO 7.5.1) with a CRAC (contingencies, CNECs, remedial actions). Unlike the other runs it works on a **copy of the network**
  (`NetworkSerDe.copy`, binary, in its own daemon thread) and of the CRAC (`CracIO.copy`, through JSON, in `prepare` on
  the FX thread). On the network itself (OpenRAO 7.5.1) it switches the working variant while running, so the FX thread
  would read intermediate states; with thread-local variants (`VariantMultiThreadAccess`) its multi-threaded network
  pool silently falls back to the initial situation; it leaves variants behind, failing the next run ("Target variants
  already exist"); and it adds network actions to the CRAC for HVDC range actions. Its result is never applied to the
  network (OpenRAO has no API for that). OpenRAO swallows interruptions: a cancelled run is abandoned, its thread
  finishing on the copies. Most failures come back as a failed result (`ComputationStatus.FAILURE`, whose other
  getters throw) rather than an exception: `compute` turns it into a `RaoFailedException` with the execution details
  and the report. `RaoRun.problems` refuses beforehand what OpenRAO's `RaoUtil.checkParameters` would fail on for
  inputs the application never gives (GLSKs, reference program: relative margins, loop flows), costly min margin
  parameters missing for `MIN_COST`, HVDC AC emulation, the PST model with elementary action limits, curative usage
  limits, FastRAO's single curative instant, and the CRAC's elements missing from the network (edited since the import):
  update it on an OpenRAO upgrade.
  `MainController.onRao` (**Run** > **Remedial Action Optimization**) runs the study's CRAC (`NetworkStudy.cracProperty()`)
  after refusing when there is none, or with every `problems` reason, or when the CRAC no longer reads against the
  network (`prepare`'s copy fails); the result is kept with the run's CRAC copy it refers to and AC/DC
  (`NetworkStudy.setRaoResult`). A `RaoFailedException`'s report is still added. The outcome notification tells a
  partial failure (`ComputationStatus.PARTIAL_FAILURE`) and flows still not secure (`isSecure(FLOW)`: OpenRAO's results
  implement the flow check only, angle and voltage CNECs being OpenRAO's monitoring module's job) as partial successes.
- The RAO Results view (`ui/network/tables/RaoResultsController`, **Results** > **Remedial Action Optimization Results**)
  shows the result through `…/rao/RaoResultRows.java`, against the run's CRAC copy (`NetworkStudy.getRaoCrac()`): a
  summary, the flow CNECs (flows and margins with `null` as the optimized instant for the initial situation, the
  preventive instant, then the CNEC's own; most limiting first, negative margins in red), the activated remedial actions
  by state (`STATE_ORDER`) with range action set points and PST taps, and the costs by instant. A value the result
  refuses (`OpenRaoException`: a failed state, an instant not optimized) is NaN, shown "-". It tells when the study's
  CRAC is no longer the one the result was computed with (`isRaoResultOutdated`: `getRaoSourceCrac` compared by
  identity, and the CRAC revision at the run). OpenRAO's CRACs (and other CRAC objects) define `equals` by id: listen to
  `NetworkStudy.cracRevisionProperty()` (bumped by `setCrac` and by every edit, `cracEdited()`), or to the CRAC property
  with an `InvalidationListener`, a `ChangeListener` missing the replacement of a CRAC by another of the same id.
- CRAC files: `…/rao/CracIO.java` reads every format an OpenRAO importer handles (JSON, CSE, CIM, NC, FlowBased
  constraint) into a `CracCreationContext`, whose creation report tells what was ignored or why it failed (a null
  CRAC), and writes OpenRAO's JSON. It picks the importer itself, leaving out the `Network` one (the CRAC generator),
  which claims any file it can read as a network and then ignores it. OpenRAO's JSON importer needs current limits on
  the CNECs' branches.
- The CRAC is `NetworkStudy.cracProperty()` (with `getCracCreationReport()`, its import's report, set together by
  `setCrac`), shown by `ui/rao/CracController` (**Inputs** > **CRAC**): contingencies, CNECs, remedial actions (sorted
  by id, CNECs by state), the selected one's details, the import report. Objects whose network elements are missing
  from the network (`…/rao/CracElements.java`, also used by `RaoRun.problems`) are marked, refreshed on
  `MainModel.updateProperty()`. Import: the file is read and its format detected (`CracIO.format`, parsing it) in a
  plain `Task`, with a copy of the CRAC Creation parameters (below); `CracImportDialog` asks the file's own values,
  only for the formats having some (`…/rao/CracImportParameters.java`: the time stamp of CIM, NC and flow-based
  constraint documents, NC's capacity calculation region, pre-filled from the tab and from the last import of the
  format), then the CRAC is created in an `AbstractNetworkTask` (it reads the network). A failed creation (null CRAC) keeps the CRAC in
  place, its report reachable from the error notification. OpenRAO 7.5.1's CRAC creation parameters serializers write
  time stamps with a literal 'Z' without converting them to UTC: `CracImportParameters.copy` restores them after its JSON
  round trip. Export writes OpenRAO's JSON, synchronously.
- CRAC editing (instants, contingencies) goes through `…/rao/CracEdits.java`, within OpenRAO 7.5.1's API: a new
  CRAC (preventive, outage, an optional auto, curative instants), instants appended (never removed; an auto instant only
  before any curative one), contingencies added (empty, or from the enabled contingency lists, skipping ids already in
  the CRAC and contingencies invalid for the network), their elements changed in place (PowSyBl's `Contingency` is
  mutable: states and CNECs keep referring to it), renamed (a new contingency) or removed only when no CNEC or usage rule
  refers to them. OpenRAO names a contingency without a name by its id. A refused edit throws an
  `IllegalArgumentException` telling why. Edits are in place, on the FX thread, each followed by
  `NetworkStudy.cracEdited()`; the CRAC view refreshes on the revision, keeping each list's selection by id and the
  selected contingency's `ContingencyForm` (so the field being edited keeps its focus). `NewCracDialog` and
  `NewInstantDialog` apply on OK, staying open with OpenRAO's reason when it refuses.
- CRAC generation (**Generate** in the CRAC view): `…/rao/CracGenerator.java` runs OpenRAO's `NetworkCracCreator`
  (crac-io-network, OpenRAO 7.5.1) with the application's `…/rao/CracGeneratorSettings.java` (the CRAC Generator
  parameters tab, `ui/parameters/CracGeneratorParametersController`) turned into `NetworkCracCreationParameters` on a
  copy of the CRAC Creation parameters. Those parameters hold lambdas (branch filters, cost and range providers) and
  have no JSON serializer: the settings are data (country sets, voltage bounds, excluded ids, text-row tables parsed by
  `CracGenerator.parseXxx`), saved in the app's own JSON, the lambdas built from them. OpenRAO requires a value for
  every instant in the multiplier maps and every post-contingency instant in the acceptable duration maps (with
  thresholds from the operational limits): missing ones are completed (multiplier 1, permanent limit), and
  `CracGenerator.problems` (shown live at the top of the tab, and refusing a generation) tells missing durations,
  unknown or reserved instants and invalid rows. Unlike OpenRAO's default, redispatching on all generators is off.
  The generation runs like an import (`AbstractNetworkTask`, report reachable from the notification).
- They all produce a `ReportNode` stored with `mainModel.addReport` (see
  [architecture.md](architecture.md#reports-and-logs)).

## Contingencies

- Lists live in `NetworkStudy.getContingencyLists()`, edited by `ui/contingency/ContingenciesController` with inline
  forms. A form edit **replaces** the list instance; the enabled flag is carried over with
  `NetworkStudy.transferContingencyListEnabled`.
- JSON import/export: `…/contingency/ContingencyListsIO.java` (Jackson + PowSyBl `ContingencyJsonModule`), kept
  toolkit-free for testing.

## Limit scalings

- PowSyBl's `LimitScaling` (`com.powsybl.security.limitscaling`, called LimitReduction before powsybl-core 7.4) is
  immutable. The ordered list lives in `NetworkStudy.getLimitScalings()` with identity-keyed enabled flags
  (`limitScalingEnabledProperty`, `transferLimitScalingEnabled`), like the contingency lists. Order matters: of
  several scalings matching a limit, `AbstractLimitsScaler` keeps the last one. `limitScalingsRevisionProperty()` is
  bumped on any change of the list or of a flag; `MainController` recomputes the violations on it. A computation
  request while the network is busy (load flow, the previous computation) waits for it to be free, requests
  meanwhile coalescing (`pendingViolations`).
- `…/limitscaling/LimitScalings.limitsComputer(scalings[, contingencyId])` wraps a `DefaultLimitScalingsApplier`.
  Monitoring only scalings scale the reported violations too (they only don't scale the conditions of remedial
  actions), but the applier only applies the scalings whose flag equals the `monitoringOnly` argument: the flag is
  cleared on copies for detection, which always asks `false`. The study, the editor, the JSON files and the security
  analysis keep the flag as set. Limits computers don't handle boundary lines: unpaired ones stay unscaled
  (`NetworkViolations`, `FlowViolation.of`, `AffectedLimits`, `OperationalLimitsPane`).
- Scaled limits are always shown next to the original ones, never instead: `OperationalLimitsPane` adds a read-only
  "Scaled Value" column in both modes (detail views' Limits tab: base case enabled scalings, part of its
  `structure()`; violations dialogs: one `ScaledLimits` per state, a security analysis' states scaling a limit set
  differently getting a column each).
- Security analysis: `SecurityAnalysisRun` passes the enabled scalings (`setLimitScalings`) and `NetworkStudy` keeps
  them with the result (`setSecurityAnalysisLimitScalings`): the results view and the map compute scaled permanent
  limits from them, per contingency, and the results view tells when they differ from the current enabled ones
  (`LimitScalings.sameContent`, through the JSON form, `LimitScaling` having no `equals`).
- Open Load Flow 2.4.0 (`LimitScalingManager.isSupported`, private) skips unsupported scalings with a log line only,
  ignores the countries of `IdentifiableCriterion`s and throws on one without nominal voltage criterion.
  `…/limitscaling/OlfLimitScalingSupport` mirrors these rules (`APPLIED`, `PARTIALLY_APPLIED`, `NOT_APPLIED`, `FAILS`):
  never hide them. The editor shows them per scaling and in its toolbar, `MainController.onSecurityAnalysis` asks to
  confirm (refuses on `FAILS`), the report gets a warning per scaling, the results view a message.
  `OlfLimitScalingSupportTest` runs Open Load Flow on each rule: update both on an Open Load Flow upgrade.
- An `IdentifiableCriterion` needs a country or a nominal voltage criterion (its constructors require them non-null).
- JSON import/export: PowSyBl's `LimitScalingListSerDeUtil` (through `LimitScalings.read`/`write`, `Path`-based). The
  format has no enabled flag: export writes all the scalings, import enables them.
- Editor: `ui/limitscaling/LimitScalingsController` (table, preview of `AffectedLimits` computed on the FX thread like
  the contingencies view's table) and `LimitScalingFormController` (one form per selected scaling, rebuilding it on
  each commit). It doesn't edit the network: no busy lock.

## Search

- `…/search/NetworkSearchIndex.java`: in-memory Lucene index per (sub)network, built in the background by
  `MainController.ensureSearchIndex` on network selection and cached in the `NetworkStudy`. Kept current in place:
  structure changes update every index of the root network (`applyStructureChange`); on `updateProperty()`, the
  selected network's index re-indexes its bus-view buses (`refreshBuses`), only the updated voltage level's when
  `setUpdate(voltageLevel)` named one. An id or name change (`Identifiable.setId`/`setName`) is re-indexed with
  `applyRename(network, identifiable, oldId)`, which swaps that one document; the detail view's id and name
  fields (`AbstractEquipmentDetailController.updateSearchIndexes`) call it on every index of the root network.
- `…/search/NetworkSearch.java`: `Kind` enum, `kindOf`, `containerOf`, `hasMatchingEquipment`. Add a new
  searchable equipment kind here and in `NetworkSearchIndex`, not per view.
- Views use `SearchBoxController` with their own set of `Kind`s. It re-runs the typed query on every
  `updateProperty()` (refreshed bus entries), keeping the current match without revealing it: revealing would move
  the view back to the match on any edit, e.g. a switch clicked in another container's diagram.

## Parameters

- Live parameters are in `…/parameters/ParametersModel.java` (via `MainModel.getParametersModel()`): load flow,
  security and sensitivity analysis (whose embedded `LoadFlowParameters` are kept wired to the single authoritative
  load flow instance), the sensitivity analysis' `SensitivityAnalysisMode` (same as load flow / AC / DC, applied
  only to the run's parameters copy by `SensitivityAnalysisRun.copyParameters`), the RAO's OpenRAO `RaoParameters`
  (always with the search tree RAO's `OpenRaoSearchTreeParameters` extension, whose sensitivity parameters embed the
  wired `LoadFlowParameters`, and FastRAO's `FastRaoParameters`, all through `…/rao/RaoParametersSupport.java`), their
  own `SensitivityAnalysisMode` (applied by `RaoRun.copyParameters`) and whether they run through FastRAO, the CRAC Creation parameters, the CRAC Generator settings
  (`CracGeneratorSettings`, see Computations), the CRAC Creation parameters (OpenRAO's
  `CracCreationParameters`, those of a CRAC file's import, edited by `ui/parameters/CracCreationParametersController`:
  every list, map or nested structure of the formats' extensions as a `ui/controls/RowsTableEditor` table of text rows,
  read and parsed by `…/rao/CracCreationParametersSupport.java`, an invalid row leaving them unchanged and telling why;
  an extension is added once one of its values is set; time stamps are asked at the import), SLD (`DesktopSldParameters`), NAD (`DesktopNadParameters`), per-format network import/export
  `Properties` (only user-edited values), and the app's own view settings, `…/parameters/GuiParameters.java` (map
  basemap, hidden base voltages, shown violation layers and whether flows are shown, the network area diagram's
  keeping positions when centering, logs level filter and sort order, reports severity filter, whether creation
  dialogs then open the created object's details, whether exiting with networks loaded asks for confirmation
  (`MainController.confirmExit`, used by both File > Exit and the main window's close request), decimals).
- They are **edited in place** by the parameter tabs (two-way binding, no DTO). After an edit, a form calls its
  `onChange` hook, which bumps `ParametersModel.parametersChanged()` (and `sldParametersChanged()` /
  `nadParametersChanged()` for diagrams, so the displayed diagram re-renders).
- `GuiParameters` are edited both from the parameters view ("User Interface" tab, `GuiParametersController`) and from
  the map, logs and reports views' own controls and the creation dialogs. Either side writes the live `GuiParameters`
  and calls `ParametersModel.guiParametersChanged()`; every side listens to `guiParametersRevisionProperty()` to
  re-apply them (also after a reset or import, which replace the instance). Read them through
  `ParametersModel.getGuiParameters()` each time rather than keeping a reference.
- `GuiParameters` also hold the decimal places of every displayed quantity (`GuiParameters.Quantity`: voltage, angle,
  power (MW, MVAr and MVA alike), power factor, current, percentage, impedance, admittance, rho, limit scaling,
  sensitivity, coordinates), the **single source** for tables, dialogs, tooltips, the substations tree and both diagrams. SLD/NAD
  `SvgParameters` precisions are neither edited nor saved: `ContainerDiagram` copies the GUI decimals into each
  render's parameters copy. Tables format through `TableColumnSupport.doubleFormat(supplier, quantity)`, which reads
  the decimals on every render, and refresh on each GUI parameters revision. Never hardcode a `%.Nf` for a displayed
  value.
- `…/parameters/ApplicationParameters.java` (record) groups all of them (PowSyBl ones and `GuiParameters`) for
  (de)serialization by `…/parameters/ApplicationParametersJson.java`: LF/SA/sensitivity/RAO via PowSyBl's `JsonLoadFlowParameters` /
  `JsonSecurityAnalysisParameters` / `JsonSensitivityAnalysisParameters` and OpenRAO's `JsonRaoParameters` (the
  analyses and the RAO without their embedded load flow parameters), the modes and the use of FastRAO as top-level
  `sensitivityAnalysisMode`, `raoMode` and `fastRao`, the CRAC Creation parameters as `cracCreation` (OpenRAO's
  `JsonCracCreationParameters`) and the CRAC Generator settings as `cracGenerator` (their own Jackson mapping), network formats as string maps, SLD/NAD/GUI field by field through a `Binder` that
  declares each field once for write and read. **A new SLD/NAD/GUI field must be added to the `Binder` functions or it
  won't be saved.** Reading is all-or-nothing; missing fields keep defaults.
- The RAO tab (`ui/parameters/RaoParametersController`) shows OpenRAO's optional MNEC and costly min margin sections
  with a check box each; editing one of their values adds the section. OpenRAO's API can add them but not remove them:
  unticking replaces the live `RaoParameters` with a copy without the section (`RaoParametersSupport.withoutMnec` /
  `withoutCostlyMinMargins`, through their JSON), which `ParametersModel` re-wires to the load flow parameters. Loop
  flows and relative margins (they need GLSKs) are not offered, and are refused by `RaoRun.problems`.
- Open Load Flow's sensitivity analysis reads the debug directory of its `OpenSensitivityAnalysisParameters`
  extension, not `SensitivityAnalysisParameters.getDebugDir()`: the tab edits the extension's.
- Parameter UI strings: `parameters.*`, `loadflow.param.*`, `securityAnalysis.param.*`, `sensitivityAnalysis.param.*`, `rao.param.*`, `cracCreation.param.*` (tables: `cracCreation.column.*`), `cracGenerator.param.*`
  (`cracGenerator.column.*`). Importer/exporter parameter
  forms are generated by `ui/parameters/ParameterFormBuilder` from PowSyBl `Parameter` metadata.

## Persistence

There is no database. Persistent state is:

| What | Where | Code |
|---|---|---|
| Parameters | `config.json` in `%APPDATA%\powsybl-desktop`, `~/Library/Application Support/powsybl-desktop`, or `~/.powsybl-desktop` | `…/parameters/ParametersConfigFile.java` (load at startup in `MainApplication.start`; an unreadable file is moved to `config.json.<timestamp>.bak`, replaced by defaults and reported as an error notification); saved from `ParametersController` |
| Max heap | The `-Xmx` line of the packaged app's launcher `.cfg` (see [build-and-testing.md](build-and-testing.md#packaging)) | `…/memory/MaxHeapConfig.java`, edited from the Memory window |
| UI language | Java Preferences API | `…/utils/LanguagePreferences.java` |
| Last file-chooser directory | Java Preferences API | `…/ui/controls/FileChooserPreferences.java` |
| Recent studies (last 10 saved or loaded `.psbx`) | Java Preferences API | `…/core/RecentStudies.java`, see [Studies](#studies) |
| Networks | User files, imported/exported through PowSyBl `Importer`/`Exporter` | `NetworksController` (import: plain `Task`; export: `AbstractNetworkTask`), `NetworkIO` |
| Studies (parameters, networks, contingency lists, limit scalings, sensitivity factors, results, reports, logs) | User-chosen `.psbx` zip file | `…/study/StudyArchive.java`, see [Studies](#studies) |
| Contingency lists, limit scalings, sensitivity factors, CRACs, parameters, reports, diagrams | User-chosen JSON/text/SVG/PNG files (CRACs: any format OpenRAO imports) | `ContingencyListsIO`, `LimitScalings`, `SensitivityFactorsIO`, `CracIO`, `ApplicationParametersJson`, `ReportsController`, `DiagramPaneController` |

Use the existing helpers (and `FileChooserPreferences` for any new `FileChooser`) rather than adding new storage.
Network state (loaded networks, results, contingency lists, limit scalings, sensitivity factors) is not persisted
between runs, except in study files.

## Studies

File > Save Study / Load Study write and read the app's state as one zip file, `.psbx`
(`…/study/StudyArchive.java`, layout in its javadoc): a `manifest.json` (format version, app version, networks in order with the
selected one, their contingency lists' and limit scalings' enabled flags and whether their sensitivity analysis
result is DC, report count, `null` when reports are not saved), then one file per part, each written by an existing
serializer — `ApplicationParametersJson`, binary IIDM (`NetworkSerDe`, subnetworks included), `ContingencyListsIO`,
`LimitScalings` (the scalings, and the ones the security analysis result was computed with), `SensitivityFactorsIO`
(factor matrices and variable sets, the sensitivity analysis result and the matrices it was computed with, which
must expand to as many factors as the result has or the read fails), PowSyBl's load flow / security analysis result
and `ReportNode` JSON serializers — plus the logs as JSON. A reader accepts any 1.x format, ignoring unknown manifest fields
and files: the sensitivity files were added without changing the format version. Entries are written and read through the JDK zip file system (`jdk.zipfs`,
listed explicitly in the packaging scripts since jdeps can't see it), so these serializers must go through
`Files`/`Path`, never `Path.toFile()`. A write goes to a `.tmp` sibling first, moved over the target once complete.

- `…/study/Study.java` is the content: `capture` snapshots the chosen root networks and `Part`s on the FX thread
  (parameters deep-copied through their JSON), `restore` replaces the loaded networks and, when the study holds them,
  the parameters, reports and logs (FX thread). Violations and search indexes are not saved: they are rebuilt when
  the networks are added, the networks carrying the load flow's values.
- Restored logs are renumbered below 0 so that they stay ordered before the session's own (`LogsModel.restoreLogs`).
- `…/ui/study/StudyCommands.java` drives both: `SaveStudyDialog` (Data and Networks check boxes with All/None), file
  choice, then a `Service` behind an application-modal progress dialog fed by the archive's per-step `Progress`.
  Saving marks the saved networks busy (refused if one already is); loading is a plain `Task` (networks not loaded
  yet), asks to confirm replacing loaded networks and offers to save them in a study first.
- `…/core/RecentStudies.java` (`MainModel.getRecentStudies()`) is the observable list of the last 10 studies saved or
  loaded successfully, most recent first, by normalized absolute path. Only the instance `MainApplication` creates
  (`RecentStudies.persisted()`) reads and writes the Java Preferences; `new MainModel()` gets an in-memory one, so that
  tests neither show nor change the developer's list. File > Recent Studies (`MainController.updateRecentStudiesMenu`)
  and the links of the networks view's empty state (`NetworksController.updateRecentStudies`) call
  `StudyCommands.loadRecent`: the load flow above without the file chooser, a file that no longer exists being removed
  from the list with an error notification. The empty state pane is `pickOnBounds="false"` rather than mouse
  transparent, its labels being mouse transparent, so that the links are clickable and clicks elsewhere reach the tree.
