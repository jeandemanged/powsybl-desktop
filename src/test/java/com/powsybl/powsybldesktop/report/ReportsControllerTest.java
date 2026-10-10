/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.report;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ReportsControllerTest extends AbstractHeadlessApplicationTest {

    private ReportsController controller;
    private MainModel mainModel;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/report/reports-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        mainModel = new MainModel();
        mainModel.addReport(ReportNode.newRootReportNode()
                .withAllResourceBundlesFromClasspath()
                .withMessageTemplate("desktop.loadFlow")
                .build());
        controller.setMainModel(mainModel);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @Test
    void clearingReportsClearsTheDetailTree() {
        TreeView<?> detailTree = lookup("#treeView").queryAs(TreeView.class);
        assertNotNull(detailTree.getRoot(), "the newest report is selected by default");

        interact(mainModel::clearReports);

        assertNull(detailTree.getRoot());
    }

    @Test
    void severityTogglesEditAndFollowGuiParameters() {
        ToggleGroup severities = ((ToggleButton) lookup(".toggle-button").query()).getToggleGroup();
        ToggleButton warn = (ToggleButton) severities.getToggles().stream()
                .filter(toggle -> "WARN".equals(toggle.getUserData())).findFirst().orElseThrow();
        clickOn(warn);
        assertEquals("WARN", mainModel.getParametersModel().getGuiParameters().getReportsMinSeverity());
        assertEquals(1, mainModel.getParametersModel().guiParametersRevisionProperty().get());

        // as when edited from the parameters view
        interact(() -> {
            mainModel.getParametersModel().getGuiParameters().setReportsMinSeverity("DEBUG");
            mainModel.getParametersModel().guiParametersChanged();
        });
        assertEquals("DEBUG", severities.getSelectedToggle().getUserData());
    }
}
