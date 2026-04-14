/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Generator;
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
class GeneratorsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private GeneratorsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/generators-view.fxml"), Messages.bundle());
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

    private Generator generator(String id) {
        return network.getGenerator(id);
    }

    private int rowOf(Generator generator) {
        return controller.currentItems.indexOf(generator);
    }

    private Object cellValue(TableColumn<Generator, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private Node cellGraphic(TableColumn<Generator, Generator> column, int row) {
        TableCell<Generator, Generator> cell = (TableCell<Generator, Generator>) column.getCellFactory().call(column);
        cell.updateTableView(controller.generatorsTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    @Test
    void tableContainsAllGeneratorsSortedByName() {
        List<Generator> expected = network.getGeneratorStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.generatorsTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        Generator generator = generator("B1-G");
        VoltageLevel voltageLevel = generator.getTerminal().getVoltageLevel();
        int row = rowOf(generator);

        assertEquals(generator.getNameOrId(), cellValue(controller.nameColumn, row));
        assertEquals(generator.getTargetP(), ((Double) cellValue(controller.targetPColumn, row)).doubleValue());

        Hyperlink voltageLevelLink = (Hyperlink) cellGraphic(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel.getNameOrId(), voltageLevelLink.getText());

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(voltageLevel.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());
    }

    @Test
    void substationColumnIsNullForAGeneratorInAVoltageLevelWithoutSubstation() {
        VoltageLevel voltageLevel = network.newVoltageLevel()
                .setId("VL_NO_SUBSTATION")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        voltageLevel.getBusBreakerView().newBus().setId("BUS_NO_SUBSTATION").add();
        Generator generator = voltageLevel.newGenerator()
                .setId("G_NO_SUBSTATION")
                .setBus("BUS_NO_SUBSTATION")
                .setConnectableBus("BUS_NO_SUBSTATION")
                .setVoltageRegulatorOn(false)
                .setTargetP(50)
                .setTargetQ(0)
                .setMinP(0)
                .setMaxP(100)
                .add();

        interact(() -> mainModel.setUpdate());

        assertNull(cellGraphic(controller.substationColumn, rowOf(generator)));
    }

    @Test
    void togglingConnectedCheckBoxDisconnectsAndReconnectsGenerator() {
        Generator generator = generator("B1-G");
        CheckBox checkBox = (CheckBox) cellGraphic(controller.connectedColumn, rowOf(generator));
        assertTrue(generator.getTerminal().isConnected());

        interact(checkBox::fire);
        assertFalse(generator.getTerminal().isConnected());

        interact(checkBox::fire);
        assertTrue(generator.getTerminal().isConnected());
    }

    @Test
    void targetPEditCommitUpdatesGenerator() {
        Generator generator = generator("B1-G");
        int row = rowOf(generator);

        interact(() -> fireEditCommit(controller.generatorsTableView, controller.targetPColumn, row, 123.0));

        assertEquals(123.0, generator.getTargetP());
    }

    @Test
    void goToGeneratorSelectsAndClearsSelection() {
        Generator generator = generator("B2-G");
        int row = rowOf(generator);

        interact(() -> controller.goToGenerator(generator));
        List<TablePosition> selectedCells = controller.generatorsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToGenerator(null));
        assertTrue(controller.generatorsTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void goToGeneratorSelectsCorrectRowWhenTableIsSorted() {
        Generator generator = generator("B1-G");

        interact(() -> {
            controller.nameColumn.setSortType(TableColumn.SortType.DESCENDING);
            controller.generatorsTableView.getSortOrder().setAll(controller.nameColumn);
        });
        int sortedRow = controller.generatorsTableView.getItems().indexOf(generator);
        assertTrue(sortedRow != rowOf(generator), "test setup should produce a different row than the unsorted order");

        interact(() -> controller.goToGenerator(generator));

        List<TablePosition> selectedCells = controller.generatorsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(sortedRow, selectedCells.get(0).getRow());
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        Generator generator = generator("B3-G");
        VoltageLevel voltageLevel = generator.getTerminal().getVoltageLevel();
        Hyperlink link = (Hyperlink) cellGraphic(controller.voltageLevelColumn, rowOf(generator));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        Generator generator = generator("B3-G");
        Substation substation = generator.getTerminal().getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(generator));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
