/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Which kinds of factors Open Load Flow's sensitivity analysis computes. A single factor of another kind makes the
 * whole analysis fail, so the app tells the user beforehand.
 * <p>
 * Mirrors {@code AbstractSensitivityAnalysis.createLfSensitivityFactor} and {@code DcSensitivityAnalysis.analyse}
 * (Open Load Flow 2.4.0): {@code OlfSensitivityFactorSupportTest} runs Open Load Flow on every kind of factor, so that
 * an upgrade changing them fails the build. Checks on the equipment itself (missing, not a phase shifter...) are not
 * made here.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class OlfSensitivityFactorSupport {

    private static final Set<SensitivityFunctionType> FLOW_FUNCTIONS = EnumSet.of(
            SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, SensitivityFunctionType.BRANCH_ACTIVE_POWER_3,
            SensitivityFunctionType.BRANCH_CURRENT_1, SensitivityFunctionType.BRANCH_CURRENT_2, SensitivityFunctionType.BRANCH_CURRENT_3);

    private static final Set<SensitivityFunctionType> DC_FUNCTIONS = EnumSet.of(
            SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, SensitivityFunctionType.BRANCH_ACTIVE_POWER_2, SensitivityFunctionType.BRANCH_ACTIVE_POWER_3);

    // the variables of the voltage and reactive power functions; the flow functions take every variable
    private static final Set<SensitivityVariableType> VOLTAGE_VARIABLES = EnumSet.of(
            SensitivityVariableType.BUS_TARGET_VOLTAGE, SensitivityVariableType.INJECTION_REACTIVE_POWER,
            SensitivityVariableType.SHUNT_COMPENSATOR_SUSCEPTANCE, SensitivityVariableType.BRANCH_RESISTANCE,
            SensitivityVariableType.BRANCH_REACTANCE, SensitivityVariableType.BRANCH_ADMITTANCE,
            SensitivityVariableType.SVC_PILOT_POINT_TARGET_VOLTAGE);

    private static final Set<SensitivityVariableType> DC_VARIABLES = EnumSet.of(
            SensitivityVariableType.INJECTION_ACTIVE_POWER, SensitivityVariableType.TRANSFORMER_PHASE,
            SensitivityVariableType.TRANSFORMER_PHASE_1, SensitivityVariableType.TRANSFORMER_PHASE_2,
            SensitivityVariableType.TRANSFORMER_PHASE_3, SensitivityVariableType.HVDC_LINE_ACTIVE_POWER);

    private OlfSensitivityFactorSupport() {
    }

    /**
     * Why Open Load Flow would fail on {@code matrix}'s factors, in AC or {@code dc}: empty when it computes them.
     */
    public static List<String> unsupportedReasons(SensitivityFactorMatrix matrix, boolean dc) {
        return unsupportedReasons(matrix.functionType(), matrix.variableType(), matrix.variableSet(), dc);
    }

    static List<String> unsupportedReasons(SensitivityFunctionType functionType, SensitivityVariableType variableType, boolean variableSet,
                                           boolean dc) {
        List<String> reasons = new ArrayList<>();
        if (variableSet) {
            if (!FLOW_FUNCTIONS.contains(functionType)) {
                reasons.add(Messages.get("sensitivity.olf.variableSetFunction"));
            } else if (variableType != SensitivityVariableType.INJECTION_ACTIVE_POWER) {
                reasons.add(Messages.get("sensitivity.olf.variableSetVariable"));
            }
        } else if (!FLOW_FUNCTIONS.contains(functionType) && !VOLTAGE_VARIABLES.contains(variableType)) {
            reasons.add(Messages.get("sensitivity.olf.variable"));
        }
        if (dc) {
            if (!DC_FUNCTIONS.contains(functionType)) {
                reasons.add(Messages.get("sensitivity.olf.dcFunction"));
            }
            if (!DC_VARIABLES.contains(variableType)) {
                reasons.add(Messages.get("sensitivity.olf.dcVariable"));
            }
        }
        return reasons;
    }
}
