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
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.Substation;
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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
class ShuntCompensatorsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private ShuntCompensatorsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        VoltageLevel voltageLevel1 = network.getVoltageLevel("VL1");
        voltageLevel1.newShuntCompensator()
                .setId("B1-SH2")
                .setBus("B1")
                .setConnectableBus("B1")
                .setSectionCount(2)
                .setVoltageRegulatorOn(true)
                .setTargetV(110)
                .setTargetDeadband(2)
                .newLinearModel()
                    .setMaximumSectionCount(3)
                    .setBPerSection(1e-5)
                    .add()
                .add()
                .setSolvedSectionCount(1);

        voltageLevel1.newShuntCompensator()
                .setId("B1-SH-REACTOR")
                .setBus("B1")
                .setConnectableBus("B1")
                .setSectionCount(1)
                .newLinearModel()
                    .setMaximumSectionCount(1)
                    .setBPerSection(-1e-5)
                    .add()
                .add();

        voltageLevel1.newShuntCompensator()
                .setId("B1-SH-NONLINEAR-MIXED")
                .setBus("B1")
                .setConnectableBus("B1")
                .setSectionCount(1)
                .newNonLinearModel()
                    .beginSection()
                        .setB(-1e-5)
                        .setG(0)
                    .endSection()
                    .beginSection()
                        .setB(1e-5)
                        .setG(0)
                    .endSection()
                .add()
                .add();

        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/shunt-compensators-view.fxml"), Messages.bundle());
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

    private ShuntCompensator shuntCompensator(String id) {
        return network.getShuntCompensator(id);
    }

    private int rowOf(ShuntCompensator shuntCompensator) {
        return controller.currentItems.indexOf(shuntCompensator);
    }

    private Object cellValue(TableColumn<ShuntCompensator, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<ShuntCompensator, T> column, int row) {
        TableCell<ShuntCompensator, T> cell = (TableCell<ShuntCompensator, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.shuntCompensatorsTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        // Cells rendered directly (not through the skin) have no TableRow by default; wire one up
        // so getTableRow().getItem() resolves for cell factories that rely on it (e.g. checkbox columns).
        TableRow<ShuntCompensator> tableRow = new TableRow<>();
        tableRow.updateTableView(controller.shuntCompensatorsTableView);
        tableRow.updateIndex(row);
        cell.updateTableRow(tableRow);
        return cell.getGraphic();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    // The section column's cell graphic is an IntStepperField (TableColumnSupport): an HBox holding a StackPane
    // with the double-click-to-edit Label/TextField pair (same as editableDoubleField's ratedU/ratedS cells)
    // followed by a VBox with the increment (top) and decrement (bottom) buttons.
    private static Label sectionLabelOf(HBox stepperBox) {
        return (Label) ((StackPane) stepperBox.getChildren().get(0)).getChildren().get(0);
    }

    private static TextField sectionFieldOf(HBox stepperBox) {
        return (TextField) ((StackPane) stepperBox.getChildren().get(0)).getChildren().get(1);
    }

    private static Button sectionIncrementButtonOf(HBox stepperBox) {
        return (Button) ((VBox) stepperBox.getChildren().get(1)).getChildren().get(0);
    }

    private static Button sectionDecrementButtonOf(HBox stepperBox) {
        return (Button) ((VBox) stepperBox.getChildren().get(1)).getChildren().get(1);
    }

    private static void doubleClick(Label label) {
        label.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, false, false, false, null));
    }

    @Test
    void tableContainsAllShuntCompensatorsSortedByName() {
        List<ShuntCompensator> expected = network.getShuntCompensatorStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.shuntCompensatorsTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        ShuntCompensator shuntCompensator = shuntCompensator("B1-SH2");
        VoltageLevel voltageLevel = shuntCompensator.getTerminal().getVoltageLevel();
        int row = rowOf(shuntCompensator);

        assertEquals(shuntCompensator.getNameOrId(), cellValue(controller.nameColumn, row));
        assertEquals(Messages.get("shuntCompensators.type.capacitor"), cellValue(controller.typeColumn, row));
        HBox modelTypeBox = (HBox) cellGraphic(controller.modelTypeColumn, row);
        assertEquals(Messages.get("shuntCompensators.modelType.linear"), ((Label) modelTypeBox.getChildren().get(1)).getText());
        assertEquals(3, cellValue(controller.maximumSectionColumn, row));
        assertEquals(shuntCompensator.getTargetV(), ((Double) cellValue(controller.targetVColumn, row)).doubleValue());
        assertEquals(shuntCompensator.getTargetDeadband(), ((Double) cellValue(controller.targetDeadbandColumn, row)).doubleValue());
        assertEquals(1, cellValue(controller.solvedSectionColumn, row));

        Hyperlink voltageLevelLink = (Hyperlink) cellGraphic(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel.getNameOrId(), voltageLevelLink.getText());

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(voltageLevel.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());
    }

    @Test
    void typeColumnReflectsSectionSusceptanceSign() {
        assertEquals(Messages.get("shuntCompensators.type.capacitor"),
                cellValue(controller.typeColumn, rowOf(shuntCompensator("B1-SH2"))));
        assertEquals(Messages.get("shuntCompensators.type.reactor"),
                cellValue(controller.typeColumn, rowOf(shuntCompensator("B1-SH-REACTOR"))));
        assertEquals(Messages.get("shuntCompensators.type.unknown"),
                cellValue(controller.typeColumn, rowOf(shuntCompensator("B1-SH-NONLINEAR-MIXED"))));
    }

    @Test
    void togglingConnectedCheckBoxDisconnectsAndReconnectsShuntCompensator() {
        ShuntCompensator shuntCompensator = shuntCompensator("B9-SH");
        CheckBox checkBox = (CheckBox) cellGraphic(controller.connectedColumn, rowOf(shuntCompensator));
        assertTrue(shuntCompensator.getTerminal().isConnected());

        interact(checkBox::fire);
        assertFalse(shuntCompensator.getTerminal().isConnected());

        interact(checkBox::fire);
        assertTrue(shuntCompensator.getTerminal().isConnected());
    }

    @Test
    void solvedSectionIsNullWhenNoCalculationHasBeenPerformed() {
        ShuntCompensator shuntCompensator = shuntCompensator("B9-SH");

        assertNull(cellValue(controller.solvedSectionColumn, rowOf(shuntCompensator)));
    }

    @Test
    void sectionFieldIsClampedBetweenZeroAndMaximumSectionCount() {
        ShuntCompensator shuntCompensator = shuntCompensator("B1-SH2");
        HBox stepperBox = (HBox) cellGraphic(controller.sectionColumn, rowOf(shuntCompensator));
        Label label = sectionLabelOf(stepperBox);
        TextField textField = sectionFieldOf(stepperBox);
        Button incrementButton = sectionIncrementButtonOf(stepperBox);
        Button decrementButton = sectionDecrementButtonOf(stepperBox);

        assertEquals(String.valueOf(shuntCompensator.getSectionCount()), label.getText());
        assertTrue(incrementButton.isVisible());
        assertTrue(decrementButton.isVisible());

        interact(() -> doubleClick(label));
        interact(() -> {
            textField.setText("99");
            textField.getOnAction().handle(new ActionEvent());
        });
        assertEquals(shuntCompensator.getMaximumSectionCount(), shuntCompensator.getSectionCount());
        assertFalse(incrementButton.isVisible());

        interact(() -> doubleClick(label));
        interact(() -> {
            textField.setText("-5");
            textField.getOnAction().handle(new ActionEvent());
        });
        assertEquals(0, shuntCompensator.getSectionCount());
        assertFalse(decrementButton.isVisible());
    }

    @Test
    void sectionFieldEditUpdatesShuntCompensator() {
        ShuntCompensator shuntCompensator = shuntCompensator("B1-SH2");
        HBox stepperBox = (HBox) cellGraphic(controller.sectionColumn, rowOf(shuntCompensator));
        Label label = sectionLabelOf(stepperBox);
        TextField textField = sectionFieldOf(stepperBox);

        interact(() -> doubleClick(label));
        interact(() -> {
            textField.setText("3");
            textField.getOnAction().handle(new ActionEvent());
        });

        assertEquals(3, shuntCompensator.getSectionCount());
    }

    @Test
    void sectionIncrementAndDecrementButtonsUpdateShuntCompensatorWithoutEnteringEditMode() {
        ShuntCompensator shuntCompensator = shuntCompensator("B1-SH2");
        HBox stepperBox = (HBox) cellGraphic(controller.sectionColumn, rowOf(shuntCompensator));
        Button incrementButton = sectionIncrementButtonOf(stepperBox);
        Button decrementButton = sectionDecrementButtonOf(stepperBox);

        interact(incrementButton::fire);
        assertEquals(3, shuntCompensator.getSectionCount());

        interact(decrementButton::fire);
        interact(decrementButton::fire);
        assertEquals(1, shuntCompensator.getSectionCount());
    }

    @Test
    void voltageRegulatorOnEditUpdatesShuntCompensator() {
        ShuntCompensator shuntCompensator = shuntCompensator("B1-SH2");
        CheckBox checkBox = (CheckBox) cellGraphic(controller.voltageRegulatorOnColumn, rowOf(shuntCompensator));
        assertTrue(shuntCompensator.isVoltageRegulatorOn());

        interact(checkBox::fire);

        assertFalse(shuntCompensator.isVoltageRegulatorOn());
    }

    @Test
    void targetVAndTargetDeadbandEditCommitUpdateShuntCompensator() {
        ShuntCompensator shuntCompensator = shuntCompensator("B1-SH2");
        int row = rowOf(shuntCompensator);

        interact(() -> fireEditCommit(controller.shuntCompensatorsTableView, controller.targetVColumn, row, 120.0));
        assertEquals(120.0, shuntCompensator.getTargetV());

        interact(() -> fireEditCommit(controller.shuntCompensatorsTableView, controller.targetDeadbandColumn, row, 3.0));
        assertEquals(3.0, shuntCompensator.getTargetDeadband());
    }

    @Test
    void goToShuntCompensatorSelectsAndClearsSelection() {
        ShuntCompensator shuntCompensator = shuntCompensator("B9-SH");
        int row = rowOf(shuntCompensator);

        interact(() -> controller.goToShuntCompensator(shuntCompensator));
        List<TablePosition> selectedCells = controller.shuntCompensatorsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToShuntCompensator(null));
        assertTrue(controller.shuntCompensatorsTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void goToShuntCompensatorSelectsCorrectRowWhenTableIsSorted() {
        ShuntCompensator shuntCompensator = shuntCompensator("B9-SH");

        interact(() -> {
            controller.nameColumn.setSortType(TableColumn.SortType.DESCENDING);
            controller.shuntCompensatorsTableView.getSortOrder().setAll(controller.nameColumn);
        });
        int sortedRow = controller.shuntCompensatorsTableView.getItems().indexOf(shuntCompensator);
        assertTrue(sortedRow != rowOf(shuntCompensator), "test setup should produce a different row than the unsorted order");

        interact(() -> controller.goToShuntCompensator(shuntCompensator));

        List<TablePosition> selectedCells = controller.shuntCompensatorsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(sortedRow, selectedCells.get(0).getRow());
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        ShuntCompensator shuntCompensator = shuntCompensator("B9-SH");
        VoltageLevel voltageLevel = shuntCompensator.getTerminal().getVoltageLevel();
        Hyperlink link = (Hyperlink) cellGraphic(controller.voltageLevelColumn, rowOf(shuntCompensator));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        ShuntCompensator shuntCompensator = shuntCompensator("B9-SH");
        Substation substation = shuntCompensator.getTerminal().getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(shuntCompensator));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
