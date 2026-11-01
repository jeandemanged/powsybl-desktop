/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.loadflow;

import com.powsybl.iidm.network.ComponentConstants;
import com.powsybl.loadflow.LoadFlowResult;

import java.util.List;

/**
 * Unlike {@link LoadFlowResult#getStatus()}, which reports {@code PARTIALLY_CONVERGED} as soon as any component
 * converged, this distinguishes the largest connected component (num {@link ComponentConstants#MAIN_NUM}, possibly
 * split into several synchronous components): if it didn't converge, the run is {@link #NOT_CONVERGED} no matter
 * how the smaller components did. Components with status {@code NO_CALCULATION} are ignored.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public enum LoadFlowConvergence {
    CONVERGED,
    PARTIALLY_CONVERGED,
    NOT_CONVERGED;

    public static LoadFlowConvergence of(LoadFlowResult result) {
        List<LoadFlowResult.ComponentResult> calculated = result.getComponentResults().stream()
                .filter(c -> c.getStatus() != LoadFlowResult.ComponentResult.Status.NO_CALCULATION)
                .toList();
        List<LoadFlowResult.ComponentResult> main = calculated.stream()
                .filter(c -> c.getConnectedComponentNum() == ComponentConstants.MAIN_NUM)
                .toList();
        if (main.isEmpty() || !main.stream().allMatch(LoadFlowConvergence::converged)) {
            return NOT_CONVERGED;
        }
        return calculated.stream().allMatch(LoadFlowConvergence::converged) ? CONVERGED : PARTIALLY_CONVERGED;
    }

    private static boolean converged(LoadFlowResult.ComponentResult componentResult) {
        return componentResult.getStatus() == LoadFlowResult.ComponentResult.Status.CONVERGED;
    }
}
