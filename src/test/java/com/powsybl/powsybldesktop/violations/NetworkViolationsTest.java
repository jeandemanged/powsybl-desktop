/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.violations;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.DetectionKind;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.iidm.network.test.BoundaryLineNetworkFactory;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.security.Security;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkViolationsTest {

    private static NetworkViolations compute(Network network) {
        return NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS);
    }

    private static String key(LimitViolation v) {
        return String.join("|", v.getSubjectId(), String.valueOf(v.getSide()), v.getLimitType().name(), String.valueOf(v.getLimitName()),
                String.valueOf(v.getAcceptableDuration()), String.valueOf(v.getLimit()), String.valueOf(v.getValue()),
                String.valueOf(v.getOperationalLimitsGroupId()));
    }

    @Test
    void currentViolationsAreTheSameAsPowSyBlOnes() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();

        List<String> expected = Security.checkLimits(network).stream()
                .filter(v -> v.getLimitType() == LimitViolationType.CURRENT)
                .map(NetworkViolationsTest::key).sorted().toList();
        List<String> actual = compute(network).getFlowViolations().values().stream()
                .flatMap(List::stream)
                .map(FlowViolation::violation)
                .filter(v -> v.getLimitType() == LimitViolationType.CURRENT)
                .map(NetworkViolationsTest::key).sorted().toList();

        assertFalse(expected.isEmpty());
        assertEquals(expected, actual);
    }

    @Test
    void temporaryLimitViolationPercentageIsRelativeToThePermanentLimit() {
        Network network = EurostagTutorialExample1Factory.createWithLFResults();
        Line line = network.getLine("NHV1_NHV2_1");
        double i = line.getTerminal1().getI();
        assertFalse(Double.isNaN(i));
        line.newCurrentLimits1()
                .setPermanentLimit(i / 1.25)
                .beginTemporaryLimit().setName("TL600").setAcceptableDuration(600).setValue(i * 0.9).endTemporaryLimit()
                .beginTemporaryLimit().setName("TL60").setAcceptableDuration(60).setValue(i * 1.1).endTemporaryLimit()
                .add();

        FlowViolation violation = compute(network).getWorstFlowViolation("NHV1_NHV2_1");

        assertEquals(ThreeSides.ONE, violation.violation().getSide());
        assertEquals(LimitViolationType.CURRENT, violation.violation().getLimitType());
        // the limit above the value gives the acceptable duration, the one below is the crossed one
        assertEquals(60, violation.violation().getAcceptableDuration());
        assertEquals("TL600", violation.violation().getLimitName());
        assertEquals(i * 0.9, violation.violation().getLimit(), 1e-9);
        assertEquals(i / 1.25, violation.permanentLimit(), 1e-9);
        assertEquals(125, violation.percent(), 1e-9);
    }

    @Test
    void lowDetectionKindViolationPercentageIsRelativeToTheCrossedLimit() {
        Network network = EurostagTutorialExample1Factory.createWithLFResults();
        Line line = network.getLine("NHV1_NHV2_1");
        double i = line.getTerminal1().getI();
        line.newCurrentLimits1()
                .setDetectionKind(DetectionKind.LOW)
                .beginTemporaryLimit().setName("TL600").setAcceptableDuration(600).setValue(i * 0.8).endTemporaryLimit()
                .beginTemporaryLimit().setName("TL60").setAcceptableDuration(60).setValue(i * 1.2).endTemporaryLimit()
                .add();

        FlowViolation violation = compute(network).getWorstFlowViolation("NHV1_NHV2_1");

        assertTrue(Double.isNaN(violation.permanentLimit()));
        assertEquals(600, violation.violation().getAcceptableDuration());
        assertEquals(125, violation.percent(), 1e-9);
    }

    @Test
    void loadingIsKnownForEquipmentsWithinTheirLimitsToo() {
        Network network = EurostagTutorialExample1Factory.createWithLFResults();
        Line line = network.getLine("NHV1_NHV2_1");
        line.newCurrentLimits1().setPermanentLimit(line.getTerminal1().getI() * 2).add();
        line.newCurrentLimits2().setPermanentLimit(line.getTerminal2().getI() * 4).add();

        NetworkViolations violations = compute(network);

        Loading loading = violations.getLoading("NHV1_NHV2_1");
        assertFalse(loading.isViolated());
        assertEquals(50, loading.percent(), 1e-9);
        assertEquals(25, violations.getLoading("NHV1_NHV2_1", ThreeSides.TWO).percent(), 1e-9);
        // no limits
        assertNull(violations.getLoading("NHV1_NHV2_2"));
    }

    @Test
    void violatedLoadingsSortBeforeTheOthers() {
        LimitViolation violation = LimitViolation.builder().subject("L").type(LimitViolationType.CURRENT).limit(10).value(11)
                .side(ThreeSides.ONE).duration(Integer.MAX_VALUE).build();
        Loading violated = Loading.of(new FlowViolation(violation, 10));
        Loading high = new Loading(99, null);
        Loading low = new Loading(10, null);
        assertEquals(List.of(violated, high, low), List.of(low, violated, high).stream().sorted().toList());
    }

    @Test
    void mostSevereViolationIsTheShortestAcceptableDurationThenTheHighestPercentage() {
        Network network = EurostagTutorialExample1Factory.createWithLFResults();
        Line line = network.getLine("NHV1_NHV2_1");
        double i1 = line.getTerminal1().getI();
        double i2 = line.getTerminal2().getI();
        // side 1: 60 s temporary limit violation, 120 %
        line.newCurrentLimits1()
                .setPermanentLimit(i1 / 1.2)
                .beginTemporaryLimit().setName("TL600").setAcceptableDuration(600).setValue(i1 * 0.9).endTemporaryLimit()
                .beginTemporaryLimit().setName("TL60").setAcceptableDuration(60).setValue(i1 * 1.1).endTemporaryLimit()
                .add();
        // side 2: 600 s temporary limit violation, 130 %
        line.newCurrentLimits2()
                .setPermanentLimit(i2 / 1.3)
                .beginTemporaryLimit().setName("TL600").setAcceptableDuration(600).setValue(i2 * 1.1).endTemporaryLimit()
                .add();

        NetworkViolations violations = compute(network);

        List<FlowViolation> lineViolations = violations.getFlowViolations("NHV1_NHV2_1");
        assertEquals(List.of(ThreeSides.ONE, ThreeSides.TWO), lineViolations.stream().map(v -> v.violation().getSide()).toList());
        assertEquals(60, lineViolations.getFirst().violation().getAcceptableDuration());
        assertEquals(lineViolations.getFirst(), violations.getWorstFlowViolation("NHV1_NHV2_1"));
    }

    @Test
    void permanentLimitViolationIsMoreSevereThanAnyTemporaryLimitViolation() {
        Network network = EurostagTutorialExample1Factory.createWithLFResults();
        Line line = network.getLine("NHV1_NHV2_1");
        double i1 = line.getTerminal1().getI();
        double i2 = line.getTerminal2().getI();
        // side 1: permanent limit only, which can't be exceeded at all: 120 %
        line.newCurrentLimits1().setPermanentLimit(i1 / 1.2).add();
        // side 2: 60 s temporary limit violation, 102 %
        line.newCurrentLimits2()
                .setPermanentLimit(i2 / 1.02)
                .beginTemporaryLimit().setName("TL600").setAcceptableDuration(600).setValue(i2 * 0.99).endTemporaryLimit()
                .beginTemporaryLimit().setName("TL60").setAcceptableDuration(60).setValue(i2 * 1.1).endTemporaryLimit()
                .add();

        NetworkViolations violations = compute(network);

        List<FlowViolation> lineViolations = violations.getFlowViolations("NHV1_NHV2_1");
        assertEquals(List.of(ThreeSides.ONE, ThreeSides.TWO), lineViolations.stream().map(v -> v.violation().getSide()).toList());
        FlowViolation worst = violations.getWorstFlowViolation("NHV1_NHV2_1");
        assertEquals(Integer.MAX_VALUE, worst.violation().getAcceptableDuration());
        assertEquals(120, worst.percent(), 1e-9);
        assertEquals(60, lineViolations.get(1).violation().getAcceptableDuration());
        assertEquals(102, lineViolations.get(1).percent(), 1e-9);
    }

    @Test
    void severityOrdersPermanentLimitViolationsByPercentage() {
        Network network = EurostagTutorialExample1Factory.createWithLFResults();
        Line line1 = network.getLine("NHV1_NHV2_1");
        Line line2 = network.getLine("NHV1_NHV2_2");
        line1.newCurrentLimits1().setPermanentLimit(line1.getTerminal1().getI() / 1.1).add();
        line2.newCurrentLimits1().setPermanentLimit(line2.getTerminal1().getI() / 1.5).add();

        NetworkViolations violations = compute(network);

        List<FlowViolation> sorted = List.of(violations.getWorstFlowViolation("NHV1_NHV2_1"), violations.getWorstFlowViolation("NHV1_NHV2_2"))
                .stream().sorted().toList();
        assertEquals("NHV1_NHV2_2", sorted.getFirst().violation().getSubjectId());
    }

    @Test
    void activeAndApparentPowerLimitsAreChecked() {
        Network network = EurostagTutorialExample1Factory.createWithLFResults();
        Line line = network.getLine("NHV1_NHV2_1");
        double p = Math.abs(line.getTerminal1().getP());
        double s = Math.hypot(line.getTerminal1().getP(), line.getTerminal1().getQ());
        line.getOrCreateSelectedOperationalLimitsGroup1().newActivePowerLimits().setPermanentLimit(p / 2).add();
        line.getOrCreateSelectedOperationalLimitsGroup1().newApparentPowerLimits().setPermanentLimit(s / 1.5).add();

        List<FlowViolation> violations = compute(network).getFlowViolations("NHV1_NHV2_1");

        FlowViolation active = violations.stream().filter(v -> v.violation().getLimitType() == LimitViolationType.ACTIVE_POWER).findFirst().orElseThrow();
        assertEquals(200, active.percent(), 1e-9);
        FlowViolation apparent = violations.stream().filter(v -> v.violation().getLimitType() == LimitViolationType.APPARENT_POWER).findFirst().orElseThrow();
        assertEquals(150, apparent.percent(), 1e-9);
    }

    @Test
    void threeWindingsTransformerLegsAreChecked() {
        Network network = ThreeWindingsTransformerNetworkFactory.create();
        ThreeWindingsTransformer transformer = network.getThreeWindingsTransformerStream().findFirst().orElseThrow();
        ThreeWindingsTransformer.Leg leg2 = transformer.getLeg2();
        leg2.getTerminal().getBusView().getBus().setV(100);
        leg2.getTerminal().setP(1000).setQ(1000);
        leg2.newCurrentLimits().setPermanentLimit(leg2.getTerminal().getI() / 2).add();

        FlowViolation violation = compute(network).getWorstFlowViolation(transformer.getId());

        assertEquals(ThreeSides.TWO, violation.violation().getSide());
        assertEquals(200, violation.percent(), 1e-9);
    }

    @Test
    void unpairedBoundaryLinesAreChecked() {
        Network network = BoundaryLineNetworkFactory.create();
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        boundaryLine.getTerminal().getBusView().getBus().setV(100);
        boundaryLine.getTerminal().setP(1000).setQ(1000);
        boundaryLine.newCurrentLimits().setPermanentLimit(boundaryLine.getTerminal().getI() / 2).add();

        FlowViolation violation = compute(network).getWorstFlowViolation(boundaryLine.getId());

        assertEquals(ThreeSides.ONE, violation.violation().getSide());
        assertEquals(200, violation.percent(), 1e-9);
    }

    @Test
    void pairedBoundaryLinesAreCheckedAsTheirTieLine() {
        Network network = EurostagTutorialExample1Factory.createWithTieLine();
        BoundaryLine boundaryLine = network.getBoundaryLineStream().filter(BoundaryLine::isPaired).findFirst().orElseThrow();
        boundaryLine.getTerminal().getBusView().getBus().setV(100);
        boundaryLine.getTerminal().setP(1000).setQ(1000);
        boundaryLine.newCurrentLimits().setPermanentLimit(0.001).add();

        NetworkViolations violations = compute(network);

        assertNull(violations.getWorstFlowViolation(boundaryLine.getId()));
        assertTrue(violations.getFlowViolations().containsKey(boundaryLine.getTieLine().orElseThrow().getId()));
    }

    @Test
    void highAndLowVoltagesAreCheckedPerBusOfTheBusView() {
        Network network = EurostagTutorialExample1Factory.createWithLFResults();
        Bus high = network.getVoltageLevel("VLHV1").getBusView().getBuses().iterator().next();
        Bus low = network.getVoltageLevel("VLHV2").getBusView().getBuses().iterator().next();
        VoltageLevel highLevel = high.getVoltageLevel();
        highLevel.setHighVoltageLimit(high.getV() - 1).setLowVoltageLimit(high.getV() - 10);
        VoltageLevel lowLevel = low.getVoltageLevel();
        lowLevel.setHighVoltageLimit(low.getV() + 10).setLowVoltageLimit(low.getV() + 2);

        NetworkViolations violations = compute(network);

        VoltageViolation highViolation = violations.getVoltageViolation(high.getId());
        assertTrue(highViolation.isHigh());
        assertEquals("VLHV1", highViolation.violation().getSubjectId());
        assertEquals(high.getV() / highLevel.getNominalV() * 100, highViolation.percentOfNominal(), 1e-9);
        VoltageViolation lowViolation = violations.getVoltageViolation(low.getId());
        assertFalse(lowViolation.isHigh());
        assertEquals(LimitViolationType.LOW_VOLTAGE, lowViolation.violation().getLimitType());
        // 2 kV beyond its limit vs 1 kV
        assertEquals(List.of(lowViolation, highViolation), List.of(highViolation, lowViolation).stream().sorted().toList());
        assertNull(violations.getVoltageViolation(null));
    }

    @Test
    void missingValuesAreNotViolations() {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        network.getBusView().getBuses().forEach(bus -> bus.setV(Double.NaN));
        network.getLineStream().forEach(line -> {
            line.getTerminal1().setP(Double.NaN).setQ(Double.NaN);
            line.getTerminal2().setP(Double.NaN).setQ(Double.NaN);
        });
        network.getVoltageLevelStream().forEach(vl -> vl.setLowVoltageLimit(1).setHighVoltageLimit(2));

        NetworkViolations violations = compute(network);

        assertTrue(violations.getVoltageViolations().isEmpty());
        assertTrue(network.getLineStream().allMatch(line -> violations.getFlowViolations(line.getId()).isEmpty()));
    }
}
