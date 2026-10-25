/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.test.BatteryNetworkFactory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
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
class BatteriesControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = BatteryNetworkFactory.create();

    private BatteriesController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/batteries-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        controller.setSceneModel(SceneModel.main(mainModel));

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private Battery battery(String id) {
        return network.getBattery(id);
    }

    private int rowOf(Battery battery) {
        return controller.currentItems.indexOf(battery);
    }

    private Object cellValue(TableColumn<Battery, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<Battery, T> column, int row) {
        TableCell<Battery, T> cell = (TableCell<Battery, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.batteriesTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        TableRow<Battery> tableRow = new TableRow<>();
        tableRow.updateTableView(controller.batteriesTableView);
        tableRow.updateIndex(row);
        cell.updateTableRow(tableRow);
        return cell.getGraphic();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    @Test
    void tableContainsAllBatteriesSortedByName() {
        List<Battery> expected = network.getBatteryStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(2, expected.size());
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.batteriesTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        Battery battery = battery("BAT2");
        VoltageLevel voltageLevel = battery.getTerminal().getVoltageLevel();
        int row = rowOf(battery);

        assertEquals(battery.getNameOrId(), cellValue(controller.nameColumn, row));
        assertEquals(100.0, ((Double) cellValue(controller.targetPColumn, row)).doubleValue());
        assertEquals(200.0, ((Double) cellValue(controller.targetQColumn, row)).doubleValue());
        assertEquals(-200.0, ((Double) cellValue(controller.minPColumn, row)).doubleValue());
        assertEquals(200.0, ((Double) cellValue(controller.maxPColumn, row)).doubleValue());

        Hyperlink voltageLevelLink = (Hyperlink) cellGraphic(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel.getNameOrId(), voltageLevelLink.getText());

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(voltageLevel.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());
    }

    @Test
    void togglingConnectedCheckBoxDisconnectsAndReconnectsBattery() {
        Battery battery = battery("BAT");
        CheckBox checkBox = (CheckBox) cellGraphic(controller.connectedColumn, rowOf(battery));
        assertTrue(battery.getTerminal().isConnected());

        interact(checkBox::fire);
        assertFalse(battery.getTerminal().isConnected());

        interact(checkBox::fire);
        assertTrue(battery.getTerminal().isConnected());
    }

    @Test
    void activePowerEditCommitsUpdateBattery() {
        Battery battery = battery("BAT2");
        int row = rowOf(battery);

        interact(() -> fireEditCommit(controller.batteriesTableView, controller.targetPColumn, row, 50.0));
        assertEquals(50.0, battery.getTargetP());

        interact(() -> fireEditCommit(controller.batteriesTableView, controller.minPColumn, row, -150.0));
        assertEquals(-150.0, battery.getMinP());

        interact(() -> fireEditCommit(controller.batteriesTableView, controller.maxPColumn, row, 150.0));
        assertEquals(150.0, battery.getMaxP());

        interact(() -> fireEditCommit(controller.batteriesTableView, controller.targetQColumn, row, 20.0));
        assertEquals(20.0, battery.getLocalTargetQ());
    }

    @Test
    void regulatingEditCreatesVoltageRegulation() {
        Battery battery = battery("BAT2");
        // regulating needs a voltage target
        battery.setLocalTargetV(400);
        CheckBox checkBox = (CheckBox) cellGraphic(controller.regulatingColumn, rowOf(battery));
        assertFalse(battery.isRegulating());

        interact(checkBox::fire);

        assertTrue(battery.isRegulating());
        assertEquals(RegulationMode.VOLTAGE, battery.getVoltageRegulation().getMode());
    }

    @Test
    void goToBatterySelectsAndClearsSelection() {
        Battery battery = battery("BAT2");
        int row = rowOf(battery);

        interact(() -> controller.goToBattery(battery));
        List<TablePosition> selectedCells = controller.batteriesTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToBattery(null));
        assertTrue(controller.batteriesTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        Battery battery = battery("BAT");
        Substation substation = battery.getTerminal().getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(battery));

        interact(link::fire);

        NavigationEvent event = mainModel.getNavigationHistory().currentEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
