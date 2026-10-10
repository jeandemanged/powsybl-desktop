# AGENTS.md

Entry point for AI coding agents working on this repository. Not every tool reads this file automatically;
if you were pointed here, read it in full before changing anything.

## Project in one paragraph

PowSyBl Desktop is a JavaFX 27 / Java 25 desktop client (Maven, classpath application, not modular) for
[PowSyBl](https://www.powsybl.org/): load power networks, browse them through equipment tables and
single-line / network-area diagrams, edit equipment values, run load flow and security analysis, and inspect
reports, logs and notifications. It is a thin UI over PowSyBl libraries: most behavior questions are really
questions about PowSyBl itself.

## Before you change code

1. Read [`docs/ai/README.md`](docs/ai/README.md) — the index of the knowledge base, with a task-to-document map.
2. Read the documents it lists for your task, and the canonical example classes they point to.
3. Find the closest existing implementation of what you are about to build and follow it. Do not introduce a
   new architectural style, framework, dependency-injection mechanism or cross-view communication path.

## Non-negotiable rules (details in `docs/ai/invariants.md`)

- `MainModel` is the single shared state; views talk to each other only through it and through
  `NavigationHistory`. Per-network state goes in `NetworkStudy`.
- Swappable controllers extend `AbstractDisposableController` and register model listeners through
  `listenerManager.listen(...)`, never `addListener` directly.
- Never call slow PowSyBl APIs on the JavaFX Application Thread. Any background job reading or writing a loaded
  network is an `AbstractNetworkTask` run by a JavaFX `Service`; results are applied in `setOnSucceeded`.
- Editors must respect the network busy lock (`MainModel.networkBusyProperty()`); add editor columns through
  `TableColumnSupport`.
- Every UI string goes in both `i18n/messages.properties` and `i18n/messages_fr.properties`; every `FXMLLoader`
  gets `Messages.bundle()`.
- Keep `fx:id` / `@FXML` field names and `onAction="#method"` handlers in sync between FXML and controller.
- Do not add dependencies without explicit justification and approval.

## Build and verify

```
mvn clean install                 # build + checkstyle + all tests (checkstyle failures fail the build)
mvn test -Dtest=ClassName         # one test class
mvn checkstyle:check@default     # style only, powsybl rules (NOT bare checkstyle:check)
mvn clean javafx:run              # run the app
```

Requires JDK 25. Tests run headless (no display needed). Report test results faithfully, including failures.

Easy to get wrong:

- Checkstyle standalone is `mvn checkstyle:check@default` (or `mvn validate`), never bare `mvn checkstyle:check`,
  which runs Sun's default rules instead of powsybl's — see `docs/ai/build-and-testing.md`.
- PowSyBl module versions come from the `powsybl-starter` BOM, not from plain numbers in `pom.xml`: resolve them with
  `mvn -o dependency:tree` before reading a PowSyBl sources jar; don't trust whichever version sits in `~/.m2`.
- Line endings: the repository stores LF (`.gitattributes`: `* text=auto`); working copies use the platform's
  convention (CRLF on Windows, LF on Linux/macOS). Every file you create or rewrite must use the same endings as the
  rest of the working copy (on Windows, CRLF everywhere). Scripted rewrites (Python `open(...).write`, `sed`,
  heredocs) silently produce LF, so convert back if needed. Exception: `*.sh` are always LF (`eol=lf`). Check with
  `git ls-files --eol` (text files must match their `attr/`: `w/crlf` on Windows, `w/lf` for `eol=lf`).
