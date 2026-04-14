# PowSyBl Desktop

A JavaFX desktop client for [PowSyBl](https://www.powsybl.org/) — load/browse power networks,
inspect substations via single-line diagrams, run load flow, and view results/logs.

## Build & run from source

Requires JDK 25 and Maven.

```
mvn clean install       # build + tests
mvn clean javafx:run    # run the app
```

## Packaging a standalone app-image (jlink + jpackage)

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

### Prerequisites (all platforms)

- A full JDK 25 (with `jmods/`, i.e. not a stripped-down JRE) — `jlink`/`jdeps`/`jpackage` must
  be on `PATH`.
- The **JavaFX 27 jmods** for your OS/architecture, downloaded separately — these are *not*
  the same as the JavaFX jars pulled from Maven Central, and jmods aren't published to Maven at
  all. Download from [gluonhq.com/products/javafx](https://gluonhq.com/products/javafx/) or
  [jdk.java.net/javafx27](https://jdk.java.net/javafx27/), matching this project's JavaFX version
  (`27` in `pom.xml`) and your platform:
  - Windows: `windows-x64`
  - Linux: `linux-x64` or `linux-aarch64`
  - macOS: `macos-x64` (Intel) or `macos-aarch64` (Apple Silicon)

  Extract the zip; you'll pass the path to its `jmods` folder to the packaging script below.
- `jpackage` builds an app-image for the OS it runs on — there is no cross-compilation. Build the
  Windows app-image on Windows, the Linux one on Linux, the macOS one on macOS.

### Running the packaging scripts

One script per OS under `packaging/`, each running the same pipeline
(`mvn package` → `jdeps` → `jlink` → `jpackage --type app-image`):

**Windows (PowerShell):**
```powershell
./packaging/package-windows.ps1 -JavafxJmods C:\path\to\javafx-jmods-27\jmods
```

**Linux:**
```bash
./packaging/package-linux.sh /path/to/javafx-jmods-27/jmods
```

**macOS:**
```bash
./packaging/package-macos.sh /path/to/javafx-jmods-27/jmods
```

All three also read the jmods path from a `JAVAFX_JMODS` environment variable, and accept
`-SkipBuild` / `--skip-build` to reuse an existing `target/app-libs` instead of re-running
`mvn clean package`.

Output:
- Windows: `target/dist/PowSyBl Desktop/PowSyBl Desktop.exe`
- Linux: `target/dist/PowSyBl Desktop/bin/PowSyBl Desktop`
- macOS: `target/dist/PowSyBl Desktop.app`

### What the scripts do, step by step

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

### Icons

`packaging/icons/app.png` is already provided (the app's existing logo), used automatically for
the Linux app-image. Windows needs `packaging/icons/app.ico` and macOS needs
`packaging/icons/app.icns` — neither is provided, since producing a valid multi-resolution
`.ico`/`.icns` needs image tooling this repo doesn't otherwise depend on. Generate one from
`src/main/resources/com/powsybl/powsybldesktop/logo.png` (e.g. with ImageMagick, an online
converter, or macOS's `iconutil`) and drop it at that path — the scripts pick it up automatically
if present, and fall back to jpackage's default icon otherwise.

### Verified so far

The Windows pipeline has been run end-to-end and the resulting app-image launches correctly. The
Linux and macOS scripts follow the identical pattern but haven't been run on those platforms yet.
