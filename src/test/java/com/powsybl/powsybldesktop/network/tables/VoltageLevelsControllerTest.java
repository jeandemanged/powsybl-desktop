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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class VoltageLevelsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private VoltageLevelsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/voltage-levels-view.fxml"), Messages.bundle());
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

    private VoltageLevel voltageLevel(String id) {
        return network.getVoltageLevel(id);
    }

    private int rowOf(VoltageLevel voltageLevel) {
        return controller.currentVoltageLevels.indexOf(voltageLevel);
    }

    private Object cellValue(TableColumn<VoltageLevel, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private Node cellGraphic(TableColumn<VoltageLevel, VoltageLevel> column, int row) {
        TableCell<VoltageLevel, VoltageLevel> cell = (TableCell<VoltageLevel, VoltageLevel>) column.getCellFactory().call(column);
        cell.updateTableView(controller.voltageLevelsTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    @Test
    void tableContainsAllVoltageLevelsSortedByName() {
        List<VoltageLevel> expected = network.getVoltageLevelStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentVoltageLevels);
        assertEquals(expected.size(), controller.voltageLevelsTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        VoltageLevel voltageLevel = voltageLevel("VL1");
        Substation substation = voltageLevel.getSubstation().orElseThrow();
        int row = rowOf(voltageLevel);

        Hyperlink nameLink = (Hyperlink) cellGraphic(controller.nameColumn, row);
        assertEquals(voltageLevel.getNameOrId(), nameLink.getText());

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(substation.getNameOrId(), substationLink.getText());

        assertEquals(substation.getNullableCountry(), cellValue(controller.countryColumn, row));
        assertEquals(voltageLevel.getTopologyKind(), cellValue(controller.topologyKindColumn, row));
        assertEquals(voltageLevel.getNominalV(), ((Double) cellValue(controller.nominalVoltageColumn, row)).doubleValue());
        assertEquals(voltageLevel.getLowVoltageLimit(), ((Double) cellValue(controller.lowVoltageLimitColumn, row)).doubleValue());
        assertEquals(voltageLevel.getHighVoltageLimit(), ((Double) cellValue(controller.highVoltageLimitColumn, row)).doubleValue());
    }

    @Test
    void substationColumnIsNullForAVoltageLevelWithoutSubstation() {
        VoltageLevel voltageLevel = network.newVoltageLevel()
                .setId("VL_NO_SUBSTATION")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();

        interact(() -> mainModel.setUpdate());

        assertNull(cellGraphic(controller.substationColumn, rowOf(voltageLevel)));
        assertNull(cellValue(controller.countryColumn, rowOf(voltageLevel)));
    }

    @Test
    void nominalVoltageEditCommitUpdatesVoltageLevel() {
        VoltageLevel voltageLevel = voltageLevel("VL1");
        int row = rowOf(voltageLevel);

        interact(() -> fireEditCommit(controller.voltageLevelsTableView, controller.nominalVoltageColumn, row, 400.0));

        assertEquals(400.0, voltageLevel.getNominalV());
    }

    @Test
    void lowVoltageLimitEditCommitUpdatesVoltageLevel() {
        VoltageLevel voltageLevel = voltageLevel("VL1");
        int row = rowOf(voltageLevel);

        interact(() -> fireEditCommit(controller.voltageLevelsTableView, controller.lowVoltageLimitColumn, row, 350.0));

        assertEquals(350.0, voltageLevel.getLowVoltageLimit());
    }

    @Test
    void highVoltageLimitEditCommitUpdatesVoltageLevel() {
        VoltageLevel voltageLevel = voltageLevel("VL1");
        int row = rowOf(voltageLevel);

        interact(() -> fireEditCommit(controller.voltageLevelsTableView, controller.highVoltageLimitColumn, row, 450.0));

        assertEquals(450.0, voltageLevel.getHighVoltageLimit());
    }

    @Test
    void goToVoltageLevelSelectsAndClearsSelection() {
        VoltageLevel voltageLevel = voltageLevel("VL2");
        int row = rowOf(voltageLevel);

        interact(() -> controller.goToVoltageLevel(voltageLevel));
        List<TablePosition> selectedCells = controller.voltageLevelsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToVoltageLevel(null));
        assertTrue(controller.voltageLevelsTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void clickingNameLinkNavigatesToSubstationsView() {
        VoltageLevel voltageLevel = voltageLevel("VL3");
        Hyperlink link = (Hyperlink) cellGraphic(controller.nameColumn, rowOf(voltageLevel));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        VoltageLevel voltageLevel = voltageLevel("VL3");
        Substation substation = voltageLevel.getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(voltageLevel));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
