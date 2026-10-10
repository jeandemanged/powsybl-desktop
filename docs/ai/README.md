# AI knowledge base — index

Project knowledge for coding agents (and humans). Agent-neutral: it describes the code, not any tool.
`AGENTS.md` at the repository root is the entry point; this directory is the canonical detail. If a
tool-specific file (e.g. `CLAUDE.md`) disagrees with these documents, verify against the source code — the
code is the final authority.

Paths written as `…/X.java` are relative to `src/main/java/com/powsybl/powsybldesktop/`; FXML and other resources
live under the mirrored package in `src/main/resources/com/powsybl/powsybldesktop/`.

## Documents

| Document | Covers |
|---|---|
| [architecture.md](architecture.md) | Package map, entry point and lifecycle, `MainModel`/`NetworkStudy`, navigation, controller lifecycle, notifications, reports and logs |
| [ui-conventions.md](ui-conventions.md) | FXML + controller pattern, i18n, equipment tables, editing cells, dialogs and secondary windows, WebView diagrams/map, CSS and icons |
| [threading.md](threading.md) | Background work with `Service`/`AbstractNetworkTask`, the network busy lock, FX-thread rules, cancellation |
| [domain-and-persistence.md](domain-and-persistence.md) | PowSyBl usage, networks/subnetworks, load flow and security analysis, contingencies, search, parameters and config file, preferences, import/export |
| [build-and-testing.md](build-and-testing.md) | Maven build, checkstyle, headless TestFX tests, test conventions, CI, packaging |
| [invariants.md](invariants.md) | Rules every change must preserve, review checklist, known inconsistencies |

## Which documents to read for a task

| Task | Read |
|---|---|
| Add a feature touching several views | architecture, ui-conventions, invariants |
| Add a new center view (screen) | architecture (Navigation, Controller lifecycle), ui-conventions, build-and-testing |
| Add an equipment table or column | ui-conventions (Equipment tables, Editing cells), threading (busy lock), build-and-testing |
| Add a dialog, form or secondary window | ui-conventions (Dialogs and windows), invariants |
| Change domain logic / PowSyBl calls | domain-and-persistence, threading |
| Add or change a parameter | domain-and-persistence (Parameters), ui-conventions |
| Change persistence (config file, preferences, import/export) | domain-and-persistence |
| Add a long-running / background operation | threading, architecture (Notifications) |
| Write or update tests | build-and-testing |
| Fix a bug | invariants, then the document for the affected area |
| Refactor | invariants, architecture; keep changes behavior-preserving and minimal |
| Review code | invariants (checklist), build-and-testing |

## Working method

1. **Inspect** the closest existing implementation (each document names canonical examples) and the tests next to it.
2. **Identify affected layers**: FXML, controller, `MainModel`/`NetworkStudy`/sub-model, navigation, i18n bundles
   (both languages), CSS, `DesktopParametersJson` (for parameters), tests.
3. **Make the smallest coherent change** that follows the existing pattern. No speculative abstractions, no
   unrelated refactoring.
4. **Update resources and tests** together with the code: FXML ids, message keys in both bundles, a controller test
   and, for table/detail views, an `*EmptyStateTest`; add new table views to `EditLockTest`.
5. **Verify** with `mvn clean install` (checkstyle + tests) or at least the affected test classes plus
   `mvn checkstyle:check@default` (never bare `mvn checkstyle:check`, see build-and-testing).
6. **Report** what changed, what was run, and the actual results — including failures or anything not verified.

