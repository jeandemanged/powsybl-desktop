/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OverloadManagementSystem;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
import com.powsybl.powsybldesktop.network.edit.NetworkEdits;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.DisposableController;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The tables of grounds, areas, overload management systems and voltage angle limits, on the Eurostag tutorial network
 * with its areas, plus one of each other kind.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkObjectTablesTest extends AbstractHeadlessApplicationTest {

    private final Network network = EurostagTutorialExample1Factory.createWithTieLinesAndAreas();
    private MainModel mainModel;
    private Stage stage;
    private DisposableController controller;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        network.getVoltageLevel("VLLOAD").newGround().setId("GROUND").setBus("NLOAD").add();
        network.getSubstation("P2").newOverloadManagementSystem().setId("OMS").setEnabled(true)
                .setMonitoredElementId("NHV2_NLOAD").setMonitoredElementSide(ThreeSides.ONE)
                .newBranchTripping().setKey("T1").setBranchToOperateId("NHV2_NLOAD").setSideToOperate(TwoSides.TWO).setCurrentLimit(1000).setOpenAction(true).add()
                .add();
        network.newVoltageAngleLimit().setId("VAL").from(network.getLoad("LOAD").getTerminal())
                .to(network.getGenerator("GEN").getTerminal()).setHighLimit(30).add();
        mainModel = new MainModel();
        mainModel.addNetwork(network);
        stage.setScene(new Scene(new Pane(), 800, 500));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private <C extends DisposableController> C load(String fxml) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/network/tables/" + fxml), Messages.bundle());
        try {
            Parent root = loader.load();
            C loaded = loader.getController();
            interact(() -> {
                stage.getScene().setRoot(root);
                ((SceneView) loaded).setSceneModel(SceneModel.main(mainModel));
            });
            controller = loaded;
            return loaded;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void groundsAreListed() {
        GroundsController grounds = load("grounds-view.fxml");

        assertEquals(1, grounds.groundsTableView.getItems().size());
        assertEquals("GROUND", grounds.groundsTableView.getItems().getFirst().getId());
    }

    @Test
    void areasAreListedAndSelectable() {
        AreasController areas = load("areas-view.fxml");

        assertEquals(network.getAreaCount(), areas.areasTableView.getItems().size());
        Area area = areas.areasTableView.getItems().getFirst();
        interact(() -> areas.goToArea(area));
        assertEquals(area, areas.areasTableView.getSelectionModel().getSelectedItem());
    }

    @Test
    void overloadManagementSystemsFollowStructureChanges() {
        OverloadManagementSystemsController systems = load("overload-management-systems-view.fxml");
        OverloadManagementSystem system = network.getOverloadManagementSystem("OMS");
        assertEquals(1, systems.overloadManagementSystemsTableView.getItems().size());

        interact(() -> NetworkEdits.apply(mainModel, network, _ -> system.remove()));

        assertEquals(0, systems.overloadManagementSystemsTableView.getItems().size());
    }

    @Test
    void voltageAngleLimitsAreListed() {
        VoltageAngleLimitsController limits = load("voltage-angle-limits-view.fxml");

        assertEquals(1, limits.voltageAngleLimitsTableView.getItems().size());
        assertFalse(limits.voltageAngleLimitsTableView.getItems().getFirst().getLowLimit().isPresent());
    }
}
