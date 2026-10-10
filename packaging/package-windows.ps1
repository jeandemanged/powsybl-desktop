<#
Copyright (c) 2026, Artelys (https://www.artelys.com)
This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at http://mozilla.org/MPL/2.0/.
SPDX-License-Identifier: MPL-2.0
#>

<#
.SYNOPSIS
  Builds a self-contained Windows app-image (jlink + jpackage) for PowSyBl Desktop.

.PARAMETER JavafxJmods
  Path to the extracted JavaFX Windows jmods directory (e.g. javafx-jmods-27\jmods),
  downloaded separately from https://gluonhq.com/products/javafx/ or https://jdk.java.net/javafx27/ .
  Falls back to the JAVAFX_JMODS environment variable if not passed.

.PARAMETER SkipBuild
  Skip "mvn clean package" and reuse the existing target/app-libs.

.EXAMPLE
  ./packaging/package-windows.ps1 -JavafxJmods C:\tools\javafx-jmods-27\jmods
#>
param(
    [string]$JavafxJmods = $env:JAVAFX_JMODS,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path "$PSScriptRoot\.."
Set-Location $repoRoot

if (-not $JavafxJmods) {
    throw "Pass -JavafxJmods <path to javafx-jmods-27\jmods> or set the JAVAFX_JMODS environment variable."
}
if (-not (Test-Path $JavafxJmods)) {
    throw "JavaFX jmods directory not found: $JavafxJmods"
}

$appName = "PowSyBl Desktop"
$mainClass = "com.powsybl.powsybldesktop.Launcher"
$appLibs = "target\app-libs"
$runtimeDir = "target\runtime"
$distDir = "target\dist"

if (-not $SkipBuild) {
    Write-Host "==> mvn clean package"
    mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
}

$mainJar = Get-ChildItem "$appLibs\powsybl-desktop-*.jar" | Select-Object -First 1
if (-not $mainJar) { throw "Main jar not found in $appLibs - did the build run?" }

$version = $mainJar.BaseName -replace '^powsybl-desktop-', '' -replace '-SNAPSHOT$', ''
Write-Host "==> App version for jpackage: $version"

Write-Host "==> Resolving java.home"
# java writes -XshowSettings to stderr; merge via cmd so PowerShell doesn't treat it as a terminating error.
$javaHomeLine = cmd /c "java -XshowSettings:properties -version 2>&1" | Select-String 'java\.home'
$javaHome = ($javaHomeLine -split '=')[1].Trim()
$jdkJmods = Join-Path $javaHome "jmods"
Write-Host "    JDK jmods: $jdkJmods"

Write-Host "==> jdeps: detecting required JDK modules"
$jdepsModules = & jdeps `
    --multi-release 25 `
    --ignore-missing-deps `
    --print-module-deps `
    --class-path "$appLibs\*" `
    $mainJar.FullName
if ($LASTEXITCODE -ne 0) { throw "jdeps failed" }
$jdepsModules = $jdepsModules.Trim()
Write-Host "    Detected: $jdepsModules"

$allModules = "$jdepsModules,javafx.controls,javafx.fxml,javafx.web"

if (Test-Path $runtimeDir) { Remove-Item $runtimeDir -Recurse -Force }
Write-Host "==> jlink: building custom runtime image"
& jlink `
    --module-path "$jdkJmods;$JavafxJmods" `
    --add-modules $allModules `
    --output $runtimeDir `
    --strip-debug `
    --no-header-files `
    --no-man-pages `
    --compress=zip-6
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

if (Test-Path $distDir) { Remove-Item $distDir -Recurse -Force }
Write-Host "==> jpackage: building app-image"
$jpackageArgs = @(
    "--type", "app-image",
    "--input", $appLibs,
    "--dest", $distDir,
    "--name", $appName,
    "--app-version", $version,
    "--vendor", "Artelys",
    "--main-jar", $mainJar.Name,
    "--main-class", $mainClass,
    "--runtime-image", $runtimeDir
)
$iconPath = "packaging\icons\app.ico"
if (Test-Path $iconPath) {
    $jpackageArgs += @("--icon", $iconPath)
} else {
    Write-Host "    No $iconPath found - using jpackage's default icon (see README to add one)."
}

& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

Write-Host "==> Done: $distDir\$appName\$appName.exe"
