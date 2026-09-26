/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
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
class BusbarSectionsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = FourSubstationsNodeBreakerFactory.create();

    private BusbarSectionsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/busbar-sections-view.fxml"), Messages.bundle());
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

    private BusbarSection busbarSection(String id) {
        return network.getBusbarSection(id);
    }

    private int rowOf(BusbarSection busbarSection) {
        return controller.currentItems.indexOf(busbarSection);
    }

    private Object cellValue(TableColumn<BusbarSection, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private Node cellGraphic(TableColumn<BusbarSection, BusbarSection> column, int row) {
        TableCell<BusbarSection, BusbarSection> cell = (TableCell<BusbarSection, BusbarSection>) column.getCellFactory().call(column);
        cell.updateTableView(controller.busbarSectionsTableView);
        cell.updateTableColumn(column);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    @Test
    void tableContainsAllBusbarSectionsSortedByName() {
        List<BusbarSection> expected = network.getBusbarSectionStream()
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
        assertEquals(expected, controller.currentItems);
        assertEquals(expected.size(), controller.busbarSectionsTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        BusbarSection busbarSection = busbarSection("S1VL1_BBS");
        VoltageLevel voltageLevel = busbarSection.getTerminal().getVoltageLevel();
        int row = rowOf(busbarSection);

        assertEquals(busbarSection.getNameOrId(), cellValue(controller.nameColumn, row));
        assertEquals(busbarSection.getV(), ((Double) cellValue(controller.vColumn, row)).doubleValue());
        assertEquals(busbarSection.getAngle(), ((Double) cellValue(controller.angleColumn, row)).doubleValue());

        Hyperlink voltageLevelLink = (Hyperlink) cellGraphic(controller.voltageLevelColumn, row);
        assertEquals(voltageLevel.getNameOrId(), voltageLevelLink.getText());

        Hyperlink substationLink = (Hyperlink) cellGraphic(controller.substationColumn, row);
        assertEquals(voltageLevel.getSubstation().orElseThrow().getNameOrId(), substationLink.getText());
    }

    @Test
    void substationColumnIsNullForABusbarSectionInAVoltageLevelWithoutSubstation() {
        VoltageLevel voltageLevel = network.newVoltageLevel()
                .setId("VL_NO_SUBSTATION")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.NODE_BREAKER)
                .add();
        BusbarSection busbarSection = voltageLevel.getNodeBreakerView().newBusbarSection()
                .setId("BBS_NO_SUBSTATION")
                .setNode(0)
                .add();

        interact(() -> mainModel.setUpdate());

        assertNull(cellGraphic(controller.substationColumn, rowOf(busbarSection)));
        assertNull(cellValue(controller.countryColumn, rowOf(busbarSection)));
    }

    @Test
    void goToBusbarSectionSelectsAndClearsSelection() {
        BusbarSection busbarSection = busbarSection("S2VL1_BBS");
        int row = rowOf(busbarSection);

        interact(() -> controller.goToBusbarSection(busbarSection));
        List<TablePosition> selectedCells = controller.busbarSectionsTableView.getSelectionModel().getSelectedCells();
        assertEquals(1, selectedCells.size());
        assertEquals(row, selectedCells.get(0).getRow());

        interact(() -> controller.goToBusbarSection(null));
        assertTrue(controller.busbarSectionsTableView.getSelectionModel().getSelectedCells().isEmpty());
    }

    @Test
    void clickingVoltageLevelLinkNavigatesToIt() {
        BusbarSection busbarSection = busbarSection("S1VL2_BBS1");
        VoltageLevel voltageLevel = busbarSection.getTerminal().getVoltageLevel();
        Hyperlink link = (Hyperlink) cellGraphic(controller.voltageLevelColumn, rowOf(busbarSection));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(voltageLevel, ((ContainerNavigationState) event.state()).getContainer());
    }

    @Test
    void clickingSubstationLinkNavigatesToIt() {
        BusbarSection busbarSection = busbarSection("S1VL2_BBS1");
        Substation substation = busbarSection.getTerminal().getVoltageLevel().getSubstation().orElseThrow();
        Hyperlink link = (Hyperlink) cellGraphic(controller.substationColumn, rowOf(busbarSection));

        interact(link::fire);

        NavigationEvent event = mainModel.navigationEventProperty().getValue();
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        assertEquals(substation, ((ContainerNavigationState) event.state()).getContainer());
    }
}
