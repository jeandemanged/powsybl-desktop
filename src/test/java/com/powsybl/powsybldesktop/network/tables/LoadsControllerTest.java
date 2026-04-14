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
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
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
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LoadsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private LoadsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/loads-view.fxml"), Messages.bundle());
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

    private Load load(String id) {
        return network.getLoad(id);
    }

    private int rowOf(Load load) {
        return controller.currentItems.indexOf(load);
    }

    private Object cellValue(TableColumn<Load, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private Node cellGraphic(TableColumn<Load, Load> column, int row) {
        TableCell<Load, Load> cell = (TableCell<Load, Load>) column.getCellFactory().call(column);
        cell.updateTableView(controller.loadsTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    @Test
    void tableContainsAllLoadsSortedByName() {
        List<Load> expected = network.getLoadStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.loadsTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        Load load = load("B2-L");
        VoltageLevel voltageLevel = load.getTerminal().getVoltageLevel();
        int row = rowOf(load);

        assertEquals(load.getNameOrId(), cellValue(controller.nameColumn, row));
        assertEquals(load.getP0(), ((Double) cellValue(controller.p0Column, row)).doubleValue());
        assertEquals(load.getQ0(), ((Double) cellValue(controller.q0Column, row)).doubleValue());

        Hyperlink voltageLevelLink = (Hyperlink) cellGraphic(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel.getNameOrId(), voltageLevelLink.getText());

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(voltageLevel.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());
    }

    @Test
    void substationColumnIsNullForALoadInAVoltageLevelWithoutSubstation() {
        VoltageLevel voltageLevel = network.newVoltageLevel()
                .setId("VL_NO_SUBSTATION")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        voltageLevel.getBusBreakerView().newBus().setId("BUS_NO_SUBSTATION").add();
        Load load = voltageLevel.newLoad()
                .setId("L_NO_SUBSTATION")
                .setBus("BUS_NO_SUBSTATION")
                .setConnectableBus("BUS_NO_SUBSTATION")
                .setP0(10)
                .setQ0(5)
                .add();

        interact(() -> mainModel.setUpdate());

        assertNull(cellGraphic(controller.substationColumn, rowOf(load)));
    }

    @Test
    void togglingConnectedCheckBoxDisconnectsAndReconnectsLoad() {
        Load load = load("B2-L");
        CheckBox checkBox = (CheckBox) cellGraphic(controller.connectedColumn, rowOf(load));
        assertTrue(load.getTerminal().isConnected());

        interact(checkBox::fire);
        assertFalse(load.getTerminal().isConnected());

        interact(checkBox::fire);
        assertTrue(load.getTerminal().isConnected());
    }

    @Test
    void p0AndQ0EditCommitUpdateLoad() {
        Load load = load("B2-L");
        int row = rowOf(load);

        interact(() -> fireEditCommit(controller.loadsTableView, controller.p0Column, row, 123.0));
        assertEquals(123.0, load.getP0());

        interact(() -> fireEditCommit(controller.loadsTableView, controller.q0Column, row, 45.0));
        assertEquals(45.0, load.getQ0());
    }

    @Test
    void goToLoadSelectsAndClearsSelection() {
        Load load = load("B3-L");
        int row = rowOf(load);

        interact(() -> controller.goToLoad(load));
        List<TablePosition> selectedCells = controller.loadsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToLoad(null));
        assertTrue(controller.loadsTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void goToLoadSelectsCorrectRowWhenTableIsSorted() {
        Load load = load("B3-L");

        interact(() -> {
            controller.nameColumn.setSortType(TableColumn.SortType.DESCENDING);
            controller.loadsTableView.getSortOrder().setAll(controller.nameColumn);
        });
        int sortedRow = controller.loadsTableView.getItems().indexOf(load);
        assertTrue(sortedRow != rowOf(load), "test setup should produce a different row than the unsorted order");

        interact(() -> controller.goToLoad(load));

        List<TablePosition> selectedCells = controller.loadsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(sortedRow, selectedCells.get(0).getRow());
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        Load load = load("B4-L");
        VoltageLevel voltageLevel = load.getTerminal().getVoltageLevel();
        Hyperlink link = (Hyperlink) cellGraphic(controller.voltageLevelColumn, rowOf(load));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        Load load = load("B4-L");
        Substation substation = load.getTerminal().getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(load));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
