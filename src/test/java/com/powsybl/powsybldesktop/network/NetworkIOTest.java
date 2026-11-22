/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Exporter;
import com.powsybl.iidm.network.Importer;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkIOTest {

    @Test
    void iidmSerializationsShareASingleImportFormatAndParametersKey() {
        Map<String, List<Importer>> formats = NetworkIO.importFormats();
        assertEquals("IIDM", formats.keySet().iterator().next());
        assertTrue(formats.keySet().stream().noneMatch(format -> format.endsWith("IIDM") && !"IIDM".equals(format)));
        assertEquals("IIDM", NetworkIO.exportParametersKey("XIIDM"));
        assertEquals("CGMES", NetworkIO.exportParametersKey("CGMES"));
    }

    @Test
    void exportFormatsListTheIidmSerializationsFirst() {
        List<String> formats = NetworkIO.exportFormats();
        assertEquals(List.of("XIIDM", "BIIDM", "JIIDM"), formats.subList(0, 3));
        assertEquals(formats.subList(3, formats.size()).stream().sorted().toList(), formats.subList(3, formats.size()));
    }

    @Test
    void exportExtensionsAreThoseOfTheImporterOfTheSameFormat() {
        assertEquals(Importer.find("XIIDM").getSupportedExtensions(), NetworkIO.exportExtensions("XIIDM"));
        assertEquals(List.of(), NetworkIO.exportExtensions("NO_SUCH_FORMAT"));
    }

    @Test
    void acceptingImportersKeepsOnlyFormatsRecognizingTheFile(@TempDir Path dir) {
        Path file = dir.resolve("network.xiidm");
        EurostagTutorialExample1Factory.create().write("XIIDM", new Properties(), file);

        Map<String, List<Importer>> accepting = NetworkIO.acceptingImporters(NetworkIO.importFormats(), file);

        assertEquals(List.of("IIDM"), List.copyOf(accepting.keySet()));
        assertEquals(List.of("XIIDM"), accepting.get("IIDM").stream().map(Importer::getFormat).toList());
    }

    @Test
    void acceptingImportersIsEmptyForAnUnsupportedFile(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("notes.txt"), "not a network");

        assertTrue(NetworkIO.acceptingImporters(NetworkIO.importFormats(), file).isEmpty());
    }

    @Test
    void exportedNetworkImportsBackWithItsReports(@TempDir Path dir) {
        Network network = EurostagTutorialExample1Factory.create();
        Path file = dir.resolve("network.xiidm");

        ReportNode exportReport = NetworkIO.exportNetwork(network, Exporter.find("XIIDM"), file, new Properties());
        // the IIDM entry's importers: the one recognizing the file is picked
        NetworkAndReport imported = NetworkIO.importNetwork(NetworkIO.importFormats().get("IIDM"), file, new Properties());

        assertEquals("desktop.networkExport", exportReport.getMessageKey());
        assertEquals("desktop.networkImport", imported.reportNode().getMessageKey());
        assertEquals(network.getId(), imported.network().getId());
        assertEquals(network.getLineCount(), imported.network().getLineCount());
    }
}
