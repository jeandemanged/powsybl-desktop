/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityState;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.SensitivityVariableType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SensitivityResultRowsTest {

    private static SensitivityFactorMatrix matrix(String name, List<String> functionIds, ContingencyContext context) {
        return new SensitivityFactorMatrix(name, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionIds,
                SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("G"), false, context);
    }

    private static final List<SensitivityFactorMatrix> MATRICES = List.of(
            matrix("all", List.of("L1", "L2"), ContingencyContext.all()),
            matrix("none", List.of("L1"), ContingencyContext.none()),
            matrix("specific", List.of("L1"), ContingencyContext.specificContingency("c1")),
            matrix("only", List.of("L1"), ContingencyContext.onlyContingencies()));

    private static SensitivityAnalysisResult result() {
        List<SensitivityFactor> factors = MATRICES.stream().flatMap(m -> m.factors().stream()).toList();
        List<SensitivityValue> values = List.of(
                new SensitivityValue(0, -1, -1, 0.5, 100),
                // factor 1 below the thresholds
                new SensitivityValue(2, -1, -1, Double.NaN, Double.NaN),
                new SensitivityValue(0, 0, -1, 0.6, 110),
                new SensitivityValue(3, 0, -1, 0.1, 50));
        return new SensitivityAnalysisResult(factors,
                List.of(new SensitivityAnalysisResult.SensitivityStateStatus(SensitivityState.postContingency("c2"), SensitivityAnalysisResult.Status.FAILURE)),
                List.of("c1", "c2"), List.of(), values);
    }

    @Test
    void statesArePreContingencyThenEachContingencyWithItsStatus() {
        assertEquals(List.of(
                        new SensitivityResultRows.State(SensitivityState.PRE_CONTINGENCY, SensitivityAnalysisResult.Status.SUCCESS),
                        new SensitivityResultRows.State(SensitivityState.postContingency("c1"), SensitivityAnalysisResult.Status.SUCCESS),
                        new SensitivityResultRows.State(SensitivityState.postContingency("c2"), SensitivityAnalysisResult.Status.FAILURE)),
                SensitivityResultRows.states(result()));
    }

    @Test
    void rowsAreTheFactorsOfTheStateWithTheirMatrixAndValue() {
        List<SensitivityResultRows.Row> preContingency = SensitivityResultRows.rows(result(), MATRICES, SensitivityState.PRE_CONTINGENCY);
        assertEquals(List.of(0, 0, 1), preContingency.stream().map(SensitivityResultRows.Row::matrixIndex).toList());
        assertEquals(0.5, preContingency.getFirst().value().getValue());
        assertEquals(100, preContingency.getFirst().value().getFunctionReference());
        assertNull(preContingency.get(1).value());
        assertTrue(Double.isNaN(preContingency.get(2).value().getValue()));

        List<SensitivityResultRows.Row> c1 = SensitivityResultRows.rows(result(), MATRICES, SensitivityState.postContingency("c1"));
        assertEquals(List.of(0, 0, 2, 3), c1.stream().map(SensitivityResultRows.Row::matrixIndex).toList());
        assertEquals(0.6, c1.getFirst().value().getValue());
        assertEquals(0.1, c1.get(2).value().getValue());

        assertEquals(List.of(0, 0, 3), SensitivityResultRows.rows(result(), MATRICES, SensitivityState.postContingency("c2")).stream()
                .map(SensitivityResultRows.Row::matrixIndex).toList());
    }
}
