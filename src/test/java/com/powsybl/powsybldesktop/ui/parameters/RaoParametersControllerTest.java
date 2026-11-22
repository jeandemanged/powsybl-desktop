/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.parameters;

import com.powsybl.openrao.raoapi.parameters.ObjectiveFunctionParameters.ObjectiveFunctionType;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.powsybldesktop.parameters.ParametersModel;
import com.powsybl.powsybldesktop.rao.RaoParametersSupport;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisMode;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class RaoParametersControllerTest extends AbstractHeadlessApplicationTest {

    private RaoParametersController controller;
    private ParametersModel parametersModel;
    private final List<Path> failedImports = new ArrayList<>();

    @TempDir
    Path tempDir;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/ui/parameters/rao-parameters.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        parametersModel = new ParametersModel();
        controller.setParametersModel(parametersModel);
        controller.setOnImportFailed((path, _) -> failedImports.add(path));
        stage.setScene(new Scene(root, 800, 600));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private RaoParameters parameters() {
        return parametersModel.raoParametersProperty().get();
    }

    // the control next to the label of the RAO parameter, in whichever category
    @SuppressWarnings("unchecked")
    private <N extends Node> N control(String param) {
        String text = Messages.get("rao.param." + param + ".label");
        for (GridPane grid : controller.categoryGrids.values()) {
            for (Node node : grid.getChildren()) {
                if (node instanceof Label label && text.equals(label.getText())) {
                    Integer row = GridPane.getRowIndex(label);
                    return (N) grid.getChildren().stream()
                            .filter(other -> Objects.equals(GridPane.getRowIndex(other), row) && Objects.equals(GridPane.getColumnIndex(other), 1))
                            .findFirst().orElseThrow();
                }
            }
        }
        throw new AssertionError("No field " + param);
    }

    private void type(String param, String text) {
        TextField field = control(param);
        interact(() -> {
            field.setText(text);
            field.fireEvent(new ActionEvent());
        });
    }

    @Test
    void editsTheParametersInPlace() {
        RaoParameters parameters = parameters();
        ChoiceBox<ObjectiveFunctionType> objective = control("objectiveFunction");
        interact(() -> objective.setValue(ObjectiveFunctionType.MAX_MIN_MARGIN));
        type("availableCpus", "4");

        assertSame(parameters, parameters());
        assertEquals(ObjectiveFunctionType.MAX_MIN_MARGIN, parameters.getObjectiveFunctionParameters().getType());
        assertEquals(4, RaoParametersSupport.searchTree(parameters).getMultithreadingParameters().getAvailableCPUs());
        assertEquals(2, parametersModel.parametersRevisionProperty().get());
    }

    @Test
    void rejectsAnInvalidValue() {
        type("availableCpus", "0");

        assertEquals(1, RaoParametersSupport.searchTree(parameters()).getMultithreadingParameters().getAvailableCPUs());
        assertEquals("1", this.<TextField>control("availableCpus").getText());
    }

    @Test
    void modeIsTheApplicationsOwn() {
        ChoiceBox<SensitivityAnalysisMode> mode = control("mode");
        assertEquals("Same as load flow (AC)", mode.getConverter().toString(SensitivityAnalysisMode.LOAD_FLOW));

        interact(() -> mode.setValue(SensitivityAnalysisMode.DC));

        assertEquals(SensitivityAnalysisMode.DC, parametersModel.raoModeProperty().get());
        assertFalse(RaoParametersSupport.sensitivityParameters(parameters()).getLoadFlowParameters().isDc());
    }

    @Test
    void useOfFastRaoIsTheApplicationsOwn() {
        CheckBox fastRao = control("useFastRao");
        RaoParameters parameters = parameters();
        assertFalse(fastRao.isSelected());

        interact(() -> fastRao.setSelected(true));
        type("numberOfCnecsToAdd", "40");
        type("numberOfCnecsToAdd", "0");

        assertTrue(parametersModel.fastRaoProperty().get());
        assertSame(parameters, parameters());
        assertEquals(40, RaoParametersSupport.fastRao(parameters()).getNumberOfCnecsToAdd());
        assertEquals("40", this.<TextField>control("numberOfCnecsToAdd").getText());

        // as after a reset or an import of all the parameters
        interact(() -> parametersModel.fastRaoProperty().set(false));
        assertFalse(fastRao.isSelected());
    }

    // removing an optional section replaces the parameters, still wired to the load flow parameters
    @Test
    void mnecCheckBoxAddsAndRemovesTheSection() {
        CheckBox mnec = control("mnec");
        RaoParameters parameters = parameters();
        assertFalse(mnec.isSelected());

        interact(() -> mnec.setSelected(true));
        assertTrue(RaoParametersSupport.hasMnec(parameters));
        assertSame(parameters, parameters());

        interact(() -> mnec.setSelected(false));
        assertFalse(RaoParametersSupport.hasMnec(parameters()));
        assertNotSame(parameters, parameters());
        assertSame(parametersModel.loadFlowParametersProperty().get(),
                RaoParametersSupport.sensitivityParameters(parameters()).getLoadFlowParameters());
        assertFalse(mnec.isSelected());
    }

    @Test
    void editingAnOptionalSectionsValueAddsIt() {
        type("shiftedViolationPenalty", "500");

        assertTrue(RaoParametersSupport.hasCostlyMinMargins(parameters()));
        assertEquals(500., RaoParametersSupport.searchTree(parameters()).getMinMarginsParameters().orElseThrow().getShiftedViolationPenalty());
        assertTrue(this.<CheckBox>control("costlyMinMargins").isSelected());

        interact(() -> this.<CheckBox>control("costlyMinMargins").setSelected(false));
        assertFalse(RaoParametersSupport.hasCostlyMinMargins(parameters()));
        assertEquals("1000.0", this.<TextField>control("shiftedViolationPenalty").getText());
    }

    @Test
    void exportsAndImportsOpenRaoJson() throws IOException {
        Path file = tempDir.resolve("rao.json");
        interact(() -> parameters().getObjectiveFunctionParameters().setType(ObjectiveFunctionType.MAX_MIN_MARGIN));
        interact(() -> controller.exportTo(file));
        interact(() -> parametersModel.raoParametersProperty().set(RaoParametersSupport.createDefault()));

        interact(() -> controller.importFrom(file));

        assertEquals(ObjectiveFunctionType.MAX_MIN_MARGIN, parameters().getObjectiveFunctionParameters().getType());
        assertSame(parametersModel.loadFlowParametersProperty().get(),
                RaoParametersSupport.sensitivityParameters(parameters()).getLoadFlowParameters());
        assertEquals(ObjectiveFunctionType.MAX_MIN_MARGIN, this.<ChoiceBox<ObjectiveFunctionType>>control("objectiveFunction").getValue());

        Path invalid = tempDir.resolve("invalid.json");
        Files.writeString(invalid, "{\"objective-function\": {\"type\": \"MIN_REGRET\"}}");
        RaoParameters before = parameters();
        interact(() -> controller.importFrom(invalid));
        assertEquals(List.of(invalid), failedImports);
        assertSame(before, parameters());
    }
}
