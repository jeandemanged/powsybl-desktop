/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SwitchesControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = FourSubstationsNodeBreakerFactory.create();

    private SwitchesController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/tables/switches-view.fxml"), Messages.bundle());
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

    private Switch aSwitch(String id) {
        return network.getSwitch(id);
    }

    private int rowOf(Switch aSwitch) {
        return controller.switchesTableView.getItems().indexOf(aSwitch);
    }

    private Object cellValue(TableColumn<Switch, ?> column, int row) {
        return column.getCellObservableValue(row).getValue();
    }

    private <T> Node cellGraphic(TableColumn<Switch, T> column, int row) {
        TableCell<Switch, T> cell = (TableCell<Switch, T>) column.getCellFactory().call(column);
        cell.updateTableView(controller.switchesTableView);
        cell.updateTableColumn(column);
        // Cells rendered directly (not through the skin) have no TableRow by default; wire one up, before
        // updateIndex (which triggers updateItem), so getTableRow().getItem() resolves for cell factories
        // that rely on it (checkbox columns).
        TableRow<Switch> tableRow = new TableRow<>();
        tableRow.updateTableView(controller.switchesTableView);
        tableRow.updateIndex(row);
        cell.updateTableRow(tableRow);
        cell.updateIndex(row);
        return cell.getGraphic();
    }

    @Test
    void noRowsUntilAContainerIsSelected() {
        assertFalse(controller.hasRows());
        assertEquals(0, controller.switchesTableView.getItems().size());
    }

    @Test
    void tableIsFilteredToTheSelectedVoltageLevel() {
        interact(() -> controller.setContainer(network.getVoltageLevel("S4VL1")));

        assertTrue(controller.hasRows());
        assertEquals(6, controller.switchesTableView.getItems().size());
    }

    @Test
    void tableIsFilteredToEveryVoltageLevelOfTheSelectedSubstation() {
        interact(() -> controller.setContainer(network.getSubstation("S4")));

        assertEquals(6, controller.switchesTableView.getItems().size());
    }

    @Test
    void cellValueFactoriesReadTheExpectedFields() {
        interact(() -> controller.setContainer(network.getVoltageLevel("S4VL1")));
        Switch disconnector = aSwitch("S4VL1_BBS_LD6_DISCONNECTOR");
        int row = rowOf(disconnector);

        assertEquals(disconnector.getNameOrId(), cellValue(controller.nameColumn, row));
        assertEquals(SwitchKind.DISCONNECTOR, cellValue(controller.kindColumn, row));
    }

    @Test
    void togglingOpenCheckBoxOpensAndClosesTheSwitch() {
        interact(() -> controller.setContainer(network.getVoltageLevel("S4VL1")));
        Switch breaker = aSwitch("S4VL1_LD6_BREAKER");
        CheckBox checkBox = (CheckBox) cellGraphic(controller.openColumn, rowOf(breaker));
        assertFalse(breaker.isOpen());

        interact(checkBox::fire);
        assertTrue(breaker.isOpen());

        interact(checkBox::fire);
        assertFalse(breaker.isOpen());
    }

    @Test
    void togglingRetainedCheckBoxChangesTheSwitchRetainStatus() {
        interact(() -> controller.setContainer(network.getVoltageLevel("S4VL1")));
        Switch disconnector = aSwitch("S4VL1_BBS_LD6_DISCONNECTOR");
        CheckBox checkBox = (CheckBox) cellGraphic(controller.retainedColumn, rowOf(disconnector));
        assertFalse(disconnector.isRetained());

        interact(checkBox::fire);
        assertTrue(disconnector.isRetained());

        interact(checkBox::fire);
        assertFalse(disconnector.isRetained());
    }

    @Test
    void retainedCheckBoxIsDisabledForABusBreakerSwitchButOpenCheckBoxStaysEnabled() {
        VoltageLevel busBreakerVl = network.newVoltageLevel()
                .setId("VL_BUS_BREAKER")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add();
        busBreakerVl.getBusBreakerView().newBus().setId("BUS1").add();
        busBreakerVl.getBusBreakerView().newBus().setId("BUS2").add();
        busBreakerVl.getBusBreakerView().newSwitch()
                .setId("BUS_BREAKER_SWITCH")
                .setBus1("BUS1")
                .setBus2("BUS2")
                .setOpen(false)
                .add();

        interact(() -> controller.setContainer(busBreakerVl));
        Switch busBreakerSwitch = aSwitch("BUS_BREAKER_SWITCH");

        CheckBox retainedCheckBox = (CheckBox) cellGraphic(controller.retainedColumn, rowOf(busBreakerSwitch));
        assertTrue(retainedCheckBox.isDisable());

        CheckBox openCheckBox = (CheckBox) cellGraphic(controller.openColumn, rowOf(busBreakerSwitch));
        assertFalse(openCheckBox.isDisable());
    }

    @Test
    void hasRowsBecomesFalseAgainWhenContainerHasNoSwitches() {
        interact(() -> controller.setContainer(network.getVoltageLevel("S4VL1")));
        assertTrue(controller.hasRows());

        interact(() -> controller.setContainer(null));
        assertFalse(controller.hasRows());
    }
}
