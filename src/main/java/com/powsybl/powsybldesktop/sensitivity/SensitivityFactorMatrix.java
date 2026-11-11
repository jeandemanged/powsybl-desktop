/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;

import java.util.List;
import java.util.Objects;

/**
 * The sensitivities of every function to every variable, in one contingency context: the unit users define, PowSyBl's
 * own {@link SensitivityFactor} being a single (function, variable) pair. Immutable, like a contingency list.
 *
 * @param variableSet whether the variable ids are ids of {@link com.powsybl.sensitivity.SensitivityVariableSet}s
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record SensitivityFactorMatrix(String name, SensitivityFunctionType functionType, List<String> functionIds,
                                      SensitivityVariableType variableType, List<String> variableIds, boolean variableSet,
                                      ContingencyContext contingencyContext) {

    public SensitivityFactorMatrix {
        Objects.requireNonNull(name);
        Objects.requireNonNull(functionType);
        functionIds = List.copyOf(functionIds);
        Objects.requireNonNull(variableType);
        variableIds = List.copyOf(variableIds);
        Objects.requireNonNull(contingencyContext);
    }

    /**
     * The factors, function by function.
     */
    public List<SensitivityFactor> factors() {
        return SensitivityFactor.createMatrix(functionType, functionIds, variableType, variableIds, variableSet, contingencyContext);
    }

    public int factorCount() {
        return functionIds.size() * variableIds.size();
    }
}
