/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.iidm.network.test.ThreeWindingsTransformerNetworkFactory;
import com.powsybl.sensitivity.SensitivityAnalysis;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisRunParameters;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs Open Load Flow's sensitivity analysis on every kind of factor, in AC and DC, with and without a variable set,
 * and compares its refusals with {@link OlfSensitivityFactorSupport}'s.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class OlfSensitivityFactorSupportTest {

    private static final String VARIABLE_SET = "GLSK";

    // an unknown id when the network has none: Open Load Flow then fails on the id, after checking the kind of factor
    private static <T extends Identifiable<T>> String first(Stream<T> identifiables) {
        return identifiables.findFirst().map(Identifiable::getId).orElse("MISSING");
    }

    private static String functionId(Network network, SensitivityFunctionType functionType) {
        return switch (functionType) {
            case BRANCH_ACTIVE_POWER_1, BRANCH_ACTIVE_POWER_2, BRANCH_CURRENT_1, BRANCH_CURRENT_2,
                 BRANCH_REACTIVE_POWER_1, BRANCH_REACTIVE_POWER_2 -> first(network.getLineStream());
            case BRANCH_ACTIVE_POWER_3, BRANCH_CURRENT_3, BRANCH_REACTIVE_POWER_3 -> first(network.getThreeWindingsTransformerStream());
            case BUS_VOLTAGE -> network.getGeneratorStream().findFirst().orElseThrow().getTerminal().getBusView().getBus().getId();
            case BUS_REACTIVE_POWER -> first(network.getGeneratorStream());
        };
    }

    private static String variableId(Network network, SensitivityVariableType variableType) {
        return switch (variableType) {
            case INJECTION_ACTIVE_POWER, INJECTION_REACTIVE_POWER, BUS_TARGET_VOLTAGE -> first(network.getGeneratorStream());
            case SHUNT_COMPENSATOR_SUSCEPTANCE -> first(network.getShuntCompensatorStream());
            case TRANSFORMER_PHASE -> first(network.getTwoWindingsTransformerStream().filter(t -> t.getPhaseTapChanger() != null));
            case TRANSFORMER_PHASE_1, TRANSFORMER_PHASE_2, TRANSFORMER_PHASE_3 -> first(network.getThreeWindingsTransformerStream());
            case HVDC_LINE_ACTIVE_POWER -> first(network.getHvdcLineStream());
            case BRANCH_RESISTANCE, BRANCH_REACTANCE, BRANCH_ADMITTANCE -> first(network.getLineStream());
            case SVC_PILOT_POINT_TARGET_VOLTAGE -> first(network.getStaticVarCompensatorStream());
        };
    }

    // null when Open Load Flow computes the factor, else the message it fails with
    private static String olfFailure(SensitivityFunctionType functionType, SensitivityVariableType variableType, boolean variableSet, boolean dc) {
        boolean side3 = functionType.getSide().orElse(0) == 3;
        Network network = side3 ? ThreeWindingsTransformerNetworkFactory.create() : FourSubstationsNodeBreakerFactory.create();
        SensitivityFactor factor = new SensitivityFactor(functionType, functionId(network, functionType), variableType,
                variableSet ? VARIABLE_SET : variableId(network, variableType), variableSet, ContingencyContext.none());
        SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters();
        parameters.getLoadFlowParameters().setDc(dc);
        SensitivityAnalysisRunParameters runParameters = SensitivityAnalysisRunParameters.getDefault()
                .setParameters(parameters)
                .setVariableSets(List.of(new SensitivityVariableSet(VARIABLE_SET,
                        List.of(new WeightedSensitivityVariable(first(network.getGeneratorStream()), 1)))));
        Throwable failure = SensitivityAnalysis.runAsync(network, network.getVariantManager().getWorkingVariantId(), List.of(factor), runParameters)
                .handle((_, e) -> e)
                .join();
        if (failure == null) {
            return null;
        }
        while (failure.getCause() != null) {
            failure = failure.getCause();
        }
        return String.valueOf(failure.getMessage());
    }

    private static boolean isSupportFailure(String message) {
        return message.contains("not supported") || message.contains("are yet supported in DC");
    }

    @Test
    void reasonsMatchOpenLoadFlowRefusals() {
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        for (SensitivityFunctionType functionType : SensitivityFunctionType.values()) {
            for (SensitivityVariableType variableType : SensitivityVariableType.values()) {
                for (boolean variableSet : List.of(false, true)) {
                    for (boolean dc : List.of(false, true)) {
                        boolean expectedUnsupported = !OlfSensitivityFactorSupport.unsupportedReasons(functionType, variableType, variableSet, dc).isEmpty();
                        String failure = olfFailure(functionType, variableType, variableSet, dc);
                        boolean unsupported = failure != null && isSupportFailure(failure);
                        // Open Load Flow checks the DC kinds once it has found the factor's equipment, which the test
                        // networks don't all have (e.g. a three-winding phase shifter)
                        boolean dcOnly = dc && OlfSensitivityFactorSupport.unsupportedReasons(functionType, variableType, variableSet, false).isEmpty();
                        if (expectedUnsupported && dcOnly && failure != null && !unsupported) {
                            continue;
                        }
                        checked++;
                        if (expectedUnsupported != unsupported) {
                            mismatches.add(functionType + " x " + variableType + (variableSet ? " (set)" : "") + (dc ? " DC" : " AC")
                                    + ": expected " + (expectedUnsupported ? "unsupported" : "supported") + ", got " + failure);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), mismatches, String.join("\n", mismatches));
        // so that the cases skipped above don't silently grow: all but 14 of 11 functions x 13 variables x 2 x 2
        assertEquals(558, checked);
    }

    @Test
    void matrixReasonsTellEveryRule() {
        SensitivityFactorMatrix voltageToActivePower = new SensitivityFactorMatrix("m", SensitivityFunctionType.BUS_VOLTAGE, List.of("B"),
                SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("G"), false, ContingencyContext.all());
        assertEquals(1, OlfSensitivityFactorSupport.unsupportedReasons(voltageToActivePower, false).size());
        assertEquals(2, OlfSensitivityFactorSupport.unsupportedReasons(voltageToActivePower, true).size());

        SensitivityFactorMatrix flowToActivePower = new SensitivityFactorMatrix("m", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("L"),
                SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of(VARIABLE_SET), true, ContingencyContext.all());
        assertTrue(OlfSensitivityFactorSupport.unsupportedReasons(flowToActivePower, false).isEmpty());
        assertTrue(OlfSensitivityFactorSupport.unsupportedReasons(flowToActivePower, true).isEmpty());
        assertFalse(OlfSensitivityFactorSupport.unsupportedReasons(new SensitivityFactorMatrix("m", SensitivityFunctionType.BRANCH_CURRENT_1,
                List.of("L"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("G"), false, ContingencyContext.all()), true).isEmpty());
    }
}
