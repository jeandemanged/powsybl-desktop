/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ParametersConfigFileTest {

    @TempDir
    Path tempDir;

    @Test
    void savedParametersAreRestoredOnLoad() {
        Path path = tempDir.resolve("sub").resolve("config.json");
        MainModel saving = new MainModel();
        saving.loadFlowParametersProperty().get().setDc(true);
        saving.getNetworkImportParameters("IIDM").setProperty("some.param", "value");
        assertFalse(ParametersConfigFile.isSaved(saving));
        ParametersConfigFile.save(saving, path);
        assertTrue(ParametersConfigFile.isSaved(saving));

        MainModel loading = new MainModel();
        assertTrue(ParametersConfigFile.load(loading, path).isEmpty());
        assertTrue(loading.loadFlowParametersProperty().get().isDc());
        assertEquals("value", loading.getNetworkImportParameters("IIDM").getProperty("some.param"));
        assertTrue(ParametersConfigFile.isSaved(loading));

        loading.sldParametersProperty().get().getSvgParameters().setUseName(false);
        assertFalse(ParametersConfigFile.isSaved(loading));
    }

    @Test
    void missingFileKeepsDefaults() {
        MainModel mainModel = new MainModel();
        assertTrue(ParametersConfigFile.load(mainModel, tempDir.resolve("config.json")).isEmpty());
        assertFalse(ParametersConfigFile.isSaved(mainModel));
    }

    @Test
    void corruptedFileIsBackedUpAndReplacedByDefaults() throws IOException {
        Path path = tempDir.resolve("config.json");
        Files.writeString(path, "{ not json");
        MainModel mainModel = new MainModel();

        Optional<Notification> notification = ParametersConfigFile.load(mainModel, path);

        assertTrue(notification.isPresent());
        assertEquals(NotificationStatus.ERROR, notification.get().status());
        List<Path> backups;
        try (Stream<Path> files = Files.list(tempDir)) {
            backups = files.filter(p -> p.getFileName().toString().matches("config\\.json\\.\\d{8}-\\d{6}\\.bak")).toList();
        }
        assertEquals(1, backups.size());
        assertEquals("{ not json", Files.readString(backups.getFirst()));
        assertEquals(DesktopParametersJson.toJson(DesktopParameters.createDefault()), DesktopParametersJson.readTree(path));
        assertTrue(ParametersConfigFile.isSaved(mainModel));
    }
}
