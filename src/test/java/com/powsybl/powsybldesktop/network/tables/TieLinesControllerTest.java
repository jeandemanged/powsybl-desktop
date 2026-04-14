/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Boundary;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.BoundaryLineNavigationState;
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
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
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
class TieLinesControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = EurostagTutorialExample1Factory.createWithTieLine();

    private TieLinesController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/tie-lines-view.fxml"), Messages.bundle());
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

    private int rowOf(TieLine tieLine) {
        return controller.currentItems.indexOf(tieLine);
    }

    private Object cellValue(TableColumn<TieLine, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<TieLine, T> column, int row) {
        TableCell<TieLine, T> cell = (TableCell<TieLine, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.tieLinesTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    private VBox cellBox(TableColumn<TieLine, TieLine> column, int row) {
        return (VBox) cellGraphic(column, row);
    }

    @Test
    void tableContainsAllTieLinesSortedByName() {
        List<TieLine> expected = network.getTieLineStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.tieLinesTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();
        VoltageLevel voltageLevel1 = tieLine.getTerminal1().getVoltageLevel();
        VoltageLevel voltageLevel2 = tieLine.getTerminal2().getVoltageLevel();
        int row = rowOf(tieLine);

        assertEquals(tieLine.getNameOrId(), cellValue(controller.nameColumn, row));
        assertEquals(tieLine.getPairingKey(), cellValue(controller.pairingKeyColumn, row));

        VBox voltageLevelBox = cellBox(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel1.getNameOrId(), ((Hyperlink) voltageLevelBox.getChildren().get(0)).getText());
        assertEquals(voltageLevel2.getNameOrId(), ((Hyperlink) voltageLevelBox.getChildren().get(1)).getText());

        VBox substationBox = cellBox(controller.substationColumn, row);
        assertEquals(voltageLevel1.getSubstation().orElseThrow().getNameOrId(), ((Hyperlink) substationBox.getChildren().get(0)).getText());
        assertEquals(voltageLevel2.getSubstation().orElseThrow().getNameOrId(), ((Hyperlink) substationBox.getChildren().get(1)).getText());
    }

    @Test
    void boundaryLineColumnLinksToBothBoundaryLines() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.boundaryLineColumn, rowOf(tieLine));

        Hyperlink link1 = (Hyperlink) box.getChildren().get(0);
        Hyperlink link2 = (Hyperlink) box.getChildren().get(1);
        assertEquals(tieLine.getBoundaryLine1().getNameOrId(), link1.getText());
        assertEquals(tieLine.getBoundaryLine2().getNameOrId(), link2.getText());

        interact(link1::fire);
        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.NETWORK_TABLE_BOUNDARY_LINES, event.navigationType());
        assertEquals(tieLine.getBoundaryLine1(), ((BoundaryLineNavigationState) event.state()).getBoundaryLine());
    }

    @Test
    void connectedColumnShowsBothTerminals() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.connectedColumn, rowOf(tieLine));

        assertEquals(tieLine.getTerminal1().isConnected(), ((CheckBox) box.getChildren().get(0)).isSelected());
        assertEquals(tieLine.getTerminal2().isConnected(), ((CheckBox) box.getChildren().get(1)).isSelected());
    }

    @Test
    void togglingConnectedCheckBoxesDisconnectsAndReconnectsEachTerminal() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();
        VBox box = cellBox(controller.connectedColumn, rowOf(tieLine));
        CheckBox terminal1CheckBox = (CheckBox) box.getChildren().get(0);
        CheckBox terminal2CheckBox = (CheckBox) box.getChildren().get(1);
        assertTrue(tieLine.getTerminal1().isConnected());
        assertTrue(tieLine.getTerminal2().isConnected());

        interact(terminal1CheckBox::fire);
        assertFalse(tieLine.getTerminal1().isConnected());
        assertTrue(tieLine.getTerminal2().isConnected());

        interact(terminal2CheckBox::fire);
        assertFalse(tieLine.getTerminal1().isConnected());
        assertFalse(tieLine.getTerminal2().isConnected());
    }

    @Test
    void componentAndPowerColumnsShowBothTerminals() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();
        int row = rowOf(tieLine);

        VBox ccBox = cellBox(controller.connectedComponentColumn, row);
        assertEquals(String.valueOf(tieLine.getTerminal1().getBusView().getBus().getConnectedComponent().getNum()),
                ((Label) ccBox.getChildren().get(0)).getText());
        assertEquals(String.valueOf(tieLine.getTerminal2().getBusView().getBus().getConnectedComponent().getNum()),
                ((Label) ccBox.getChildren().get(1)).getText());

        VBox scBox = cellBox(controller.synchronousComponentColumn, row);
        assertEquals(String.valueOf(tieLine.getTerminal1().getBusView().getBus().getSynchronousComponent().getNum()),
                ((Label) scBox.getChildren().get(0)).getText());
        assertEquals(String.valueOf(tieLine.getTerminal2().getBusView().getBus().getSynchronousComponent().getNum()),
                ((Label) scBox.getChildren().get(1)).getText());

        VBox pBox = cellBox(controller.pColumn, row);
        assertEquals(doubleText(tieLine.getTerminal1().getP()), ((Label) pBox.getChildren().get(0)).getText());
        assertEquals(doubleText(tieLine.getTerminal2().getP()), ((Label) pBox.getChildren().get(1)).getText());

        VBox qBox = cellBox(controller.qColumn, row);
        assertEquals(doubleText(tieLine.getTerminal1().getQ()), ((Label) qBox.getChildren().get(0)).getText());
        assertEquals(doubleText(tieLine.getTerminal2().getQ()), ((Label) qBox.getChildren().get(1)).getText());
    }

    private static String doubleText(double value) {
        return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.2f", value);
    }

    @Test
    void patlIViolationColumnIsEmptyWhenNotOverloaded() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();
        assertNull(cellGraphic(controller.patlIViolationColumn, rowOf(tieLine)));
    }

    @Test
    void patlIViolationColumnShowsOverloadedIcon() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();
        tieLine.getTerminal1().getBusView().getBus().setV(100);
        tieLine.getTerminal1().setP(1000).setQ(1000);
        tieLine.newCurrentLimits1().setPermanentLimit(0.001).add();
        interact(() -> mainModel.setUpdate());

        Label label = (Label) cellGraphic(controller.patlIViolationColumn, rowOf(tieLine));
        assertEquals(Messages.get("common.patlIViolation.overloaded"), label.getText());
    }

    @Test
    void boundaryColumnsReadTheSharedXNodeValuesOffBoundaryLine1() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();
        int row = rowOf(tieLine);
        Boundary boundary = tieLine.getBoundaryLine1().getBoundary();

        assertEquals(boundary.getP(), ((Double) cellValue(controller.pBoundaryColumn, row)).doubleValue());
        assertEquals(boundary.getQ(), ((Double) cellValue(controller.qBoundaryColumn, row)).doubleValue());
        assertEquals(boundary.getV(), ((Double) cellValue(controller.vBoundaryColumn, row)).doubleValue());
        assertEquals(boundary.getAngle(), ((Double) cellValue(controller.angleBoundaryColumn, row)).doubleValue());

        // boundary line 1 and 2 meet at the same fictitious X-node bus, so either side reports essentially the
        // same voltage state there, down to the displayed precision (P/Q are directional - from the X-node
        // towards each side - so they differ)
        Boundary otherSideBoundary = tieLine.getBoundaryLine2().getBoundary();
        assertEquals(doubleText(boundary.getV()), doubleText(otherSideBoundary.getV()));
        assertEquals(doubleText(boundary.getAngle()), doubleText(otherSideBoundary.getAngle()));
    }

    @Test
    void twoSidedColumnsAreNotSortable() {
        assertFalse(controller.substationColumn.isSortable());
        assertFalse(controller.voltageLevelColumn.isSortable());
        assertFalse(controller.boundaryLineColumn.isSortable());
        assertFalse(controller.connectedColumn.isSortable());
        assertFalse(controller.connectedComponentColumn.isSortable());
        assertFalse(controller.synchronousComponentColumn.isSortable());
        assertFalse(controller.pColumn.isSortable());
        assertFalse(controller.qColumn.isSortable());
    }

    @Test
    void goToTieLineSelectsAndClearsSelection() {
        TieLine tieLine = network.getTieLineStream().skip(1).findFirst().orElseThrow();
        int row = rowOf(tieLine);

        interact(() -> controller.goToTieLine(tieLine));
        List<TablePosition> selectedCells = controller.tieLinesTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToTieLine(null));
        assertTrue(controller.tieLinesTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void goToTieLineSelectsCorrectRowWhenTableIsSorted() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();

        interact(() -> {
            controller.nameColumn.setSortType(TableColumn.SortType.DESCENDING);
            controller.tieLinesTableView.getSortOrder().setAll(controller.nameColumn);
        });
        int sortedRow = controller.tieLinesTableView.getItems().indexOf(tieLine);
        assertTrue(sortedRow != rowOf(tieLine), "test setup should produce a different row than the unsorted order");

        interact(() -> controller.goToTieLine(tieLine));

        List<TablePosition> selectedCells = controller.tieLinesTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(sortedRow, selectedCells.get(0).getRow());
    }

    @Test
    void clickingVoltageLevel1LinkNavigatesToIt() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();
        VoltageLevel voltageLevel1 = tieLine.getTerminal1().getVoltageLevel();
        Hyperlink link = (Hyperlink) cellBox(controller.voltageLevelColumn, rowOf(tieLine)).getChildren().get(0);

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel1, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstation1LinkNavigatesToIt() {
        TieLine tieLine = network.getTieLineStream().findFirst().orElseThrow();
        Substation substation1 = tieLine.getTerminal1().getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellBox(controller.substationColumn, rowOf(tieLine)).getChildren().get(0);

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation1, ((ContainerNavigationState) event.state()).getContainer());
    }

    private static Network networkWithOneBoundaryLine(String networkId, String pairingKey) {
        Network subNetwork = NetworkFactory.findDefault().createNetwork(networkId, "test");
        VoltageLevel voltageLevel = subNetwork.newSubstation().setId(networkId + "_S").add()
                .newVoltageLevel().setId(networkId + "_VL").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        voltageLevel.getBusBreakerView().newBus().setId(networkId + "_B").add();
        voltageLevel.newBoundaryLine()
                .setId(networkId + "_BL")
                .setP0(0).setQ0(0).setR(1).setX(1).setG(0).setB(0)
                .setPairingKey(pairingKey)
                .setBus(networkId + "_B")
                .add();
        return subNetwork;
    }

    @Test
    void includesATieLineStraddlingTwoSubnetworksWhenASubnetworkIsSelected() {
        Network network1 = networkWithOneBoundaryLine("N1", "PAIR");
        Network network2 = networkWithOneBoundaryLine("N2", "PAIR");
        Network merged = Network.merge("MERGED", network1, network2);
        TieLine straddlingTieLine = merged.getTieLineStream().findFirst().orElseThrow();
        Network subnetwork1 = merged.getSubnetwork("N1");

        interact(() -> mainModel.setNetwork(subnetwork1));

        assertTrue(controller.currentItems.contains(straddlingTieLine));
    }
}
