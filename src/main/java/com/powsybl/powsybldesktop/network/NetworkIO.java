/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.DataSource;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Exporter;
import com.powsybl.iidm.network.Exporters;
import com.powsybl.iidm.network.Importer;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Network import and export: the formats offered, which ones recognize a file, and the import and export themselves,
 * each with its report.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkIO {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkIO.class);
    private static final List<String> IIDM_FORMATS = List.of("XIIDM", "BIIDM", "JIIDM");

    private NetworkIO() {
    }

    /**
     * Import formats in menu order, XIIDM/BIIDM/JIIDM grouped under a single "IIDM" entry since the importer
     * is picked from the file content at import time.
     */
    public static Map<String, List<Importer>> importFormats() {
        Map<String, List<Importer>> formats = new LinkedHashMap<>();
        List<Importer> iidmImporters = IIDM_FORMATS.stream().map(Importer::find).filter(Objects::nonNull).toList();
        if (!iidmImporters.isEmpty()) {
            formats.put("IIDM", iidmImporters);
        }
        Importer.getFormats().stream()
                .filter(format -> !IIDM_FORMATS.contains(format))
                .sorted()
                .forEach(format -> formats.put(format, List.of(Importer.find(format))));
        return formats;
    }

    /**
     * Key under which an export format's parameters are stored: XIIDM/BIIDM/JIIDM share a single "IIDM" entry,
     * like on import, since they only differ by serialization and expose the same parameters.
     */
    public static String exportParametersKey(String format) {
        return IIDM_FORMATS.contains(format) ? "IIDM" : format;
    }

    /**
     * Export formats in menu order: the IIDM serializations first.
     */
    public static List<String> exportFormats() {
        return Stream.concat(
                IIDM_FORMATS.stream().filter(Exporter.getFormats()::contains),
                Exporter.getFormats().stream().filter(format -> !IIDM_FORMATS.contains(format)).sorted()
        ).toList();
    }

    /**
     * The file extensions {@code importers} read, without duplicates.
     */
    public static List<String> importExtensions(List<Importer> importers) {
        return importers.stream().flatMap(importer -> importer.getSupportedExtensions().stream()).distinct().toList();
    }

    /**
     * The file extensions of {@code format}: Exporter doesn't expose them, the importer of the same format does.
     */
    public static List<String> exportExtensions(String format) {
        Importer matchingImporter = Importer.find(format);
        return matchingImporter == null ? List.of() : matchingImporter.getSupportedExtensions();
    }

    /**
     * Formats of {@code formats}, in the same order, having at least one importer recognizing {@code path}, each
     * mapped to its recognizing importers only. Reads {@code path}, hence to be called off the FX thread.
     */
    public static Map<String, List<Importer>> acceptingImporters(Map<String, List<Importer>> formats, Path path) {
        Map<String, List<Importer>> accepting = new LinkedHashMap<>();
        ReadOnlyDataSource dataSource;
        try {
            dataSource = Exporters.createDataSource(path);
        } catch (PowsyblException | UncheckedIOException e) {
            LOGGER.debug("Cannot read {}", path, e);
            return accepting;
        }
        formats.forEach((format, importers) -> {
            List<Importer> matching = importers.stream().filter(importer -> accepts(importer, dataSource)).toList();
            if (!matching.isEmpty()) {
                accepting.put(format, matching);
            }
        });
        return accepting;
    }

    private static boolean accepts(Importer importer, ReadOnlyDataSource dataSource) {
        try {
            return importer.exists(dataSource);
        } catch (PowsyblException | UncheckedIOException e) {
            // some importers throw on content they cannot parse instead of returning false
            LOGGER.debug("Importer {} failed to check data source {}", importer.getFormat(), dataSource.getBaseName(), e);
            return false;
        }
    }

    /**
     * Imports the network at {@code path} (a file or, for CGMES, a folder) with the first of {@code importers}
     * recognizing it, to be called off the FX thread.
     */
    public static NetworkAndReport importNetwork(List<Importer> importers, Path path, Properties parameters) {
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("desktop.networkImport")
                .withTimestamp()
                .build();
        ReadOnlyDataSource dataSource = Exporters.createDataSource(path);
        Importer importer = importers.size() == 1 ? importers.getFirst()
                : importers.stream().filter(candidate -> candidate.exists(dataSource)).findFirst().orElse(importers.getFirst());
        Network network = importer.importData(dataSource, NetworkFactory.findDefault(), parameters, reportNode);
        return new NetworkAndReport(network, reportNode);
    }

    /**
     * Exports {@code network} to {@code path}, to be called off the FX thread; returns the export's report.
     */
    public static ReportNode exportNetwork(Network network, Exporter exporter, Path path, Properties parameters) {
        ReportNode reportNode = ReportNode.newRootReportNode()
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("desktop.networkExport")
                .withTimestamp()
                .build();
        DataSource dataSource = Exporters.createDataSource(path);
        exporter.export(network, parameters, dataSource, reportNode);
        return reportNode;
    }
}
