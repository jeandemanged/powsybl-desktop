/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.HvdcLineNavigationState;
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
class VscConverterStationsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = HvdcTestNetwork.createVsc();

    private VscConverterStationsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/vsc-converter-stations-view.fxml"), Messages.bundle());
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

    private VscConverterStation station(String id) {
        return network.getVscConverterStation(id);
    }

    private int rowOf(VscConverterStation station) {
        return controller.currentItems.indexOf(station);
    }

    private Object cellValue(TableColumn<VscConverterStation, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<VscConverterStation, T> column, int row) {
        TableCell<VscConverterStation, T> cell = (TableCell<VscConverterStation, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.vscConverterStationsTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        TableRow<VscConverterStation> tableRow = new TableRow<>();
        tableRow.updateTableView(controller.vscConverterStationsTableView);
        tableRow.updateIndex(row);
        cell.updateTableRow(tableRow);
        return cell.getGraphic();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    @Test
    void tableContainsAllVscConverterStationsSortedByName() {
        List<VscConverterStation> expected = network.getVscConverterStationStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(2, expected.size());
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.vscConverterStationsTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        VscConverterStation station = station("C1");
        VoltageLevel voltageLevel = station.getTerminal().getVoltageLevel();
        int row = rowOf(station);

        assertEquals("Converter1", cellValue(controller.nameColumn, row));
        assertEquals(1.1, (Double) cellValue(controller.lossFactorColumn, row), 1e-6);
        assertEquals(RegulationMode.VOLTAGE, cellValue(controller.regulationModeColumn, row));
        assertEquals(405.0, ((Double) cellValue(controller.targetVColumn, row)).doubleValue());
        assertEquals(100.0, ((Double) cellValue(controller.pColumn, row)).doubleValue());

        Hyperlink voltageLevelLink = (Hyperlink) cellGraphic(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel.getNameOrId(), voltageLevelLink.getText());

        Hyperlink hvdcLineLink = (Hyperlink) cellGraphic(controller.hvdcLineColumn, row);
        assertEquals("HVDC", hvdcLineLink.getText());
    }

    @Test
    void editCommitsUpdateStation() {
        VscConverterStation station = station("C1");
        int row = rowOf(station);

        interact(() -> fireEditCommit(controller.vscConverterStationsTableView, controller.lossFactorColumn, row, 2.5));
        assertEquals(2.5f, station.getLossFactor());

        interact(() -> fireEditCommit(controller.vscConverterStationsTableView, controller.targetVColumn, row, 410.0));
        assertEquals(410.0, station.getRegulatingTargetV());

        interact(() -> fireEditCommit(controller.vscConverterStationsTableView, controller.targetQColumn, row, 12.0));
        assertEquals(12.0, station.getLocalTargetQ());

        // a regulating station without a remote terminal only accepts the voltage mode
        station.getVoltageRegulation().setRegulating(false);
        interact(() -> fireEditCommit(controller.vscConverterStationsTableView, controller.regulationModeColumn, row,
                RegulationMode.REACTIVE_POWER));
        assertEquals(RegulationMode.REACTIVE_POWER, station.getVoltageRegulation().getMode());
    }

    @Test
    void regulatingEditTogglesRegulation() {
        VscConverterStation station = station("C1");
        // not regulating needs a reactive power target
        station.setLocalTargetQ(0);
        CheckBox checkBox = (CheckBox) cellGraphic(controller.regulatingColumn, rowOf(station));
        assertTrue(station.isRegulating());

        interact(checkBox::fire);
        assertFalse(station.isRegulating());

        interact(checkBox::fire);
        assertTrue(station.isRegulating());
    }

    @Test
    void goToVscConverterStationSelectsAndClearsSelection() {
        VscConverterStation station = station("C2");
        int row = rowOf(station);

        interact(() -> controller.goToVscConverterStation(station));
        List<TablePosition> selectedCells = controller.vscConverterStationsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToVscConverterStation(null));
        assertTrue(controller.vscConverterStationsTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        VscConverterStation station = station("C1");
        VoltageLevel voltageLevel = station.getTerminal().getVoltageLevel();
        Hyperlink link = (Hyperlink) cellGraphic(controller.voltageLevelColumn, rowOf(station));

        interact(link::fire);

        NavigationEvent event = mainModel.getNavigationHistory().currentEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingHvdcLineLinkNavigatesToHvdcLinesTable() {
        VscConverterStation station = station("C1");
        Hyperlink link = (Hyperlink) cellGraphic(controller.hvdcLineColumn, rowOf(station));

        interact(link::fire);

        NavigationEvent event = mainModel.getNavigationHistory().currentEventProperty().getValue();
        assertEquals(NavigationType.NETWORK_TABLE_HVDC_LINES, event.navigationType());
        assertEquals(network.getHvdcLine("L"), ((HvdcLineNavigationState) event.state()).getHvdcLine());
    }
}
