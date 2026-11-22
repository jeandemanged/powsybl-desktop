/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.study;

import ch.qos.logback.classic.Level;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.logs.LogsModel.LogEntry;
import com.powsybl.powsybldesktop.rao.RaoResultAndReport;
import com.powsybl.powsybldesktop.rao.RaoRun;
import com.powsybl.powsybldesktop.rao.RaoTestCases;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisMode;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorMatrix;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.limitscaling.LimitScaling;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityState;
import com.powsybl.sensitivity.SensitivityValue;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class StudyTest {

    // MainModel starts a LogsModel Timeline, which needs the JavaFX toolkit up, see MainModelTest
    @BeforeAll
    static void initJavaFxToolkit() {
        System.setProperty("prism.order", "sw");
        System.setProperty("java.awt.headless", "true");
        System.setProperty("glass.platform", "Headless");
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException _) {
            // toolkit already started by another test class running in this fork
        }
    }

    @Test
    void savedStudyRestoresNetworksResultsParametersReportsAndLogs(@TempDir Path tempDir) throws Exception {
        MainModel mainModel = new MainModel();
        Network ieee14 = IeeeCdfNetworkFactory.create14();
        Network ieee9 = IeeeCdfNetworkFactory.create9();
        mainModel.addNetwork(ieee14);
        mainModel.addNetwork(ieee9);
        mainModel.setNetwork(ieee9);
        NetworkStudy study14 = mainModel.getStudy(ieee14);
        ContingencyList enabled = new DefaultContingencyList("enabled", List.of(Contingency.line("L1-2-1")));
        ContingencyList disabled = new DefaultContingencyList("disabled", List.of(Contingency.line("L1-5-1")));
        study14.getContingencyLists().setAll(enabled, disabled);
        study14.contingencyListEnabledProperty(disabled).set(false);
        LimitScaling scaling = new LimitScaling(LimitType.CURRENT, 0.9);
        LimitScaling disabledScaling = new LimitScaling(LimitType.ACTIVE_POWER, 0.8);
        study14.getLimitScalings().setAll(scaling, disabledScaling);
        study14.limitScalingEnabledProperty(disabledScaling).set(false);
        study14.setSecurityAnalysisLimitScalings(List.of(new LimitScaling(LimitType.CURRENT, 0.7)));
        study14.setLoadFlowResult(new LoadFlowResultImpl(true, Map.of(), "lf log",
                List.of(new LoadFlowResultImpl.ComponentResultImpl(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 0, "slack", 0, 0))));
        study14.setSecurityAnalysisResult(SecurityAnalysisResult.empty());
        SensitivityFactorMatrix matrix = new SensitivityFactorMatrix("lines", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, List.of("L1-2-1", "L1-5-1"),
                SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("GLSK"), true, ContingencyContext.specificContingency("L1-2-1"));
        SensitivityVariableSet glsk = new SensitivityVariableSet("GLSK", List.of(new WeightedSensitivityVariable("B1-G", 0.5)));
        study14.getSensitivityFactorMatrices().add(matrix);
        study14.getSensitivityVariableSets().add(glsk);
        SensitivityAnalysisResult sensitivityResult = new SensitivityAnalysisResult(matrix.factors(),
                List.of(new SensitivityAnalysisResult.SensitivityStateStatus(SensitivityState.postContingency("L1-2-1"), SensitivityAnalysisResult.Status.FAILURE)),
                List.of("L1-2-1"), List.of(), List.of(new SensitivityValue(0, -1, -1, 0.25, 12)));
        study14.setSensitivityAnalysisResult(sensitivityResult, List.of(matrix), true);
        mainModel.getParametersModel().loadFlowParametersProperty().getValue().setDc(true);
        mainModel.addReport(ReportNode.newRootReportNode().withAllResourceBundlesFromClasspath().withMessageTemplate("desktop.loadFlow").build());
        mainModel.getLogsModel().restoreLogs(List.of(new LogEntry(3, 1000, Level.WARN, "a warning"), new LogEntry(4, 2000, Level.INFO, "an info")));

        Path file = tempDir.resolve("study.psbx");
        Files.writeString(file, "previous content");
        List<Integer> steps = new ArrayList<>();
        StudyArchive.write(Study.capture(mainModel, List.of(ieee14, ieee9), EnumSet.allOf(Study.Part.class)), file,
                (done, total, _) -> steps.add(done * 100 / total));
        assertFalse(Files.exists(tempDir.resolve("study.psbx.tmp")));
        assertEquals(steps.stream().sorted().toList(), steps);

        Study read = StudyArchive.read(file, (_, _, _) -> { });
        MainModel restored = new MainModel();
        restored.addNetwork(IeeeCdfNetworkFactory.create30());
        read.restore(restored);

        assertEquals(List.of(ieee14.getId(), ieee9.getId()), restored.getNetworks().stream().map(Network::getId).toList());
        assertEquals(ieee14.getLineCount(), restored.getNetworks().getFirst().getLineCount());
        assertSame(restored.getNetworks().getLast(), restored.getNetwork());
        NetworkStudy restored14 = restored.getStudy(restored.getNetworks().getFirst());
        assertEquals(List.of("enabled", "disabled"), restored14.getContingencyLists().stream().map(ContingencyList::getName).toList());
        assertTrue(restored14.contingencyListEnabledProperty(restored14.getContingencyLists().getFirst()).get());
        assertFalse(restored14.contingencyListEnabledProperty(restored14.getContingencyLists().getLast()).get());
        assertEquals(List.of(0.9, 0.8), restored14.getLimitScalings().stream().map(LimitScaling::getValue).toList());
        assertTrue(restored14.limitScalingEnabledProperty(restored14.getLimitScalings().getFirst()).get());
        assertFalse(restored14.limitScalingEnabledProperty(restored14.getLimitScalings().getLast()).get());
        assertEquals(List.of(0.7), restored14.getSecurityAnalysisLimitScalings().stream().map(LimitScaling::getValue).toList());
        assertTrue(restored14.getLoadFlowResult().isFullyConverged());
        assertNotNull(restored14.getSecurityAnalysisResult());
        assertEquals(List.of(matrix), restored14.getSensitivityFactorMatrices());
        assertEquals("GLSK", restored14.getSensitivityVariableSets().getFirst().getId());
        assertEquals(0.5, restored14.getSensitivityVariableSets().getFirst().getVariables().iterator().next().getWeight());
        assertEquals(List.of(matrix), restored14.getSensitivityAnalysisMatrices());
        assertTrue(restored14.isSensitivityAnalysisDc());
        assertEquals(0.25, restored14.getSensitivityAnalysisResult().getPreContingencyValues().getFirst().getValue());
        assertEquals(SensitivityAnalysisResult.Status.FAILURE,
                restored14.getSensitivityAnalysisResult().getStateStatus(SensitivityState.postContingency("L1-2-1")));
        assertNull(restored.getStudy(restored.getNetworks().getLast()).getSensitivityAnalysisResult());
        assertNull(restored.getStudy(restored.getNetworks().getLast()).getLoadFlowResult());
        assertTrue(restored.getParametersModel().loadFlowParametersProperty().getValue().isDc());
        assertEquals(1, restored.getReports().size());
        assertEquals(List.of("a warning", "an info"), restored.getLogsModel().getLogs().stream().map(LogEntry::message).toList());
        assertEquals(Level.WARN, restored.getLogsModel().getLogs().getFirst().level());
        assertTrue(restored.getLogsModel().getLogs().getLast().sequenceNumber() < 0);
    }

    @Test
    void partsNotSavedAreLeftAsTheyAre(@TempDir Path tempDir) throws Exception {
        MainModel mainModel = new MainModel();
        Network ieee14 = IeeeCdfNetworkFactory.create14();
        mainModel.addNetwork(ieee14);
        mainModel.getStudy(ieee14).getContingencyLists().add(new DefaultContingencyList("list", List.of(Contingency.line("L1-2-1"))));
        mainModel.getStudy(ieee14).getLimitScalings().add(new LimitScaling(LimitType.CURRENT, 0.9));
        mainModel.getStudy(ieee14).setLoadFlowResult(new LoadFlowResultImpl(true, Map.of(), ""));
        mainModel.getStudy(ieee14).getSensitivityVariableSets().add(new SensitivityVariableSet("GLSK", List.of()));

        Path file = tempDir.resolve("study.psbx");
        StudyArchive.write(Study.capture(mainModel, List.of(ieee14), EnumSet.noneOf(Study.Part.class)), file, (_, _, _) -> { });
        Study read = StudyArchive.read(file, (_, _, _) -> { });

        assertNull(read.parameters());
        assertNull(read.reports());
        assertNull(read.logs());
        assertTrue(read.networks().getFirst().contingencyLists().isEmpty());
        assertTrue(read.networks().getFirst().limitScalings().isEmpty());
        assertTrue(read.networks().getFirst().sensitivityVariableSets().isEmpty());
        assertNull(read.networks().getFirst().loadFlowResult());

        MainModel restored = new MainModel();
        restored.addReport(ReportNode.newRootReportNode().withAllResourceBundlesFromClasspath().withMessageTemplate("desktop.loadFlow").build());
        restored.getParametersModel().loadFlowParametersProperty().getValue().setDc(true);
        read.restore(restored);
        assertEquals(1, restored.getReports().size());
        assertTrue(restored.getParametersModel().loadFlowParametersProperty().getValue().isDc());
        assertEquals(1, restored.getNetworks().size());
    }

    // the study's CRAC, its creation report, and the result of a RAO run with it
    private static MainModel withCracAndRaoResult(Network network, boolean dc) throws Exception {
        MainModel mainModel = new MainModel();
        mainModel.addNetwork(network);
        NetworkStudy study = mainModel.getStudy(network);
        Crac crac = RaoTestCases.crac(network);
        study.setCrac(crac, List.of("a report line"));
        RaoParameters parameters = RaoRun.copyParameters(RaoTestCases.parameters(1), dc ? SensitivityAnalysisMode.DC : SensitivityAnalysisMode.AC);
        RaoResultAndReport run = RaoRun.prepare(network, crac, parameters, false).compute();
        study.setRaoResult(run.raoResult(), run.crac(), crac, study.cracRevisionProperty().get(), dc);
        return mainModel;
    }

    private static NetworkStudy saveAndRestore(MainModel mainModel, Path file, Set<Study.Part> parts, List<String> warnings) throws Exception {
        StudyArchive.write(Study.capture(mainModel, mainModel.getNetworks(), parts), file, (_, _, _) -> { });
        Study read = StudyArchive.read(file, (_, _, _) -> { });
        warnings.addAll(read.warnings());
        MainModel restored = new MainModel();
        read.restore(restored);
        return restored.getStudy(restored.getNetworks().getFirst());
    }

    @Test
    void savedStudyRestoresTheCracAndTheRaoResult(@TempDir Path tempDir) throws Exception {
        MainModel mainModel = withCracAndRaoResult(RaoTestCases.network(), false);
        NetworkStudy study = mainModel.getStudy(mainModel.getNetworks().getFirst());
        Crac crac = study.getCrac();
        FlowCnec cnec = crac.getFlowCnec("L1 curative");
        double flowA = study.getRaoResult().getFlow(crac.getInstant("curative"), study.getRaoCrac().getFlowCnec("L1 curative"), TwoSides.ONE, Unit.AMPERE);
        double flowMw = study.getRaoResult().getFlow(crac.getInstant("curative"), study.getRaoCrac().getFlowCnec("L1 curative"), TwoSides.ONE, Unit.MEGAWATT);

        List<String> warnings = new ArrayList<>();
        NetworkStudy restored = saveAndRestore(mainModel, tempDir.resolve("study.psbx"), EnumSet.allOf(Study.Part.class), warnings);

        assertEquals(List.of(), warnings);
        Crac restoredCrac = restored.getCrac();
        assertEquals(crac.getId(), restoredCrac.getId());
        assertEquals(crac.getCnecs().size(), restoredCrac.getCnecs().size());
        assertEquals(cnec.getThresholds().size(), restoredCrac.getFlowCnec("L1 curative").getThresholds().size());
        assertEquals(List.of("a report line"), restored.getCracCreationReport());
        // up to date, on a CRAC of its own
        assertFalse(restored.isRaoResultOutdated());
        assertFalse(restored.isRaoDc());
        Crac resultCrac = restored.getRaoCrac();
        assertNotSame(restoredCrac, resultCrac);
        RaoResult result = restored.getRaoResult();
        assertEquals(2, result.getOptimizedTapOnState(resultCrac.getPreventiveState(), resultCrac.getPstRangeAction(RaoTestCases.PST)));
        assertEquals(Set.of(resultCrac.getNetworkAction(RaoTestCases.LOAD_SHEDDING)),
                result.getActivatedNetworkActionsDuringState(resultCrac.getState(RaoTestCases.CONTINGENCY, resultCrac.getInstant("curative"))));
        FlowCnec resultCnec = resultCrac.getFlowCnec("L1 curative");
        // OpenRAO's JSON rounds them to 2 decimals
        assertEquals(flowA, result.getFlow(resultCrac.getInstant("curative"), resultCnec, TwoSides.ONE, Unit.AMPERE), 0.01);
        assertEquals(flowMw, result.getFlow(resultCrac.getInstant("curative"), resultCnec, TwoSides.ONE, Unit.MEGAWATT), 0.01);

        // an edit of the restored CRAC outdates it
        restored.cracEdited();
        assertTrue(restored.isRaoResultOutdated());
    }

    @Test
    void anOutdatedRaoResultStaysOutdated(@TempDir Path tempDir) throws Exception {
        // in DC: its flows in A too
        MainModel mainModel = withCracAndRaoResult(RaoTestCases.network(), true);
        NetworkStudy study = mainModel.getStudy(mainModel.getNetworks().getFirst());
        study.cracEdited();
        List<String> warnings = new ArrayList<>();
        assertTrue(saveAndRestore(mainModel, tempDir.resolve("edited.psbx"), EnumSet.allOf(Study.Part.class), warnings).isRaoResultOutdated());

        // without its CRAC: outdated too
        NetworkStudy restored = saveAndRestore(mainModel, tempDir.resolve("noCrac.psbx"), EnumSet.complementOf(EnumSet.of(Study.Part.CRAC)), warnings);
        assertNull(restored.getCrac());
        assertTrue(restored.isRaoResultOutdated());
        assertTrue(restored.isRaoDc());
        assertEquals(List.of(), warnings);
    }

    // a CRAC on elements the network no longer has can't be read back against it: the study is restored without it
    @Test
    void aCracTheNetworkNoLongerMatchesIsLeftOut(@TempDir Path tempDir) throws Exception {
        Network network = RaoTestCases.network();
        MainModel mainModel = withCracAndRaoResult(network, false);
        network.getLine("L1").remove();

        List<String> warnings = new ArrayList<>();
        NetworkStudy restored = saveAndRestore(mainModel, tempDir.resolve("study.psbx"), EnumSet.allOf(Study.Part.class), warnings);

        assertEquals(2, warnings.size());
        assertTrue(warnings.getFirst().startsWith(Messages.get("study.warning.crac", network.getNameOrId(), "")));
        assertNull(restored.getCrac());
        assertNull(restored.getRaoResult());
    }
}
