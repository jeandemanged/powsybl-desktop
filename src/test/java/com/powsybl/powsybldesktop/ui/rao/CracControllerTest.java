/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.test.PhaseShifterTestCaseFactory;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationStatus;
import com.powsybl.powsybldesktop.rao.CracIO;
import com.powsybl.powsybldesktop.rao.RaoTestCases;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CracControllerTest extends AbstractHeadlessApplicationTest {

    private final Network network = RaoTestCases.network();
    private MainModel mainModel;
    private NetworkStudy study;
    private CracController controller;
    // the formats the import dialog was asked for
    private final List<String> askedFormats = new ArrayList<>();

    @TempDir
    Path tempDir;

    // the import dialog, modal, answered with the default parameters
    private final class AnsweringCracController extends CracController {
        @Override
        Optional<CracCreationParameters> askParameters(String fileName, String format) {
            askedFormats.add(format);
            return Optional.of(new CracCreationParameters());
        }

        Optional<CracCreationParameters> ask(String fileName, String format) {
            return super.askParameters(fileName, format);
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        mainModel = new MainModel();
        mainModel.addNetwork(network);
        mainModel.setNetwork(network);
        study = mainModel.getStudy(network);

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/powsybl/powsybldesktop/ui/rao/crac-view.fxml"), Messages.bundle());
        loader.setControllerFactory(_ -> new AnsweringCracController());
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

    private Optional<Notification> notification(String keyPrefix) {
        return mainModel.getNotificationsModel().getNotifications().stream()
                .filter(notification -> notification.messageKey().startsWith(keyPrefix) && notification.status() != NotificationStatus.RUNNING)
                .findFirst();
    }

    private String details() {
        WaitForAsyncUtils.waitForFxEvents();
        GridPane grid = (GridPane) controller.detailsHost.getChildren().getFirst();
        return String.join("\n", grid.getChildren().stream().map(node -> ((Label) node).getText()).toList());
    }

    @Test
    void emptyWithoutCrac() {
        assertEquals(Messages.get("crac.none"), controller.headerLabel.getText());
        assertTrue(controller.exportButton.isDisabled());
        assertTrue(controller.removeButton.isDisabled());
        assertFalse(controller.importButton.isDisabled());
        assertTrue(controller.contingenciesListView.getItems().isEmpty());
    }

    @Test
    void listsTheCracAndDetailsTheSelectedObject() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of("a report line")));

        assertEquals(Messages.get("crac.header", "crac", "preventive, outage, curative"), controller.headerLabel.getText());
        assertEquals(1, controller.contingenciesListView.getItems().size());
        assertEquals(List.of("L1 preventive", "L1 curative"), controller.cnecsListView.getItems().stream().map(cnec -> cnec.getId()).toList());
        assertEquals(List.of(RaoTestCases.LOAD_SHEDDING, RaoTestCases.PST), controller.remedialActionsListView.getItems().stream().map(action -> action.getId()).toList());
        assertEquals(List.of("a report line"), controller.reportListView.getItems());
        assertFalse(controller.exportButton.isDisabled());

        interact(() -> {
            controller.tabPane.getSelectionModel().select(controller.cnecsTab);
            controller.cnecsListView.getSelectionModel().select(1);
        });
        String cnec = details();
        assertTrue(cnec.contains(Messages.get("raoResults.stateName", "L2 out", "curative")), cnec);
        assertTrue(cnec.contains(Messages.get("crac.details.side", 1) + " " + Messages.get("crac.details.threshold", "-150.0", "150.0", "A")), cnec);

        interact(() -> {
            controller.tabPane.getSelectionModel().select(controller.remedialActionsTab);
            controller.remedialActionsListView.getSelectionModel().select(0);
        });
        String action = details();
        assertTrue(action.contains(Messages.get("crac.usageRule.onState", Messages.get("raoResults.stateName", "L2 out", "curative"))), action);
        assertTrue(action.contains("LD2"), action);
    }

    // after a network edit removing an element of the CRAC
    @Test
    void marksTheObjectsWhoseElementsAreMissing() {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        interact(() -> {
            network.getLoad("LD2").remove();
            mainModel.setUpdate();
            controller.tabPane.getSelectionModel().select(controller.remedialActionsTab);
        });
        WaitForAsyncUtils.waitForFxEvents();

        ListCell<?> loadShedding = from(controller.remedialActionsListView).lookup(".list-cell").queryAll().stream()
                .map(node -> (ListCell<?>) node).filter(cell -> cell.getIndex() == 0).findFirst().orElseThrow();
        assertNotNull(loadShedding.getGraphic());
        assertEquals(Messages.get("crac.missingElements", "LD2"), loadShedding.getTooltip().getText());
        interact(() -> controller.remedialActionsListView.getSelectionModel().select(0));
        assertTrue(details().contains(Messages.get("crac.details.missingElement", "LD2")));
    }

    @Test
    void importsAJsonCrac() throws IOException, TimeoutException {
        Path file = tempDir.resolve("crac.json");
        Files.write(file, CracIO.toJson(RaoTestCases.crac(network)));
        Crac previous = RaoTestCases.crac(network);
        interact(() -> study.setCrac(previous, List.of()));

        interact(() -> controller.importFile(file));

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> study.getCrac() != previous);
        assertEquals(List.of("JSON"), askedFormats);
        assertNotSame(previous, study.getCrac());
        assertEquals(2, study.getCrac().getFlowCnecs().size());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(2, controller.cnecsListView.getItems().size());
        assertEquals(NotificationStatus.SUCCESS, notification("crac.import.").orElseThrow().status());
        assertFalse(mainModel.isBusy(network));
    }

    @Test
    void refusesAFileOfNoCracFormat() throws IOException, TimeoutException {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "not a CRAC");

        interact(() -> controller.importFile(file));

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> notification("crac.import.").isPresent());
        assertEquals("crac.import.unknownFormat", notification("crac.import.").orElseThrow().messageKey());
        assertTrue(askedFormats.isEmpty());
    }

    // OpenRAO's JSON importer needs current limits on the CNECs' branches: the CRAC in place is kept
    @Test
    void keepsTheCracWhenTheImportFails() throws IOException, TimeoutException {
        Path file = tempDir.resolve("crac.json");
        Files.write(file, CracIO.toJson(RaoTestCases.crac(network)));
        Network withoutLimits = PhaseShifterTestCaseFactory.create();
        interact(() -> {
            mainModel.addNetwork(withoutLimits);
            mainModel.setNetwork(withoutLimits);
        });
        Crac previous = RaoTestCases.crac(network);
        NetworkStudy otherStudy = mainModel.getStudy(withoutLimits);
        interact(() -> otherStudy.setCrac(previous, List.of()));

        interact(() -> controller.importFile(file));

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> notification("crac.import.").isPresent());
        Notification failure = notification("crac.import.").orElseThrow();
        assertEquals("crac.import.creationFailed", failure.messageKey());
        assertEquals("crac.import.viewReport", failure.actions().getFirst().messageKey());
        assertSame(previous, otherStudy.getCrac());
    }

    // with the CRAC Generator parameters
    @Test
    void generatesFromTheNetwork() throws TimeoutException {
        mainModel.getParametersModel().cracGeneratorSettingsProperty().get().setRedispatching(true);

        interact(controller::generate);

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> study.getCrac() != null);
        assertEquals(3, study.getCrac().getContingencies().size());
        assertTrue(study.getCrac().getRangeActions().stream().anyMatch(action -> action.getNetworkElementsIds().contains("G1")));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> notification("crac.generate.").isPresent());
        assertEquals("crac.generate.completed", notification("crac.generate.").orElseThrow().messageKey());
        assertEquals("crac.generate.viewReport", notification("crac.generate.").orElseThrow().actions().getFirst().messageKey());
        assertFalse(mainModel.isBusy(network));
    }

    // the CRAC Creation parameters tab's, with nothing to ask for a JSON CRAC
    @Test
    void importsWithTheCracCreationParameters() {
        CracCreationParameters parameters = mainModel.getParametersModel().cracCreationParametersProperty().get();
        parameters.setDefaultMonitoredLineSide(CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_SIDE_TWO);

        Optional<CracCreationParameters> asked = ((AnsweringCracController) controller).ask("crac.json", "JSON");

        assertEquals(Set.of(TwoSides.TWO), asked.orElseThrow().getDefaultMonitoredSides());
        assertNotSame(parameters, asked.orElseThrow());
    }

    @Test
    void exportsInOpenRaoJsonAndRemoves() throws IOException {
        interact(() -> study.setCrac(RaoTestCases.crac(network), List.of()));
        Path file = tempDir.resolve("exported.json");

        interact(() -> controller.exportTo(file));
        assertEquals(2, CracIO.readJson(Files.newInputStream(file), network).getCrac().getFlowCnecs().size());

        interact(controller::remove);
        assertNull(study.getCrac());
        assertEquals(Messages.get("crac.none"), controller.headerLabel.getText());
        assertTrue(controller.cnecsListView.getItems().isEmpty());
    }
}
