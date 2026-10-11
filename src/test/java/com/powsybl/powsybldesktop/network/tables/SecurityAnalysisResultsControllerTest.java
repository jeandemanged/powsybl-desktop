/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.contingency.ContingencyNames;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.Loading;
import com.powsybl.security.LimitViolationsResult;
import com.powsybl.security.SecurityAnalysis;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.SecurityAnalysisRunParameters;
import com.powsybl.security.results.BusResult;
import com.powsybl.security.results.NetworkResult;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SecurityAnalysisResultsControllerTest extends AbstractHeadlessApplicationTest {

    private static final String LINE_1 = "NHV1_NHV2_1";
    private static final String LINE_2 = "NHV1_NHV2_2";

    private Network network;
    private double permanentLimit;
    private SecurityAnalysisResultsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        network = EurostagTutorialExample1Factory.createWithLFResults();
        Line line1 = network.getLine(LINE_1);
        double i1 = line1.getTerminal1().getI();
        permanentLimit = i1 / 1.2;
        // pre-contingency: 600 s temporary limit violation; after LINE_2's loss, carrying about twice as much: 60 s
        line1.newCurrentLimits1()
                .setPermanentLimit(permanentLimit)
                .beginTemporaryLimit().setName("TL600").setAcceptableDuration(600).setValue(i1 * 1.5).endTemporaryLimit()
                .beginTemporaryLimit().setName("TL60").setAcceptableDuration(60).setValue(i1 * 3).endTemporaryLimit()
                .add();
        VoltageLevel vlhv1 = network.getVoltageLevel("VLHV1");
        vlhv1.setHighVoltageLimit(vlhv1.getBusView().getBuses().iterator().next().getV() - 1);

        mainModel = new MainModel();
        mainModel.addNetwork(network);
        SecurityAnalysisRunParameters runParameters = new SecurityAnalysisRunParameters()
                .addMonitor(SecurityAnalysisResultsController.preContingencyMonitor(network));
        mainModel.getStudy(network).setSecurityAnalysisResult(SecurityAnalysis.find("OpenLoadFlow")
                .run(network, List.of(Contingency.line(LINE_2), Contingency.line(LINE_1)), runParameters).getResult());

        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/security-analysis-results-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        controller.setSceneModel(SceneModel.main(mainModel));

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private String contingencyName(String id) {
        return ContingencyNames.displayName(Contingency.line(id), network);
    }

    @Test
    void contingenciesAreSortedByTheirMostSevereFlowViolationAfterThePreContingencyState() {
        List<SecurityAnalysisResultsController.ContingencyRow> rows = controller.contingenciesTableView.getItems();
        assertEquals(List.of(Messages.get("securityAnalysisResults.preContingency"), contingencyName(LINE_2), contingencyName(LINE_1)),
                rows.stream().map(row -> row.state().name()).toList());
        assertEquals(Messages.get("securityAnalysisResults.tab.contingencies", 3), controller.contingenciesTab.getText());
        assertEquals(1, rows.getFirst().voltageCount());
        assertFalse(rows.getFirst().state().failed());
        assertEquals(1, rows.get(1).flowCount());
        assertEquals(60, rows.get(1).worst().violation().getAcceptableDuration());
        // LINE_1's own loss: no flow violation left
        assertNull(rows.get(2).worst());
    }

    @Test
    void flowViolationsByContingencyAreRelativeToThePermanentLimit() {
        List<SecurityAnalysisResultsController.ContingencyFlowRow> rows = controller.flowByContingencyTableView.getItems();
        assertEquals(List.of(Messages.get("securityAnalysisResults.preContingency"), contingencyName(LINE_2)),
                rows.stream().map(row -> row.state().name()).toList());
        FlowViolation worst = rows.get(1).worst();
        assertEquals(LINE_1, worst.violation().getSubjectId());
        assertEquals("TL600", worst.violation().getLimitName());
        assertEquals(permanentLimit, worst.permanentLimit(), 1e-6);
        assertEquals(worst.violation().getValue() / permanentLimit * 100, worst.percent(), 1e-6);
    }

    @Test
    void preContingencyRowsStayFirstWhenSortedByAColumn() {
        TableColumn<SecurityAnalysisResultsController.ContingencyFlowRow, Loading> loadingColumn =
                ViolationsTestSupport.column(controller.flowByContingencyTableView, "loading");
        for (TableColumn.SortType sortType : TableColumn.SortType.values()) {
            interact(() -> {
                loadingColumn.setSortType(sortType);
                controller.flowByContingencyTableView.getSortOrder().setAll(List.of(loadingColumn));
            });

            assertEquals(List.of(Messages.get("securityAnalysisResults.preContingency"), contingencyName(LINE_2)),
                    controller.flowByContingencyTableView.getItems().stream().map(row -> row.state().name()).toList());
        }
    }

    @Test
    void postContingencyRowsShowThePreContingencyValues() {
        TableView<SecurityAnalysisResultsController.ContingencyFlowRow> table = controller.flowByContingencyTableView;
        TableColumn<SecurityAnalysisResultsController.ContingencyFlowRow, Double> valueColumn = ViolationsTestSupport.column(table, "value");
        TableColumn<SecurityAnalysisResultsController.ContingencyFlowRow, Double> preValueColumn = ViolationsTestSupport.column(table, "preContingencyValue");
        TableColumn<SecurityAnalysisResultsController.ContingencyFlowRow, Loading> preLoadingColumn = ViolationsTestSupport.column(table, "preContingencyLoading");
        // none for the pre-contingency row itself
        assertNull(preValueColumn.getCellObservableValue(0).getValue());
        assertNull(preLoadingColumn.getCellObservableValue(0).getValue());
        // the post-contingency row's: the pre-contingency violation's
        assertEquals(valueColumn.getCellObservableValue(0).getValue(), preValueColumn.getCellObservableValue(1).getValue());
        Loading preLoading = preLoadingColumn.getCellObservableValue(1).getValue();
        assertTrue(preLoading.isViolated());
        assertEquals(120, preLoading.percent(), 1);
    }

    @Test
    void preContingencyValuesOfUnviolatedEquipmentAreMonitored() {
        // LINE_2 and VLHV2's voltage only violated after LINE_1's loss
        VoltageLevel vlhv2 = network.getVoltageLevel("VLHV2");
        double v2 = vlhv2.getBusView().getBuses().iterator().next().getV();
        interact(() -> {
            Line line2 = network.getLine(LINE_2);
            line2.newCurrentLimits1().setPermanentLimit(line2.getTerminal1().getI() * 1.5).add();
            vlhv2.setLowVoltageLimit(v2 - 1);
            mainModel.getStudy(network).setSecurityAnalysisResult(SecurityAnalysis.find("OpenLoadFlow")
                    .run(network, List.of(Contingency.line(LINE_1)), new SecurityAnalysisRunParameters()
                            .addMonitor(SecurityAnalysisResultsController.preContingencyMonitor(network))).getResult());
            mainModel.setUpdate();
        });

        TableView<SecurityAnalysisResultsController.ContingencyFlowRow> table = controller.flowByContingencyTableView;
        int row = table.getItems().indexOf(table.getItems().stream()
                .filter(r -> r.equipment().getId().equals(LINE_2)).findFirst().orElseThrow());
        TableColumn<SecurityAnalysisResultsController.ContingencyFlowRow, Double> preValueColumn = ViolationsTestSupport.column(table, "preContingencyValue");
        TableColumn<SecurityAnalysisResultsController.ContingencyFlowRow, Loading> preLoadingColumn = ViolationsTestSupport.column(table, "preContingencyLoading");
        assertEquals(network.getLine(LINE_2).getTerminal1().getI(), preValueColumn.getCellObservableValue(row).getValue(), 1);
        Loading preLoading = preLoadingColumn.getCellObservableValue(row).getValue();
        assertFalse(preLoading.isViolated());
        assertEquals(100 / 1.5, preLoading.percent(), 1);

        // the voltage violation's pre-contingency voltage, monitored too
        SecurityAnalysisResultsController.VoltageRow voltageRow = controller.voltageTableView.getItems().stream()
                .filter(r -> r.violation().violation().getSubjectId().equals("VLHV2")).findFirst().orElseThrow();
        assertFalse(voltageRow.state().preContingency());
        TableColumn<SecurityAnalysisResultsController.VoltageRow, Double> preVoltageColumn = ViolationsTestSupport.column(controller.voltageTableView, "preContingencyValue");
        int voltageRowIndex = controller.voltageTableView.getItems().indexOf(voltageRow);
        assertEquals(v2, preVoltageColumn.getCellObservableValue(voltageRowIndex).getValue(), 0.5);
        TableColumn<SecurityAnalysisResultsController.VoltageRow, VoltageRange> preRangeColumn =
                ViolationsTestSupport.column(controller.voltageTableView, "preContingencyVoltageRange");
        VoltageRange preRange = preRangeColumn.getCellObservableValue(voltageRowIndex).getValue();
        assertEquals(v2, preRange.v(), 0.5);
        assertNull(preRange.violation());
    }

    @Test
    void preContingencyColumnsAreLeftOfTheirPostContingencyOnes() {
        assertColumnsInOrder(controller.flowByContingencyTableView, "preContingencyValue", "value");
        assertColumnsInOrder(controller.flowByContingencyTableView, "preContingencyLoading", "loading");
        assertColumnsInOrder(controller.flowByEquipmentTableView, "preContingencyValue", "value");
        assertColumnsInOrder(controller.flowByEquipmentTableView, "preContingencyLoading", "loading");
        assertColumnsInOrder(controller.voltageTableView, "preContingencyValue", "value");
        assertColumnsInOrder(controller.voltageTableView, "preContingencyVoltageRange", "voltageRange");
    }

    private static void assertColumnsInOrder(TableView<?> table, String leftId, String rightId) {
        List<String> ids = table.getColumns().stream().map(TableColumn::getId).toList();
        assertEquals(ids.indexOf(leftId) + 1, ids.indexOf(rightId), ids.toString());
    }

    @Test
    void busResultsOfAnyIdKindAreMappedToTheirBusViewBus() {
        Network nodeBreaker = FourSubstationsNodeBreakerFactory.create();
        VoltageLevel voltageLevel = nodeBreaker.getVoltageLevel("S1VL2");
        BusbarSection busbarSection = voltageLevel.getNodeBreakerView().getBusbarSections().iterator().next();
        Bus busViewBus = busbarSection.getTerminal().getBusView().getBus();
        Bus busBreakerViewBus = busbarSection.getTerminal().getBusBreakerView().getBus();
        Bus otherBusViewBus = nodeBreaker.getVoltageLevel("S1VL1").getBusView().getBuses().iterator().next();
        Bus eurostagBus = network.getVoltageLevel("VLHV1").getBusBreakerView().getBuses().iterator().next();

        assertEquals(Map.of(busViewBus.getId(), 401.0),
                SecurityAnalysisResultsController.busViewVoltages(busResults(new BusResult("S1VL2", busbarSection.getId(), 401, 0)), nodeBreaker));
        assertEquals(Map.of(busViewBus.getId(), 402.0),
                SecurityAnalysisResultsController.busViewVoltages(busResults(new BusResult("S1VL2", busBreakerViewBus.getId(), 402, 0)), nodeBreaker));
        assertEquals(Map.of(otherBusViewBus.getId(), 225.0),
                SecurityAnalysisResultsController.busViewVoltages(busResults(new BusResult("S1VL1", otherBusViewBus.getId(), 225, 0)), nodeBreaker));
        // a bus/breaker topology's configured bus
        assertEquals(Map.of(network.getVoltageLevel("VLHV1").getBusView().getBuses().iterator().next().getId(), 403.0),
                SecurityAnalysisResultsController.busViewVoltages(busResults(new BusResult("VLHV1", eurostagBus.getId(), 403, 0)), network));
        assertEquals(Map.of(), SecurityAnalysisResultsController.busViewVoltages(busResults(new BusResult("S1VL2", "unknown", 400, 0)), nodeBreaker));
    }

    private static NetworkResult busResults(BusResult bus) {
        return new NetworkResult(List.of(), List.of(bus), List.of());
    }

    @Test
    void flowViolationsByEquipmentShowTheWorstContingency() {
        List<SecurityAnalysisResultsController.EquipmentFlowRow> rows = controller.flowByEquipmentTableView.getItems();
        assertEquals(1, rows.size());
        SecurityAnalysisResultsController.EquipmentFlowRow row = rows.getFirst();
        assertEquals(LINE_1, row.equipment().getId());
        assertEquals(2, row.rows().size());
        assertEquals(1, row.contingencyCount());
        assertEquals(contingencyName(LINE_2), row.worstRow().state().name());
    }

    @Test
    void voltageViolationsAreLocatedOnTheirBus() {
        List<SecurityAnalysisResultsController.VoltageRow> rows = controller.voltageTableView.getItems();
        assertTrue(rows.stream().anyMatch(row -> row.state().preContingency()));
        SecurityAnalysisResultsController.VoltageRow preContingency = rows.stream().filter(row -> row.state().preContingency()).findFirst().orElseThrow();
        assertEquals(LimitViolationType.HIGH_VOLTAGE, preContingency.violation().violation().getLimitType());
        assertEquals(network.getVoltageLevel("VLHV1").getBusView().getBuses().iterator().next().getId(), preContingency.violation().busId());
    }

    @Test
    void failedPreContingencyStateIsTheOnlyRow() {
        interact(() -> {
            mainModel.getStudy(network).setSecurityAnalysisResult(new SecurityAnalysisResult(
                    LimitViolationsResult.empty(), LoadFlowResult.ComponentResult.Status.FAILED, List.of()));
            mainModel.setUpdate();
        });

        assertEquals(1, controller.contingenciesTableView.getItems().size());
        assertTrue(controller.contingenciesTableView.getItems().getFirst().state().failed());
        assertTrue(controller.flowByContingencyTableView.getItems().isEmpty());
        assertTrue(controller.flowByEquipmentTableView.getItems().isEmpty());
        assertTrue(controller.voltageTableView.getItems().isEmpty());
    }
}
