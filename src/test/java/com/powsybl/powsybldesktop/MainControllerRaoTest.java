/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.InstantKind;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationStatus;
import com.powsybl.powsybldesktop.rao.RaoTestCases;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisMode;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MainControllerRaoTest extends AbstractHeadlessApplicationTest {

    private final Network network = RaoTestCases.network();

    private MainModel mainModel;
    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        mainModel = new MainModel();
        controller = new MainController(mainModel);

        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/main-view.fxml"), Messages.bundle());
        loader.setControllerFactory(_ -> controller);
        Parent root = loader.load();

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    private NetworkStudy load() throws TimeoutException {
        interact(() -> {
            mainModel.addNetwork(network);
            mainModel.setNetwork(network);
        });
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> !mainModel.isBusy(network));
        return mainModel.getStudy(network);
    }

    private Optional<Notification> raoNotification() {
        return mainModel.getNotificationsModel().getNotifications().stream()
                .filter(notification -> notification.messageKey().startsWith("main.rao.")).findFirst();
    }

    // the refusal's alert, closed: its showAndWait would block interact()
    private String refusal() {
        Platform.runLater(controller::onRao);
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane dialogPane = lookup(".dialog-pane").queryAs(DialogPane.class);
        String text = dialogPane.getContentText();
        interact(() -> ((Button) dialogPane.lookupButton(ButtonType.OK)).fire());
        return text;
    }

    @Test
    void storesTheResultOfARunOnCopies() throws TimeoutException {
        NetworkStudy study = load();
        Crac crac = RaoTestCases.crac(network);
        interact(() -> {
            study.cracProperty().set(crac);
            mainModel.getParametersModel().raoModeProperty().set(SensitivityAnalysisMode.AC);
            controller.onRao();
        });

        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> study.getRaoResult() != null);
        interact(() -> {
            assertSame(crac, study.getCrac());
            assertNotSame(crac, study.getRaoCrac());
            assertFalse(study.isRaoDc());
            assertEquals(2, study.getRaoResult().getOptimizedTapOnState(study.getRaoCrac().getPreventiveState(),
                    study.getRaoCrac().getPstRangeAction(RaoTestCases.PST)));
            assertEquals(1, network.getTwoWindingsTransformer("PS1").getPhaseTapChanger().getTapPosition());
            assertEquals(1, mainModel.getReports().size());
            Notification outcome = raoNotification().orElseThrow();
            assertEquals("main.rao.completed", outcome.messageKey());
            assertEquals(NotificationStatus.SUCCESS, outcome.status());
        });
    }

    // shedding LD2 leaves L1 at about 134 A after the loss of L2, above a 130 A threshold
    @Test
    void tellsWhenTheFlowsAreStillNotSecure() throws TimeoutException {
        NetworkStudy study = load();
        interact(() -> {
            study.cracProperty().set(RaoTestCases.crac(network, 130.));
            controller.onRao();
        });

        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS, () -> study.getRaoResult() != null);
        interact(() -> {
            Notification outcome = raoNotification().orElseThrow();
            assertEquals("main.rao.unsecure", outcome.messageKey());
            assertEquals(NotificationStatus.PARTIAL_SUCCESS, outcome.status());
        });
    }

    @Test
    void refusesWithoutCrac() throws TimeoutException {
        NetworkStudy study = load();

        assertEquals(Messages.get("main.rao.noCrac", Messages.get("main.toolbar.crac")), refusal());
        assertTrue(raoNotification().isEmpty());
        assertNull(study.getRaoResult());
    }

    // with every reason, FastRAO's included when the RAO runs through it
    @Test
    void refusesWithEveryProblem() throws TimeoutException {
        NetworkStudy study = load();
        Crac crac = RaoTestCases.crac(network);
        crac.newInstant("curative 2", InstantKind.CURATIVE);
        interact(() -> {
            study.cracProperty().set(crac);
            network.getLoad("LD2").remove();
            mainModel.getParametersModel().fastRaoProperty().set(true);
        });

        String refusal = refusal();

        assertTrue(refusal.contains(Messages.get("rao.problem.fastRaoCurativeInstants", 2)), refusal);
        assertTrue(refusal.contains(Messages.get("rao.problem.remedialActionElements", RaoTestCases.LOAD_SHEDDING, "LD2")), refusal);
        assertTrue(raoNotification().isEmpty());
    }
}
