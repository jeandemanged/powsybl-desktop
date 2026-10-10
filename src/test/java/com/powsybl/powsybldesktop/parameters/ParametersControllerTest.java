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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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
    private Button saveButton;

    @TempDir
    Path tempDir;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/parameters/parameters-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        tabPane = (TabPane) root.lookup("#tabPane");

        mainModel = new MainModel();
        controller.setMainModel(mainModel);
        controller.setConfigPath(tempDir.resolve("config.json"));

        stage.setScene(new Scene(root));
        stage.show();
        // toolbar items are only in the scene graph once the ToolBar skin is created, on show
        saveButton = (Button) root.lookup("#saveButton");
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @Test
    void hostsParametersTabsInOrder() {
        List<Tab> tabs = tabPane.getTabs();
        assertEquals(7, tabs.size());
        assertEquals("User Interface", tabs.get(0).getText());
        assertEquals("Network Import", tabs.get(1).getText());
        assertEquals("Network Export", tabs.get(2).getText());
        assertEquals("Single Line Diagram", tabs.get(3).getText());
        assertEquals("Network Area Diagram", tabs.get(4).getText());
        assertEquals("Load Flow", tabs.get(5).getText());
        assertEquals("Security Analysis", tabs.get(6).getText());
    }

    @Test
    void guiTabEditsAndFollowsGuiParameters() {
        GuiParameters gui = mainModel.getParametersModel().getGuiParameters();
        // the first check box is the first voltage range of the Map category
        CheckBox checkBox = from(tabPane.getTabs().get(0).getContent()).lookup(".check-box").query();
        clickOn(checkBox);
        assertEquals(1, gui.getMapHiddenBaseVoltages().size());
        assertEquals(1, mainModel.getParametersModel().guiParametersRevisionProperty().get());
        assertFalse(saveButton.isDisabled());

        // as when edited from the map view
        interact(() -> {
            gui.setMapHiddenBaseVoltages(Set.of());
            mainModel.getParametersModel().guiParametersChanged();
        });
        assertTrue(checkBox.isSelected());
    }

    @Test
    void networkImportTabListsIidmFirstAndCgmes() {
        ListView<?> formatList = from(tabPane.getTabs().get(1).getContent()).lookup(".list-view").queryListView();
        assertEquals("IIDM", formatList.getItems().getFirst());
        assertTrue(formatList.getItems().contains("CGMES"));
    }

    @Test
    void networkExportTabGroupsIidmFormats() {
        ListView<?> formatList = from(tabPane.getTabs().get(2).getContent()).lookup(".list-view").queryListView();
        assertEquals("IIDM", formatList.getItems().getFirst());
        assertFalse(formatList.getItems().contains("XIIDM"));
        assertFalse(formatList.getItems().contains("BIIDM"));
        assertFalse(formatList.getItems().contains("JIIDM"));
    }

    @Test
    void editingImportParameterWritesIntoMainModel() {
        interact(() -> tabPane.getSelectionModel().select(1));
        CheckBox checkBox = from(tabPane.getTabs().get(1).getContent()).lookup(".check-box").query();
        boolean initial = checkBox.isSelected();
        clickOn(checkBox);
        assertEquals(String.valueOf(!initial), mainModel.getParametersModel().getNetworkImportParameters("IIDM").values().iterator().next());
    }

    @Test
    void editingDiagramParametersBumpsMainModelRevisions() {
        interact(() -> tabPane.getSelectionModel().select(3));
        clickOn(from(tabPane.getTabs().get(3).getContent()).lookup(".check-box").<CheckBox>query());
        assertEquals(1, mainModel.getParametersModel().sldParametersRevisionProperty().get());
        assertEquals(0, mainModel.getParametersModel().nadParametersRevisionProperty().get());

        interact(() -> tabPane.getSelectionModel().select(4));
        clickOn(from(tabPane.getTabs().get(4).getContent()).lookup(".check-box").<CheckBox>query());
        assertEquals(1, mainModel.getParametersModel().nadParametersRevisionProperty().get());
    }

    @Test
    void embeddedControllersShareMainModelParameters() {
        assertSame(mainModel.getParametersModel().loadFlowParametersProperty().get(),
                mainModel.getParametersModel().securityAnalysisParametersProperty().get().getLoadFlowParameters());
    }

    @Test
    void saveButtonIsDisabledWhileParametersMatchTheSavedFile() {
        assertFalse(saveButton.isDisabled());
        clickOn(saveButton);
        assertTrue(saveButton.isDisabled());
        assertTrue(Files.exists(tempDir.resolve("config.json")));

        interact(() -> tabPane.getSelectionModel().select(1));
        clickOn(from(tabPane.getTabs().get(1).getContent()).lookup(".check-box").<CheckBox>query());
        assertFalse(saveButton.isDisabled());
        clickOn(from(tabPane.getTabs().get(1).getContent()).lookup(".check-box").<CheckBox>query());
        assertTrue(saveButton.isDisabled());
    }

    @Test
    void importReplacesParametersAndRefreshesNetworkFormatForm() {
        Path path = tempDir.resolve("exported.json");
        interact(() -> tabPane.getSelectionModel().select(1));
        CheckBox checkBox = from(tabPane.getTabs().get(1).getContent()).lookup(".check-box").query();
        boolean initial = checkBox.isSelected();
        clickOn(checkBox);
        mainModel.getParametersModel().loadFlowParametersProperty().get().setDc(true);
        interact(() -> controller.exportTo(path));

        interact(() -> mainModel.getParametersModel().setParameters(ApplicationParameters.createDefault()));
        interact(() -> controller.importFrom(path));

        assertTrue(mainModel.getParametersModel().loadFlowParametersProperty().get().isDc());
        CheckBox refreshed = from(tabPane.getTabs().get(1).getContent()).lookup(".check-box").query();
        assertEquals(!initial, refreshed.isSelected());
    }

    @Test
    void importOfInvalidFileKeepsParametersAndNotifies() throws IOException {
        Path path = tempDir.resolve("invalid.json");
        Files.writeString(path, "[]");
        mainModel.getParametersModel().loadFlowParametersProperty().get().setDc(true);

        interact(() -> controller.importFrom(path));

        assertTrue(mainModel.getParametersModel().loadFlowParametersProperty().get().isDc());
        assertEquals(1, mainModel.getNotificationsModel().getNotifications().size());
    }
}
