/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.limitscaling;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.criteria.AtLeastOneCountryCriterion;
import com.powsybl.iidm.criteria.AtLeastOneNominalVoltageCriterion;
import com.powsybl.iidm.criteria.IdentifiableCriterion;
import com.powsybl.iidm.criteria.LineCriterion;
import com.powsybl.iidm.criteria.VoltageInterval;
import com.powsybl.iidm.criteria.duration.AllTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.EqualityTemporaryDurationCriterion;
import com.powsybl.iidm.criteria.duration.PermanentDurationCriterion;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.limitscaling.OlfLimitScalingSupport.Status;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisRunParameters;
import com.powsybl.security.limitscaling.LimitScaling;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs Open Load Flow's security analysis on each of {@link OlfLimitScalingSupport}'s rules: a factor of 0.1 makes
 * every line overloaded when applied, none otherwise.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class OlfLimitScalingSupportTest {

    private static final double FACTOR = 0.1;
    private static final AtLeastOneNominalVoltageCriterion HV = new AtLeastOneNominalVoltageCriterion(VoltageInterval.between(300, 500, true, true));

    // the pre-contingency current violations' limit scaling, empty if none
    private static OptionalDouble olfScaling(LimitScaling scaling) {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        SecurityAnalysisRunParameters parameters = SecurityAnalysisRunParameters.getDefault().setLimitScalings(List.of(scaling));
        List<LimitViolation> violations = SecurityAnalysis.run(network, List.of(), parameters)
                .getResult().getPreContingencyResult().getLimitViolationsResult().getLimitViolations();
        return violations.stream()
                .filter(v -> v.getLimitType() == LimitViolationType.CURRENT && v.getLimitScaling() < 1)
                .mapToDouble(LimitViolation::getLimitScaling)
                .findFirst();
    }

    private static void assertApplied(LimitScaling scaling, Status status) {
        assertEquals(status, OlfLimitScalingSupport.of(scaling).status());
        assertEquals(FACTOR, olfScaling(scaling).orElseThrow(), 1e-9);
    }

    private static void assertNotApplied(LimitScaling scaling) {
        assertEquals(Status.NOT_APPLIED, OlfLimitScalingSupport.of(scaling).status());
        assertTrue(olfScaling(scaling).isEmpty());
    }

    private static LimitScaling.Builder current() {
        return LimitScaling.builder(LimitType.CURRENT, FACTOR);
    }

    @Test
    void supported() {
        assertApplied(current().build(), Status.APPLIED);
        assertApplied(current().withNetworkElementCriteria(new IdentifiableCriterion(HV)).build(), Status.APPLIED);
        assertApplied(current().withLimitDurationCriteria(new PermanentDurationCriterion(), new AllTemporaryDurationCriterion()).build(), Status.APPLIED);
    }

    @Test
    void countriesAreIgnored() {
        // the lines are in France
        assertApplied(current().withNetworkElementCriteria(new IdentifiableCriterion(new AtLeastOneCountryCriterion(List.of(Country.BE)), HV)).build(),
                Status.PARTIALLY_APPLIED);
    }

    @Test
    void contingencyContextOtherThanAll() {
        assertNotApplied(current().withContingencyContext(ContingencyContext.none()).build());
        assertNotApplied(current().withContingencyContext(ContingencyContext.onlyContingencies()).build());
    }

    @Test
    void monitoringOnly() {
        assertNotApplied(current().withMonitoringOnly(true).build());
    }

    @Test
    void otherLimitTypes() {
        LimitScaling scaling = LimitScaling.builder(LimitType.ACTIVE_POWER, FACTOR).build();
        assertEquals(Status.NOT_APPLIED, OlfLimitScalingSupport.of(scaling).status());
    }

    @Test
    void criteriaOtherThanIdentifiable() {
        assertNotApplied(current().withNetworkElementCriteria(new LineCriterion(null, null)).build());
    }

    @Test
    void durationCriteriaOfTheSameKind() {
        assertNotApplied(current().withLimitDurationCriteria(new AllTemporaryDurationCriterion(), new EqualityTemporaryDurationCriterion(60)).build());
    }

    @Test
    void limitsGroupsSelection() {
        assertNotApplied(current().withOperationalLimitsGroupIdSelection("DEFAULT").build());
    }

    @Test
    void identifiableCriterionWithoutNominalVoltage() {
        LimitScaling scaling = current().withNetworkElementCriteria(new IdentifiableCriterion(new AtLeastOneCountryCriterion(List.of(Country.FR)))).build();
        assertEquals(Status.FAILS, OlfLimitScalingSupport.of(scaling).status());
        assertThrows(RuntimeException.class, () -> olfScaling(scaling));
    }
}
