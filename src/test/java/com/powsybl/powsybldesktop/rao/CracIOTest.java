/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.iidm.serde.NetworkSerDe;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.CracCreationContext;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CracIOTest {

    private final Network network = RaoTestCases.network();
    private final Crac crac = RaoTestCases.crac(network);

    @Test
    void jsonRoundTrips() throws IOException {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        CracIO.writeJson(crac, json);

        CracCreationContext context = CracIO.readJson(new ByteArrayInputStream(json.toByteArray()), network);

        assertTrue(context.isCreationSuccessful(), String.join("\n", CracIO.report(context)));
        Crac read = context.getCrac();
        assertEquals(crac.getContingencies().size(), read.getContingencies().size());
        assertEquals(crac.getFlowCnecs().size(), read.getFlowCnecs().size());
        assertEquals(crac.getRemedialActions().size(), read.getRemedialActions().size());
        assertEquals(crac.getPstRangeAction(RaoTestCases.PST).getTapToAngleConversionMap(),
                read.getPstRangeAction(RaoTestCases.PST).getTapToAngleConversionMap());
    }

    @Test
    void tellsTheFormat() {
        assertEquals(Optional.of(CracIO.JSON_FORMAT), CracIO.format("crac.json", CracIO.toJson(crac)));
        assertEquals(Optional.empty(), CracIO.format("notes.txt", "not a CRAC".getBytes(StandardCharsets.UTF_8)));
    }

    // OpenRAO's CRAC generator claims any file it can read as a network, then ignores it
    @Test
    void neverTakesANetworkFileForACrac() {
        ByteArrayOutputStream xiidm = new ByteArrayOutputStream();
        NetworkSerDe.write(network, xiidm);
        byte[] bytes = xiidm.toByteArray();

        assertEquals(Optional.empty(), CracIO.format("network.xiidm", bytes));
        assertThrows(IllegalArgumentException.class, () -> CracIO.read("network.xiidm", bytes, network, new CracCreationParameters()));
    }

    @Test
    void failedCreationTellsWhy() {
        // without current limits, the JSON importer can't read the CNECs' branches
        Network withoutLimits = PhaseShifterTestCaseFactory.create();

        CracCreationContext context = CracIO.read("crac.json", CracIO.toJson(crac), withoutLimits, new CracCreationParameters());

        assertFalse(context.isCreationSuccessful());
        assertNull(context.getCrac());
        assertTrue(CracIO.report(context).stream().anyMatch(line -> line.contains("current limits")), CracIO.report(context).toString());
        IllegalStateException copyFailure = assertThrows(IllegalStateException.class, () -> CracIO.copy(crac, withoutLimits));
        assertTrue(copyFailure.getMessage().contains("current limits"), copyFailure.getMessage());
    }

    @Test
    void copyIsIndependent() {
        Crac copy = CracIO.copy(crac, network);

        assertNotSame(crac, copy);
        copy.removeNetworkAction(RaoTestCases.LOAD_SHEDDING);
        assertNull(copy.getNetworkAction(RaoTestCases.LOAD_SHEDDING));
        assertNotNull(crac.getNetworkAction(RaoTestCases.LOAD_SHEDDING));
    }
}
