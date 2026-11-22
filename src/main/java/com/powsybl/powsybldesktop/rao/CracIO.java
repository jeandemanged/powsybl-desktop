/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.commons.util.ServiceLoaderCache;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.CracCreationContext;
import com.powsybl.openrao.data.crac.api.io.Importer;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

/**
 * Reads CRAC files in every format an OpenRAO importer handles, and writes them in OpenRAO's JSON format. Kept
 * toolkit-free for testing.
 * <p>
 * The importer is chosen here rather than by {@link Crac#readWithContext}, which takes any importer claiming the file:
 * OpenRAO's {@value #NETWORK_FORMAT} importer (the CRAC generator) claims any file it can read as a network, through a
 * temporary copy, and then ignores it, generating a CRAC from the network it is given.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CracIO {

    public static final String JSON_FORMAT = "JSON";
    static final String NETWORK_FORMAT = "Network";
    private static final String JSON_FILE_NAME = "crac.json";
    private static final ServiceLoaderCache<Importer> IMPORTERS = new ServiceLoaderCache<>(Importer.class);

    private CracIO() {
    }

    /**
     * The format of the CRAC file {@code fileName} holding {@code bytes}, if an importer handles it.
     *
     * @throws com.powsybl.openrao.commons.OpenRaoException when the importer of its format finds it invalid (a JSON CRAC
     *                                                     that doesn't match its schema)
     */
    public static Optional<String> format(String fileName, byte[] bytes) {
        return importer(fileName, bytes).map(Importer::getFormat);
    }

    /**
     * Imports the CRAC file {@code fileName} holding {@code bytes} for {@code network}: the context holds the CRAC, null
     * when its creation failed, and the creation report telling what was ignored or altered, and why it failed.
     *
     * @throws IllegalArgumentException when no importer handles the file
     */
    public static CracCreationContext read(String fileName, byte[] bytes, Network network, CracCreationParameters parameters) {
        Importer importer = importer(fileName, bytes)
                .orElseThrow(() -> new IllegalArgumentException("No CRAC importer handles " + fileName));
        return importer.importData(new ByteArrayInputStream(bytes), parameters, network);
    }

    public static void writeJson(Crac crac, OutputStream outputStream) {
        crac.write(JSON_FORMAT, outputStream);
    }

    public static byte[] toJson(Crac crac) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        writeJson(crac, outputStream);
        return outputStream.toByteArray();
    }

    /**
     * Reads a CRAC written by {@link #writeJson} for {@code network}.
     */
    public static CracCreationContext readJson(InputStream inputStream, Network network) throws IOException {
        return read(JSON_FILE_NAME, inputStream.readAllBytes(), network, new CracCreationParameters());
    }

    /**
     * A copy of {@code crac}, through its JSON, for {@code network}.
     *
     * @throws IllegalStateException when the copy can't be created for the network, with the creation report's lines
     */
    public static Crac copy(Crac crac, Network network) {
        CracCreationContext context = read(JSON_FILE_NAME, toJson(crac), network, new CracCreationParameters());
        if (!context.isCreationSuccessful()) {
            throw new IllegalStateException(String.join("\n", report(context)));
        }
        return context.getCrac();
    }

    /**
     * The lines of the creation report of {@code context}.
     */
    public static List<String> report(CracCreationContext context) {
        return List.copyOf(context.getCreationReport().getReport());
    }

    private static Optional<Importer> importer(String fileName, byte[] bytes) {
        return IMPORTERS.getServices().stream()
                .filter(importer -> !NETWORK_FORMAT.equals(importer.getFormat()))
                .filter(importer -> importer.exists(fileName, new ByteArrayInputStream(bytes)))
                .findFirst();
    }
}
