/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SubstationsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    private SubstationsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/network/substations-view.fxml"), Messages.bundle());
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

    private TreeView<Object> treeView() {
        return (TreeView<Object>) lookup(".tree-view").queryAs(TreeView.class);
    }

    private TabPane tabPane() {
        return (TabPane) lookup(".tab-pane").queryAs(TabPane.class);
    }

    private static TreeItem<Object> findItem(TreeItem<Object> item, Object value) {
        if (Objects.equals(item.getValue(), value)) {
            return item;
        }
        for (TreeItem<Object> child : item.getChildren()) {
            TreeItem<Object> found = findItem(child, value);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void select(Container<?> container) {
        interact(() -> treeView().getSelectionModel().select(findItem(treeView().getRoot(), container)));
    }

    private boolean hasTabWithText(String text) {
        return tabPane().getTabs().stream().anyMatch(tab -> text.equals(tab.getText()));
    }

    private Tab tabWithText(String text) {
        return tabPane().getTabs().stream().filter(tab -> text.equals(tab.getText())).findFirst().orElseThrow();
    }

    @Test
    void onlyTabsWithMatchingEquipmentAreShown() {
        select(network.getVoltageLevel("VL1"));

        assertTrue(hasTabWithText(Messages.get("main.toolbar.generators")), "VL1 has a generator, so its tab should show");
        assertFalse(hasTabWithText(Messages.get("main.toolbar.shuntCompensators")), "VL1 has no shunt compensator");

        select(network.getVoltageLevel("VL9"));

        assertTrue(hasTabWithText(Messages.get("main.toolbar.shuntCompensators")), "VL9 has a shunt compensator, so its tab should show");
    }

    @Test
    void substationsVoltageLevelsAndComponentsAreNeverShownAsTabs() {
        select(network.getVoltageLevel("VL1"));

        assertFalse(hasTabWithText(Messages.get("main.toolbar.substationsTable")));
        assertFalse(hasTabWithText(Messages.get("main.toolbar.voltageLevelsTable")));
        assertFalse(hasTabWithText(Messages.get("main.toolbar.components")));
    }

    @Test
    void selectingAnEquipmentTabRecordsItInNavigationHistory() {
        VoltageLevel vl1 = network.getVoltageLevel("VL1");
        select(vl1);

        interact(() -> tabPane().getSelectionModel().select(tabWithText(Messages.get("main.toolbar.generators"))));

        // tab selection is recorded in history (like the tree selection above) without becoming the
        // "current" navigation event, since it doesn't need to re-trigger MainController's view swap
        NavigationEvent event = mainModel.getNavigationPast().get(mainModel.getNavigationPast().size() - 1);
        assertEquals(NavigationType.SUBSTATIONS, event.navigationType());
        ContainerNavigationState state = (ContainerNavigationState) event.state();
        assertEquals(vl1, state.getContainer());
        assertEquals(ContainerNavigationState.ContainerTab.GENERATORS, state.getTab());
    }

    @Test
    void navigateToFallsBackToSingleLineDiagramWhenTargetTabIsNotShown() {
        VoltageLevel vl1 = network.getVoltageLevel("VL1");

        interact(() -> controller.navigateTo(vl1, ContainerNavigationState.ContainerTab.SHUNT_COMPENSATORS));

        assertEquals(Messages.get("substations.tab.singleLineDiagram"), tabPane().getSelectionModel().getSelectedItem().getText());
    }

    @Test
    void navigateToRestoresASelectedEquipmentTab() {
        VoltageLevel vl9 = network.getVoltageLevel("VL9");

        interact(() -> controller.navigateTo(vl9, ContainerNavigationState.ContainerTab.SHUNT_COMPENSATORS));

        assertEquals(Messages.get("main.toolbar.shuntCompensators"), tabPane().getSelectionModel().getSelectedItem().getText());
    }
}
