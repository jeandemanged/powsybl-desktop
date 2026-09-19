#!/usr/bin/env bash
# Copyright (c) 2026, Artelys (https://www.artelys.com)
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
# SPDX-License-Identifier: MPL-2.0

# Builds a self-contained macOS app-image (jlink + jpackage) for PowSyBl Desktop.
#
# Usage:
#   ./packaging/package-macos.sh <path-to-javafx-jmods-27/jmods> [--skip-build]
#   JAVAFX_JMODS=/opt/javafx-jmods-27/jmods ./packaging/package-macos.sh
#
# JavaFX jmods (distinct from the jars on Maven Central) must be downloaded separately from
# https://gluonhq.com/products/javafx/ or https://jdk.java.net/javafx27/ - pick the mac or
# mac-aarch64 build matching this Mac's CPU (Intel vs Apple Silicon).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

JAVAFX_JMODS="${JAVAFX_JMODS:-}"
SKIP_BUILD=0
for arg in "$@"; do
    case "$arg" in
        --skip-build) SKIP_BUILD=1 ;;
        *) JAVAFX_JMODS="$arg" ;;
    esac
done

if [ -z "$JAVAFX_JMODS" ]; then
    echo "Pass the javafx jmods directory as an argument, or set JAVAFX_JMODS." >&2
    exit 1
fi
if [ ! -d "$JAVAFX_JMODS" ]; then
    echo "JavaFX jmods directory not found: $JAVAFX_JMODS" >&2
    exit 1
fi

APP_NAME="PowSyBl Desktop"
MAIN_CLASS="com.powsybl.powsybldesktop.Launcher"
APP_LIBS="target/app-libs"
RUNTIME_DIR="target/runtime"
DIST_DIR="target/dist"

if [ "$SKIP_BUILD" -eq 0 ]; then
    echo "==> mvn clean package"
    mvn clean package -DskipTests
fi

MAIN_JAR="$(ls "$APP_LIBS"/powsybl-desktop-*.jar | head -n1)"
if [ -z "$MAIN_JAR" ]; then
    echo "Main jar not found in $APP_LIBS - did the build run?" >&2
    exit 1
fi

VERSION="$(basename "$MAIN_JAR" .jar | sed -E 's/^powsybl-desktop-//; s/-SNAPSHOT$//')"
echo "==> App version for jpackage: $VERSION"

JAVA_HOME_RESOLVED="$(java -XshowSettings:properties -version 2>&1 | grep 'java.home' | sed -E 's/.*= *//')"
JDK_JMODS="$JAVA_HOME_RESOLVED/jmods"
echo "    JDK jmods: $JDK_JMODS"

echo "==> jdeps: detecting required JDK modules"
JDEPS_MODULES="$(jdeps \
    --multi-release 25 \
    --ignore-missing-deps \
    --print-module-deps \
    --class-path "$APP_LIBS/*" \
    "$MAIN_JAR")"
echo "    Detected: $JDEPS_MODULES"

ALL_MODULES="$JDEPS_MODULES,javafx.controls,javafx.fxml,javafx.web"

rm -rf "$RUNTIME_DIR"
echo "==> jlink: building custom runtime image"
jlink \
    --module-path "$JDK_JMODS:$JAVAFX_JMODS" \
    --add-modules "$ALL_MODULES" \
    --output "$RUNTIME_DIR" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6

rm -rf "$DIST_DIR"
echo "==> jpackage: building app-image"
JPACKAGE_ARGS=(
    --type app-image
    --input "$APP_LIBS"
    --dest "$DIST_DIR"
    --name "$APP_NAME"
    --app-version "$VERSION"
    --vendor "Artelys"
    --main-jar "$(basename "$MAIN_JAR")"
    --main-class "$MAIN_CLASS"
    --runtime-image "$RUNTIME_DIR"
    --mac-package-name "$APP_NAME"
)
ICON_PATH="packaging/icons/app.icns"
if [ -f "$ICON_PATH" ]; then
    JPACKAGE_ARGS+=(--icon "$ICON_PATH")
else
    echo "    No $ICON_PATH found - using jpackage's default icon (see README to add one)."
fi

jpackage "${JPACKAGE_ARGS[@]}"

echo "==> Done: $DIST_DIR/$APP_NAME.app"
