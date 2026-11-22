# AI knowledge base — index

Project knowledge for coding agents (and humans). Agent-neutral: it describes the code, not any tool.
`AGENTS.md` at the repository root is the entry point; this directory is the canonical detail. If a
tool-specific file (e.g. `CLAUDE.md`) disagrees with these documents, verify against the source code — the
code is the final authority.

Paths written as `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`; FXML and other resources
live under the mirrored package in `src/main/resources/com/powsybl/powsybldesktop/`.

Each rule has one home: `invariants.md` states it briefly and links to the document holding the detail. When a rule
changes, change it there and in the invariant, nowhere else.

## Documents

| Document | Covers |
|---|---|
| [architecture.md](architecture.md) | Package map, enforced layering, entry point and lifecycle, `MainModel`/`NetworkStudy`/`SceneModel`, navigation, controller lifecycle, notifications, reports and logs |
| [windows.md](windows.md) | Separate windows: tabs, keys, which views open where, network binding, drag to open, language reload |
| [ui-conventions.md](ui-conventions.md) | FXML + controller pattern, i18n and wording, dialogs and secondary windows, CSS and icons |
| [editing.md](editing.md) | Structural edits (`NetworkChanges`), equipment tables, editing cells, creation dialogs and edit menus, equipment detail views, adding an equipment kind |
| [diagrams-and-map.md](diagrams-and-map.md) | WebView diagrams and `sld.js`, the Map view (Java tiles, Leaflet in a WebView and its rendering workarounds), position edits, basemap |
| [threading.md](threading.md) | Background work with `Service`/`AbstractNetworkTask`, the network busy lock, FX-thread rules, cancellation |
| [domain-and-persistence.md](domain-and-persistence.md) | PowSyBl usage and versions, networks/subnetworks, violations, load flow, security and sensitivity analysis, contingencies, limit scalings, search, parameters and config file, persistence, studies |
| [build-and-testing.md](build-and-testing.md) | Maven build, checkstyle, headless TestFX tests, test conventions, debugging rendering, CI, packaging, licenses |
| [invariants.md](invariants.md) | Rules every change must preserve, review checklist, known inconsistencies |

## Which documents to read for a task

| Task | Read |
|---|---|
| Add a feature touching several views | architecture, ui-conventions, invariants |
| Add a new center view (screen) | architecture (Navigation, Controller lifecycle), windows, ui-conventions, build-and-testing |
| Add an equipment table or column | editing (Equipment tables, Editing cells), threading (busy lock), build-and-testing |
| Add an equipment kind (table, details, creation, search...) | editing (Adding an equipment kind) and the documents it points to |
| Change an equipment detail view | editing (Equipment detail views) |
| Add or change a creation, removal or edit menu | editing (Structural edits, Creation dialogs and edit menus) |
| Add a dialog, form or secondary window | ui-conventions (Dialogs), windows, invariants |
| Change a diagram or the map | diagrams-and-map, threading |
| Add or change UI text | ui-conventions (Internationalization) |
| Change domain logic / PowSyBl calls | domain-and-persistence, threading |
| Add or change a parameter | domain-and-persistence (Parameters), ui-conventions |
| Change persistence (config file, preferences, import/export, study files) | domain-and-persistence |
| Change limit scalings | domain-and-persistence (Limit scalings) |
| Change sensitivity factors, analysis or results | domain-and-persistence (Computations), editing (Equipment tables) for the results table |
| Change study save/load or the `.psbx` format | domain-and-persistence (Studies), threading |
| Add a toolbar entry (Inputs, Run, Results, Tables, Diagnostics) | architecture (Navigation), windows |
| Add a long-running / background operation | threading, architecture (Notifications) |
| Write or update tests | build-and-testing |
| Fix a visual / rendering bug | build-and-testing (Debugging rendering), diagrams-and-map for WebView views |
| Fix a bug | invariants, then the document for the affected area |
| Change the build, packaging or a dependency | build-and-testing |
| Write or update the user's guide (`docs/ug`) | `docs/ug/README.md`, build-and-testing (User's guide screenshots, CI) |
| Refactor | invariants, architecture; keep changes behavior-preserving and minimal |
| Review code | invariants (checklist), build-and-testing |

## Working method

1. **Inspect** the closest existing implementation (each document names canonical examples) and the tests next to it.
2. **Identify affected layers**: FXML, controller, `MainModel`/`NetworkStudy`/sub-model, navigation, i18n bundles
   (both languages), CSS, `ApplicationParametersJson` (for parameters), tests, `README.md` and the user's guide
   `docs/ug` (user-visible features; a new screenshot needs `UserGuideScreenshotsTest`).
3. **Make the smallest coherent change** that follows the existing pattern. No speculative abstractions, no
   unrelated refactoring.
4. **Update resources, tests and docs** together with the code: FXML ids, message keys in both bundles, a controller
   test and, for table views, an `*EmptyStateTest`; add new table views to `EditLockTest`; update the `docs/ai`
   document describing what changed.
5. **Verify** with `mvn clean install` (checkstyle + tests) or at least the affected test classes plus
   `mvn checkstyle:check@default`.
6. **Report** what changed, what was run, and the actual results — including failures or anything not verified.
