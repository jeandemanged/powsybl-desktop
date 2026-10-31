/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.security;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.monitor.StateMonitor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SecurityAnalysisRunTest {

    private static final DefaultContingencyList LINES = new DefaultContingencyList("lines",
            List.of(Contingency.line("NHV1_NHV2_1"), Contingency.line("NHV1_NHV2_2")));

    @Test
    void variantMultiThreadAccessIsAllowedFromPrepareUntilRestored() {
        Network network = EurostagTutorialExample1Factory.create();
        assertFalse(network.getVariantManager().isVariantMultiThreadAccessAllowed());

        SecurityAnalysisRun run = SecurityAnalysisRun.prepare(network, LINES, new SecurityAnalysisParameters());
        assertTrue(network.getVariantManager().isVariantMultiThreadAccessAllowed());

        run.restoreVariantAccess();
        assertFalse(network.getVariantManager().isVariantMultiThreadAccessAllowed());
    }

    @Test
    void computeSimulatesEveryContingencyWithItsReport() throws Exception {
        Network network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
        SecurityAnalysisRun run = SecurityAnalysisRun.prepare(network, LINES, new SecurityAnalysisParameters());
        assertTrue(run.computation().isEmpty());

        SecurityAnalysisResultAndReport result = run.compute();
        run.restoreVariantAccess();

        assertEquals(List.of("NHV1_NHV2_1", "NHV1_NHV2_2"), result.securityAnalysisResult().getPostContingencyResults().stream()
                .map(postContingencyResult -> postContingencyResult.getContingency().getId())
                .sorted()
                .toList());
        assertEquals("desktop.securityAnalysis", result.reportNode().getMessageKey());
        assertTrue(run.computation().orElseThrow().isDone());
    }

    @Test
    void preContingencyMonitorCoversEveryBranchAndVoltageLevel() {
        Network network = EurostagTutorialExample1Factory.create();
        StateMonitor monitor = SecurityAnalysisRun.preContingencyMonitor(network);
        assertEquals(network.getBranchStream().map(Identifiable::getId).collect(Collectors.toSet()), monitor.getBranchIds());
        assertEquals(network.getVoltageLevelStream().map(Identifiable::getId).collect(Collectors.toSet()), monitor.getVoltageLevelIds());
    }

    @Test
    void copiedParametersDontFollowLaterEditsOfTheOriginal() {
        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
        parameters.addExtension(OpenSecurityAnalysisParameters.class, new OpenSecurityAnalysisParameters().setThreadCount(3));

        SecurityAnalysisParameters copy = SecurityAnalysisRun.copyParameters(parameters);
        parameters.getLoadFlowParameters().setDc(true);
        parameters.getExtension(OpenSecurityAnalysisParameters.class).setThreadCount(5);

        assertNotSame(parameters.getLoadFlowParameters(), copy.getLoadFlowParameters());
        assertFalse(copy.getLoadFlowParameters().isDc());
        assertEquals(3, copy.getExtension(OpenSecurityAnalysisParameters.class).getThreadCount());
    }
}
