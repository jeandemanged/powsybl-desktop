/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class StaticVarCompensatorsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private StaticVarCompensatorsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        VoltageLevel voltageLevel1 = network.getVoltageLevel("VL1");
        voltageLevel1.newStaticVarCompensator()
                .setId("B1-SVC")
                .setBus("B1")
                .setConnectableBus("B1")
                .setBmin(-0.01)
                .setBmax(0.01)
                .setRegulating(true)
                .setRegulationMode(StaticVarCompensator.RegulationMode.VOLTAGE)
                .setVoltageSetpoint(110)
                .setReactivePowerSetpoint(5)
                .add();

        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/static-var-compensators-view.fxml"), Messages.bundle());
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

    private StaticVarCompensator staticVarCompensator(String id) {
        return network.getStaticVarCompensator(id);
    }

    private int rowOf(StaticVarCompensator staticVarCompensator) {
        return controller.currentItems.indexOf(staticVarCompensator);
    }

    private Object cellValue(TableColumn<StaticVarCompensator, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<StaticVarCompensator, T> column, int row) {
        TableCell<StaticVarCompensator, T> cell = (TableCell<StaticVarCompensator, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.staticVarCompensatorsTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        // Cells rendered directly (not through the skin) have no TableRow by default; wire one up
        // so getTableRow().getItem() resolves for cell factories that rely on it (e.g. checkbox columns).
        TableRow<StaticVarCompensator> tableRow = new TableRow<>();
        tableRow.updateTableView(controller.staticVarCompensatorsTableView);
        tableRow.updateIndex(row);
        cell.updateTableRow(tableRow);
        return cell.getGraphic();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    @Test
    void tableContainsAllStaticVarCompensatorsSortedByName() {
        List<StaticVarCompensator> expected = network.getStaticVarCompensatorStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.staticVarCompensatorsTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        StaticVarCompensator staticVarCompensator = staticVarCompensator("B1-SVC");
        VoltageLevel voltageLevel = staticVarCompensator.getTerminal().getVoltageLevel();
        int row = rowOf(staticVarCompensator);

        assertEquals(staticVarCompensator.getNameOrId(), cellValue(controller.nameColumn, row));
        assertEquals(StaticVarCompensator.RegulationMode.VOLTAGE, cellValue(controller.regulationModeColumn, row));
        assertEquals(staticVarCompensator.getVoltageSetpoint(), ((Double) cellValue(controller.targetVColumn, row)).doubleValue());
        assertEquals(staticVarCompensator.getReactivePowerSetpoint(), ((Double) cellValue(controller.targetQColumn, row)).doubleValue());

        Hyperlink voltageLevelLink = (Hyperlink) cellGraphic(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel.getNameOrId(), voltageLevelLink.getText());

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(voltageLevel.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());
    }

    @Test
    void togglingConnectedCheckBoxDisconnectsAndReconnectsStaticVarCompensator() {
        StaticVarCompensator staticVarCompensator = staticVarCompensator("B1-SVC");
        CheckBox checkBox = (CheckBox) cellGraphic(controller.connectedColumn, rowOf(staticVarCompensator));
        assertTrue(staticVarCompensator.getTerminal().isConnected());

        interact(checkBox::fire);
        assertFalse(staticVarCompensator.getTerminal().isConnected());

        interact(checkBox::fire);
        assertTrue(staticVarCompensator.getTerminal().isConnected());
    }

    @Test
    void regulatingEditUpdatesStaticVarCompensator() {
        StaticVarCompensator staticVarCompensator = staticVarCompensator("B1-SVC");
        CheckBox checkBox = (CheckBox) cellGraphic(controller.regulatingColumn, rowOf(staticVarCompensator));
        assertTrue(staticVarCompensator.isRegulating());

        interact(checkBox::fire);

        assertFalse(staticVarCompensator.isRegulating());
    }

    @Test
    void regulationModeEditCommitUpdatesStaticVarCompensator() {
        StaticVarCompensator staticVarCompensator = staticVarCompensator("B1-SVC");
        int row = rowOf(staticVarCompensator);

        interact(() -> fireEditCommit(controller.staticVarCompensatorsTableView, controller.regulationModeColumn, row,
                StaticVarCompensator.RegulationMode.REACTIVE_POWER));

        assertEquals(StaticVarCompensator.RegulationMode.REACTIVE_POWER, staticVarCompensator.getRegulationMode());
    }

    @Test
    void targetVAndTargetQEditCommitUpdateStaticVarCompensator() {
        StaticVarCompensator staticVarCompensator = staticVarCompensator("B1-SVC");
        int row = rowOf(staticVarCompensator);

        interact(() -> fireEditCommit(controller.staticVarCompensatorsTableView, controller.targetVColumn, row, 120.0));
        assertEquals(120.0, staticVarCompensator.getVoltageSetpoint());

        interact(() -> fireEditCommit(controller.staticVarCompensatorsTableView, controller.targetQColumn, row, 8.0));
        assertEquals(8.0, staticVarCompensator.getReactivePowerSetpoint());
    }

    @Test
    void goToStaticVarCompensatorSelectsAndClearsSelection() {
        StaticVarCompensator staticVarCompensator = staticVarCompensator("B1-SVC");
        int row = rowOf(staticVarCompensator);

        interact(() -> controller.goToStaticVarCompensator(staticVarCompensator));
        List<TablePosition> selectedCells = controller.staticVarCompensatorsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToStaticVarCompensator(null));
        assertTrue(controller.staticVarCompensatorsTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        StaticVarCompensator staticVarCompensator = staticVarCompensator("B1-SVC");
        VoltageLevel voltageLevel = staticVarCompensator.getTerminal().getVoltageLevel();
        Hyperlink link = (Hyperlink) cellGraphic(controller.voltageLevelColumn, rowOf(staticVarCompensator));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        StaticVarCompensator staticVarCompensator = staticVarCompensator("B1-SVC");
        Substation substation = staticVarCompensator.getTerminal().getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(staticVarCompensator));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
