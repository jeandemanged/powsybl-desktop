/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class RecentStudiesTest {

    // a node of its own, not the application's
    private final Preferences preferences = Preferences.userRoot().node("powsybl-desktop-test-" + UUID.randomUUID());

    @TempDir
    private Path directory;

    @AfterEach
    void tearDown() throws BackingStoreException {
        preferences.removeNode();
    }

    @Test
    void mostRecentFirstWithoutDuplicates() {
        RecentStudies recentStudies = new RecentStudies();
        Path a = directory.resolve("a.psbx");
        Path b = directory.resolve("b.psbx");
        recentStudies.add(a);
        recentStudies.add(b);
        recentStudies.add(directory.resolve("sub").resolve("..").resolve("a.psbx"));

        assertEquals(List.of(a, b), recentStudies.getFiles());
    }

    @Test
    void keepsTheMostRecentOnly() {
        RecentStudies recentStudies = new RecentStudies();
        List<Path> files = IntStream.range(0, RecentStudies.MAX_SIZE + 2).mapToObj(i -> directory.resolve(i + ".psbx")).toList();
        files.forEach(recentStudies::add);

        assertEquals(RecentStudies.MAX_SIZE, recentStudies.getFiles().size());
        assertEquals(files.getLast(), recentStudies.getFiles().getFirst());
        assertEquals(files.get(2), recentStudies.getFiles().getLast());
    }

    @Test
    void persistedAcrossInstances() {
        Path a = directory.resolve("a.psbx");
        Path b = directory.resolve("b.psbx");
        Path c = directory.resolve("c.psbx");
        RecentStudies recentStudies = new RecentStudies(preferences);
        recentStudies.add(a);
        recentStudies.add(b);
        recentStudies.add(c);
        recentStudies.remove(b);

        assertEquals(List.of(c, a), new RecentStudies(preferences).getFiles());

        recentStudies.clear();

        assertTrue(new RecentStudies(preferences).getFiles().isEmpty());
    }
}
