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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // whether Open Load Flow computes the factor (possibly NaN), throws, or gives NaN
    private enum Outcome { COMPUTED, FAILED, NAN }

    private record Result(Outcome outcome, String failure) { }

    private record Case(Network network, boolean dc, String what, boolean eligible, boolean refusedWhenSkipped, SensitivityFactor factor) { }

    // each case's result as if run alone, in a few runs: Open Load Flow fails a whole run on its first refused factor,
    // so a failing run is split in halves until each refused factor runs alone
    private static void run(List<Case> cases, Map<Case, Result> results) {
        Network network = cases.getFirst().network();
        SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters();
        parameters.getLoadFlowParameters().setDc(cases.getFirst().dc());
        SensitivityAnalysisRunParameters runParameters = SensitivityAnalysisRunParameters.getDefault()
                .setParameters(parameters)
                .setVariableSets(List.of(new SensitivityVariableSet("GLSK", List.of(new WeightedSensitivityVariable(firstGenerator(network), 1)))));
        Object outcome = SensitivityAnalysis.runAsync(network, network.getVariantManager().getWorkingVariantId(),
                        cases.stream().map(Case::factor).toList(), runParameters)
                .handle((result, e) -> e != null ? e : result)
                .join();
        if (outcome instanceof Throwable failure) {
            if (cases.size() > 1) {
                int middle = cases.size() / 2;
                run(cases.subList(0, middle), results);
                run(cases.subList(middle, cases.size()), results);
                return;
            }
            Throwable cause = failure;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            results.put(cases.getFirst(), new Result(Outcome.FAILED, cause.getMessage()));
            return;
        }
        Set<Integer> nan = ((SensitivityAnalysisResult) outcome).getValues().stream()
                .filter(value -> Double.isNaN(value.getValue()))
                .map(SensitivityValue::getFactorIndex)
                .collect(Collectors.toSet());
        for (int i = 0; i < cases.size(); i++) {
            results.put(cases.get(i), new Result(nan.contains(i) ? Outcome.NAN : Outcome.COMPUTED, null));
        }
    }

    // Open Load Flow merely skips these unknown elements: refused all the same, surely a mistake
    private static final Set<SensitivityVariableType> REFUSED_WHEN_SKIPPED = EnumSet.of(SensitivityVariableType.BRANCH_RESISTANCE,
            SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE, SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE);

    @Test
    void eligibleElementsAreTheOnesOpenLoadFlowAccepts() {
        List<Case> cases = new ArrayList<>();
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
                        cases.add(new Case(network, dc, network.getId() + " " + type + " " + id, SensitivityElements.isEligible(network, type, id), false,
                                new SensitivityFactor(type, id, variableType, generator, false, ContingencyContext.none())));
                    }
                }
                for (SensitivityVariableType type : SensitivityVariableType.values()) {
                    if (OlfSensitivityFactorSupport.unsupportedReasons(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, type, false, dc).isEmpty()) {
                        cases.add(new Case(network, dc, network.getId() + " " + type + " " + id, SensitivityElements.isEligible(network, type, id),
                                REFUSED_WHEN_SKIPPED.contains(type), new SensitivityFactor(SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
                                flowFunctionId(network), type, id, false, ContingencyContext.none())));
                    }
                }
            }
        }
        // the eligible factors are expected computed: run together per network; the others are expected refused: run alone
        Map<Case, Result> results = new HashMap<>();
        cases.stream().filter(Case::eligible).collect(Collectors.groupingBy(Case::network, LinkedHashMap::new, Collectors.toList()))
                .values().forEach(eligible -> run(eligible, results));
        cases.stream().filter(c -> !c.eligible()).forEach(c -> run(List.of(c), results));

        List<String> mismatches = new ArrayList<>();
        for (Case c : cases) {
            Result actual = results.get(c);
            if (c.eligible() ? actual.outcome() == Outcome.FAILED : actual.outcome() == Outcome.COMPUTED && !c.refusedWhenSkipped()) {
                mismatches.add(c.what() + ": eligible " + c.eligible() + ", Open Load Flow " + actual.outcome()
                        + (actual.outcome() == Outcome.FAILED ? " " + actual.failure() : ""));
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
