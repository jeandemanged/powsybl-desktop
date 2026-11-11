/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The user's guide (Sphinx sources in {@code docs/ug}), built separately from the application, so either format may be
 * missing. The packaging scripts copy it to a {@code user-guide} directory next to the application's jar; when not
 * packaged (e.g. {@code mvn javafx:run} from the repository root), it is read from the documentation build directory.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record UserGuide(Optional<Path> pdf, Optional<Path> html) {

    private static final String PDF_FILE_NAME = "powsybl-desktop-user-guide.pdf";

    public static UserGuide locate() {
        // set by the jpackage launcher
        if (System.getProperty("jpackage.app-path") == null) {
            Path build = Path.of("docs", "ug", "_build").toAbsolutePath();
            return of(build.resolve("latex").resolve(PDF_FILE_NAME), build.resolve("html").resolve("index.html"));
        }
        Path directory;
        try {
            directory = Path.of(UserGuide.class.getProtectionDomain().getCodeSource().getLocation().toURI()).resolveSibling("user-guide");
        } catch (URISyntaxException _) {
            return new UserGuide(Optional.empty(), Optional.empty());
        }
        return of(directory.resolve(PDF_FILE_NAME), directory.resolve("html").resolve("index.html"));
    }

    private static UserGuide of(Path pdf, Path html) {
        return new UserGuide(Optional.of(pdf).filter(Files::isRegularFile), Optional.of(html).filter(Files::isRegularFile));
    }
}
