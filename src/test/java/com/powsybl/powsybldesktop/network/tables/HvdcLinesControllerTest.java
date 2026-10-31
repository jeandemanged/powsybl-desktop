/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.VscConverterStationNavigationState;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class HvdcLinesControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = HvdcTestNetwork.createVsc();

    private HvdcLinesController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/hvdc-lines-view.fxml"), Messages.bundle());
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

    private HvdcLine hvdcLine() {
        return network.getHvdcLine("L");
    }

    private int rowOf(HvdcLine hvdcLine) {
        return controller.currentItems.indexOf(hvdcLine);
    }

    private Object cellValue(TableColumn<HvdcLine, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<HvdcLine, T> column, int row) {
        TableCell<HvdcLine, T> cell = (TableCell<HvdcLine, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.hvdcLinesTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    private VBox cellBox(TableColumn<HvdcLine, HvdcLine> column, int row) {
        return (VBox) cellGraphic(column, row);
    }

    // single values rendered as a one-slot multi-sided column: a StackPane slot holding the field (see CellFields.sidedColumn)
    private static TextField fieldOf(VBox box, int index) {
        return (TextField) ((StackPane) box.getChildren().get(index)).getChildren().getFirst();
    }

    @SuppressWarnings("unchecked")
    private static ChoiceBox<HvdcLine.ConvertersMode> choiceBoxOf(VBox box, int index) {
        return (ChoiceBox<HvdcLine.ConvertersMode>) ((StackPane) box.getChildren().get(index)).getChildren().getFirst();
    }

    @Test
    void tableContainsTheHvdcLine() {
        assertEquals(List.of(hvdcLine()), controller.currentItems);
        assertEquals(1, controller.hvdcLinesTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        HvdcLine hvdcLine = hvdcLine();
        int row = rowOf(hvdcLine);

        assertEquals("HVDC", cellValue(controller.nameColumn, row));
        assertEquals(HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER, choiceBoxOf(cellBox(controller.convertersModeColumn, row), 0).getValue());

        VBox voltageLevelBox = cellBox(controller.voltageLevelColumn, row);
        assertEquals("VL1", ((Hyperlink) voltageLevelBox.getChildren().getFirst()).getText());
        assertEquals("VL2", ((Hyperlink) voltageLevelBox.getChildren().get(1)).getText());

        VBox stationBox = cellBox(controller.converterStationColumn, row);
        assertEquals("Converter1", ((Hyperlink) stationBox.getChildren().getFirst()).getText());
        assertEquals("Converter2", ((Hyperlink) stationBox.getChildren().get(1)).getText());

        assertEquals("280.0", fieldOf(cellBox(controller.activePowerSetpointColumn, row), 0).getText());
        assertEquals("300.0", fieldOf(cellBox(controller.maxPColumn, row), 0).getText());
    }

    @Test
    void convertersModeEditUpdatesTheLine() {
        HvdcLine hvdcLine = hvdcLine();
        VBox box = cellBox(controller.convertersModeColumn, rowOf(hvdcLine));
        ChoiceBox<HvdcLine.ConvertersMode> choiceBox = choiceBoxOf(box, 0);

        interact(() -> choiceBox.setValue(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER));

        assertEquals("Side 1 rectifier, side 2 inverter", choiceBox.getConverter().toString(choiceBox.getValue()));
        assertEquals(HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER, hvdcLine.getConvertersMode());
    }

    @Test
    void editingActivePowerSetpointUpdatesTheLine() {
        HvdcLine hvdcLine = hvdcLine();
        VBox box = cellBox(controller.activePowerSetpointColumn, rowOf(hvdcLine));
        TextField field = fieldOf(box, 0);

        interact(() -> {
            field.setText("150");
            field.getOnAction().handle(new ActionEvent());
        });

        assertEquals(150.0, hvdcLine.getActivePowerSetpoint());
    }

    @Test
    void togglingConnectedCheckBoxesDisconnectsAndReconnectsEachStation() {
        HvdcLine hvdcLine = hvdcLine();
        VBox box = cellBox(controller.connectedColumn, rowOf(hvdcLine));
        CheckBox checkBox1 = (CheckBox) box.getChildren().getFirst();
        assertTrue(hvdcLine.getConverterStation1().getTerminal().isConnected());

        interact(checkBox1::fire);
        assertFalse(hvdcLine.getConverterStation1().getTerminal().isConnected());
        assertTrue(hvdcLine.getConverterStation2().getTerminal().isConnected());

        interact(checkBox1::fire);
        assertTrue(hvdcLine.getConverterStation1().getTerminal().isConnected());
    }

    @Test
    void goToHvdcLineSelectsAndClearsSelection() {
        HvdcLine hvdcLine = hvdcLine();

        interact(() -> controller.goToHvdcLine(hvdcLine));
        List<TablePosition> selectedCells = controller.hvdcLinesTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(rowOf(hvdcLine), selectedCells.getFirst().getRow());

        interact(() -> controller.goToHvdcLine(null));
        assertTrue(controller.hvdcLinesTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        HvdcLine hvdcLine = hvdcLine();
        Hyperlink link = (Hyperlink) cellBox(controller.voltageLevelColumn, rowOf(hvdcLine)).getChildren().get(1);

        interact(link::fire);

        NavigationEvent event = mainModel.getNavigationHistory().currentEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(network.getVoltageLevel("VL2"), ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingVscConverterStationLinkNavigatesToVscTable() {
        HvdcLine hvdcLine = hvdcLine();
        Hyperlink link = (Hyperlink) cellBox(controller.converterStationColumn, rowOf(hvdcLine)).getChildren().getFirst();

        interact(link::fire);

        NavigationEvent event = mainModel.getNavigationHistory().currentEventProperty().getValue();
        assertEquals(NavigationType.NETWORK_TABLE_VSC_CONVERTER_STATIONS, event.navigationType());
        assertEquals(network.getVscConverterStation("C1"), ((VscConverterStationNavigationState) event.state()).getVscConverterStation());
    }
}
