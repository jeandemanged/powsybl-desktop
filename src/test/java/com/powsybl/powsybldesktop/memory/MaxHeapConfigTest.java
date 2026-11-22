/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MaxHeapConfigTest {

    private static final long GB = 1024L * 1024 * 1024;

    @Test
    void availableChoicesStopAtPhysicalMemory() {
        assertEquals(List.of(0, 4, 8, 12), MaxHeapConfig.availableChoices(15 * GB));
        assertEquals(List.of(0, 4, 8, 12, 24, 32, 40, 48, 64, 128), MaxHeapConfig.availableChoices(256 * GB));
    }

    @Test
    void availableChoicesAlwaysOfferSmallestHeap() {
        assertEquals(List.of(0, 4), MaxHeapConfig.availableChoices(2 * GB));
    }

    @Test
    void maxHeapGbTakesLastXmx() {
        assertEquals(MaxHeapConfig.AUTOMATIC, MaxHeapConfig.maxHeapGb(List.of("-Dfoo=bar")));
        assertEquals(8, MaxHeapConfig.maxHeapGb(List.of("-Xmx4g", "-Dfoo=bar", "-Xmx8G")));
        assertEquals(MaxHeapConfig.AUTOMATIC, MaxHeapConfig.maxHeapGb(List.of("-Xmx4g", "-Xmx512m")));
    }

    @Test
    void writeReplacesXmxAndKeepsLineSeparator(@TempDir Path dir) throws IOException {
        Path config = dir.resolve("PowSyBl Desktop.cfg");
        Files.writeString(config, "[Application]\r\napp.mainclass=Main\r\n\r\n[JavaOptions]\r\n"
                + "java-options=-Djpackage.app-version=0.1.0\r\njava-options=-Xmx4g\r\n");

        MaxHeapConfig.write(config, 8);
        assertEquals("[Application]\r\napp.mainclass=Main\r\n\r\n[JavaOptions]\r\njava-options=-Xmx8g\r\n"
                + "java-options=-Djpackage.app-version=0.1.0\r\n", Files.readString(config));
        assertEquals(8, MaxHeapConfig.read(config));

        MaxHeapConfig.write(config, MaxHeapConfig.AUTOMATIC);
        assertEquals("[Application]\r\napp.mainclass=Main\r\n\r\n[JavaOptions]\r\n"
                + "java-options=-Djpackage.app-version=0.1.0\r\n", Files.readString(config));
        assertEquals(MaxHeapConfig.AUTOMATIC, MaxHeapConfig.read(config));
    }

    @Test
    void withMaxHeapAddsMissingSection() {
        assertEquals(List.of("[Application]", "[JavaOptions]", "java-options=-Xmx12g"),
                MaxHeapConfig.withMaxHeap(List.of("[Application]"), 12));
    }
}
