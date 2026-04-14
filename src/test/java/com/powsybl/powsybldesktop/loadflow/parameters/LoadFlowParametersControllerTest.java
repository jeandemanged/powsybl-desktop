/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.loadflow.parameters;

import com.powsybl.iidm.network.Country;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class LoadFlowParametersControllerTest extends AbstractHeadlessApplicationTest {

    private LoadFlowParametersController controller;
    private ObjectProperty<LoadFlowParameters> params;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/loadflow/lf-parameters.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        LoadFlowParameters parameters = new LoadFlowParameters();
        OpenLoadFlowParameters.create(parameters); // mirrors MainModel's own wiring
        params = new SimpleObjectProperty<>(parameters);
        controller.setLoadFlowParametersProperty(params);

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @SuppressWarnings("unchecked")
    private GridPane selectCategory(String title) {
        ListView<String> categories = (ListView<String>) controller.splitPane.getItems().get(0);
        StackPane detail = (StackPane) controller.splitPane.getItems().get(1);
        interact(() -> categories.getSelectionModel().select(title));
        ScrollPane scrollPane = (ScrollPane) detail.getChildren().get(0);
        return (GridPane) scrollPane.getContent();
    }

    private Control controlForLabel(GridPane grid, String labelText) {
        for (Node node : grid.getChildren()) {
            if (node instanceof Label label && labelText.equals(label.getText())) {
                Integer rowIndex = GridPane.getRowIndex(node);
                int row = rowIndex == null ? 0 : rowIndex;
                return grid.getChildren().stream()
                        .filter(n -> n instanceof Control && !(n instanceof Label))
                        .filter(n -> {
                            Integer r = GridPane.getRowIndex(n);
                            return (r == null ? 0 : r) == row;
                        })
                        .map(n -> (Control) n)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No control next to label: " + labelText));
            }
        }
        throw new AssertionError("No label found: " + labelText);
    }

    @Test
    void categoryListShowsAllExpectedTitlesInOrder() {
        @SuppressWarnings("unchecked")
        ListView<String> categories = (ListView<String>) controller.splitPane.getItems().get(0);
        assertEquals(List.copyOf(LoadFlowParametersController.buildCategoryTitles().values()), categories.getItems());
    }

    @Test
    void toggleTransformerSplitShuntAdmittanceUpdatesModel() {
        GridPane grid = selectCategory("Model");
        CheckBox checkBox = (CheckBox) controlForLabel(grid, "Transformer Split Shunt Admittance");
        boolean before = params.get().isTwtSplitShuntAdmittance();

        clickOn(checkBox);

        assertEquals(!before, params.get().isTwtSplitShuntAdmittance());
    }

    @Test
    void debugDirEmptyTextMapsToNullNotEmptyString() {
        GridPane grid = selectCategory("Debug");
        TextField debugDir = (TextField) controlForLabel(grid, "Debug Directory");
        assertNull(params.get().getDebugDir());
        assertEquals("", debugDir.getText());

        clickOn(debugDir).write("C:\\tmp\\lf-debug").push(KeyCode.TAB);
        assertEquals("C:\\tmp\\lf-debug", params.get().getDebugDir());

        clickOn(debugDir);
        interact(debugDir::clear);
        push(KeyCode.TAB);

        assertNull(params.get().getDebugDir(), "empty text must clear to null, not \"\"");
    }

    @Test
    void countriesToBalanceCheckModelRoundTripsToParameters() {
        GridPane grid = selectCategory("Slack Distribution");
        @SuppressWarnings("unchecked")
        CheckComboBox<String> combo = (CheckComboBox<String>) controlForLabel(grid, "Countries To Balance");

        interact(() -> {
            combo.getCheckModel().check("FR");
            combo.getCheckModel().check("BE");
        });

        assertEquals(Set.of(Country.FR, Country.BE), params.get().getCountriesToBalance());
    }

    @Test
    void exportThenImportRoundTripsParameters(@TempDir Path tempDir) {
        Path file = tempDir.resolve("lf-params.json");
        interact(() -> {
            params.get().setDc(true);
            controller.exportTo(file);
        });
        assertTrue(Files.exists(file));

        LoadFlowParameters fresh = new LoadFlowParameters();
        OpenLoadFlowParameters.create(fresh);
        interact(() -> {
            params.setValue(fresh);
            controller.importFrom(file);
        });

        assertTrue(params.get().isDc());
    }
}
