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
import com.powsybl.sensitivity.SensitivityState;
import com.powsybl.sensitivity.SensitivityValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A sensitivity analysis result as shown, one state at a time: Open Load Flow doesn't report the values below the
 * sensitivity analysis parameters' thresholds, and gives NaN for a factor it couldn't compute (e.g. disconnected
 * equipment).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SensitivityResultRows {

    /**
     * The pre-contingency state or a post-contingency one, and its status: only post-contingency states have one in
     * the result, a failed base case failing the whole analysis.
     */
    public record State(SensitivityState state, SensitivityAnalysisResult.Status status) {
        public boolean preContingency() {
            return state.contingencyId() == null;
        }
    }

    /**
     * A factor of the shown state, the index of its matrix and its value: null when below the thresholds.
     */
    public record Row(int matrixIndex, SensitivityFactor factor, SensitivityValue value) {
    }

    private SensitivityResultRows() {
    }

    /**
     * The pre-contingency state then the post-contingency ones, in the result's order.
     */
    public static List<State> states(SensitivityAnalysisResult result) {
        Map<SensitivityState, SensitivityAnalysisResult.Status> statuses = new HashMap<>();
        result.getStateStatuses().forEach(status -> statuses.put(status.getState(), status.getStatus()));
        List<State> states = new ArrayList<>();
        states.add(new State(SensitivityState.PRE_CONTINGENCY, SensitivityAnalysisResult.Status.SUCCESS));
        for (String contingencyId : result.getContingencyIds()) {
            SensitivityState state = SensitivityState.postContingency(contingencyId);
            states.add(new State(state, statuses.getOrDefault(state, SensitivityAnalysisResult.Status.SUCCESS)));
        }
        return states;
    }

    /**
     * The factors of {@code state}, as their contingency context says, in the order of {@code matrices}: the ones the
     * result was computed from, which it doesn't hold.
     */
    public static List<Row> rows(SensitivityAnalysisResult result, List<SensitivityFactorMatrix> matrices, SensitivityState state) {
        Map<Integer, SensitivityValue> values = new HashMap<>();
        result.getValues(state).forEach(value -> values.put(value.getFactorIndex(), value));
        List<SensitivityFactor> factors = result.getFactors();
        List<Row> rows = new ArrayList<>();
        int index = 0;
        for (int matrixIndex = 0; matrixIndex < matrices.size(); matrixIndex++) {
            int end = index + matrices.get(matrixIndex).factorCount();
            for (; index < end; index++) {
                SensitivityFactor factor = factors.get(index);
                if (appliesTo(factor.getContingencyContext(), state)) {
                    rows.add(new Row(matrixIndex, factor, values.get(index)));
                }
            }
        }
        return rows;
    }

    static boolean appliesTo(ContingencyContext context, SensitivityState state) {
        boolean preContingency = state.contingencyId() == null;
        return switch (context.getContextType()) {
            case ALL -> true;
            case NONE -> preContingency;
            case ONLY_CONTINGENCIES -> !preContingency;
            case SPECIFIC -> !preContingency && context.getContingencyId().equals(state.contingencyId());
        };
    }
}
