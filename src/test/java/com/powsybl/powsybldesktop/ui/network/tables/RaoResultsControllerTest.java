/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.tables;

import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.rao.RaoResultAndReport;
import com.powsybl.powsybldesktop.rao.RaoRun;
import com.powsybl.powsybldesktop.rao.RaoTestCases;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class RaoResultsControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = RaoTestCases.network();
    private MainModel mainModel;
    private RaoResultsController controller;
    private NetworkStudy study;

    @Override
    public void start(Stage stage) throws Exception {
        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        study = mainModel.getStudy(network);
        Crac crac = RaoTestCases.crac(network);
        study.cracProperty().set(crac);
        RaoResultAndReport resultAndReport = RaoRun.prepare(network, crac, RaoTestCases.parameters(1), false).compute();
        study.setRaoResult(resultAndReport.raoResult(), resultAndReport.crac(), crac, false);

        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/ui/network/tables/rao-results-view.fxml"), Messages.bundle());
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

    private TableCell<?, ?> cell(TableView<?> table, int row, String columnId) {
        WaitForAsyncUtils.waitForFxEvents();
        // by index: the table reuses its cells, and keeps spare ones, when its rows are replaced
        return from(table).lookup(".table-cell").queryAll().stream()
                .map(node -> (TableCell<?, ?>) node)
                .filter(cell -> cell.isVisible() && cell.getIndex() == row && cell.getTableColumn() != null && columnId.equals(cell.getTableColumn().getId()))
                .findFirst().orElseThrow();
    }

    private String cellText(TableView<?> table, int row, String columnId) {
        return cell(table, row, columnId).getText();
    }

    private void showTab(int index) {
        interact(() -> controller.tabPane.getSelectionModel().select(index));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void summaryTellsTheStatusTheSecurityAndTheMode() {
        String summary = controller.summaryLabel.getText();
        assertTrue(summary.startsWith(Messages.get("raoResults.status.completed") + Messages.get("raoResults.flowsSecure")), summary);
        assertTrue(summary.contains(Messages.get("raoResults.mode.ac")), summary);
        assertFalse(controller.alertLabel.isVisible());
    }

    // the curative CNEC is the most limiting after optimization, its initial and after preventive margins negative
    @Test
    void cnecsMostLimitingFirstInTheUnitChosen() {
        TableView<?> table = controller.cnecsTable;
        assertEquals(2, table.getItems().size());
        assertEquals("L1 curative", cellText(table, 0, "cnec"));
        assertEquals("L1", cellText(table, 0, "element"));
        assertEquals(Messages.get("raoResults.stateName", "L2 out", "curative"), cellText(table, 0, "state"));
        assertEquals("150.0", cellText(table, 0, "upperBound"));
        assertEquals("175.3", cellText(table, 0, "initialFlow"));
        assertEquals("134.0", cellText(table, 0, "flow"));
        assertTrue(cell(table, 0, "preventiveMargin").getStyleClass().contains("rao-negative-margin"));
        assertFalse(cell(table, 0, "margin").getStyleClass().contains("rao-negative-margin"));
        assertEquals("L1 preventive", cellText(table, 1, "cnec"));
        assertEquals("-", cellText(table, 1, "preventiveFlow"));

        interact(() -> controller.unitChoiceBox.setValue(Unit.MEGAWATT));
        assertEquals("100.4", cellText(table, 0, "initialFlow"));

        interact(() -> controller.stateChoiceBox.setValue(controller.stateChoiceBox.getItems().stream()
                .filter(choice -> choice.state() != null && choice.state().isPreventive()).findFirst().orElseThrow()));
        assertEquals(1, table.getItems().size());
        assertEquals("L1 preventive", cellText(table, 0, "cnec"));
    }

    @Test
    void activatedActionsByState() {
        showTab(1);
        TableView<?> table = controller.actionsTable;

        assertEquals(2, table.getItems().size());
        assertEquals(Messages.get("raoResults.preventive"), cellText(table, 0, "state"));
        assertEquals(RaoTestCases.PST, cellText(table, 0, "action"));
        assertEquals(Messages.get("raoResults.actionType.pst"), cellText(table, 0, "type"));
        assertEquals("1", cellText(table, 0, "initialTap"));
        assertEquals("2", cellText(table, 0, "tap"));
        assertEquals("20.00", cellText(table, 0, "setPoint"));
        assertEquals(RaoTestCases.LOAD_SHEDDING, cellText(table, 1, "action"));
        assertEquals(Messages.get("raoResults.actionType.network"), cellText(table, 1, "type"));
        assertEquals("-", cellText(table, 1, "setPoint"));
    }

    @Test
    void costsOfTheInitialSituationThenByInstant() {
        showTab(2);
        TableView<?> table = controller.costsTable;

        assertEquals(1 + study.getRaoCrac().getSortedInstants().size(), table.getItems().size());
        assertEquals(Messages.get("raoResults.initial"), cellText(table, 0, "instant"));
        assertEquals("preventive", cellText(table, 1, "instant"));
    }

    // by one of the same id: OpenRAO's CRACs are equal by id
    @Test
    void replacingTheCracOutdatesTheResult() {
        interact(() -> study.cracProperty().set(RaoTestCases.crac(network)));

        assertTrue(controller.alertLabel.isVisible());
        assertEquals(Messages.get("raoResults.outdated"), controller.alertLabel.getText());
    }

    @Test
    void emptyWithoutResult() {
        Network other = RaoTestCases.network();
        interact(() -> {
            mainModel.addNetwork(other);
            mainModel.setNetwork(other);
        });

        assertEquals(Messages.get("raoResults.none"), controller.summaryLabel.getText());
        assertEquals(List.of(), controller.cnecsTable.getItems());
        assertEquals(Messages.get("raoResults.none"), ((Label) controller.cnecsTable.getPlaceholder()).getText());
        assertTrue(controller.stateChoiceBox.isDisabled());
        assertEquals(List.of(), controller.actionsTable.getItems());
        assertEquals(List.of(), controller.costsTable.getItems());
        assertFalse(controller.alertLabel.isVisible());
    }
}
