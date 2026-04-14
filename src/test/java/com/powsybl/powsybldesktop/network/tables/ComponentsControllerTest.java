/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ComponentsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private ComponentsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/components-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        controller.setMainModel(mainModel);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private Object cellValue(TableColumn<ComponentsController.ComponentRow, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private Node graphicOf(TableColumn<ComponentsController.ComponentRow, ComponentsController.ComponentRow> column, int row) {
        TableCell<ComponentsController.ComponentRow, ComponentsController.ComponentRow> cell =
                (TableCell<ComponentsController.ComponentRow, ComponentsController.ComponentRow>) column.getCellFactory().call(column);
        cell.updateTableView(controller.componentsTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    private Node statusGraphic(int row) {
        return graphicOf(controller.statusColumn, row);
    }

    private Region statusMarker(int row) {
        return (Region) ((HBox) statusGraphic(row)).getChildren().get(0);
    }

    private Label statusLabel(int row) {
        return (Label) ((HBox) statusGraphic(row)).getChildren().get(1);
    }

    private int rowOf(int connectedComponentNum, int synchronousComponentNum) {
        return controller.currentComponents.indexOf(controller.currentComponents.stream()
                .filter(row -> row.key().connectedComponentNum() == connectedComponentNum
                        && row.key().synchronousComponentNum() == synchronousComponentNum)
                .findFirst().orElseThrow());
    }

    @Test
    void tableContainsOneRowForTheFullyConnectedNetworkWithNoLoadFlowResult() {
        assertEquals(1, controller.currentComponents.size());
        ComponentsController.ComponentRow row = controller.currentComponents.get(0);
        assertEquals(0, row.key().connectedComponentNum());
        assertEquals(0, row.key().synchronousComponentNum());
        assertEquals((int) network.getBusView().getBusStream().count(), row.busCount());

        assertEquals(0, cellValue(controller.connectedComponentColumn, 0));
        assertEquals(0, cellValue(controller.synchronousComponentColumn, 0));
        assertEquals(row.busCount(), cellValue(controller.busCountColumn, 0));
        assertNull(cellValue(controller.statusTextColumn, 0));
        assertNull(cellValue(controller.iterationCountColumn, 0));
        assertNull(graphicOf(controller.referenceBusIdColumn, 0));
        assertNull(graphicOf(controller.slackBusesColumn, 0));
        assertNull(cellValue(controller.distributedActivePowerColumn, 0));
        assertNull(statusGraphic(0));
    }

    // a bus with no equipment at all is excluded from the network's BusView entirely, so it needs
    // at least one connectable (a load here) to show up as its own component row
    private VoltageLevel createIsolatedVoltageLevel() {
        VoltageLevel isolatedVoltageLevel = network.newVoltageLevel()
                .setId("VL_ISOLATED")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        isolatedVoltageLevel.getBusBreakerView().newBus().setId("BUS_ISOLATED").add();
        isolatedVoltageLevel.newLoad()
                .setId("L_ISOLATED")
                .setBus("BUS_ISOLATED")
                .setConnectableBus("BUS_ISOLATED")
                .setP0(1)
                .setQ0(0)
                .add();
        return isolatedVoltageLevel;
    }

    @Test
    void isolatedBusFormsItsOwnComponentRow() {
        VoltageLevel isolatedVoltageLevel = createIsolatedVoltageLevel();

        interact(() -> mainModel.setUpdate());

        assertEquals(2, controller.currentComponents.size());
        Bus isolatedBus = isolatedVoltageLevel.getBusView().getBusStream().findFirst().orElseThrow();
        int row = rowOf(isolatedBus.getConnectedComponent().getNum(), isolatedBus.getSynchronousComponent().getNum());
        assertEquals(1, controller.currentComponents.get(row).busCount());
    }

    @Test
    void componentResultsAreMatchedByComponentNumbersAndColorCodedByStatus() {
        VoltageLevel isolatedVoltageLevel = createIsolatedVoltageLevel();
        interact(() -> mainModel.setUpdate());

        Bus mainBus = network.getVoltageLevel("VL1").getBusView().getBusStream().findFirst().orElseThrow();
        Bus isolatedBus = isolatedVoltageLevel.getBusView().getBusStream().findFirst().orElseThrow();
        int mainCc = mainBus.getConnectedComponent().getNum();
        int mainSc = mainBus.getSynchronousComponent().getNum();
        int isolatedCc = isolatedBus.getConnectedComponent().getNum();
        int isolatedSc = isolatedBus.getSynchronousComponent().getNum();

        LoadFlowResult.ComponentResult mainResult = new LoadFlowResultImpl.ComponentResultImpl(
                mainCc, mainSc, LoadFlowResult.ComponentResult.Status.CONVERGED, "Converged", Map.of(),
                5, mainBus.getId(), List.of(new LoadFlowResultImpl.SlackBusResultImpl(mainBus.getId(), 0.001)), 2.5);
        // an unresolved reference bus id (not a bus of this network) must render as plain text, no navigation
        LoadFlowResult.ComponentResult isolatedResult = new LoadFlowResultImpl.ComponentResultImpl(
                isolatedCc, isolatedSc, LoadFlowResult.ComponentResult.Status.NO_CALCULATION, "Not computed", Map.of(),
                0, "UNKNOWN_BUS", List.of(new LoadFlowResultImpl.SlackBusResultImpl(isolatedBus.getId(), 0.0)), 0.0);
        mainModel.setLoadFlowResult(network, new LoadFlowResultImpl(true, Map.of(), "", List.of(mainResult, isolatedResult)));
        interact(() -> mainModel.setUpdate());

        int mainRow = rowOf(mainCc, mainSc);
        assertEquals("Converged", cellValue(controller.statusTextColumn, mainRow));
        assertEquals(5, cellValue(controller.iterationCountColumn, mainRow));
        assertEquals(2.5, cellValue(controller.distributedActivePowerColumn, mainRow));

        assertEquals("component-status-converged", statusMarker(mainRow).getStyleClass().stream()
                .filter(styleClass -> styleClass.startsWith("component-status-")).findFirst().orElseThrow());
        assertEquals(Messages.get("components.status.converged"), statusLabel(mainRow).getText());

        Hyperlink referenceBusLink = assertInstanceOf(Hyperlink.class, graphicOf(controller.referenceBusIdColumn, mainRow));
        assertEquals(mainBus.getId(), referenceBusLink.getText());
        interact(referenceBusLink::fire);
        NavigationEvent navigationEvent = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, navigationEvent.navigationType());
        assertEquals(mainBus.getVoltageLevel(), ((ContainerNavigationState) navigationEvent.state()).getContainer());

        VBox slackBusesBox = assertInstanceOf(VBox.class, graphicOf(controller.slackBusesColumn, mainRow));
        assertEquals(1, slackBusesBox.getChildren().size());
        HBox slackBusLine = (HBox) slackBusesBox.getChildren().get(0);
        Hyperlink slackBusLink = assertInstanceOf(Hyperlink.class, slackBusLine.getChildren().get(0));
        assertEquals(mainBus.getId(), slackBusLink.getText());
        assertEquals(": 0.001", ((Label) slackBusLine.getChildren().get(1)).getText());

        int isolatedRow = rowOf(isolatedCc, isolatedSc);
        assertEquals("Not computed", cellValue(controller.statusTextColumn, isolatedRow));
        assertEquals("component-status-no-calculation", statusMarker(isolatedRow).getStyleClass().stream()
                .filter(styleClass -> styleClass.startsWith("component-status-")).findFirst().orElseThrow());
        assertEquals(Messages.get("components.status.noCalculation"), statusLabel(isolatedRow).getText());

        Label unresolvedReferenceBus = assertInstanceOf(Label.class, graphicOf(controller.referenceBusIdColumn, isolatedRow));
        assertEquals("UNKNOWN_BUS", unresolvedReferenceBus.getText());
        assertFalse(unresolvedReferenceBus.getStyleClass().contains("container-link"));
    }

    @Test
    void failedAndMaxIterationReachedAreBothRedButDistinguishedByText() {
        VoltageLevel isolatedVoltageLevel = createIsolatedVoltageLevel();
        interact(() -> mainModel.setUpdate());

        Bus mainBus = network.getVoltageLevel("VL1").getBusView().getBusStream().findFirst().orElseThrow();
        Bus isolatedBus = isolatedVoltageLevel.getBusView().getBusStream().findFirst().orElseThrow();

        LoadFlowResult.ComponentResult failedResult = new LoadFlowResultImpl.ComponentResultImpl(
                mainBus.getConnectedComponent().getNum(), mainBus.getSynchronousComponent().getNum(),
                LoadFlowResult.ComponentResult.Status.FAILED, "Failed", Map.of(), 0, null, List.of(), 0.0);
        LoadFlowResult.ComponentResult maxIterationResult = new LoadFlowResultImpl.ComponentResultImpl(
                isolatedBus.getConnectedComponent().getNum(), isolatedBus.getSynchronousComponent().getNum(),
                LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, "Max iteration reached", Map.of(), 15, null, List.of(), 0.0);
        mainModel.setLoadFlowResult(network, new LoadFlowResultImpl(false, Map.of(), "", List.of(failedResult, maxIterationResult)));
        interact(() -> mainModel.setUpdate());

        int failedRow = rowOf(mainBus.getConnectedComponent().getNum(), mainBus.getSynchronousComponent().getNum());
        int maxIterationRow = rowOf(isolatedBus.getConnectedComponent().getNum(), isolatedBus.getSynchronousComponent().getNum());

        assertTrue(statusMarker(failedRow).getStyleClass().contains("component-status-other"));
        assertTrue(statusMarker(maxIterationRow).getStyleClass().contains("component-status-other"));
        assertEquals(Messages.get("components.status.failed"), statusLabel(failedRow).getText());
        assertEquals(Messages.get("components.status.maxIterationReached"), statusLabel(maxIterationRow).getText());
    }
}
