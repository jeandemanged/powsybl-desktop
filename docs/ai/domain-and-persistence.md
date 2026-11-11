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
- Merging and detaching are done in `NetworksController`; detaching goes through `MainModel.detachSubnetworks`, which
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
  only to the run's parameters copy by `SensitivityAnalysisRun.copyParameters`), SLD (`DesktopSldParameters`), NAD (`DesktopNadParameters`), per-format network import/export
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
  (de)serialization by `…/parameters/ApplicationParametersJson.java`: LF/SA/sensitivity via PowSyBl's `JsonLoadFlowParameters` /
  `JsonSecurityAnalysisParameters` / `JsonSensitivityAnalysisParameters` (the analyses without their embedded load
  flow parameters), the sensitivity mode as a top-level `sensitivityAnalysisMode`, network formats as string maps, SLD/NAD/GUI field by field through a `Binder` that
  declares each field once for write and read. **A new SLD/NAD/GUI field must be added to the `Binder` functions or it
  won't be saved.** Reading is all-or-nothing; missing fields keep defaults.
- Open Load Flow's sensitivity analysis reads the debug directory of its `OpenSensitivityAnalysisParameters`
  extension, not `SensitivityAnalysisParameters.getDebugDir()`: the tab edits the extension's.
- Parameter UI strings: `parameters.*`, `loadflow.param.*`, `securityAnalysis.param.*`, `sensitivityAnalysis.param.*`. Importer/exporter parameter
  forms are generated by `ui/parameters/ParameterFormBuilder` from PowSyBl `Parameter` metadata.

## Persistence

There is no database. Persistent state is:

| What | Where | Code |
|---|---|---|
| Parameters | `config.json` in `%APPDATA%\powsybl-desktop`, `~/Library/Application Support/powsybl-desktop`, or `~/.powsybl-desktop` | `…/parameters/ParametersConfigFile.java` (load at startup in `MainApplication.start`; an unreadable file is moved to `config.json.<timestamp>.bak`, replaced by defaults and reported as an error notification); saved from `ParametersController` |
| Max heap | The `-Xmx` line of the packaged app's launcher `.cfg` (see [build-and-testing.md](build-and-testing.md#packaging)) | `…/memory/MaxHeapConfig.java`, edited from the Memory window |
| UI language | Java Preferences API | `…/utils/LanguagePreferences.java` |
| Last file-chooser directory | Java Preferences API | `…/ui/controls/FileChooserPreferences.java` |
| Networks | User files, imported/exported through PowSyBl `Importer`/`Exporter` | `NetworksController` (import: plain `Task`; export: `AbstractNetworkTask`), `NetworkIO` |
| Studies (parameters, networks, contingency lists, limit scalings, sensitivity factors, results, reports, logs) | User-chosen `.psbx` zip file | `…/study/StudyArchive.java`, see [Studies](#studies) |
| Contingency lists, limit scalings, sensitivity factors, parameters, reports, diagrams | User-chosen JSON/text/SVG/PNG files | `ContingencyListsIO`, `LimitScalings`, `SensitivityFactorsIO`, `ApplicationParametersJson`, `ReportsController`, `DiagramPaneController` |

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
