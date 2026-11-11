/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.sensitivity.SensitivityAnalysis;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityAnalysisRunParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SensitivityElementsTest {

    // an id of every kind of element of the network, and unknown ones
    private static List<String> sampleIds(Network network) {
        List<String> ids = new ArrayList<>(List.of("MISSING"));
        network.getIdentifiables().stream()
                .filter(identifiable -> identifiable.getType() != null)
                .collect(Collectors.groupingBy(Identifiable::getType))
                .values().forEach(identifiables -> ids.add(identifiables.getFirst().getId()));
        Bus generatorBus = network.getGeneratorStream().findFirst().orElseThrow().getTerminal().getBusBreakerView().getBus();
        ids.add(generatorBus.getId());
        ids.add(network.getGeneratorStream().findFirst().orElseThrow().getTerminal().getBusView().getBus().getId());
        return ids;
    }

    private static String firstGenerator(Network network) {
        return network.getGeneratorStream().findFirst().orElseThrow().getId();
    }

    private static String flowFunctionId(Network network) {
        return network.getBranchStream().findFirst().map(Identifiable::getId)
                .orElseGet(() -> network.getThreeWindingsTransformerStream().findFirst().orElseThrow().getId());
    }

    private static String lastFailure;

    // whether Open Load Flow computes the factor (possibly NaN), throws, or gives NaN
    private enum Outcome { COMPUTED, FAILED, NAN }

    private static Outcome run(Network network, boolean dc, SensitivityFactor factor) {
        SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters();
        parameters.getLoadFlowParameters().setDc(dc);
        SensitivityAnalysisRunParameters runParameters = SensitivityAnalysisRunParameters.getDefault()
                .setParameters(parameters)
                .setVariableSets(List.of(new SensitivityVariableSet("GLSK", List.of(new WeightedSensitivityVariable(firstGenerator(network), 1)))));
        Object outcome = SensitivityAnalysis.runAsync(network, network.getVariantManager().getWorkingVariantId(), List.of(factor), runParameters)
                .handle((result, e) -> e != null ? e : result)
                .join();
        if (outcome instanceof Throwable failure) {
            Throwable cause = failure;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            lastFailure = cause.getMessage();
            return Outcome.FAILED;
        }
        List<SensitivityValue> values = ((SensitivityAnalysisResult) outcome).getValues();
        return !values.isEmpty() && Double.isNaN(values.getFirst().getValue()) ? Outcome.NAN : Outcome.COMPUTED;
    }

    // Open Load Flow merely skips these unknown elements: refused all the same, surely a mistake
    private static final Set<SensitivityVariableType> REFUSED_WHEN_SKIPPED = EnumSet.of(SensitivityVariableType.BRANCH_RESISTANCE,
            SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE, SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE);

    private static void check(List<String> mismatches, String what, boolean eligible, boolean refusedWhenSkipped, Supplier<Outcome> outcome) {
        Outcome actual = outcome.get();
        if (eligible ? actual == Outcome.FAILED : actual == Outcome.COMPUTED && !refusedWhenSkipped) {
            mismatches.add(what + ": eligible " + eligible + ", Open Load Flow " + actual + (actual == Outcome.FAILED ? " " + lastFailure : ""));
        }
    }

    @Test
    void eligibleElementsAreTheOnesOpenLoadFlowAccepts() {
        List<String> mismatches = new ArrayList<>();
        // the three windings transformer network doesn't converge in AC: run in DC, for the kinds of factors DC computes
        for (Network network : List.of(FourSubstationsNodeBreakerFactory.create(), ThreeWindingsTransformerNetworkFactory.create(),
                EurostagTutorialExample1Factory.createWithTieLine())) {
            boolean dc = network.getThreeWindingsTransformerCount() > 0;
            String generator = firstGenerator(network);
            for (String id : sampleIds(network)) {
                for (SensitivityFunctionType type : SensitivityFunctionType.values()) {
                    boolean flow = OlfSensitivityFactorSupport.unsupportedReasons(type, SensitivityVariableType.INJECTION_ACTIVE_POWER, false, dc).isEmpty();
                    if (flow || !dc) {
                        SensitivityVariableType variableType = flow ? SensitivityVariableType.INJECTION_ACTIVE_POWER : SensitivityVariableType.BUS_TARGET_VOLTAGE;
                        check(mismatches, network.getId() + " " + type + " " + id, SensitivityElements.isEligible(network, type, id), false,
                                () -> run(network, dc, new SensitivityFactor(type, id, variableType, generator, false, ContingencyContext.none())));
                    }
                }
                for (SensitivityVariableType type : SensitivityVariableType.values()) {
                    if (OlfSensitivityFactorSupport.unsupportedReasons(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, type, false, dc).isEmpty()) {
                        check(mismatches, network.getId() + " " + type + " " + id, SensitivityElements.isEligible(network, type, id), REFUSED_WHEN_SKIPPED.contains(type),
                                () -> run(network, dc, new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, flowFunctionId(network), type, id,
                                        false, ContingencyContext.none())));
                    }
                }
            }
        }
        assertEquals(List.of(), mismatches, String.join("\n", mismatches));
    }

    @Test
    void candidatesAreEligible() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        for (SensitivityFunctionType type : SensitivityFunctionType.values()) {
            SensitivityElements.candidates(network, type).forEach(candidate -> assertTrue(SensitivityElements.isEligible(network, type, candidate.id()),
                    type + " " + candidate.id()));
        }
        for (SensitivityVariableType type : SensitivityVariableType.values()) {
            SensitivityElements.candidates(network, type).forEach(candidate -> assertTrue(SensitivityElements.isEligible(network, type, candidate.id()),
                    type + " " + candidate.id()));
        }
        assertEquals(List.of("TWT"), SensitivityElements.candidates(network, SensitivityVariableType.TRANSFORMER_PHASE).stream()
                .map(SensitivityElements.Candidate::id).toList());
        assertFalse(SensitivityElements.candidates(network, SensitivityVariableType.INJECTION_ACTIVE_POWER).isEmpty());
    }

    @Test
    void problemsListAtMostFiveElementsOfAKind() {
        Network network = EurostagTutorialExample1Factory.create();
        SensitivityFactorMatrix matrix = new SensitivityFactorMatrix("m", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                List.of("A", "B", "C", "D", "E", "F", "G", "NHV1_NHV2_1"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("GEN"), false,
                ContingencyContext.none());
        List<String> problems = SensitivityElements.problems(network, matrix, List.of(), List.of(), false);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("A, B, C, D, E") && !problems.getFirst().contains("F"), problems.getFirst());
    }
}
