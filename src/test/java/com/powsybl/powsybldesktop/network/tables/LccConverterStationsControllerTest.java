/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
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
class LccConverterStationsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = HvdcTestNetwork.createLcc();

    private LccConverterStationsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/lcc-converter-stations-view.fxml"), Messages.bundle());
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

    private LccConverterStation station(String id) {
        return network.getLccConverterStation(id);
    }

    private int rowOf(LccConverterStation station) {
        return controller.currentItems.indexOf(station);
    }

    private Object cellValue(TableColumn<LccConverterStation, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<LccConverterStation, T> column, int row) {
        TableCell<LccConverterStation, T> cell = (TableCell<LccConverterStation, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.lccConverterStationsTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        TableRow<LccConverterStation> tableRow = new TableRow<>();
        tableRow.updateTableView(controller.lccConverterStationsTableView);
        tableRow.updateIndex(row);
        cell.updateTableRow(tableRow);
        return cell.getGraphic();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    @Test
    void tableContainsAllLccConverterStationsSortedByName() {
        List<LccConverterStation> expected = network.getLccConverterStationStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(2, expected.size());
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.lccConverterStationsTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        LccConverterStation station = station("C2");
        int row = rowOf(station);

        assertEquals("Converter2", cellValue(controller.nameColumn, row));
        assertEquals(1.1, (Double) cellValue(controller.lossFactorColumn, row), 1e-6);
        assertEquals(0.6, (Double) cellValue(controller.powerFactorColumn, row), 1e-6);
        assertEquals(75.0, ((Double) cellValue(controller.pColumn, row)).doubleValue());
        assertEquals(25.0, ((Double) cellValue(controller.qColumn, row)).doubleValue());

        Hyperlink hvdcLineLink = (Hyperlink) cellGraphic(controller.hvdcLineColumn, row);
        assertEquals("HVDC", hvdcLineLink.getText());
    }

    @Test
    void editCommitsUpdateStation() {
        LccConverterStation station = station("C1");
        int row = rowOf(station);

        interact(() -> fireEditCommit(controller.lccConverterStationsTableView, controller.lossFactorColumn, row, 2.5));
        assertEquals(2.5f, station.getLossFactor());

        interact(() -> fireEditCommit(controller.lccConverterStationsTableView, controller.powerFactorColumn, row, 0.75));
        assertEquals(0.75f, station.getPowerFactor());
    }

    @Test
    void togglingConnectedCheckBoxDisconnectsAndReconnectsStation() {
        LccConverterStation station = station("C1");
        CheckBox checkBox = (CheckBox) cellGraphic(controller.connectedColumn, rowOf(station));
        assertTrue(station.getTerminal().isConnected());

        interact(checkBox::fire);
        assertFalse(station.getTerminal().isConnected());

        interact(checkBox::fire);
        assertTrue(station.getTerminal().isConnected());
    }

    @Test
    void goToLccConverterStationSelectsAndClearsSelection() {
        LccConverterStation station = station("C2");
        int row = rowOf(station);

        interact(() -> controller.goToLccConverterStation(station));
        List<TablePosition> selectedCells = controller.lccConverterStationsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.getFirst().getRow());

        interact(() -> controller.goToLccConverterStation(null));
        assertTrue(controller.lccConverterStationsTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        LccConverterStation station = station("C1");
        Substation substation = station.getTerminal().getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(station));

        interact(link::fire);

        NavigationEvent event = mainModel.getNavigationHistory().currentEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingHvdcLineLinkNavigatesToHvdcLinesTable() {
        LccConverterStation station = station("C1");
        Hyperlink link = (Hyperlink) cellGraphic(controller.hvdcLineColumn, rowOf(station));

        interact(link::fire);

        NavigationEvent event = mainModel.getNavigationHistory().currentEventProperty().getValue();
        assertEquals(NavigationType.NETWORK_TABLE_HVDC_LINES, event.navigationType());
        assertEquals(network.getHvdcLine("L"), ((HvdcLineNavigationState) event.state()).getHvdcLine());
    }
}
