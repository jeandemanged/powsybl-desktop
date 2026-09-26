/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ParametersControllerTest extends AbstractHeadlessApplicationTest {

    private ParametersController controller;
    private TabPane tabPane;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/parameters/parameters-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        tabPane = (TabPane) root;

        mainModel = new MainModel();
        controller.setMainModel(mainModel);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @Test
    void hostsParametersTabsInOrder() {
        List<Tab> tabs = tabPane.getTabs();
        assertEquals(6, tabs.size());
        assertEquals("Network Import", tabs.get(0).getText());
        assertEquals("Network Export", tabs.get(1).getText());
        assertEquals("Single Line Diagram", tabs.get(2).getText());
        assertEquals("Network Area Diagram", tabs.get(3).getText());
        assertEquals("Load Flow", tabs.get(4).getText());
        assertEquals("Security Analysis", tabs.get(5).getText());
    }

    @Test
    void networkImportTabListsIidmFirstAndCgmes() {
        ListView<?> formatList = from(tabPane.getTabs().get(0).getContent()).lookup(".list-view").queryListView();
        assertEquals("IIDM", formatList.getItems().getFirst());
        assertTrue(formatList.getItems().contains("CGMES"));
    }

    @Test
    void networkExportTabGroupsIidmFormats() {
        ListView<?> formatList = from(tabPane.getTabs().get(1).getContent()).lookup(".list-view").queryListView();
        assertEquals("IIDM", formatList.getItems().getFirst());
        assertFalse(formatList.getItems().contains("XIIDM"));
        assertFalse(formatList.getItems().contains("BIIDM"));
        assertFalse(formatList.getItems().contains("JIIDM"));
    }

    @Test
    void editingImportParameterWritesIntoMainModel() {
        CheckBox checkBox = from(tabPane.getTabs().get(0).getContent()).lookup(".check-box").query();
        boolean initial = checkBox.isSelected();
        clickOn(checkBox);
        assertEquals(String.valueOf(!initial), mainModel.getNetworkImportParameters("IIDM").values().iterator().next());
    }

    @Test
    void embeddedControllersShareMainModelParameters() {
        assertSame(mainModel.loadFlowParametersProperty().get(),
                mainModel.securityAnalysisParametersProperty().get().getLoadFlowParameters());
    }
}
