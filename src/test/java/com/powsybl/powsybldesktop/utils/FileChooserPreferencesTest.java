/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import com.powsybl.commons.datasource.CompressionFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class FileChooserPreferencesTest {

    @Test
    void extensionPatternsIncludesBaseAndCompressedVariants() {
        List<String> patterns = FileChooserPreferences.extensionPatterns(List.of("xiidm"));

        assertEquals(CompressionFormat.values().length + 1, patterns.size());
        assertTrue(patterns.contains("*.xiidm"));
        assertTrue(patterns.contains("*.xiidm." + CompressionFormat.GZIP.getExtension()));
    }

    @Test
    void extensionPatternsCoversEachBaseExtension() {
        List<String> patterns = FileChooserPreferences.extensionPatterns(List.of("xiidm", "uct"));

        assertTrue(patterns.contains("*.xiidm"));
        assertTrue(patterns.contains("*.uct"));
        assertEquals((CompressionFormat.values().length + 1) * 2, patterns.size());
    }

    @Test
    void extensionPatternsOnEmptyInputIsEmpty() {
        assertTrue(FileChooserPreferences.extensionPatterns(List.of()).isEmpty());
    }
}
