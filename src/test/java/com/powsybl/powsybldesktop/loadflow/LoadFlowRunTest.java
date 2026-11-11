/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.loadflow;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LoadFlowRunTest {

    @Test
    void runComputesTheStateItsReportAndTheViolationsOfTheNewState() throws Exception {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();

        LoadFlowResultAndReport result = LoadFlowRun.run(network, new LoadFlowParameters(), List.of());

        assertEquals(LoadFlowConvergence.CONVERGED, LoadFlowConvergence.of(result.loadFlowResult()));
        assertFalse(Double.isNaN(network.getLine("NHV1_NHV2_1").getTerminal1().getI()));
        assertEquals("desktop.loadFlow", result.reportNode().getMessageKey());
        assertFalse(result.reportNode().getChildren().isEmpty());
        assertEquals(NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS).getLoading("NHV1_NHV2_1").percent(),
                result.violations().getLoading("NHV1_NHV2_1").percent(), 1e-9);
    }
}
