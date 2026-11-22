/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.tables;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisRun;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorMatrix;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SensitivityAnalysisResultsControllerTest extends AbstractHeadlessApplicationTest {

    private static final SensitivityFactorMatrix LINES = new SensitivityFactorMatrix("lines", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
            List.of("NHV1_NHV2_1", "NHV1_NHV2_2"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("GEN", "LOAD"), false, ContingencyContext.all());
    private static final SensitivityFactorMatrix TRANSFORMER = new SensitivityFactorMatrix("transformer", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
            List.of("NGEN_NHV1"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("GEN"), false, ContingencyContext.none());

    private SensitivityAnalysisResultsController controller;
    private NetworkStudy study;

    @Override
    public void start(Stage stage) throws Exception {
        Network network = EurostagTutorialExample1Factory.create();
        MainModel mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        study = mainModel.getStudy(network);
        study.getSensitivityFactorMatrices().setAll(LINES, TRANSFORMER);
        // the generator's sensitivities are 0, below the threshold: its injection is compensated by itself, the only one
        SensitivityAnalysisParameters parameters = new SensitivityAnalysisParameters().setFlowFlowSensitivityValueThreshold(1e-3);
        parameters.getLoadFlowParameters().setDc(true);
        SensitivityAnalysisRun run = SensitivityAnalysisRun.prepare(network, List.of(LINES, TRANSFORMER), List.of(),
                new DefaultContingencyList("lines", List.of(Contingency.line("NHV1_NHV2_2"))), parameters);
        study.setSensitivityAnalysisResult(run.compute().sensitivityAnalysisResult(), List.of(LINES, TRANSFORMER), true);
        run.restoreVariantAccess();

        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/ui/network/tables/sensitivity-analysis-results-view.fxml"), Messages.bundle());
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

    private String cellText(int row, String columnId) {
        return lookup(".table-row-cell").nth(row).lookup(".table-cell").queryAll().stream()
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> columnId.equals(cell.getTableColumn().getId()))
                .findFirst().orElseThrow().getText();
    }

    @Test
    void showsThePreContingencyStateFirstWithEachFactorOfItsMatrices() {
        assertEquals(2, controller.stateChoiceBox.getItems().size());
        assertEquals(Messages.get("sensitivityAnalysisResults.mode.dc"), controller.modeLabel.getText());
        assertFalse(controller.alertLabel.isVisible());
        assertEquals(5, controller.tableView.getItems().size());
        assertEquals("lines", cellText(0, "matrix"));
        assertEquals(Messages.get("sensitivity.function.branch_active_power_1"), cellText(0, "functionType"));
        assertEquals("NHV1_NHV2_1", cellText(0, "function"));
        assertEquals("GEN", cellText(0, "variable"));
        assertEquals(Messages.get("sensitivityAnalysisResults.belowThreshold"), cellText(0, "sensitivity"));
        assertEquals("LOAD", cellText(1, "variable"));
        assertFalse(cellText(1, "sensitivity").equals(Messages.get("sensitivityAnalysisResults.belowThreshold")));
        assertEquals("transformer", cellText(4, "matrix"));
    }

    @Test
    void aContingencyShowsTheFactorsOfItsContextOnly() {
        interact(() -> controller.stateChoiceBox.getSelectionModel().select(1));
        assertEquals(4, controller.tableView.getItems().size());
        assertTrue(controller.tableView.getItems().stream().allMatch(row -> row.matrixIndex() == 0));

        interact(() -> controller.matrixChoiceBox.getSelectionModel().select(Integer.valueOf(1)));
        assertEquals(0, controller.tableView.getItems().size());
    }

    @Test
    void tellsWhenTheFactorsChangedSinceTheRun() {
        interact(() -> study.getSensitivityFactorMatrices().remove(TRANSFORMER));
        assertTrue(controller.alertLabel.isVisible());
        interact(() -> study.getSensitivityFactorMatrices().add(TRANSFORMER));
        assertFalse(controller.alertLabel.isVisible());
    }
}
