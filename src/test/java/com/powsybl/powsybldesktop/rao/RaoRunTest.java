/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.CracFactory;
import com.powsybl.openrao.data.crac.api.InstantKind;
import com.powsybl.openrao.data.raoresult.api.ComputationStatus;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.parameters.LoopFlowParameters;
import com.powsybl.openrao.raoapi.parameters.ObjectiveFunctionParameters.ObjectiveFunctionType;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.OpenRaoSearchTreeParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.SearchTreeRaoRangeActionsOptimizationParameters.PstModel;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisMode;
import com.powsybl.powsybldesktop.utils.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class RaoRunTest {

    private final Network network = RaoTestCases.network();
    private final Crac crac = RaoTestCases.crac(network);

    private static void assertOptimized(RaoResultAndReport resultAndReport) {
        RaoResult result = resultAndReport.raoResult();
        Crac runCrac = resultAndReport.crac();
        assertEquals(ComputationStatus.DEFAULT, result.getComputationStatus());
        assertTrue(result.isSecure(PhysicalParameter.FLOW));
        assertEquals(2, result.getOptimizedTapOnState(runCrac.getPreventiveState(), runCrac.getPstRangeAction(RaoTestCases.PST)));
        assertEquals(Set.of(runCrac.getNetworkAction(RaoTestCases.LOAD_SHEDDING)),
                result.getActivatedNetworkActionsDuringState(runCrac.getState(RaoTestCases.CONTINGENCY, runCrac.getInstant("curative"))));
    }

    // the network pool of several threads is where the RAO and thread-local variants disagreed
    @Test
    void optimizesCopiesLeavingTheNetworkAndTheCracUntouched() throws Exception {
        int networkActions = crac.getNetworkActions().size();
        RaoRun run = RaoRun.prepare(network, crac, RaoTestCases.parameters(4), false);

        RaoResultAndReport resultAndReport = run.compute();

        assertOptimized(resultAndReport);
        assertSame(run.crac(), resultAndReport.crac());
        assertNotSame(crac, resultAndReport.crac());
        assertEquals(networkActions, crac.getNetworkActions().size());
        assertEquals(1, network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().getTapPosition());
        assertEquals(100., network.getLoad("LD2").getP0());
        assertEquals(List.of("InitialState"), List.copyOf(network.getVariantManager().getVariantIds()));
        assertEquals("InitialState", network.getVariantManager().getWorkingVariantId());
        assertTrue(resultAndReport.reportNode().getChildren().stream().findAny().isPresent());
    }

    // on the network itself, the RAO would leave variants making a second run fail
    @Test
    void runsTwiceOnTheSameNetwork() throws Exception {
        RaoRun.prepare(network, crac, RaoTestCases.parameters(1), false).compute();

        assertOptimized(RaoRun.prepare(network, crac, RaoTestCases.parameters(1), false).compute());
    }

    @Test
    void fastRaoOptimizesToo() throws Exception {
        RaoResultAndReport resultAndReport = RaoRun.prepare(network, crac, RaoTestCases.parameters(1), true).compute();

        assertOptimized(resultAndReport);
        assertEquals(List.of("InitialState"), List.copyOf(network.getVariantManager().getVariantIds()));
    }

    @Test
    void fastRaoHandlesOneCurativeInstantAtMost() {
        crac.newInstant("curative 2", InstantKind.CURATIVE);

        assertEquals(List.of(), RaoRun.problems(network, crac, RaoTestCases.parameters(1), false));
        assertEquals(List.of(Messages.get("rao.problem.fastRaoCurativeInstants", 2)),
                RaoRun.problems(network, crac, RaoTestCases.parameters(1), true));
    }

    @Test
    void failedResultThrowsWithItsDetailsAndReport() {
        RaoParameters parameters = RaoTestCases.parameters(1);
        parameters.getObjectiveFunctionParameters().setType(ObjectiveFunctionType.MIN_COST);
        RaoRun run = RaoRun.prepare(network, crac, parameters, false);

        RaoFailedException failure = assertThrows(RaoFailedException.class, run::compute);

        assertTrue(failure.getMessage().contains("costly min margin"), failure.getMessage());
        assertTrue(failure.getReportNode().getChildren().stream().findAny().isPresent());
    }

    @Test
    void noProblemsForAValidCase() {
        assertEquals(List.of(), RaoRun.problems(network, crac, RaoTestCases.parameters(1), false));
    }

    @Test
    void elementsMissingFromTheNetwork() {
        network.getLine("L2").remove();
        network.getLoad("LD2").remove();

        assertEquals(List.of(
                Messages.get("rao.problem.contingencyElements", RaoTestCases.CONTINGENCY, "L2"),
                Messages.get("rao.problem.remedialActionElements", RaoTestCases.LOAD_SHEDDING, "LD2")
        ), RaoRun.problems(network, crac, RaoTestCases.parameters(1), false));
    }

    @Test
    void longListsOfMissingElementsAreShortened() {
        Crac manyMissing = CracFactory.findDefault().create("crac");
        manyMissing.newInstant("preventive", InstantKind.PREVENTIVE).newInstant("outage", InstantKind.OUTAGE);
        var adder = manyMissing.newContingency().withId("c");
        for (int i = 1; i <= 7; i++) {
            adder.withContingencyElement("X" + i, ContingencyElementType.LINE);
        }
        adder.add();

        assertEquals(List.of(
                Messages.get("rao.problem.noCnec"),
                Messages.get("rao.problem.contingencyElements", "c", Messages.get("rao.problem.more", "X1, X2, X3, X4, X5", 2))
        ), RaoRun.problems(network, manyMissing, RaoTestCases.parameters(1), false));
    }

    @Test
    void parametersNeedingInputsTheApplicationDoesNotGive() {
        RaoParameters parameters = RaoTestCases.parameters(1);
        parameters.getObjectiveFunctionParameters().setType(ObjectiveFunctionType.MAX_MIN_RELATIVE_MARGIN);
        parameters.setLoopFlowParameters(new LoopFlowParameters());

        assertEquals(List.of(
                Messages.get("rao.problem.relativeMargins", ObjectiveFunctionType.MAX_MIN_RELATIVE_MARGIN),
                Messages.get("rao.problem.loopFlows")
        ), RaoRun.problems(network, crac, parameters, false));
    }

    @Test
    void costOptimizationNeedsCostlyMinMarginParameters() {
        RaoParameters parameters = RaoTestCases.parameters(1);
        parameters.getObjectiveFunctionParameters().setType(ObjectiveFunctionType.MIN_COST);

        assertEquals(List.of(Messages.get("rao.problem.costlyMinMargins", ObjectiveFunctionType.MIN_COST)),
                RaoRun.problems(network, crac, parameters, false));
    }

    @Test
    void elementaryActionLimitsNeedIntegerPsts() {
        crac.newRaUsageLimits("preventive").withMaxElementaryActionPerTso(Map.of("TSO", 1)).add();

        assertEquals(List.of(Messages.get("rao.problem.pstModel")), RaoRun.problems(network, crac, RaoTestCases.parameters(1), false));

        RaoParameters integers = RaoTestCases.parameters(1);
        integers.getExtension(OpenRaoSearchTreeParameters.class).getRangeActionsOptimizationParameters()
                .setPstModel(PstModel.APPROXIMATED_INTEGERS);
        assertEquals(List.of(), RaoRun.problems(network, crac, integers, false));
    }

    @Test
    void copiedParametersAreIndependent() {
        RaoParameters parameters = RaoTestCases.parameters(3);
        LoadFlowParameters loadFlowParameters = RaoParametersSupport.sensitivityParameters(parameters).getLoadFlowParameters();
        ObjectiveFunctionType objective = parameters.getObjectiveFunctionParameters().getType();

        RaoParameters copy = RaoRun.copyParameters(parameters, SensitivityAnalysisMode.LOAD_FLOW);
        parameters.getObjectiveFunctionParameters().setType(ObjectiveFunctionType.MIN_COST);
        parameters.getExtension(OpenRaoSearchTreeParameters.class).getMultithreadingParameters().setAvailableCPUs(1);
        loadFlowParameters.setDc(true);

        assertEquals(objective, copy.getObjectiveFunctionParameters().getType());
        assertEquals(3, copy.getExtension(OpenRaoSearchTreeParameters.class).getMultithreadingParameters().getAvailableCPUs());
        LoadFlowParameters copiedLoadFlowParameters = RaoParametersSupport.sensitivityParameters(copy).getLoadFlowParameters();
        assertNotSame(loadFlowParameters, copiedLoadFlowParameters);
        assertFalse(copiedLoadFlowParameters.isDc());
    }

    @Test
    void modeOverridesTheLoadFlowsAcOrDcOnTheCopyOnly() {
        RaoParameters parameters = RaoTestCases.parameters(1);

        assertTrue(RaoParametersSupport.sensitivityParameters(RaoRun.copyParameters(parameters, SensitivityAnalysisMode.DC)).getLoadFlowParameters().isDc());
        assertFalse(RaoParametersSupport.sensitivityParameters(parameters).getLoadFlowParameters().isDc());
        RaoParametersSupport.sensitivityParameters(parameters).getLoadFlowParameters().setDc(true);
        assertFalse(RaoParametersSupport.sensitivityParameters(RaoRun.copyParameters(parameters, SensitivityAnalysisMode.AC)).getLoadFlowParameters().isDc());
        assertTrue(RaoParametersSupport.sensitivityParameters(RaoRun.copyParameters(parameters, SensitivityAnalysisMode.LOAD_FLOW)).getLoadFlowParameters().isDc());
    }
}
