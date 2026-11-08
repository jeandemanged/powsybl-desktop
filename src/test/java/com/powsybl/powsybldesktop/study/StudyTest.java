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
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.logs.LogsModel.LogEntry;
import com.powsybl.security.SecurityAnalysisResult;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        study14.setLoadFlowResult(new LoadFlowResultImpl(true, Map.of(), "lf log",
                List.of(new LoadFlowResultImpl.ComponentResultImpl(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 0, "slack", 0, 0))));
        study14.setSecurityAnalysisResult(SecurityAnalysisResult.empty());
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
        assertTrue(restored14.getLoadFlowResult().isFullyConverged());
        assertNotNull(restored14.getSecurityAnalysisResult());
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
        mainModel.getStudy(ieee14).setLoadFlowResult(new LoadFlowResultImpl(true, Map.of(), ""));

        Path file = tempDir.resolve("study.psbx");
        StudyArchive.write(Study.capture(mainModel, List.of(ieee14), EnumSet.noneOf(Study.Part.class)), file, (_, _, _) -> { });
        Study read = StudyArchive.read(file, (_, _, _) -> { });

        assertNull(read.parameters());
        assertNull(read.reports());
        assertNull(read.logs());
        assertTrue(read.networks().getFirst().contingencyLists().isEmpty());
        assertNull(read.networks().getFirst().loadFlowResult());

        MainModel restored = new MainModel();
        restored.addReport(ReportNode.newRootReportNode().withAllResourceBundlesFromClasspath().withMessageTemplate("desktop.loadFlow").build());
        restored.getParametersModel().loadFlowParametersProperty().getValue().setDc(true);
        read.restore(restored);
        assertEquals(1, restored.getReports().size());
        assertTrue(restored.getParametersModel().loadFlowParametersProperty().getValue().isDc());
        assertEquals(1, restored.getNetworks().size());
    }
}
