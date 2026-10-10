/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import com.powsybl.commons.datasource.CompressionFormat;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

/**
 * Remembers the last directory used in a {@link FileChooser}, across all choosers in the application,
 * using the Java Preferences API so it persists between runs.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class FileChooserPreferences {

    private static final String LAST_DIRECTORY_KEY = "lastDirectory";

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(FileChooserPreferences.class);

    private static final List<String> COMPRESSION_EXTENSIONS = Arrays.stream(CompressionFormat.values())
            .map(CompressionFormat::getExtension)
            .toList();

    private FileChooserPreferences() {
    }

    /**
     * Builds glob patterns (e.g. {@code *.xiidm}, {@code *.xiidm.gz}) for the given base extensions, including
     * their compressed variants ({@link CompressionFormat}), since PowSyBl datasources transparently read/write
     * those regardless of format.
     */
    public static List<String> extensionPatterns(Collection<String> baseExtensions) {
        return baseExtensions.stream()
                .flatMap(ext -> Stream.concat(Stream.of(ext), COMPRESSION_EXTENSIONS.stream().map(c -> ext + "." + c)))
                .map(ext -> "*." + ext)
                .toList();
    }

    public static void applyLastDirectory(FileChooser fileChooser) {
        String path = PREFERENCES.get(LAST_DIRECTORY_KEY, null);
        if (path != null) {
            File directory = new File(path);
            if (directory.isDirectory()) {
                fileChooser.setInitialDirectory(directory);
            }
        }
    }

    public static void applyLastDirectory(DirectoryChooser directoryChooser) {
        String path = PREFERENCES.get(LAST_DIRECTORY_KEY, null);
        if (path != null) {
            File directory = new File(path);
            if (directory.isDirectory()) {
                directoryChooser.setInitialDirectory(directory);
            }
        }
    }

    public static void saveLastDirectory(File selectedFile) {
        File directory = selectedFile.isDirectory() ? selectedFile : selectedFile.getParentFile();
        if (directory != null) {
            PREFERENCES.put(LAST_DIRECTORY_KEY, directory.getAbsolutePath());
        }
    }
}
