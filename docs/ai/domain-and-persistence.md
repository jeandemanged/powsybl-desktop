# Domain logic and persistence

Paths `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`.

## The domain is PowSyBl

The app has almost no domain model of its own: it displays and edits PowSyBl IIDM objects (`Network`, `Substation`,
`VoltageLevel`, `Load`, `Generator`, ...) directly, and runs PowSyBl computations. Business rules (validation of
values, topology, convergence) come from PowSyBl; the app surfaces them (e.g. a `PowsyblException` thrown by a setter
becomes an edit-error popup, see [ui-conventions.md](ui-conventions.md#editing-cells)).

**Check PowSyBl's actual behavior before relying on it.** Module versions are not written in `pom.xml`: they come
from the `com.powsybl:powsybl-starter` BOM (`powsybl-starter.version`, currently 2026.1.0) under the
`powsybl-parent` parent (version 30). `powsybl-cgmes-conformity`, `powsybl-cgmes-gl` and `powsybl-iidm-extensions`
are pinned to `powsybl-core.version` (7.3.0) because they are outside the BOM. Resolve the in-use version with
`mvn -o dependency:tree` before reading a PowSyBl sources jar; the local Maven cache may contain unrelated versions.

## Networks and subnetworks

- `MainModel.getNetworks()` contains **root** networks only. The selected network (`MainModel.getNetwork()`) may be a
  subnetwork; `network.getNetwork()` gives its root. `MainModel.getStudy(n)` accepts either.
- Merging and detaching are done in `NetworksController`; detaching goes through `MainModel.detachSubnetworks`, which
  drops navigation entries, search indexes and map views referring to the detached subnetworks (the subnetwork object is
  left empty by `Subnetwork.detach()`).
- Load flow and security analysis always run on the **root** network (a subnetwork-scoped run would ignore tie lines
  to the rest of the merged network) — see the comment in `MainController.onLoadFlow`.
- Results are stored per root network in `NetworkStudy` (`setLoadFlowResult`, `setSecurityAnalysisResult`). The
  network itself carries the solved values (P/Q/I...) written by the load flow; views re-read them on
  `updateProperty()`.

## Computations

- Load flow: `LoadFlow.runAsync(network, LoadFlowRunParameters...)` with Open Load Flow parameters
  (`OpenLoadFlowParameters` extension edited by `LoadFlowParametersController`). Outcome classified by
  `…/loadflow/LoadFlowConvergence.java` (converged / partially / not converged) — completing without exception does
  not mean converged.
- Security analysis: `SecurityAnalysis.runAsync` with the enabled contingency lists of the study, aggregated in a
  `ListOfContingencyLists` and de-duplicated by `ContingencyNames.deduplicate`. A non-converged pre-contingency result
  is reported as an error.
- Both produce a `ReportNode` stored with `mainModel.addReport`. New report message keys go into
  `reports.properties` and `reports_fr.properties`.

## Contingencies

- Lists live in `NetworkStudy.getContingencyLists()`, edited by `contingency/ContingenciesController` with inline
  forms. A form edit **replaces** the list instance; the enabled flag is carried over with
  `NetworkStudy.transferContingencyListEnabled`.
- JSON import/export: `…/contingency/ContingencyListsIO.java` (Jackson + PowSyBl `ContingencyJsonModule`), kept
  toolkit-free for testing.

## Search

- `…/network/search/NetworkSearchIndex.java`: in-memory Lucene index per (sub)network, built in the background by
  `MainController.ensureSearchIndex` on network selection and cached in the `NetworkStudy`. Only bus-view buses are
  re-indexed after topology changes (`refreshBuses`, triggered from `updateProperty()`).
- `…/network/search/NetworkSearch.java`: `Kind` enum, `kindOf`, `containerOf`, `hasMatchingEquipment`. Add a new
  searchable equipment kind here and in `NetworkSearchIndex`, not per view.
- Views use `SearchBoxController` with their own set of `Kind`s.

## Parameters

- Live parameters are in `…/parameters/ParametersModel.java` (via `MainModel.getParametersModel()`): load flow,
  security analysis (whose embedded `LoadFlowParameters` is kept wired to the single authoritative load flow
  instance), SLD (`DesktopSldParameters`), NAD (`DesktopNadParameters`), and per-format network import/export
  `Properties` (only user-edited values).
- They are **edited in place** by the parameter tabs (two-way binding, no DTO). After an edit, a form calls its
  `onChange` hook, which bumps `ParametersModel.parametersChanged()` (and `sldParametersChanged()` /
  `nadParametersChanged()` for diagrams, so the displayed diagram re-renders).
- `…/parameters/DesktopParameters.java` (record) groups them for (de)serialization by
  `…/parameters/DesktopParametersJson.java`: LF/SA via PowSyBl's `JsonLoadFlowParameters` /
  `JsonSecurityAnalysisParameters`, network formats as string maps, SLD/NAD field by field through a `Binder` that
  declares each field once for write and read. **A new SLD/NAD field must be added to the `Binder` functions or it
  won't be saved.** Reading is all-or-nothing; missing fields keep defaults.
- Parameter UI strings: `parameters.*`, `loadflow.param.*`, `securityAnalysis.param.*`. Importer/exporter parameter
  forms are generated by `ParameterFormBuilder` from PowSyBl `Parameter` metadata.

## Persistence

There is no database. Persistent state is:

| What | Where | Code |
|---|---|---|
| Parameters | `config.json` in `%APPDATA%\powsybl-desktop`, `~/Library/Application Support/powsybl-desktop`, or `~/.powsybl-desktop` | `…/parameters/ParametersConfigFile.java` (load at startup in `MainApplication.start`; an unreadable file is moved to `config.json.<timestamp>.bak`, replaced by defaults and reported as an error notification); saved from `ParametersController` |
| UI language | Java Preferences API | `…/utils/LanguagePreferences.java` |
| Last file-chooser directory | Java Preferences API | `…/utils/FileChooserPreferences.java` |
| Networks | User files, imported/exported through PowSyBl `Importer`/`Exporter` | `NetworksController` (import: plain `Task`; export: `AbstractNetworkTask`) |
| Contingency lists, parameters, reports | User-chosen JSON/text files | `ContingencyListsIO`, `DesktopParametersJson`, `ReportsController` |

Use the existing helpers (and `FileChooserPreferences` for any new `FileChooser`) rather than adding new storage.
Network state (loaded networks, results, contingency lists) is not persisted between runs.
