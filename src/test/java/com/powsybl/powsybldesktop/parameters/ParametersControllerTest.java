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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
    void hostsLoadFlowAndSecurityAnalysisTabsInOrder() {
        List<Tab> tabs = tabPane.getTabs();
        assertEquals(2, tabs.size());
        assertEquals("Load Flow", tabs.get(0).getText());
        assertEquals("Security Analysis", tabs.get(1).getText());
    }

    @Test
    void embeddedControllersShareMainModelParameters() {
        assertSame(mainModel.loadFlowParametersProperty().get(),
                mainModel.securityAnalysisParametersProperty().get().getLoadFlowParameters());
    }
}
