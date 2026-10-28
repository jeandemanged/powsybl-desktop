# Build, checkstyle, tests, CI, packaging

## Toolchain

- Java 25 (`java.version` in `pom.xml`), JavaFX 27 (`javafx.version`), Maven. The JavaFX platform classifier is
  chosen by OS-activated profiles (`win`, `linux`, `mac` = Apple Silicon only).
- Parent POM `com.powsybl:powsybl-parent:30` contributes plugins not visible in this `pom.xml` (checkstyle,
  buildnumber, templating, surefire defaults). Use `mvn help:effective-pom` before concluding a build behavior is absent.
- Classpath application: there is no `module-info.java`; `javafx-maven-plugin` runs
  `com.powsybl.powsybldesktop.MainApplication`; the jar manifest main class is `Launcher`.
- Main libraries: PowSyBl (`powsybl-starter` BOM), ControlsFX, Ikonli (Material Design 2), Lucene, Logback, Jackson
  (via PowSyBl), Leaflet webjar (build-time only), AutoService (annotation processor).

## Commands

```
mvn clean install                 # full build: checkstyle (validate phase) + compile + all tests
mvn test                          # tests (checkstyle still runs first)
mvn test -Dtest=LoadsControllerTest
mvn checkstyle:check@default     # style only, powsybl rules (NOT bare checkstyle:check)
mvn clean javafx:run              # run the app
mvn -o dependency:tree            # resolve actual PowSyBl module versions
```

## Checkstyle (fails the build)

Rules come from `powsybl-build-tools` (version 30, `checkstyle.xml`), applied to `java`, `properties` and `xml`
files, test sources included.

**Run it the right way.** `powsybl-parent` puts the checkstyle configuration (`configLocation`, `failsOnError`,
`includeTestSourceDirectory`, excludes) inside an execution bound to `validate` with no explicit id, i.e. the
execution id `default` — not at plugin level. Consequently:

- `mvn checkstyle:check@default` — powsybl rules, standalone. **Use this.**
- `mvn validate` — same rules (runs the bound execution), plus the other `validate`-phase plugins.
- `mvn clean install` / `mvn test` — run it automatically before compiling.
- **Bare `mvn checkstyle:check` is wrong**: it runs the `default-cli` execution, which ignores that configuration and
  falls back to the plugin's built-in Sun checks, reporting thousands of irrelevant violations (`FinalParameters`,
  `MagicNumber`, ...). Never act on its output.

Rules that commonly bite:

- **Properties files must keep keys in sorted order** (`OrderedProperties`) and unique (`UniqueProperties`): insert new
  `messages*.properties` / `reports*.properties` keys at their sorted position, not at the end (the existing files
  are sorted case-insensitively, e.g. `pairingKey` before `pBoundary`).
- `reports*.properties` must have a `fr` translation with the same keys (`Translation`). The `i18n/messages` bundle
  isn't covered by that check, but the project keeps both languages in sync anyway.
- No two consecutive blank lines, no trailing whitespace, no tabs, newline at end of file.
- Java line length max 200; braces required; no unused/redundant imports; custom import order; utility classes
  need a private constructor and `final`; catching `RuntimeException`/`NullPointerException` is an error,
  `Exception`/`Throwable` a warning.

Every source, FXML and XML file starts with the MPL-2.0 Artelys copyright header, and every Java type has an
`@author` javadoc tag — copy them from a neighboring file.

## Test stack

JUnit 5 (+ params), TestFX (`testfx-core`, `testfx-junit5`). Tests mirror the main package layout under
`src/test/java/com/powsybl/powsybldesktop/`. There is no `src/test/resources`: test networks come from PowSyBl factories
(`IeeeCdfNetworkFactory.create14()`, `EurostagTutorialExample1Factory`, `CgmesConformity1Catalog...`) or are built in
code.

### Headless UI tests

- Extend `src/test/java/com/powsybl/powsybldesktop/testutil/AbstractHeadlessApplicationTest.java`. Its static
  initializer selects JavaFX's built-in headless Glass platform (`glass.platform=Headless`, `testfx.robot=glass`,
  `prism.order=sw`). Do **not** add `openjfx-monocle` or set `testfx.headless` — that makes TestFX install Monocle and
  breaks the suite.
- Surefire pins `-Duser.language=en -Duser.country=US` as a JVM arg so assertions on English UI strings are stable.
- Test classes run in parallel across forked JVMs (`forkCount` `0.5C`, one per two cores: at `1C` a many-core dev box
  times out UI tests; CI passes `-DforkCount=1C` as runners have few cores; override with `-DforkCount=N`,
  `-DforkCount=1` for a serial run). Don't enable JUnit's in-JVM parallel execution: a JVM has a
  single JavaFX toolkit and TestFX shares one primary stage, so concurrent UI tests would overwrite each other's
  scene. Tests must not share state outside the JVM: write files under `@TempDir`.

Canonical controller test: `network/tables/LoadsControllerTest.java`:

```text
start(Stage): FXMLLoader(resource, Messages.bundle()) → load → getController()
              new MainModel(); addNetwork(network); setNetwork(network); controller.setSceneModel(SceneModel.main(mainModel))
              stage.setScene(new Scene(root)); stage.show()
@AfterEach:   interact(controller::dispose)
tests:        read controller fields (columns, currentItems), build cells via the column's cell factory,
              fire edit commits, mutate inside interact(...), WaitForAsyncUtils.waitForFxEvents() for async work
```

Testing the shell: `MainControllerMapButtonTest` loads `main-view.fxml` with a controller factory supplying
`new MainController(mainModel)`.

### Conventions

- `XControllerTest` for behavior; `XControllerEmptyStateTest` for table/detail views, asserting the view renders with
  no network selected (`new MainModel()` without networks). Follow this pair for new views.
- Add every new table FXML to `EditLockTest.VIEWS`; it fails if any editor stays enabled while the network is busy.
- Pure logic is tested with plain JUnit, no toolkit: e.g. `SubstationDiagramRendererTest`, `ContingencyListsIOTest`,
  `ApplicationParametersJsonTest`, `NetworkSearchTest`, `LoadFlowConvergenceTest`, `CategoryTitlesTest`. Prefer factoring
  logic into such a class when it is non-trivial.
- Tests needing the toolkit without a stage call `Platform.startup` in `@BeforeAll` (`NetworkTaskTest`).
- Test names are sentences in camelCase describing behavior (`togglingConnectedCheckBoxDisconnectsAndReconnectsLoad`).

## CI

`.github/workflows/ci.yml`: on every push, `mvn -B clean install` on Windows, Ubuntu and macOS with Temurin 25.
`.github/workflows/package-release.yml` (manual dispatch, publishes from branch `init`): tags the commit, runs the three
packaging scripts on Windows, Linux and macOS (Apple Silicon) runners with downloaded JavaFX jmods, archives the
app-images and creates a GitHub release.

## Packaging

`packaging/package-windows.ps1`, `package-linux.sh`, `package-macos.sh` (all run by the release workflow):
`mvn package` → `jdeps` → `jlink` (JDK + separately downloaded JavaFX 27 jmods) → `jpackage --type app-image`. The
`copy-dependencies` execution excludes `org.openjfx` on purpose (JavaFX is jlinked as modules; having the jars on the
classpath too breaks startup) — do not remove that exclusion. Full details in `README.md`.
Don't add `-Xmx` through `--java-options`: the Memory popup owns that line of the launcher `.cfg`
(`memory/MaxHeapConfig`). It must stay in the `.cfg`: a `-XX:VMOptionsFile` pointing to a per-user file stops the JVM
from starting while that file doesn't exist.

## Third-party licenses

`license-maven-plugin` (configured in the `com.powsybl:powsybl` grandparent POM) regenerates `THIRD-PARTY.txt` at the
repo root on every build (`generate-resources`); it is committed, so dependency changes show up in diffs. It is also
copied into the jar (shown in the About dialog's "Third-Party Licenses" tab) and into `target/app-libs` (shipped in the
app-image). `excludedScopes` is overridden to `test` only so the `provided` Leaflet webjar, whose files we
redistribute, is listed. The build fails on a dependency without license metadata (`failOnMissing`): declare it in
`THIRD-PARTY.properties` at the repo root.
