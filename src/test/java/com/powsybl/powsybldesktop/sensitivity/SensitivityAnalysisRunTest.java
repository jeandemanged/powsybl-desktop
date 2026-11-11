/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityState;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SensitivityAnalysisRunTest {

    private static final DefaultContingencyList LINE_2 = new DefaultContingencyList("lines", List.of(Contingency.line("NHV1_NHV2_2")));
    private static final SensitivityFactorMatrix LINES_TO_GENERATOR = new SensitivityFactorMatrix("lines", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
            List.of("NHV1_NHV2_1", "NHV1_NHV2_2"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("GEN"), false, ContingencyContext.all());
    private static final SensitivityFactorMatrix LINE_TO_SET = new SensitivityFactorMatrix("set", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
            List.of("NHV1_NHV2_1"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("GLSK"), true, ContingencyContext.none());
    private static final SensitivityVariableSet GLSK = new SensitivityVariableSet("GLSK", List.of(new WeightedSensitivityVariable("LOAD", 1)));

    @Test
    void variantMultiThreadAccessIsAllowedFromPrepareUntilRestored() {
        Network network = EurostagTutorialExample1Factory.create();
        assertFalse(network.getVariantManager().isVariantMultiThreadAccessAllowed());

        SensitivityAnalysisRun run = SensitivityAnalysisRun.prepare(network, List.of(LINES_TO_GENERATOR), List.of(), LINE_2,
                new SensitivityAnalysisParameters());
        assertTrue(network.getVariantManager().isVariantMultiThreadAccessAllowed());

        run.restoreVariantAccess();
        assertFalse(network.getVariantManager().isVariantMultiThreadAccessAllowed());
    }

    @Test
    void computeGivesEveryFactorOfEveryStateWithItsReport() throws Exception {
        Network network = EurostagTutorialExample1Factory.create();
        SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters();
        // through the multi-threaded path, which needs the variant access prepared
        parameters.addExtension(OpenSensitivityAnalysisParameters.class, new OpenSensitivityAnalysisParameters().setThreadCount(2));
        SensitivityAnalysisRun run = SensitivityAnalysisRun.prepare(network, List.of(LINES_TO_GENERATOR, LINE_TO_SET), List.of(GLSK), LINE_2, parameters);
        assertTrue(run.computation().isEmpty());

        SensitivityAnalysisResultAndReport resultAndReport = run.compute();
        run.restoreVariantAccess();

        SensitivityAnalysisResult result = resultAndReport.sensitivityAnalysisResult();
        assertEquals(3, result.getFactors().size());
        assertEquals(List.of("NHV1_NHV2_2"), result.getContingencyIds());
        assertEquals(3, result.getValues(SensitivityState.PRE_CONTINGENCY).size());
        // the variable set's factor is pre-contingency only
        assertEquals(2, result.getValues(SensitivityState.postContingency("NHV1_NHV2_2")).size());
        assertEquals(List.of(GLSK), run.variableSets());
        assertEquals("desktop.sensitivityAnalysis", resultAndReport.reportNode().getMessageKey());
        assertTrue(run.computation().orElseThrow().isDone());
    }

    @Test
    void copiedParametersDontFollowLaterEditsOfTheOriginal() {
        SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters().setFlowFlowSensitivityValueThreshold(0.01);
        parameters.addExtension(OpenSensitivityAnalysisParameters.class, new OpenSensitivityAnalysisParameters().setThreadCount(3));

        SensitivityAnalysisParameters copy = SensitivityAnalysisRun.copyParameters(parameters, SensitivityAnalysisMode.LOAD_FLOW);
        parameters.getLoadFlowParameters().setDc(true);
        parameters.setFlowFlowSensitivityValueThreshold(0.5);
        parameters.getExtension(OpenSensitivityAnalysisParameters.class).setThreadCount(5);

        assertNotSame(parameters.getLoadFlowParameters(), copy.getLoadFlowParameters());
        assertFalse(copy.getLoadFlowParameters().isDc());
        assertEquals(0.01, copy.getFlowFlowSensitivityValueThreshold());
        assertEquals(3, copy.getExtension(OpenSensitivityAnalysisParameters.class).getThreadCount());
    }

    @Test
    void copiedParametersAreAcOrDcAsTheModeSays() {
        SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters();
        parameters.getLoadFlowParameters().setDc(true);

        assertTrue(SensitivityAnalysisRun.copyParameters(parameters, SensitivityAnalysisMode.LOAD_FLOW).getLoadFlowParameters().isDc());
        assertFalse(SensitivityAnalysisRun.copyParameters(parameters, SensitivityAnalysisMode.AC).getLoadFlowParameters().isDc());
        assertTrue(parameters.getLoadFlowParameters().isDc());
        parameters.getLoadFlowParameters().setDc(false);
        assertTrue(SensitivityAnalysisRun.copyParameters(parameters, SensitivityAnalysisMode.DC).getLoadFlowParameters().isDc());
        assertFalse(parameters.getLoadFlowParameters().isDc());
    }
}
