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
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.BusNavigationState;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class BusesBusBreakerViewControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private BusesBusBreakerViewController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/buses-bus-breaker-view.fxml"), Messages.bundle());
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

    private Bus bus(String voltageLevelId) {
        return network.getVoltageLevel(voltageLevelId).getBusBreakerView().getBusStream().findFirst().orElseThrow();
    }

    private Bus createIsolatedBus() {
        VoltageLevel voltageLevel = network.newVoltageLevel()
                .setId("VL_ISOLATED")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        return voltageLevel.getBusBreakerView().newBus().setId("BUS_ISOLATED").add();
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
        List<Bus> expected = network.getBusBreakerView().getBusStream()
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
        assertEquals(bus.getFictitiousP0(), ((Double) cellValue(controller.fictitiousP0Column, row)).doubleValue());
        assertEquals(bus.getFictitiousQ0(), ((Double) cellValue(controller.fictitiousQ0Column, row)).doubleValue());

        Hyperlink voltageLevelLink = (Hyperlink) cellGraphic(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel.getNameOrId(), voltageLevelLink.getText());

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(voltageLevel.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());
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
    void voltageViolationColumnIsEmptyWithinLimitsOrWhenVIsMissing() {
        Bus bus = bus("VL2");
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        voltageLevel.setLowVoltageLimit(90).setHighVoltageLimit(110);
        bus.setV(100);
        assertNull(cellGraphic(controller.voltageViolationColumn, rowOf(bus)));

        bus.setV(Double.NaN);
        assertNull(cellGraphic(controller.voltageViolationColumn, rowOf(bus)));
    }

    @Test
    void voltageViolationColumnShowsOvervoltageAboveHighLimit() {
        Bus bus = bus("VL2");
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        voltageLevel.setNominalV(100).setLowVoltageLimit(90).setHighVoltageLimit(110);
        bus.setV(120);

        Label label = (Label) cellGraphic(controller.voltageViolationColumn, rowOf(bus));
        assertEquals(Messages.get("buses.voltageViolation.overvoltage", 120L), label.getText());
        assertEquals(Messages.get("buses.voltageViolation.limitTooltip", "110.00"), label.getTooltip().getText());
    }

    @Test
    void voltageViolationColumnShowsUndervoltageBelowLowLimit() {
        Bus bus = bus("VL2");
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        voltageLevel.setNominalV(100).setLowVoltageLimit(90).setHighVoltageLimit(110);
        bus.setV(80);

        Label label = (Label) cellGraphic(controller.voltageViolationColumn, rowOf(bus));
        assertEquals(Messages.get("buses.voltageViolation.undervoltage", 80L), label.getText());
        assertEquals(Messages.get("buses.voltageViolation.limitTooltip", "90.00"), label.getTooltip().getText());
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        Bus bus = bus("VL4");
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        Hyperlink link = (Hyperlink) cellGraphic(controller.voltageLevelColumn, rowOf(bus));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        Bus bus = bus("VL4");
        Substation substation = bus.getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(bus));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void busInBusViewColumnLinksToTheMergedBusAndNavigatesToIt() {
        Bus bus = bus("VL2");
        Bus mergedBus = bus.getVoltageLevel().getBusView().getMergedBus(bus.getId());
        Hyperlink link = (Hyperlink) cellGraphic(controller.busInBusViewColumn, rowOf(bus));
        assertEquals(mergedBus.getNameOrId(), link.getText());

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.NETWORK_TABLE_BUSES_BUS_VIEW, event.navigationType());
        assertEquals(mergedBus, ((BusNavigationState) event.state()).getBus());
    }

    @Test
    void busInBusViewColumnIsEmptyForABusWithNoMergedBus() {
        Bus isolatedBus = createIsolatedBus();
        interact(() -> mainModel.setUpdate());

        assertNull(cellGraphic(controller.busInBusViewColumn, rowOf(isolatedBus)));
    }

    @Test
    void asOwnEntityReturnsAConfiguredBusMatchDirectly() {
        Bus configuredBus = bus("VL2");

        assertEquals(Optional.of(configuredBus), controller.asOwnEntity(configuredBus));
    }

    @Test
    void asOwnEntityResolvesABusViewMatchDownToItsConfiguredBus() {
        Bus configuredBus = bus("VL2");
        Bus mergedBus = configuredBus.getVoltageLevel().getBusView().getMergedBus(configuredBus.getId());

        assertEquals(Optional.of(configuredBus), controller.asOwnEntity(mergedBus));
    }
}
