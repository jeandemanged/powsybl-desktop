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
import com.powsybl.iidm.network.Line;
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
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LinesControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private LinesController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/lines-view.fxml"), Messages.bundle());
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

    private int rowOf(Line line) {
        return controller.currentItems.indexOf(line);
    }

    private Object cellValue(TableColumn<Line, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<Line, T> column, int row) {
        TableCell<Line, T> cell = (TableCell<Line, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.linesTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    private VBox cellBox(TableColumn<Line, Line> column, int row) {
        return (VBox) cellGraphic(column, row);
    }

    // R/X/G1/B1/G2/B2 are single values rendered as a one-slot multi-sided column (see LinesController) - each
    // slot is a StackPane holding a Label (shown at rest) and a TextField (shown while editing), same as
    // TransformersController's RatedU/RatedS - see TableColumnSupport.editableDoubleField.
    private static Label labelOf(VBox box, int index) {
        return (Label) ((StackPane) box.getChildren().get(index)).getChildren().get(0);
    }

    private static TextField fieldOf(VBox box, int index) {
        return (TextField) ((StackPane) box.getChildren().get(index)).getChildren().get(1);
    }

    private static void doubleClick(Label label) {
        label.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, false, false, false, null));
    }

    @Test
    void tableContainsAllLinesSortedByName() {
        List<Line> expected = network.getLineStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.linesTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        VoltageLevel voltageLevel1 = line.getTerminal1().getVoltageLevel();
        VoltageLevel voltageLevel2 = line.getTerminal2().getVoltageLevel();
        int row = rowOf(line);

        assertEquals(line.getNameOrId(), cellValue(controller.nameColumn, row));

        VBox voltageLevelBox = cellBox(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel1.getNameOrId(), ((Hyperlink) voltageLevelBox.getChildren().get(0)).getText());
        assertEquals(voltageLevel2.getNameOrId(), ((Hyperlink) voltageLevelBox.getChildren().get(1)).getText());

        VBox substationBox = cellBox(controller.substationColumn, row);
        assertEquals(voltageLevel1.getSubstation().orElseThrow().getNameOrId(), ((Hyperlink) substationBox.getChildren().get(0)).getText());
        assertEquals(voltageLevel2.getSubstation().orElseThrow().getNameOrId(), ((Hyperlink) substationBox.getChildren().get(1)).getText());
    }

    @Test
    void connectedColumnShowsBothTerminals() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.connectedColumn, rowOf(line));

        assertEquals(line.getTerminal1().isConnected(), ((CheckBox) box.getChildren().get(0)).isSelected());
        assertEquals(line.getTerminal2().isConnected(), ((CheckBox) box.getChildren().get(1)).isSelected());
    }

    @Test
    void togglingConnectedCheckBoxesDisconnectsAndReconnectsEachTerminal() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.connectedColumn, rowOf(line));
        CheckBox terminal1CheckBox = (CheckBox) box.getChildren().get(0);
        CheckBox terminal2CheckBox = (CheckBox) box.getChildren().get(1);
        assertTrue(line.getTerminal1().isConnected());
        assertTrue(line.getTerminal2().isConnected());

        interact(terminal1CheckBox::fire);
        assertFalse(line.getTerminal1().isConnected());
        assertTrue(line.getTerminal2().isConnected());

        interact(terminal2CheckBox::fire);
        assertFalse(line.getTerminal1().isConnected());
        assertFalse(line.getTerminal2().isConnected());
    }

    @Test
    void componentAndPowerColumnsShowBothTerminals() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        int row = rowOf(line);

        VBox ccBox = cellBox(controller.connectedComponentColumn, row);
        assertEquals(String.valueOf(line.getTerminal1().getBusView().getBus().getConnectedComponent().getNum()),
                ((Label) ccBox.getChildren().get(0)).getText());
        assertEquals(String.valueOf(line.getTerminal2().getBusView().getBus().getConnectedComponent().getNum()),
                ((Label) ccBox.getChildren().get(1)).getText());

        VBox scBox = cellBox(controller.synchronousComponentColumn, row);
        assertEquals(String.valueOf(line.getTerminal1().getBusView().getBus().getSynchronousComponent().getNum()),
                ((Label) scBox.getChildren().get(0)).getText());
        assertEquals(String.valueOf(line.getTerminal2().getBusView().getBus().getSynchronousComponent().getNum()),
                ((Label) scBox.getChildren().get(1)).getText());

        VBox pBox = cellBox(controller.pColumn, row);
        assertEquals(doubleText(line.getTerminal1().getP()), ((Label) pBox.getChildren().get(0)).getText());
        assertEquals(doubleText(line.getTerminal2().getP()), ((Label) pBox.getChildren().get(1)).getText());

        VBox qBox = cellBox(controller.qColumn, row);
        assertEquals(doubleText(line.getTerminal1().getQ()), ((Label) qBox.getChildren().get(0)).getText());
        assertEquals(doubleText(line.getTerminal2().getQ()), ((Label) qBox.getChildren().get(1)).getText());
    }

    private static String doubleText(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String sixDecimalText(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.6f", value);
    }

    @Test
    void parameterColumnsShowRXG1B1G2B2AtTheExpectedPrecision() {
        interact(() -> List.of(controller.rColumn, controller.xColumn, controller.g1Column,
                controller.b1Column, controller.g2Column, controller.b2Column).forEach(column -> column.setVisible(true)));
        Line line = network.getLineStream().findFirst().orElseThrow();
        int row = rowOf(line);

        assertEquals(doubleText(line.getR()), labelOf(cellBox(controller.rColumn, row), 0).getText());
        assertEquals(doubleText(line.getX()), labelOf(cellBox(controller.xColumn, row), 0).getText());
        assertEquals(sixDecimalText(line.getG1()), labelOf(cellBox(controller.g1Column, row), 0).getText());
        assertEquals(sixDecimalText(line.getB1()), labelOf(cellBox(controller.b1Column, row), 0).getText());
        assertEquals(sixDecimalText(line.getG2()), labelOf(cellBox(controller.g2Column, row), 0).getText());
        assertEquals(sixDecimalText(line.getB2()), labelOf(cellBox(controller.b2Column, row), 0).getText());
    }

    @Test
    void editingRColumnUpdatesTheLine() {
        interact(() -> controller.rColumn.setVisible(true));
        Line line = network.getLineStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.rColumn, rowOf(line));
        Label rLabel = labelOf(box, 0);
        TextField rField = fieldOf(box, 0);

        interact(() -> doubleClick(rLabel));
        interact(() -> {
            rField.setText("12.34");
            rField.getOnAction().handle(new ActionEvent());
        });

        assertEquals(12.34, line.getR());
        assertEquals("12.34", rLabel.getText());
    }

    @Test
    void editingG1ColumnUpdatesTheLineAtSixDecimalPrecision() {
        interact(() -> controller.g1Column.setVisible(true));
        Line line = network.getLineStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.g1Column, rowOf(line));
        Label g1Label = labelOf(box, 0);
        TextField g1Field = fieldOf(box, 0);

        interact(() -> doubleClick(g1Label));
        interact(() -> {
            g1Field.setText("0.000123");
            g1Field.getOnAction().handle(new ActionEvent());
        });

        assertEquals(0.000123, line.getG1());
        assertEquals("0.000123", g1Label.getText());
    }

    @Test
    void patlIViolationColumnIsEmptyWhenNotOverloaded() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        assertNull(cellGraphic(controller.patlIViolationColumn, rowOf(line)));
    }

    @Test
    void patlIViolationColumnShowsOverloadedIcon() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        line.getTerminal1().getBusView().getBus().setV(100);
        line.getTerminal1().setP(1000).setQ(1000);
        line.newCurrentLimits1().setPermanentLimit(0.001).add();
        interact(() -> mainModel.setUpdate());

        Label label = (Label) cellGraphic(controller.patlIViolationColumn, rowOf(line));
        assertEquals(Messages.get("common.patlIViolation.overloaded"), label.getText());
    }

    @Test
    void twoSidedColumnsAreNotSortable() {
        assertFalse(controller.substationColumn.isSortable());
        assertFalse(controller.voltageLevelColumn.isSortable());
        assertFalse(controller.connectedColumn.isSortable());
        assertFalse(controller.connectedComponentColumn.isSortable());
        assertFalse(controller.synchronousComponentColumn.isSortable());
        assertFalse(controller.pColumn.isSortable());
        assertFalse(controller.qColumn.isSortable());
    }

    @Test
    void substationColumnIsNullForALineInAVoltageLevelWithoutSubstation() {
        VoltageLevel voltageLevel1 = network.newVoltageLevel()
                .setId("VL_NO_SUBSTATION_1")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        voltageLevel1.getBusBreakerView().newBus().setId("BUS_NO_SUBSTATION_1").add();
        VoltageLevel voltageLevel2 = network.newVoltageLevel()
                .setId("VL_NO_SUBSTATION_2")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        voltageLevel2.getBusBreakerView().newBus().setId("BUS_NO_SUBSTATION_2").add();
        Line line = network.newLine()
                .setId("L_NO_SUBSTATION")
                .setVoltageLevel1("VL_NO_SUBSTATION_1")
                .setBus1("BUS_NO_SUBSTATION_1")
                .setConnectableBus1("BUS_NO_SUBSTATION_1")
                .setVoltageLevel2("VL_NO_SUBSTATION_2")
                .setBus2("BUS_NO_SUBSTATION_2")
                .setConnectableBus2("BUS_NO_SUBSTATION_2")
                .setR(1)
                .setX(1)
                .setG1(0)
                .setB1(0)
                .setG2(0)
                .setB2(0)
                .add();

        interact(() -> mainModel.setUpdate());

        VBox substationBox = cellBox(controller.substationColumn, rowOf(line));
        assertFalse(substationBox.getChildren().get(0) instanceof Hyperlink);
        assertFalse(substationBox.getChildren().get(1) instanceof Hyperlink);
    }

    @Test
    void goToLineSelectsAndClearsSelection() {
        Line line = network.getLineStream().skip(1).findFirst().orElseThrow();
        int row = rowOf(line);

        interact(() -> controller.goToLine(line));
        List<TablePosition> selectedCells = controller.linesTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToLine(null));
        assertTrue(controller.linesTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void goToLineSelectsCorrectRowWhenTableIsSorted() {
        Line line = network.getLineStream().findFirst().orElseThrow();

        interact(() -> {
            controller.nameColumn.setSortType(TableColumn.SortType.DESCENDING);
            controller.linesTableView.getSortOrder().setAll(controller.nameColumn);
        });
        int sortedRow = controller.linesTableView.getItems().indexOf(line);
        assertTrue(sortedRow != rowOf(line), "test setup should produce a different row than the unsorted order");

        interact(() -> controller.goToLine(line));

        List<TablePosition> selectedCells = controller.linesTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(sortedRow, selectedCells.get(0).getRow());
    }

    @Test
    void clickingVoltageLevel1LinkNavigatesToIt() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        VoltageLevel voltageLevel1 = line.getTerminal1().getVoltageLevel();
        Hyperlink link = (Hyperlink) cellBox(controller.voltageLevelColumn, rowOf(line)).getChildren().get(0);

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel1, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstation1LinkNavigatesToIt() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        Substation substation1 = line.getTerminal1().getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellBox(controller.substationColumn, rowOf(line)).getChildren().get(0);

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation1, ((ContainerNavigationState) event.state()).getContainer());
    }
}
