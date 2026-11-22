/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.entsoe.utils.CapacityCalculationRegion;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.io.cim.parameters.CimCracCreationParameters;
import com.powsybl.openrao.data.crac.io.cse.parameters.CseCracCreationParameters;
import com.powsybl.openrao.data.crac.io.fbconstraint.parameters.FbConstraintCracCreationParameters;
import com.powsybl.openrao.data.crac.io.nc.parameters.NcCracCreationParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CracCreationParametersSupportTest {

    private final CracCreationParameters parameters = new CracCreationParameters();

    private static String invalid(int row, String reason) {
        return Messages.get("cracCreation.row.invalid", row, reason);
    }

    @Test
    void usageLimitsPerInstantAndTso() {
        List<List<String>> rows = List.of(List.of("curative", "3"));
        List<List<String>> perTso = List.of(List.of("curative", "FR", "1", "", "2", ""), List.of("preventive", "BE", "", "4", "", ""));

        CracCreationParametersSupport.setUsageLimits(parameters, rows, perTso);

        assertEquals(3, parameters.getRaUsageLimitsPerInstant().get("curative").getMaxRa());
        assertEquals(1, parameters.getRaUsageLimitsPerInstant().get("curative").getMaxTopoPerTso().get("FR"));
        assertNull(parameters.getRaUsageLimitsPerInstant().get("preventive").getMaxRa());
        assertEquals(List.of(List.of("curative", "3"), List.of("preventive", "")), CracCreationParametersSupport.usageLimitsRows(parameters));
        assertEquals(List.of(List.of("curative", "FR", "1", "", "2", ""), List.of("preventive", "BE", "", "4", "", "")),
                CracCreationParametersSupport.usageLimitsPerTsoRows(parameters));
    }

    @Test
    void invalidRowsLeaveTheParametersUnchanged() {
        CracCreationParametersSupport.setUsageLimits(parameters, List.of(List.of("curative", "3")), List.of());

        IllegalArgumentException notANumber = assertThrows(IllegalArgumentException.class,
                () -> CracCreationParametersSupport.setUsageLimits(parameters, List.of(List.of("curative", "three")), List.of()));
        assertEquals(invalid(1, Messages.get("cracCreation.row.integer", 2, "three")), notANumber.getMessage());
        IllegalArgumentException noInstant = assertThrows(IllegalArgumentException.class,
                () -> CracCreationParametersSupport.setUsageLimits(parameters, List.of(List.of("curative", "1"), List.of(" ", "2")), List.of()));
        assertEquals(invalid(2, Messages.get("cracCreation.row.required", 1)), noInstant.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> CracCreationParametersSupport.setUsageLimits(parameters, List.of(List.of("curative", "1"), List.of("curative", "2")), List.of()));

        assertEquals(3, parameters.getRaUsageLimitsPerInstant().get("curative").getMaxRa());
    }

    @Test
    void rangeActionGroups() {
        CseCracCreationParameters cse = CracCreationParametersSupport.cse(parameters);

        CracCreationParametersSupport.setRangeActionGroups(cse, List.of(List.of("pst1+ pst2 + pst3")));

        assertEquals(List.of("pst1 + pst2 + pst3"), cse.getRangeActionGroupsAsString());
        assertEquals(List.of(List.of("pst1 + pst2 + pst3")), CracCreationParametersSupport.rangeActionGroupsRows(cse));
        assertThrows(IllegalArgumentException.class, () -> CracCreationParametersSupport.setRangeActionGroups(cse, List.of(List.of("pst1"))));
    }

    @Test
    void busBarChangesByRemedialAction() {
        CseCracCreationParameters cse = CracCreationParametersSupport.cse(parameters);
        List<List<String>> rows = List.of(List.of("ra1", "open1", "close1"), List.of("ra1", "open2", "close2"), List.of("ra2", "open3", "close3"));

        CracCreationParametersSupport.setBusBarChangeSwitches(cse, rows);

        assertEquals(2, cse.getBusBarChangeSwitchesSet().size());
        assertEquals(2, cse.getBusBarChangeSwitches("ra1").getSwitchPairs().size());
        assertEquals(rows, CracCreationParametersSupport.busBarChangeSwitchesRows(cse));
    }

    @Test
    void cimTimeseriesSpeedsAndVoltageCnecs() {
        CimCracCreationParameters cim = CracCreationParametersSupport.cim(parameters);

        CracCreationParametersSupport.setTimeseriesMrids(cim, List.of(List.of("ts1"), List.of("ts2")));
        CracCreationParametersSupport.setRangeActionSpeeds(cim, List.of(List.of("pst1", "1"), List.of("pst2", "2")));
        CracCreationParametersSupport.setVoltageCnecs(cim, List.of(List.of("vl1")),
                List.of(List.of("preventive", ""), List.of("curative", "co1, co2")),
                List.of(List.of("preventive", "400", "380", "420"), List.of("curative", "400", "", "430")));

        assertEquals(Set.of("ts1", "ts2"), cim.getTimeseriesMrids());
        assertEquals(List.of(List.of("pst1", "1"), List.of("pst2", "2")), CracCreationParametersSupport.rangeActionSpeedsRows(cim));
        assertEquals(List.of(List.of("vl1")), CracCreationParametersSupport.voltageMonitoredElementsRows(cim));
        assertEquals(List.of(List.of("curative", "co1, co2"), List.of("preventive", "")), CracCreationParametersSupport.voltageMonitoredStatesRows(cim));
        assertEquals(List.of(List.of("curative", "400", "", "430"), List.of("preventive", "400", "380", "420")),
                CracCreationParametersSupport.voltageThresholdsRows(cim));

        // an instant needs thresholds
        assertThrows(IllegalArgumentException.class, () -> CracCreationParametersSupport.setVoltageCnecs(cim, List.of(List.of("vl1")),
                List.of(List.of("preventive", "")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> CracCreationParametersSupport.setVoltageCnecs(cim, List.of(List.of("vl1")),
                List.of(List.of("preventive", "")), List.of(List.of("curative", "400", "", "430"))));
        CracCreationParametersSupport.setVoltageCnecs(cim, List.of(), List.of(), List.of());
        assertNull(cim.getVoltageCnecsCreationParameters());
    }

    @Test
    void ncCurativeInstantsAndRegion() {
        NcCracCreationParameters nc = CracCreationParametersSupport.nc(parameters);
        assertEquals(List.of(List.of("curative 1", "300"), List.of("curative 2", "600"), List.of("curative 3", "1200")),
                CracCreationParametersSupport.curativeInstantsRows(nc));

        CracCreationParametersSupport.setCurativeInstants(nc, List.of(List.of("curative", "900")));
        nc.setCapacityCalculationRegion(CapacityCalculationRegion.CORE);

        assertEquals(List.of(List.of("curative", "900")), CracCreationParametersSupport.curativeInstantsRows(nc));
        assertThrows(IllegalArgumentException.class, () -> CracCreationParametersSupport.setCurativeInstants(nc, List.of(List.of("curative", ""))));
    }

    @Test
    void internalHvdcsGroupedByHvdcAndPole() {
        FbConstraintCracCreationParameters flowBased = CracCreationParametersSupport.flowBasedConstraint(parameters);
        List<List<String>> converters = List.of(List.of("eic1", "code1", "pole1", "node1", "station1"), List.of("eic1", "code1", "pole1", "node2", "station2"),
                List.of("eic1", "code1", "pole2", "node3", "station3"));
        List<List<String>> lines = List.of(List.of("eic1", "code1", "pole1", "line1", "node1", "node2"));

        CracCreationParametersSupport.setInternalHvdcs(flowBased, converters, lines);

        assertEquals(1, flowBased.getInternalHvdcs().size());
        assertEquals(2, flowBased.getInternalHvdcs().getFirst().poles().size());
        assertEquals(converters, CracCreationParametersSupport.hvdcConvertersRows(flowBased));
        assertEquals(lines, CracCreationParametersSupport.hvdcLinesRows(flowBased));
    }

    @Test
    void readingAddsNoExtensionAndJsonRoundTrips() {
        CracCreationParametersSupport.read(parameters, CimCracCreationParameters.class, CimCracCreationParameters::new);
        assertNull(parameters.getExtension(CimCracCreationParameters.class));

        parameters.setDefaultMonitoredLineSide(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_SIDE_TWO);
        CracCreationParametersSupport.setTimeseriesMrids(CracCreationParametersSupport.cim(parameters), List.of(List.of("ts1")));
        CracCreationParameters read = CracCreationParametersSupport.fromJson(CracCreationParametersSupport.toJson(parameters));

        assertEquals(parameters.getDefaultMonitoredSides(), read.getDefaultMonitoredSides());
        assertTrue(read.getExtension(CimCracCreationParameters.class).getTimeseriesMrids().contains("ts1"));
    }
}
