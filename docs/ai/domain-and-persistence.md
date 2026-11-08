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
- Load flow and security analysis always run on the **root** network (a subnetwork-scoped run would ignore tie lines
  to the rest of the merged network) — see the comment in `MainController.onLoadFlow`.
- Results are stored per root network in `NetworkStudy` (`setLoadFlowResult`, `setSecurityAnalysisResult`). The
  network itself carries the solved values (P/Q/I...) written by the load flow; views re-read them on
  `updateProperty()`.
- Limit violations (`violations/NetworkViolations`: current, active and apparent power limits of branches,
  three-winding transformers and unpaired boundary lines, plus bus voltages) are computed off the FX thread once per
  loaded root network (`MainController.computeViolations`) and after each load flow run (in its task), then cached in
  `NetworkStudy.setViolations`, with every equipment's loading (percentage of its permanent limits, violated or not).
  Edits don't recompute them. Every violation indication (Violations view, the equipment tables' "Loading" and
  "Voltage Range" columns) reads this cache; never check limits on the fly in a cell.
  Detection takes a `LimitsComputer` (`NO_MODIFICATIONS` for now) so that limit scalings
  (`com.powsybl.security.limitscaling`) can be plugged in later.
  The Security Analysis Results view shows the security analysis' own violations the same way, converted by
  `FlowViolation.of`/`VoltageViolation.of` (permanent limit and bus-view bus resolved against the network). Its
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
  the FX thread, setting the FX thread's and the task's working variant, and `restoreVariantAccess` undoes it once the
  computation is over (also after a cancel, once the future completes), so that the FX thread can still read the
  network meanwhile. The desktop code otherwise never uses variants.
- Both produce a `ReportNode` stored with `mainModel.addReport` (see
  [architecture.md](architecture.md#reports-and-logs)).

## Contingencies

- Lists live in `NetworkStudy.getContingencyLists()`, edited by `ui/contingency/ContingenciesController` with inline
  forms. A form edit **replaces** the list instance; the enabled flag is carried over with
  `NetworkStudy.transferContingencyListEnabled`.
- JSON import/export: `…/contingency/ContingencyListsIO.java` (Jackson + PowSyBl `ContingencyJsonModule`), kept
  toolkit-free for testing.

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
- Views use `SearchBoxController` with their own set of `Kind`s.

## Parameters

- Live parameters are in `…/parameters/ParametersModel.java` (via `MainModel.getParametersModel()`): load flow,
  security analysis (whose embedded `LoadFlowParameters` is kept wired to the single authoritative load flow
  instance), SLD (`DesktopSldParameters`), NAD (`DesktopNadParameters`), per-format network import/export
  `Properties` (only user-edited values), and the app's own view settings, `…/parameters/GuiParameters.java` (map
  basemap and hidden base voltages, logs level filter and sort order, reports severity filter, whether creation
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
  coordinates), the **single source** for tables, dialogs, tooltips, the substations tree and both diagrams. SLD/NAD
  `SvgParameters` precisions are neither edited nor saved: `ContainerDiagram` copies the GUI decimals into each
  render's parameters copy. Tables format through `TableColumnSupport.doubleFormat(supplier, quantity)`, which reads
  the decimals on every render, and refresh on each GUI parameters revision. Never hardcode a `%.Nf` for a displayed
  value.
- `…/parameters/ApplicationParameters.java` (record) groups all of them (PowSyBl ones and `GuiParameters`) for
  (de)serialization by `…/parameters/ApplicationParametersJson.java`: LF/SA via PowSyBl's `JsonLoadFlowParameters` /
  `JsonSecurityAnalysisParameters`, network formats as string maps, SLD/NAD/GUI field by field through a `Binder` that
  declares each field once for write and read. **A new SLD/NAD/GUI field must be added to the `Binder` functions or it
  won't be saved.** Reading is all-or-nothing; missing fields keep defaults.
- Parameter UI strings: `parameters.*`, `loadflow.param.*`, `securityAnalysis.param.*`. Importer/exporter parameter
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
| Contingency lists, parameters, reports, diagrams | User-chosen JSON/text/SVG/PNG files | `ContingencyListsIO`, `ApplicationParametersJson`, `ReportsController`, `DiagramPaneController` |

Use the existing helpers (and `FileChooserPreferences` for any new `FileChooser`) rather than adding new storage.
Network state (loaded networks, results, contingency lists) is not persisted between runs.
