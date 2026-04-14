/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworksControllerEmptyStateTest extends AbstractHeadlessApplicationTest {

    private NetworksController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/networks-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        mainModel = new MainModel();
        controller.setMainModel(mainModel);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @SuppressWarnings("unchecked")
    private TreeView<Network> treeView() {
        return (TreeView<Network>) lookup(".tree-view").queryAs(TreeView.class);
    }

    private Label noNetworksLabel() {
        return lookup("#noNetworksLabel").queryAs(Label.class);
    }

    private BorderPane networkDetailsPane() {
        return lookup("#networkDetailsPane").queryAs(BorderPane.class);
    }

    private StackPane noNetworkSelectedPane() {
        return (StackPane) lookup("#noNetworkSelectedPane").queryAs(StackPane.class);
    }

    @SuppressWarnings("unchecked")
    private TableView<Pair<String, String>> networkInfoTable() {
        return (TableView<Pair<String, String>>) lookup("#networkInfoTable").queryAs(TableView.class);
    }

    @Test
    void noNetworksLabelIsShownWhenNoNetworkIsLoaded() {
        assertTrue(noNetworksLabel().isVisible());
    }

    @Test
    void detailsPaneShowsNoNetworkSelected() {
        assertFalse(networkDetailsPane().isVisible());
        assertTrue(noNetworkSelectedPane().isVisible());
        assertTrue(networkInfoTable().getItems().isEmpty());
    }

    @Test
    void clickingOnEmptyTreeViewDoesNotThrow() {
        clickOn(treeView());
        interact(() -> { });

        assertNull(mainModel.getNetwork());
    }

    @Test
    void noNetworksLabelIsHiddenOnceANetworkIsAdded() {
        interact(() -> mainModel.addNetwork(IeeeCdfNetworkFactory.create14()));

        assertFalse(noNetworksLabel().isVisible());
    }

    @Test
    void firstNetworkAddedIsAutomaticallySelected() {
        Network ieee14 = IeeeCdfNetworkFactory.create14();
        interact(() -> mainModel.addNetwork(ieee14));

        assertEquals(ieee14, mainModel.getNetwork());
        assertTrue(networkDetailsPane().isVisible());
        assertFalse(noNetworkSelectedPane().isVisible());
    }

    @Test
    void secondNetworkAddedIsNotAutomaticallySelected() {
        Network ieee14 = IeeeCdfNetworkFactory.create14();
        Network ieee118 = IeeeCdfNetworkFactory.create118();
        interact(() -> mainModel.addNetwork(ieee14));
        interact(() -> mainModel.addNetwork(ieee118));

        assertEquals(ieee14, mainModel.getNetwork());
    }
}
