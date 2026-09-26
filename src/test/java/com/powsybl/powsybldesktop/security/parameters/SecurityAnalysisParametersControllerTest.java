/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.security.parameters;

import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.security.SecurityAnalysisParameters;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SecurityAnalysisParametersControllerTest extends AbstractHeadlessApplicationTest {

    private SecurityAnalysisParametersController controller;
    private ObjectProperty<SecurityAnalysisParameters> params;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/security/parameters/sa-parameters.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();

        SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
        parameters.addExtension(OpenSecurityAnalysisParameters.class, new OpenSecurityAnalysisParameters()); // mirrors MainModel's own wiring
        params = new SimpleObjectProperty<>(parameters);
        controller.setSecurityAnalysisParametersProperty(params);

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
    void categoryListShowsGeneralFirstAndMiscellaneousLast() {
        @SuppressWarnings("unchecked")
        ListView<String> categories = (ListView<String>) controller.splitPane.getItems().get(0);
        assertEquals(List.of("General", "Increased Violations", "Modified Monitored Elements", "Miscellaneous"), categories.getItems());
    }

    @Test
    void toggleContingencyPropagationUpdatesModel() {
        CheckBox checkBox = (CheckBox) controlForLabel(selectCategory("General"), "Contingency Propagation");
        boolean before = OpenSecurityAnalysisParameters.getOrDefault(params.get()).isContingencyPropagation();

        clickOn(checkBox);

        assertEquals(!before, OpenSecurityAnalysisParameters.getOrDefault(params.get()).isContingencyPropagation());
    }

    @Test
    void debugDirEmptyTextMapsToNullNotEmptyString() {
        TextField debugDir = (TextField) controlForLabel(selectCategory("Miscellaneous"), "Debug Directory");
        assertNull(params.get().getDebugDir());
        assertEquals("", debugDir.getText());

        clickOn(debugDir).write("C:\\tmp\\sa-debug").push(KeyCode.TAB);
        assertEquals("C:\\tmp\\sa-debug", params.get().getDebugDir());

        clickOn(debugDir);
        interact(debugDir::clear);
        push(KeyCode.TAB);

        assertNull(params.get().getDebugDir(), "empty text must clear to null, not \"\"");
    }

    @Test
    void threadCountRejectsInvalidValueAndRevertsDisplay() {
        TextField threadCount = (TextField) controlForLabel(selectCategory("General"), "Thread Count");
        assertEquals("1", threadCount.getText());

        clickOn(threadCount);
        interact(() -> threadCount.replaceText(0, threadCount.getText().length(), "0"));
        push(KeyCode.TAB);

        assertEquals(1, OpenSecurityAnalysisParameters.getOrDefault(params.get()).getThreadCount());
        assertEquals("1", threadCount.getText());
    }

    @Test
    void contingencyActivePowerLossDistributionRoundTripsToExtension() {
        @SuppressWarnings("unchecked")
        ChoiceBox<String> choiceBox = (ChoiceBox<String>) controlForLabel(selectCategory("General"), "Contingency Active Power Loss Distribution");
        assertEquals("Default", choiceBox.getValue());

        interact(() -> choiceBox.getSelectionModel().select("Default"));

        assertEquals("Default", OpenSecurityAnalysisParameters.getOrDefault(params.get()).getContingencyActivePowerLossDistribution());
    }

    @Test
    void flowProportionalThresholdRoundTripsToParameters() {
        TextField textField = (TextField) controlForLabel(selectCategory("Increased Violations"), "Flow Proportional Threshold");
        assertEquals(Double.toString(params.get().getIncreasedViolationsParameters().getFlowProportionalThreshold()), textField.getText());

        clickOn(textField);
        interact(textField::clear);
        write("0.25").push(KeyCode.TAB);

        assertEquals(0.25, params.get().getIncreasedViolationsParameters().getFlowProportionalThreshold());
    }

    @Test
    void exportThenImportRoundTripsParameters(@TempDir Path tempDir) {
        Path file = tempDir.resolve("sa-params.json");
        interact(() -> {
            params.get().setIntermediateResultsInOperatorStrategy(true);
            controller.exportTo(file);
        });
        assertTrue(Files.exists(file));

        SecurityAnalysisParameters fresh = new SecurityAnalysisParameters();
        interact(() -> {
            params.setValue(fresh);
            controller.importFrom(file);
        });

        assertTrue(params.get().getIntermediateResultsInOperatorStrategy());
    }
}
