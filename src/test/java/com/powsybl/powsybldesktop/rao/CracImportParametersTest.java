/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.entsoe.utils.CapacityCalculationRegion;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.io.cim.parameters.CimCracCreationParameters;
import com.powsybl.openrao.data.crac.io.fbconstraint.parameters.FbConstraintCracCreationParameters;
import com.powsybl.openrao.data.crac.io.nc.parameters.NcCracCreationParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CracImportParametersTest {

    private static final OffsetDateTime TIMESTAMP = OffsetDateTime.parse("2026-11-22T10:30Z");

    @Test
    void whatEachFormatNeeds() {
        for (String format : List.of(CracImportParameters.CIM, CracImportParameters.NC, CracImportParameters.FLOW_BASED_CONSTRAINT)) {
            assertTrue(CracImportParameters.needsTimestamp(format), format);
        }
        assertFalse(CracImportParameters.needsTimestamp(CracImportParameters.JSON));
        assertFalse(CracImportParameters.needsTimestamp(CracImportParameters.CSE));
        assertTrue(CracImportParameters.needsCapacityCalculationRegion(CracImportParameters.NC));
        assertFalse(CracImportParameters.needsCapacityCalculationRegion(CracImportParameters.CIM));
        assertTrue(CracImportParameters.hasIcsCosts(CracImportParameters.FLOW_BASED_CONSTRAINT));
    }

    @Test
    void timestampsGoToEachFormatsExtension() {
        CracCreationParameters parameters = new CracCreationParameters();
        assertNull(CracImportParameters.timestamp(parameters, CracImportParameters.CIM));

        CracImportParameters.setTimestamp(parameters, CracImportParameters.CIM, TIMESTAMP);
        CracImportParameters.setTimestamp(parameters, CracImportParameters.NC, TIMESTAMP.plusHours(1));
        CracImportParameters.setTimestamp(parameters, CracImportParameters.FLOW_BASED_CONSTRAINT, TIMESTAMP.plusHours(2));

        assertEquals(TIMESTAMP, parameters.getExtension(CimCracCreationParameters.class).getTimestamp());
        assertEquals(TIMESTAMP.plusHours(1), parameters.getExtension(NcCracCreationParameters.class).getTimestamp());
        assertEquals(TIMESTAMP.plusHours(2), parameters.getExtension(FbConstraintCracCreationParameters.class).getTimestamp());
        assertNull(CracImportParameters.timestamp(parameters, CracImportParameters.JSON));
    }

    @Test
    void missingRequiredValues() {
        CracCreationParameters parameters = new CracCreationParameters();
        assertEquals(List.of(), CracImportParameters.missing(parameters, CracImportParameters.JSON));
        assertEquals(List.of(Messages.get("crac.import.missingTimestamp"), Messages.get("crac.import.missingRegion")),
                CracImportParameters.missing(parameters, CracImportParameters.NC));

        CracImportParameters.setTimestamp(parameters, CracImportParameters.NC, TIMESTAMP);
        CracImportParameters.setCapacityCalculationRegion(parameters, CapacityCalculationRegion.SOUTH_WESTERN_EUROPE);
        assertEquals(List.of(), CracImportParameters.missing(parameters, CracImportParameters.NC));
    }

    @Test
    void copyKeepsTheFormatsParameters() {
        CracCreationParameters parameters = new CracCreationParameters();
        parameters.setDefaultMonitoredLineSide(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_SIDE_TWO);
        CracImportParameters.setTimestamp(parameters, CracImportParameters.FLOW_BASED_CONSTRAINT, TIMESTAMP);
        CracImportParameters.flowBasedConstraint(parameters).setIcsCostUp(42.);

        CracCreationParameters copy = CracImportParameters.copy(parameters);

        assertNotSame(parameters, copy);
        assertEquals(parameters.getDefaultMonitoredSides(), copy.getDefaultMonitoredSides());
        assertEquals(TIMESTAMP.toInstant(), CracImportParameters.timestamp(copy, CracImportParameters.FLOW_BASED_CONSTRAINT).toInstant());
        assertEquals(42., CracImportParameters.flowBasedConstraint(copy).getIcsCostUp());

        // OpenRAO's JSON writes time stamps with a literal Z, without converting them to UTC
        CracImportParameters.setTimestamp(parameters, CracImportParameters.CIM, OffsetDateTime.parse("2026-11-22T12:00+02:00"));
        assertEquals(OffsetDateTime.parse("2026-11-22T10:00Z").toInstant(),
                CracImportParameters.timestamp(CracImportParameters.copy(parameters), CracImportParameters.CIM).toInstant());
    }

    @Test
    void elementsMissingFromTheNetwork() {
        Network network = RaoTestCases.network();
        Crac crac = RaoTestCases.crac(network);
        network.getLoad("LD2").remove();

        assertEquals(List.of(), CracElements.missing(network, crac.getContingency(RaoTestCases.CONTINGENCY)));
        assertEquals(List.of("LD2"), CracElements.missing(network, crac.getRemedialAction(RaoTestCases.LOAD_SHEDDING)));
        assertEquals(List.of(), CracElements.missing(network, crac.getFlowCnec("L1 curative")));
    }
}
