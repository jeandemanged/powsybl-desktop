/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class BusesBusViewControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private BusesBusViewController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/buses-bus-view.fxml"), Messages.bundle());
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

    private Bus bus(String voltageLevelId) {
        return network.getVoltageLevel(voltageLevelId).getBusView().getBusStream().findFirst().orElseThrow();
    }

    private int rowOf(Bus bus) {
        return controller.currentItems.indexOf(bus);
    }

    private Object cellValue(TableColumn<Bus, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<Bus, T> column, int row) {
        TableCell<Bus, T> cell = (TableCell<Bus, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.busesTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    private static <S, T> void fireEditCommit(TableView<S> tableView, TableColumn<S, T> column, int row, T newValue) {
        TablePosition<S, T> position = new TablePosition<>(tableView, row, column);
        column.getOnEditCommit().handle(new TableColumn.CellEditEvent<>(tableView, position, TableColumn.editCommitEvent(), newValue));
    }

    @Test
    void tableContainsAllBusesSortedByName() {
        List<Bus> expected = network.getBusView().getBusStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.busesTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        Bus bus = bus("VL2");
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        int row = rowOf(bus);

        assertEquals(bus.getNameOrId(), cellValue(controller.nameColumn, row));
        assertEquals(bus.getV(), ((Double) cellValue(controller.vColumn, row)).doubleValue());
        assertEquals(bus.getAngle(), ((Double) cellValue(controller.angleColumn, row)).doubleValue());
        assertEquals(bus.getConnectedComponent().getNum(), cellValue(controller.connectedComponentColumn, row));
        assertEquals(bus.getSynchronousComponent().getNum(), cellValue(controller.synchronousComponentColumn, row));
        assertEquals(bus.getFictitiousP0(), ((Double) cellValue(controller.fictitiousP0Column, row)).doubleValue());
        assertEquals(bus.getFictitiousQ0(), ((Double) cellValue(controller.fictitiousQ0Column, row)).doubleValue());

        Hyperlink voltageLevelLink = (Hyperlink) cellGraphic(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel.getNameOrId(), voltageLevelLink.getText());

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(voltageLevel.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());
    }

    @Test
    void fictitiousP0AndQ0AreLockedWhileTheNetworkIsBusy() {
        int row = rowOf(bus("VL2"));
        Runnable[] release = new Runnable[1];
        interact(() -> release[0] = mainModel.markBusy(network));

        assertFalse(controller.busesTableView.isEditable());
        assertTrue(cell(controller.fictitiousP0Column, row).isDisabled());
        assertTrue(cell(controller.fictitiousQ0Column, row).isDisabled());
        assertFalse(cell(controller.vColumn, row).isDisabled(), "a read-only value isn't greyed");

        interact(release[0]);
        assertTrue(controller.busesTableView.isEditable());
        assertFalse(cell(controller.fictitiousP0Column, row).isDisabled());
    }

    private <T> TableCell<Bus, T> cell(TableColumn<Bus, T> column, int row) {
        TableCell<Bus, T> cell = (TableCell<Bus, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.busesTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell;
    }

    @Test
    void fictitiousP0AndQ0EditCommitUpdateBus() {
        Bus bus = bus("VL2");
        int row = rowOf(bus);

        interact(() -> fireEditCommit(controller.busesTableView, controller.fictitiousP0Column, row, 12.0));
        assertEquals(12.0, bus.getFictitiousP0());

        interact(() -> fireEditCommit(controller.busesTableView, controller.fictitiousQ0Column, row, 3.0));
        assertEquals(3.0, bus.getFictitiousQ0());
    }

    @Test
    void goToBusSelectsAndClearsSelection() {
        Bus bus = bus("VL3");
        int row = rowOf(bus);

        interact(() -> controller.goToBus(bus));
        List<TablePosition> selectedCells = controller.busesTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.getFirst().getRow());

        interact(() -> controller.goToBus(null));
        assertTrue(controller.busesTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void voltageRangeColumnShowsVoltageWithinLimitsAndIsEmptyWhenVIsMissing() {
        Bus bus = bus("VL2");
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        voltageLevel.setNominalV(100).setLowVoltageLimit(90).setHighVoltageLimit(110);
        bus.setV(105);
        interact(() -> ViolationsTestSupport.recompute(mainModel, network));
        Node graphic = cellGraphic(controller.voltageRangeColumn, rowOf(bus));
        assertEquals("105.0 %", ViolationsTestSupport.voltageLabel(graphic).getText());
        assertNull(ViolationsTestSupport.voltageBar(graphic).getRange().violation());

        bus.setV(Double.NaN);
        interact(() -> ViolationsTestSupport.recompute(mainModel, network));
        assertNull(cellGraphic(controller.voltageRangeColumn, rowOf(bus)));
    }

    @Test
    void voltageRangeColumnShowsOvervoltageAboveHighLimit() {
        Bus bus = bus("VL2");
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        voltageLevel.setNominalV(100).setLowVoltageLimit(90).setHighVoltageLimit(110);
        bus.setV(120);
        interact(() -> ViolationsTestSupport.recompute(mainModel, network));

        Node graphic = cellGraphic(controller.voltageRangeColumn, rowOf(bus));
        assertEquals("120.0 %", ViolationsTestSupport.voltageLabel(graphic).getText());
        assertEquals(110, ViolationsTestSupport.voltageBar(graphic).getRange().violation().violation().getLimit());
    }

    @Test
    void voltageRangeColumnShowsUndervoltageBelowLowLimit() {
        Bus bus = bus("VL2");
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        voltageLevel.setNominalV(100).setLowVoltageLimit(90).setHighVoltageLimit(110);
        bus.setV(80);
        interact(() -> ViolationsTestSupport.recompute(mainModel, network));

        Node graphic = cellGraphic(controller.voltageRangeColumn, rowOf(bus));
        assertEquals("80.0 %", ViolationsTestSupport.voltageLabel(graphic).getText());
        assertEquals(90, ViolationsTestSupport.voltageBar(graphic).getRange().violation().violation().getLimit());
    }

    @Test
    void voltageRangeColumnSortsByDistanceBeyondTheLimitNotFromNominal() {
        Bus overvoltageBus = bus("VL2"); // 105% of nominal -> 4 points beyond its limit
        VoltageLevel overvoltageLevel = overvoltageBus.getVoltageLevel();
        overvoltageLevel.setNominalV(100).setLowVoltageLimit(90).setHighVoltageLimit(101);
        overvoltageBus.setV(105);

        Bus undervoltageBus = bus("VL3"); // 94% of nominal -> further from nominal, but 2 points beyond its limit only
        VoltageLevel undervoltageLevel = undervoltageBus.getVoltageLevel();
        undervoltageLevel.setNominalV(100).setLowVoltageLimit(96).setHighVoltageLimit(110);
        undervoltageBus.setV(94);
        interact(() -> ViolationsTestSupport.recompute(mainModel, network));

        VoltageRange overvoltage = (VoltageRange) cellValue(controller.voltageRangeColumn, rowOf(overvoltageBus));
        VoltageRange undervoltage = (VoltageRange) cellValue(controller.voltageRangeColumn, rowOf(undervoltageBus));
        // most severe first
        assertTrue(controller.voltageRangeColumn.getComparator().compare(overvoltage, undervoltage) < 0);
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        Bus bus = bus("VL4");
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        Hyperlink link = (Hyperlink) cellGraphic(controller.voltageLevelColumn, rowOf(bus));

        interact(link::fire);

        NavigationEvent event = mainModel.getNavigationHistory().currentEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        Bus bus = bus("VL4");
        Substation substation = bus.getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(bus));

        interact(link::fire);

        NavigationEvent event = mainModel.getNavigationHistory().currentEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
