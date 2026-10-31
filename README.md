# PowSyBl Desktop

PowSyBl Desktop is a desktop application for [PowSyBl](https://www.powsybl.org/), the open-source power system
blocks framework, to explore, edit and simulate power networks.

PowSyBl is a set of Java libraries, also available in Python through [PyPowSyBl](https://github.com/powsybl/pypowsybl):
using it usually means writing code, or first building your own tooling around it. PowSyBl Desktop removes three
barriers to adoption:

1. **Technical**: no Java, Python or PowSyBl programming skills are needed.
2. **Usability**: a graphical desktop environment instead of APIs or scripts.
3. **Adoption**: organizations can use PowSyBl without first building their own desktop tooling.

What PowSyBl Desktop brings is productivity, not access to the underlying Java or Python APIs.

It has three goals:

- **Make PowSyBl easy to try.** It is distributed as a self-contained application that bundles its own Java runtime:
  download, unzip and run. No Python, Docker, Java or any other third-party installation is needed.
- **Make PowSyBl's capabilities tangible.** Loading a network and clicking through its diagrams, tables and
  simulations shows what PowSyBl can do faster than reading its documentation and API references: understanding
  comes hands-on, from the first minutes.
- **Be a thin UI layer over PowSyBl.** The application has no business logic of its own: networks are PowSyBl IIDM
  networks, imports and exports are PowSyBl's, every value shown or edited is read from or written to the network
  through PowSyBl's API and validated by it, and computations, diagrams and limit violations come from PowSyBl
  libraries. What you see is what PowSyBl does. The notable exceptions are:
  - the **search**, a fuzzy search by id or name built by the application on its own index;
  - the **map**, drawn by the application itself from the network's geographical positions;
  - the **load flow convergence verdict** (converged / partially converged / not converged), derived by the
    application from PowSyBl's per-component statuses.

## Who is it for

| Audience                                                                                          | Use                                                                                                                |
|---------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| Transmission and Distribution System Operators (TSOs, DSOs), Regional Coordination Centres (RCCs) | Network modelling and analysis, operational studies                                                                |
| Energy consultants and engineering companies                                                      | Faster engineering studies                                                                                         |
| Universities, research laboratories, electrical engineering departments                           | An accessible power-system simulation environment: network visualization and simulation without custom development |
| Developers building PowSyBl-based applications                                                    | An example of a desktop client over the PowSyBl APIs                                                               |

## Installation

Download the archive for your platform (Windows, Linux, macOS on Apple Silicon) from the
[releases](https://github.com/jeandemanged/powsybl-desktop/releases), extract it and run the executable inside:

- Windows: `PowSyBl Desktop/PowSyBl Desktop.exe`
- Linux: `PowSyBl Desktop/bin/PowSyBl Desktop`
- macOS: `PowSyBl Desktop.app`

The archive is a portable app-image, not an installer: nothing is installed system-wide.

## Getting started

Start the application, then either load one of the sample networks (_Sample Networks_ in the networks toolbar),
open a network file (from the file chooser or by drag and drop), or create a new empty network. Run a load flow from
the toolbar, and browse the results in the equipment tables, diagrams and violations view.

## What it can do

- **Networks**: import CGMES, UCTE-DEF, IIDM (XML, JSON, binary), MATPOWER, PSS®E, IEEE CDF and PowerFactory
  networks from a file, a folder or by drag and drop, with per-format import parameters; built-in sample networks;
  export with per-format parameters; create a new network; merge networks and detach subnetworks.
- **Substations**: substation / voltage-level tree with interactive single-line diagrams (switch operation, click to
  navigate) and network-area diagrams, with PNG/SVG export.
- **Equipment tables**: substations, voltage levels, busbar sections, buses (bus and bus/breaker views), switches,
  generators, batteries, loads, lines, tie lines, boundary lines, transformers, shunt and static var compensators, HVDC
  lines with VSC and LCC converter stations, grounds, areas, overload management systems and voltage angle limits,
  with in-place editing validated by PowSyBl.
- **Equipment details**: a detail view per equipment type, including reactive capability curves, tap changers,
  operational limits (editable) and IIDM extensions (editable).
- **Network editing**: create and remove substations, voltage levels and equipment, and edit the topology, through
  PowSyBl network modifications.
- **Map**: substations and lines drawn on a basemap from the network's geographical positions; substation positions
  and line routes can be edited from the map.
- **Search**: fuzzy search by id or name across the network's equipment.
- **Simulation**: AC and DC load flow (OpenLoadFlow) with its full parameters, and security analysis with editable
  contingency lists (JSON import/export); load flow, security analysis results and limit violations views.
- **Reports, logs and notifications** for every operation; parameters saved to a per-user configuration file;
  English and French UI.

## What it cannot do yet

PowSyBl offers much more than what the PowSyBl Desktop application currently exposes.

The following PowSyBl features are not supported yet in PowSyBl Desktop and are candidates for future work:

- **Other simulations**: [sensitivity analysis](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/simulation/sensitivity/index.html),
  [short-circuit analysis](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/simulation/shortcircuit/index.html),
  [dynamic simulation](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/simulation/dynamic/index.html) and
  [dynamic security analysis](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/simulation/dynamic_security/index.html).
- **Security analysis features**: operator strategies and remedial actions,
  [limit scalings](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/simulation/security/limit-scalings.html),
  two-pass security analysis.
- **Optimization**: optimal power flow ([powsybl-optimizer](https://powsybl.readthedocs.io/projects/powsybl-optimizer)),
  remedial actions optimization ([OpenRAO](https://powsybl.readthedocs.io/projects/openrao)), multi-variant
  simulation ([powsybl-metrix](https://powsybl.readthedocs.io/projects/powsybl-metrix)).
- **Grid features**: [network reduction](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/grid_features/extraction.html),
  [scaling](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/grid_features/network_modifications/scaling.html),
  [load flow validation](https://powsybl.readthedocs.io/projects/powsybl-core/en/latest/grid_features/loadflow_validation/index.html),
  network variants, time series.
- **Persistence of a study**: loaded networks, results and contingency lists are not saved between runs.

## Developer guide

### Build & run from source

Requires JDK 25 and Maven.

```
mvn clean install       # build + tests
mvn clean javafx:run    # run the app
```

### Packaging a standalone app-image (jlink + jpackage)

This produces a self-contained app directory that bundles its own minimal Java runtime — end
users don't need Java installed. It's an **app-image**, not an installer (no `.msi`/`.deb`/`.dmg`):
just a folder you can zip and hand to someone, who runs the executable inside directly.

The app's own dependencies (PowSyBl, ControlsFX, Logback, etc.) aren't JPMS modules, so the app
stays classpath-based (as it runs today); only the JDK and JavaFX itself are jlinked into a
trimmed custom runtime. Because of that, this pipeline has one correctness rule worth knowing
before touching it: **the JavaFX jars must never end up in the same directory as the app's other
dependency jars.** Once JavaFX lives in the runtime image as real `javafx.*` modules, having its
classes *also* present on the classpath splits those packages between a named module and the
classpath, which the JVM rejects at startup with a module-layer error. `pom.xml`'s
`copy-dependencies` execution excludes `org.openjfx` for exactly this reason — don't remove that
exclusion.

#### Prerequisites (all platforms)

- A full JDK 25 (with `jmods/`, i.e. not a stripped-down JRE) — `jlink`/`jdeps`/`jpackage` must
  be on `PATH`.
- The **JavaFX 27 jmods** for your OS/architecture, downloaded separately — these are *not*
  the same as the JavaFX jars pulled from Maven Central, and jmods aren't published to Maven at
  all. Download from [gluonhq.com/products/javafx](https://gluonhq.com/products/javafx/) or
  [jdk.java.net/javafx27](https://jdk.java.net/javafx27/), matching this project's JavaFX version
  (`27` in `pom.xml`) and your platform:
  - Windows: `windows-x64`
  - Linux: `linux-x64` or `linux-aarch64`
  - macOS: `macos-aarch64` (Apple Silicon only; Intel Macs aren't supported)

  Extract the zip; it unpacks straight into a `javafx-jmods-27/` folder containing the `.jmod`
  files themselves (no nested `jmods` subfolder) — pass that extracted folder's path to the
  packaging script below.
- `jpackage` builds an app-image for the OS it runs on — there is no cross-compilation. Build the
  Windows app-image on Windows, the Linux one on Linux, the macOS one on macOS.

#### Running the packaging scripts

One script per OS under `packaging/`, each running the same pipeline
(`mvn package` → `jdeps` → `jlink` → `jpackage --type app-image`):

**Windows (PowerShell):**
```powershell
./packaging/package-windows.ps1 -JavafxJmods C:\path\to\javafx-jmods-27
```

**Linux:**
```bash
./packaging/package-linux.sh /path/to/javafx-jmods-27
```

**macOS:**
```bash
./packaging/package-macos.sh /path/to/javafx-jmods-27
```

All three also read the jmods path from a `JAVAFX_JMODS` environment variable, and accept
`-SkipBuild` / `--skip-build` to reuse an existing `target/app-libs` instead of re-running
`mvn clean package`.

Output:
- Windows: `target/dist/PowSyBl Desktop/PowSyBl Desktop.exe`
- Linux: `target/dist/PowSyBl Desktop/bin/PowSyBl Desktop`
- macOS: `target/dist/PowSyBl Desktop.app`

#### What the scripts do, step by step

If you need to debug the pipeline or adapt it, here's the same sequence run manually (Windows
paths shown; swap `;` → `:` and backslashes → forward slashes on Linux/macOS):

```
mvn -o clean package -DskipTests
# -> target/app-libs/powsybl-desktop-<version>.jar + all runtime dependency jars (no javafx-*.jar)

jdeps --multi-release 25 --ignore-missing-deps --print-module-deps ^
      --class-path "target/app-libs/*" target/app-libs/powsybl-desktop-<version>.jar
# -> comma-separated list of JDK platform modules actually used (java.base, java.desktop, ...)

jlink --module-path "<jdk>/jmods;<javafx-jmods-dir>" ^
      --add-modules <jdeps output>,javafx.controls,javafx.fxml,javafx.web ^
      --output target/runtime --strip-debug --no-header-files --no-man-pages --compress=zip-6

jpackage --type app-image --input target/app-libs --dest target/dist ^
         --name "PowSyBl Desktop" --app-version <version without -SNAPSHOT> ^
         --main-jar powsybl-desktop-<version>.jar --main-class com.powsybl.powsybldesktop.Launcher ^
         --runtime-image target/runtime [--icon packaging/icons/app.ico]
```

The max heap defaults to the JVM's (25% of physical memory). Users change it in Help > Memory, which writes a
`java-options=-Xmx…` line to the app-image's launcher config (`app/PowSyBl Desktop.cfg`); it applies at the next start
and needs write access to the app-image folder.

#### Icons

`packaging/icons/app.png` is already provided (the app's existing logo), used automatically for
the Linux app-image. Windows needs `packaging/icons/app.ico` and macOS needs
`packaging/icons/app.icns` — neither is provided, since producing a valid multi-resolution
`.ico`/`.icns` needs image tooling this repo doesn't otherwise depend on. Generate one from
`src/main/resources/com/powsybl/powsybldesktop/logo.png` (e.g. with ImageMagick, an online
converter, or macOS's `iconutil`) and drop it at that path — the scripts pick it up automatically
if present, and fall back to jpackage's default icon otherwise.

#### Continuous integration and releases

All three scripts are run on GitHub Actions by `.github/workflows/package-release.yml` (manual dispatch, publishes
from branch `init`): it tags the commit, packages the app-image on Windows, Linux and macOS (Apple Silicon) runners
with the downloaded JavaFX jmods, archives each one and publishes them as a GitHub release. Every push is also built
and tested on the same three platforms by `.github/workflows/ci.yml`.
