/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.limitscaling;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.criteria.AtLeastOneCountryCriterion;
import com.powsybl.iidm.criteria.AtLeastOneNominalVoltageCriterion;
import com.powsybl.iidm.criteria.IdentifiableCriterion;
import com.powsybl.iidm.criteria.LineCriterion;
import com.powsybl.iidm.criteria.NetworkElementIdListCriterion;
import com.powsybl.iidm.criteria.TwoCountriesCriterion;
import com.powsybl.iidm.criteria.VoltageInterval;
import com.powsybl.iidm.criteria.duration.EqualityTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.IntervalTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.PermanentDurationCriterion;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import com.powsybl.security.limitscaling.LimitScaling;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LimitScalingsTest {

    // NHV1_NHV2_1 carries about 457 A on side 1 after a load flow
    private static Network network() {
        Network network = EurostagTutorialExample1Factory.createWithLFResults();
        Line line = network.getLine("NHV1_NHV2_1");
        line.getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits().setPermanentLimit(500).add();
        return network;
    }

    private static double current(Network network) {
        return network.getLine("NHV1_NHV2_1").getTerminal1().getI();
    }

    @Test
    void violationsAndLoadingsAreRelativeToTheScaledLimits() {
        Network network = network();
        double i = current(network);
        assertNull(NetworkViolations.compute(network, LimitScalings.limitsComputer(List.of())).getWorstFlowViolation("NHV1_NHV2_1"));

        NetworkViolations violations = NetworkViolations.compute(network, LimitScalings.limitsComputer(List.of(new LimitScaling(LimitType.CURRENT, 0.8))));

        FlowViolation violation = violations.getWorstFlowViolation("NHV1_NHV2_1");
        assertEquals(0.8, violation.violation().getLimitScaling(), 1e-9);
        assertEquals(400, violation.permanentLimit(), 1e-9);
        assertEquals(i / 400 * 100, violations.getLoading("NHV1_NHV2_1").percent(), 1e-9);
    }

    @Test
    void monitoringOnlyScalingsScaleTheReportedViolations() {
        Network network = network();
        LimitScaling monitoringOnly = new LimitScaling(LimitType.CURRENT, 0.8, true);

        assertTrue(monitoringOnly.isMonitoringOnly());
        assertEquals(0.8, NetworkViolations.compute(network, LimitScalings.limitsComputer(List.of(monitoringOnly)))
                .getWorstFlowViolation("NHV1_NHV2_1").violation().getLimitScaling(), 1e-9);
    }

    @Test
    void theLastMatchingScalingWins() {
        Network network = network();
        List<LimitScaling> scalings = List.of(new LimitScaling(LimitType.CURRENT, 0.8), new LimitScaling(LimitType.CURRENT, 1.2, true));

        assertNull(NetworkViolations.compute(network, LimitScalings.limitsComputer(scalings)).getWorstFlowViolation("NHV1_NHV2_1"));
        assertFalse(NetworkViolations.compute(network, LimitScalings.limitsComputer(scalings.reversed())).getFlowViolations().isEmpty());
    }

    @Test
    void contingencyContextSelectsTheScalings() {
        Network network = network();
        List<LimitScaling> scalings = List.of(LimitScaling.builder(LimitType.CURRENT, 0.8)
                .withContingencyContext(ContingencyContext.specificContingency("C1")).build());

        assertNull(NetworkViolations.compute(network, LimitScalings.limitsComputer(scalings)).getWorstFlowViolation("NHV1_NHV2_1"));
        assertNull(NetworkViolations.compute(network, LimitScalings.limitsComputer(scalings, "C2")).getWorstFlowViolation("NHV1_NHV2_1"));
        assertFalse(NetworkViolations.compute(network, LimitScalings.limitsComputer(scalings, "C1")).getFlowViolations().isEmpty());
    }

    @Test
    void jsonRoundTripKeepsEveryField(@TempDir Path folder) {
        List<LimitScaling> scalings = List.of(
                new LimitScaling(LimitType.CURRENT, 0.9),
                LimitScaling.builder(LimitType.APPARENT_POWER, 1.1)
                        .withMonitoringOnly(true)
                        .withContingencyContext(ContingencyContext.onlyContingencies())
                        .withNetworkElementCriteria(
                                new LineCriterion(new TwoCountriesCriterion(List.of(Country.FR), List.of()), null),
                                new IdentifiableCriterion(new AtLeastOneCountryCriterion(List.of(Country.BE)),
                                        new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(200, 400, true, false))),
                                new NetworkElementIdListCriterion(Set.of("L1", "L2")))
                        .withLimitDurationCriteria(new PermanentDurationCriterion(), IntervalTemporaryDurationCriterion.between(60, 600, true, true))
                        .withOperationalLimitsGroupIdSelection("WINTER")
                        .build(),
                LimitScaling.builder(LimitType.ACTIVE_POWER, 0.5)
                        .withContingencyContext(ContingencyContext.specificContingency("C1"))
                        .withLimitDurationCriteria(new EqualityTemporaryDurationCriterion(60))
                        .build());
        Path file = folder.resolve("scalings.json");

        LimitScalings.write(scalings, file);
        List<LimitScaling> read = LimitScalings.read(file);

        assertTrue(LimitScalings.sameContent(scalings, read));
        assertEquals(3, read.size());
        assertEquals(3, read.get(1).getNetworkElementCriteria().size());
        assertFalse(LimitScalings.sameContent(scalings, read.reversed()));
    }

    @Test
    void affectedLimitsListTheMatchedLimitsWithTheirScaledValue() {
        Network network = network();

        List<AffectedLimits.AffectedLimit> all = AffectedLimits.compute(network, new LimitScaling(LimitType.CURRENT, 0.8));
        List<AffectedLimits.AffectedLimit> one = AffectedLimits.compute(network, LimitScaling.builder(LimitType.CURRENT, 1.0)
                .withNetworkElementCriteria(new NetworkElementIdListCriterion(Set.of("NHV1_NHV2_1")))
                .build());
        List<AffectedLimits.AffectedLimit> otherType = AffectedLimits.compute(network, new LimitScaling(LimitType.ACTIVE_POWER, 0.8));

        AffectedLimits.AffectedLimit line = all.stream().filter(a -> a.equipment().getId().equals("NHV1_NHV2_1")).findFirst().orElseThrow();
        assertEquals(500, line.permanentLimit(), 1e-9);
        assertEquals(400, line.scaledPermanentLimit(), 1e-9);
        // a factor of 1 still shows what the criteria match
        assertEquals(1, one.size());
        assertEquals(500, one.getFirst().scaledPermanentLimit(), 1e-9);
        assertTrue(otherType.isEmpty());
    }
}
