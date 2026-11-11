/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.sensitivity;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.sensitivity.SensitivityElements;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorMatrix;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorsIO;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SensitivityFactorsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = EurostagTutorialExample1Factory.create();
    private NetworkStudy study;
    private SensitivityFactorsController controller;

    @Override
    public void start(Stage stage) throws Exception {
        MainModel mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        study = mainModel.getStudy(network);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/ui/sensitivity/sensitivity-factors-view.fxml"),
                Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        controller.setSceneModel(SceneModel.main(mainModel));
        stage.setScene(new Scene(root, 1000, 600));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private static SensitivityFactorMatrix matrix(List<String> functionIds, List<String> variableIds, boolean variableSet) {
        return new SensitivityFactorMatrix("m", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1, functionIds, SensitivityVariableType.INJECTION_ACTIVE_POWER,
                variableIds, variableSet, ContingencyContext.all());
    }

    private ListCell<?> firstCell(String listId) {
        return lookup("#" + listId + " .list-cell").query();
    }

    private Label problemsLabel() {
        return lookup(".alert-label").query();
    }

    @Test
    void addingAMatrixSelectsItAndShowsItsForm() {
        interact(() -> controller.addMenuButton.getItems().getFirst().fire());
        assertEquals(1, study.getSensitivityFactorMatrices().size());
        assertEquals(Messages.get("sensitivityFactors.matrix.item", Messages.get("sensitivityFactors.matrix.defaultName", 1), 0),
                firstCell("matricesListView").getText());
        assertEquals(Messages.get("sensitivityFactors.matrix.defaultName", 1), lookup(TextField.class::isInstance).<TextField>query().getText());
        assertFalse(problemsLabel().isVisible());
    }

    @Test
    void aMatrixOpenLoadFlowWouldFailOnIsMarkedAndTellsWhy() {
        interact(() -> {
            study.getSensitivityFactorMatrices().add(matrix(List.of("NHV1_NHV2_1", "GEN"), List.of("GEN"), false));
            controller.matricesListView.getSelectionModel().selectFirst();
        });
        assertNotNull(firstCell("matricesListView").getGraphic());
        assertTrue(problemsLabel().isVisible());
        assertTrue(problemsLabel().getText().contains("GEN"), problemsLabel().getText());

        interact(() -> study.getSensitivityFactorMatrices().set(0, matrix(List.of("NHV1_NHV2_1"), List.of("GEN"), false)));
        assertNull(firstCell("matricesListView").getGraphic());
    }

    @Test
    void renamingAVariableSetRenamesItInTheMatrices() {
        interact(() -> {
            study.getSensitivityVariableSets().add(new SensitivityVariableSet("GLSK", List.of(new WeightedSensitivityVariable("GEN", 1))));
            study.getSensitivityFactorMatrices().add(matrix(List.of("NHV1_NHV2_1"), List.of("GLSK"), true));
            controller.tabPane.getSelectionModel().select(controller.variableSetsTab);
            controller.variableSetsListView.getSelectionModel().selectFirst();
        });
        interact(() -> {
            TextField idField = from(controller.formHost).lookup(TextField.class::isInstance).query();
            idField.setText("RENAMED");
            idField.fireEvent(new ActionEvent());
        });
        assertEquals("RENAMED", study.getSensitivityVariableSets().getFirst().getId());
        assertEquals(List.of("RENAMED"), study.getSensitivityFactorMatrices().getFirst().variableIds());
    }

    @Test
    void importedVariableSetsReplaceTheOnesOfTheSameId(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("sets.json");
        SensitivityFactorsIO.writeVariableSets(List.of(new SensitivityVariableSet("GLSK", List.of(new WeightedSensitivityVariable("LOAD", 2))),
                new SensitivityVariableSet("OTHER", List.of())), file);
        interact(() -> {
            study.getSensitivityVariableSets().add(new SensitivityVariableSet("GLSK", List.of(new WeightedSensitivityVariable("GEN", 1))));
            controller.run(controller::importVariableSets, file, "sensitivityFactors.import.failed");
        });
        assertEquals(List.of("GLSK", "OTHER"), study.getSensitivityVariableSets().stream().map(SensitivityVariableSet::getId).toList());
        assertEquals("LOAD", study.getSensitivityVariableSets().getFirst().getVariables().iterator().next().getId());
    }

    @Test
    void thePickerOffersTheEligibleElementsNotAlreadyPickedAndFiltersThem() {
        List<SensitivityElements.Candidate> candidates = SensitivityElements.candidates(network, SensitivityFunctionType.BRANCH_ACTIVE_POWER_1);
        ElementPickerDialog[] dialog = new ElementPickerDialog[1];
        interact(() -> dialog[0] = new ElementPickerDialog(null, "t", candidates, Set.of("NHV1_NHV2_1")));
        assertEquals(candidates.size() - 1, dialog[0].listView.getItems().size());
        interact(() -> {
            dialog[0].searchField.setText("nhv1_nhv2");
            dialog[0].selectShownButton.fire();
        });
        assertEquals(List.of("NHV1_NHV2_2"), dialog[0].listView.getItems().stream().map(SensitivityElements.Candidate::id).toList());
        assertEquals(List.of("NHV1_NHV2_2"), dialog[0].getResultConverter().call(ButtonType.OK));
        assertEquals(List.of(), dialog[0].getResultConverter().call(ButtonType.CANCEL));
    }
}
