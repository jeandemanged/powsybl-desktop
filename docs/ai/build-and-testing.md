# Build, checkstyle, tests, CI, packaging

## Toolchain

- Java 25 (`java.version` in `pom.xml`), JavaFX 27 (`javafx.version`), Maven. The JavaFX platform classifier is
  chosen by OS-activated profiles (`win`, `linux`, `mac` = Apple Silicon only).
- Parent POM `com.powsybl:powsybl-parent` contributes plugins and configuration not visible in this `pom.xml`
  (checkstyle, buildnumber, templating, surefire defaults, license). Use `mvn help:effective-pom` before concluding a
  build behavior is absent.
- Classpath application: there is no `module-info.java`; `javafx-maven-plugin` runs
  `com.powsybl.powsybldesktop.MainApplication`; the jar manifest main class is `Launcher`.
- Main libraries: PowSyBl (`powsybl-dependencies` BOM, see [domain-and-persistence.md](domain-and-persistence.md)), ControlsFX,
  Ikonli (Material Design 2), Lucene, Logback, Jackson (via PowSyBl), Leaflet webjar (build-time only, see
  [diagrams-and-map.md](diagrams-and-map.md#leaflet-in-a-webview)), AutoService (annotation processor).

## Commands

```
mvn clean install                 # full build: checkstyle (validate phase) + compile + all tests
mvn test                          # tests (checkstyle still runs first)
mvn test -Dtest=LoadsControllerTest
mvn checkstyle:check@default     # style only, powsybl rules (NOT bare checkstyle:check)
mvn clean javafx:run              # run the app
mvn -o dependency:tree            # resolve actual PowSyBl module versions
mvn test -Pug-screenshots         # user's guide screenshots only, to docs/ug/_generated/images
```

## Checkstyle (fails the build)

Rules come from `powsybl-build-tools` (`checkstyle.xml`), applied to `java`, `properties` and `xml` files, test
sources included.

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
- Java line length max 200; method length limited (long dispatch methods get split, e.g.
  `MainController.onNetworkObjectTableEvent`); lambdas max 20 lines; braces required; no unused/redundant imports;
  custom import order; utility classes need a private constructor and `final`; catching
  `RuntimeException`/`NullPointerException` is an error, `Exception`/`Throwable` a warning.

Every source, FXML and XML file starts with the MPL-2.0 Artelys copyright header, and every Java type has an
`@author` javadoc tag — copy them from a neighboring file.

## Test stack

JUnit 5 (+ params), TestFX (`testfx-core`, `testfx-junit5`), ArchUnit (`archunit`, driven from plain JUnit tests).
Tests mirror the main package layout under `src/test/java/com/powsybl/powsybldesktop/`. There is no
`src/test/resources`: test networks come from PowSyBl factories (`IeeeCdfNetworkFactory.create14()`,
`EurostagTutorialExample1Factory`, `CgmesConformity1Catalog...`) or are built in code.

### Headless UI tests

- Extend `src/test/java/com/powsybl/powsybldesktop/ui/testutil/AbstractHeadlessApplicationTest.java`. Its static
  initializer selects JavaFX's built-in headless Glass platform (`glass.platform=Headless`, `testfx.robot=glass`,
  `prism.order=sw`). Do **not** add `openjfx-monocle` or set `testfx.headless` — that makes TestFX install Monocle and
  breaks the suite. The headless platform never positions a stage, so the base class places the shared primary stage
  when it has no position: robot clicks (`clickOn`) on a stage at NaN miss, failing only after some test orders.
- Surefire's `argLine` is `-Duser.language=en -Duser.country=US` (a JVM arg, so that assertions on English UI strings
  are stable). It replaces the parent's `@{argLine} -XX:+EnableDynamicAgentLoading`: adding a Java agent (JaCoCo,
  Mockito's inline mock maker) requires putting `@{argLine}` back.
- Test classes run in parallel across forked JVMs (`forkCount` `0.5C`, one per two cores: at `1C` a many-core dev box
  times out UI tests; CI passes `-DforkCount=1C` as runners have few cores; override with `-DforkCount=N`,
  `-DforkCount=1` for a serial run). Don't enable JUnit's in-JVM parallel execution: a JVM has a
  single JavaFX toolkit and TestFX shares one primary stage, so concurrent UI tests would overwrite each other's
  scene. Tests must not share state outside the JVM: write files under `@TempDir`. A UI test flaky only in CI is more
  likely a race with a short timer than a fork problem.

Canonical controller test: `ui/network/tables/LoadsControllerTest.java`:

```text
start(Stage): FXMLLoader(resource, Messages.bundle()) → load → getController()
              new MainModel(); addNetwork(network); setNetwork(network); controller.setSceneModel(SceneModel.main(mainModel))
              stage.setScene(new Scene(root)); stage.show()
@AfterEach:   interact(controller::dispose)
tests:        read controller fields (columns, currentItems), build cells via the column's cell factory,
              fire edit commits, mutate inside interact(...), WaitForAsyncUtils.waitForFxEvents() for async work
```

Testing the shell: `MainControllerDragToOpenTest` (and the other `MainController*Test`s) load `main-view.fxml` with a
controller factory supplying `new MainController(mainModel)`.

### Conventions

- `XControllerTest` for behavior; `XControllerEmptyStateTest` for table views, asserting the view renders with no
  network selected (`new MainModel()` without networks). Follow this pair for new views. Detail views are covered
  together by `EquipmentDetailViewsTest`.
- Add every new table FXML to `EditLockTest.VIEWS`; it fails if any editor stays enabled while the network is busy.
- Pure logic is tested with plain JUnit, no toolkit: e.g. `SubstationDiagramRendererTest`, `ContingencyListsIOTest`,
  `ApplicationParametersJsonTest`, `NetworkSearchTest`, `LoadFlowConvergenceTest`, `InjectionsTest`. Prefer factoring
  logic into such a class when it is non-trivial.
- Tests needing the toolkit without a stage call `Platform.startup` in `@BeforeAll` (`NetworkTaskTest`,
  `MainModelTest`, `NetworkChangesTest`).
- `ArchitectureTest` enforces the package layering, strictly; see [architecture.md](architecture.md#enforced-layering).
  Logic outside `ui` gets plain JUnit tests; TestFX tests cover the views' wiring.
- Test names are sentences in camelCase describing behavior (`togglingConnectedCheckBoxDisconnectsAndReconnectsLoad`).

### Debugging rendering

For a view that renders wrong (WebView diagrams and map especially), look at it instead of theorizing from the source:
write a throwaway headless test extending `AbstractHeadlessApplicationTest` that loads the view's FXML with a real
network, puts its root in a `Pane` at a realistic size, waits for the async content (map tiles take seconds), then on
the FX thread writes `node.snapshot(...)` through `SwingFXUtils.fromFXImage` and `ImageIO.write` to a PNG outside the
repository, and read the image. Dump the page's state in the same run with
`webView.getEngine().executeScript("JSON.stringify({...})")`. Resize by changing the root's `prefSize` inside a
fixed-size `Scene`: resizing the `Stage` crashes the headless platform (`HeadlessWindow.blit` →
`IndexOutOfBoundsException`). The tests render with `prism.order=sw`: a hardware-pipeline-only problem won't
reproduce there. Delete the harness afterwards: network-dependent and slow, not suite material.

### User's guide screenshots

`UserGuideScreenshotsTest` is not a regression test: it drives the main window through the user's guide quick start
tutorials (robot clicks through the real menus) and writes the PNGs the guide shows to `docs/ug/_generated/images`
(git-ignored; override with `-Dug.screenshots.dir`). It is tagged `user-guide`, excluded by default through the
`surefire.excludedGroups` property; the `ug-screenshots` profile runs it alone. Each screenshot composites every
showing window over the main one, so open menus and notifications appear. The headless screen is a fixed 1000 x 1000
that keeps popups within it: the window is smaller than that, at its origin, and shows the top left of a main view laid
out larger (1680 x 1050), which is what gets snapshot; robot targets must stay in that visible part, and the
notifications popup, placed in the window's corner, is moved to the view's. Move the robot into menus with
`Motion.HORIZONTAL_FIRST`: a diagonal move crosses sibling items and closes the submenu being aimed at. A page of
`docs/ug` referencing a new image needs that image added to the test.

## CI

`.github/workflows/ci.yml`: on every branch push (no pull request or tag trigger), `mvn -B clean install
-DforkCount=1C` on Windows, Ubuntu and macOS with Temurin 25. In parallel, the `user-guide` jobs build the user's guide
on the same three platforms, and `user-guide-artifact` merges them into one `user-guide` artifact with a `windows`,
`linux` and `macos` folder, each holding `html/` and the PDF (from the platforms that succeeded).
`.github/workflows/package-release.yml` (manual dispatch, publishes from branch `init`): tags the commit
(`vYYYYMMDD-sha`), builds the user's guide and runs the three packaging scripts on Windows, Linux and macOS (Apple
Silicon) runners with downloaded JavaFX jmods, archives the app-images and creates a GitHub release marked latest, then
deletes all but the newest 5 such releases and their tags. Each packaging job also
uploads its guide (`user-guide-<platform>`), and the release job zips them into one
`powsybl-desktop-<version>-<sha>-user-guide.zip` asset (`user-guide/{windows,linux,macos}`, each with `html/` and the
PDF); it downloads the app archives by the `powsybl-desktop-*` pattern so that the guide's files stay out of `dist`.
Both build the guide with the composite action `.github/actions/build-user-guide`: Python 3.12, TeX Live from its
official installer (`install-tl`, scheme-basic plus the collections Sphinx's LaTeX needs, in `~/texlive`, cached per OS
with a key changing with the action file), the screenshots, then `sphinx -M html` with warnings as errors and
`-M latexpdf`. Only GitHub's own actions are used: the third-party TeX Live action first tried had disappeared.
The installer uses the first answering of a fixed list of CTAN mirrors: `mirror.ctan.org` redirects to a random one,
and some have HTTPS certificate chains `curl` can't verify.

## Packaging

`packaging/package-windows.ps1`, `package-linux.sh`, `package-macos.sh` (all run by the release workflow):
`mvn clean package -DskipTests` → `jdeps` → `jlink` (JDK + separately downloaded JavaFX 27 jmods) →
`jpackage --type app-image`. Full details in `README.md`.
- Before `jpackage`, the scripts copy the user's guide, if built in `docs/ug/_build` (see `docs/ug/README.md`), to
  `target/app-libs/user-guide` (the PDF and the `html` directory), which lands next to the jar in the app-image.
  `utils/UserGuide` looks for it there (or in `docs/ug/_build` when not packaged), and the Help menu's
  **User's Guide (PDF)** / **(HTML)** items, opening it with the system's default application, are disabled for a
  missing format.
- The `copy-dependencies` execution excludes `org.openjfx` on purpose (JavaFX is jlinked as modules; having the jars
  on the classpath too breaks startup) — do not remove that exclusion.
- Hence `jdeps` can't see which JavaFX modules the app uses: the scripts list them by hand
  (`javafx.controls,javafx.fxml,javafx.swing,javafx.web`). Code using another JavaFX module (e.g. `javafx.media`) must
  add it to all three scripts and to the README's manual sequence, or the packaged app fails at runtime while tests
  and `javafx:run` pass.
- Don't add `-Xmx` through `--java-options`: the Memory window owns that line of the launcher `.cfg`
  (`memory/MaxHeapConfig`). It must stay in the `.cfg`: a `-XX:VMOptionsFile` pointing to a per-user file stops the
  JVM from starting while that file doesn't exist.

## Third-party licenses

`license-maven-plugin` (configured in the `com.powsybl:powsybl` grandparent POM) regenerates `THIRD-PARTY.txt` at the
repo root on every build (`generate-resources`); it is committed, so dependency changes show up in diffs. It is also
copied into the jar (shown in the About dialog's "Third-Party Licenses" tab) and into `target/app-libs` (shipped in the
app-image). PowSyBl's own artifacts are excluded (`excludedGroups`). `excludedScopes` is overridden to `test` only so
the `provided` Leaflet webjar, whose files we redistribute, is listed; `licenseMerges` appends the project's merges of
equivalent license names. The build fails on a dependency without license metadata (`failOnMissing`): declare it in
`THIRD-PARTY.properties` at the repo root (the configured `missingFile`, absent until needed).
