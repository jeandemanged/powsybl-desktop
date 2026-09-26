/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.TieLineNavigationState;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
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
class BoundaryLinesControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = EurostagTutorialExample1Factory.createWithTieLine();

    private BoundaryLinesController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/boundary-lines-view.fxml"), Messages.bundle());
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

    private int rowOf(BoundaryLine boundaryLine) {
        return controller.currentItems.indexOf(boundaryLine);
    }

    private Object cellValue(TableColumn<BoundaryLine, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> TableCell<BoundaryLine, T> cell(TableColumn<BoundaryLine, T> column, int row) {
        @SuppressWarnings("unchecked")
        TableCell<BoundaryLine, T> cell = (TableCell<BoundaryLine, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.boundaryLinesTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell;
    }

    private Node cellGraphic(TableColumn<BoundaryLine, BoundaryLine> column, int row) {
        return cell(column, row).getGraphic();
    }

    private String cellText(TableColumn<BoundaryLine, Double> column, int row) {
        return cell(column, row).getText();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    @Test
    void tableContainsAllBoundaryLinesSortedByName() {
        List<BoundaryLine> expected = network.getBoundaryLineStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.boundaryLinesTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        VoltageLevel voltageLevel = boundaryLine.getTerminal().getVoltageLevel();
        int row = rowOf(boundaryLine);

        assertEquals(boundaryLine.getNameOrId(), cellValue(controller.nameColumn, row));
        assertEquals(boundaryLine.getPairingKey(), cellValue(controller.pairingKeyColumn, row));
        assertEquals(boundaryLine.getP0(), ((Double) cellValue(controller.p0Column, row)).doubleValue());
        assertEquals(boundaryLine.getQ0(), ((Double) cellValue(controller.q0Column, row)).doubleValue());
        assertEquals(boundaryLine.getTerminal().getP(), ((Double) cellValue(controller.pNetColumn, row)).doubleValue());
        assertEquals(boundaryLine.getTerminal().getQ(), ((Double) cellValue(controller.qNetColumn, row)).doubleValue());

        Hyperlink voltageLevelLink = (Hyperlink) cellGraphic(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel.getNameOrId(), voltageLevelLink.getText());

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(voltageLevel.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());
    }

    @Test
    void tieLineColumnLinksToTheContainingTieLine() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        TieLine tieLine = boundaryLine.getTieLine().orElseThrow();

        Hyperlink link = (Hyperlink) cellGraphic(controller.tieLineColumn, rowOf(boundaryLine));
        assertEquals(tieLine.getNameOrId(), link.getText());

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.NETWORK_TABLE_TIE_LINES, event.navigationType());
        assertEquals(tieLine, ((TieLineNavigationState) event.state()).getTieLine());
    }

    @Test
    void tieLineColumnIsNullForAnUnpairedBoundaryLine() {
        BoundaryLine paired = network.getBoundaryLineStream().findFirst().orElseThrow();
        VoltageLevel voltageLevel = paired.getTerminal().getVoltageLevel();
        String busId = paired.getTerminal().getBusBreakerView().getConnectableBus().getId();
        BoundaryLine unpaired = voltageLevel.newBoundaryLine()
                .setId("BL_UNPAIRED")
                .setP0(0)
                .setQ0(0)
                .setR(0)
                .setX(1)
                .setG(0)
                .setB(0)
                .setBus(busId)
                .add();

        interact(() -> mainModel.setUpdate());

        assertNull(cellGraphic(controller.tieLineColumn, rowOf(unpaired)));
    }

    @Test
    void generationColumnsShowDashAndRefuseEditWhenGenerationAbsent() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        int row = rowOf(boundaryLine);
        assertNull(boundaryLine.getGeneration());

        assertEquals("-", cellText(controller.minPColumn, row));
        assertEquals("-", cellText(controller.maxPColumn, row));
        assertEquals("-", cellText(controller.targetVColumn, row));
        assertEquals("-", cellText(controller.targetQColumn, row));

        TableCell<BoundaryLine, Boolean> voltageRegulatorCell = cell(controller.voltageRegulatorOnColumn, row);
        assertEquals("-", voltageRegulatorCell.getText());
        assertNull(voltageRegulatorCell.getGraphic());

        interact(cell(controller.minPColumn, row)::startEdit);
        assertFalse(cell(controller.minPColumn, row).isEditing());
    }

    @Test
    void boundarySideColumnsReadTheBoundaryValues() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        int row = rowOf(boundaryLine);

        assertEquals(boundaryLine.getBoundary().getP(), ((Double) cellValue(controller.pBoundaryColumn, row)).doubleValue());
        assertEquals(boundaryLine.getBoundary().getQ(), ((Double) cellValue(controller.qBoundaryColumn, row)).doubleValue());
        assertEquals(boundaryLine.getBoundary().getV(), ((Double) cellValue(controller.vBoundaryColumn, row)).doubleValue());
        assertEquals(boundaryLine.getBoundary().getAngle(), ((Double) cellValue(controller.angleBoundaryColumn, row)).doubleValue());
    }

    @Test
    void patlIViolationColumnIsEmptyWhenNotOverloaded() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        assertNull(cell(controller.patlIViolationColumn, rowOf(boundaryLine)).getGraphic());
    }

    @Test
    void patlIViolationColumnShowsOverloadedIcon() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        boundaryLine.getTerminal().getBusView().getBus().setV(100);
        boundaryLine.getTerminal().setP(1000).setQ(1000);
        boundaryLine.newCurrentLimits().setPermanentLimit(0.001).add();
        interact(() -> mainModel.setUpdate());

        Label label = (Label) cell(controller.patlIViolationColumn, rowOf(boundaryLine)).getGraphic();
        assertEquals(Messages.get("common.patlIViolation.overloaded"), label.getText());
    }

    @Test
    void togglingConnectedCheckBoxDisconnectsAndReconnectsBoundaryLine() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        CheckBox checkBox = (CheckBox) cellGraphic(controller.connectedColumn, rowOf(boundaryLine));
        assertTrue(boundaryLine.getTerminal().isConnected());

        interact(checkBox::fire);
        assertFalse(boundaryLine.getTerminal().isConnected());

        interact(checkBox::fire);
        assertTrue(boundaryLine.getTerminal().isConnected());
    }

    @Test
    void p0AndQ0EditCommitUpdateBoundaryLine() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        int row = rowOf(boundaryLine);

        interact(() -> fireEditCommit(controller.boundaryLinesTableView, controller.p0Column, row, 12.0));
        assertEquals(12.0, boundaryLine.getP0());

        interact(() -> fireEditCommit(controller.boundaryLinesTableView, controller.q0Column, row, 3.0));
        assertEquals(3.0, boundaryLine.getQ0());
    }

    @Test
    void goToBoundaryLineSelectsAndClearsSelection() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().skip(1).findFirst().orElseThrow();
        int row = rowOf(boundaryLine);

        interact(() -> controller.goToBoundaryLine(boundaryLine));
        List<TablePosition> selectedCells = controller.boundaryLinesTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToBoundaryLine(null));
        assertTrue(controller.boundaryLinesTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        VoltageLevel voltageLevel = boundaryLine.getTerminal().getVoltageLevel();
        Hyperlink link = (Hyperlink) cellGraphic(controller.voltageLevelColumn, rowOf(boundaryLine));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        BoundaryLine boundaryLine = network.getBoundaryLineStream().findFirst().orElseThrow();
        Substation substation = boundaryLine.getTerminal().getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(boundaryLine));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
