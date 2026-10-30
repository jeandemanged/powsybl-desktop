/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.navigation.BusbarSectionNavigationState;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.navigation.NetworkNavigationState;
import com.powsybl.powsybldesktop.navigation.TieLineNavigationState;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.testutil.SubnetworksTestFactory;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Which network navigation selects, in a root network with subnetworks.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MainControllerSubnetworkNavigationTest extends AbstractHeadlessApplicationTest {

    private final Network root = SubnetworksTestFactory.create();
    private final Network nodeBreaker = root.getSubnetwork(SubnetworksTestFactory.NODE_BREAKER);
    private final Network busBreaker = root.getSubnetwork(SubnetworksTestFactory.BUS_BREAKER);
    private final BusbarSection busbarSection = nodeBreaker.getBusbarSectionStream().findFirst().orElseThrow();

    private MainModel mainModel;
    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        mainModel = new MainModel();
        controller = new MainController(mainModel);
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/main-view.fxml"), Messages.bundle());
        loader.setControllerFactory(_ -> controller);
        Parent root = loader.load();
        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private void navigate(NavigationEvent event) {
        interact(() -> mainModel.getNavigationHistory().navigate(event));
    }

    private void selectNetwork(Network network) {
        navigate(NavigationEvent.create(NavigationType.NETWORKS, NetworkNavigationState.create(network)));
    }

    private void navigateToBusbarSection() {
        navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSBAR_SECTIONS, BusbarSectionNavigationState.create(busbarSection)));
    }

    @Test
    void navigationWithinTheSelectedSubnetworkKeepsIt() {
        interact(() -> mainModel.addNetwork(root));
        selectNetwork(nodeBreaker);

        navigateToBusbarSection();
        assertSame(nodeBreaker, mainModel.getNetwork());
        navigate(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(busbarSection.getTerminal().getVoltageLevel())));
        assertSame(nodeBreaker, mainModel.getNetwork());
    }

    // a tie line between subnetworks is held by the root network, only shown with it selected
    @Test
    void rootNetworkSelectedToShowItsOwnEquipmentIsLeftForTheNextSubnetworkEquipment() {
        interact(() -> mainModel.addNetwork(root));
        selectNetwork(nodeBreaker);

        navigate(NavigationEvent.create(NavigationType.NETWORK_TABLE_TIE_LINES, TieLineNavigationState.create(root.getTieLine(SubnetworksTestFactory.TIE_LINE))));
        assertSame(root, mainModel.getNetwork());

        navigateToBusbarSection();
        assertSame(nodeBreaker, mainModel.getNetwork());
    }

    @Test
    void rootNetworkSelectedByTheUserIsKept() {
        interact(() -> mainModel.addNetwork(root));
        selectNetwork(root);

        navigateToBusbarSection();
        assertSame(root, mainModel.getNetwork());
    }

    @Test
    void anotherSubnetworksEquipmentSelectsIt() {
        interact(() -> mainModel.addNetwork(root));
        selectNetwork(nodeBreaker);

        navigate(NavigationEvent.create(NavigationType.SUBSTATIONS, ContainerNavigationState.create(busBreaker.getVoltageLevel("VLHV1"))));
        assertSame(busBreaker, mainModel.getNetwork());
    }
}
