/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.loadflow;

import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status.CONVERGED;
import static com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status.FAILED;
import static com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED;
import static com.powsybl.loadflow.LoadFlowResult.ComponentResult.Status.NO_CALCULATION;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LoadFlowConvergenceTest {

    private static LoadFlowResult.ComponentResult component(int cc, int sc, LoadFlowResult.ComponentResult.Status status) {
        return new LoadFlowResultImpl.ComponentResultImpl(cc, sc, status, 0, "slack", 0, 0);
    }

    private static LoadFlowConvergence convergence(LoadFlowResult.ComponentResult... components) {
        return LoadFlowConvergence.of(new LoadFlowResultImpl(true, Map.of(), null, List.of(components)));
    }

    @Test
    void allCalculatedConverged() {
        assertEquals(LoadFlowConvergence.CONVERGED, convergence(
                component(0, 0, CONVERGED), component(0, 1, CONVERGED), component(1, 2, NO_CALCULATION)));
    }

    @Test
    void mainConvergedOthersNot() {
        assertEquals(LoadFlowConvergence.PARTIALLY_CONVERGED, convergence(
                component(0, 0, CONVERGED), component(1, 1, MAX_ITERATION_REACHED)));
    }

    @Test
    void mainNotConverged() {
        assertEquals(LoadFlowConvergence.NOT_CONVERGED, convergence(
                component(0, 0, CONVERGED), component(0, 1, FAILED), component(1, 2, CONVERGED)));
    }

    @Test
    void mainNotCalculated() {
        assertEquals(LoadFlowConvergence.NOT_CONVERGED, convergence(
                component(0, 0, NO_CALCULATION), component(1, 1, CONVERGED)));
    }
}
