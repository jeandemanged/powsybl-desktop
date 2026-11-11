/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyContext;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisParameters;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationStatus;
import com.powsybl.powsybldesktop.parameters.ApplicationParameters;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisMode;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorMatrix;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityState;
import com.powsybl.sensitivity.SensitivityVariableType;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MainControllerSensitivityAnalysisTest extends AbstractHeadlessApplicationTest {

    private static final SensitivityFactorMatrix LINES_TO_GENERATOR = new SensitivityFactorMatrix("lines", SensitivityFunctionType.BRANCH_ACTIVE_POWER_1,
            List.of("NHV1_NHV2_1", "NHV1_NHV2_2"), SensitivityVariableType.INJECTION_ACTIVE_POWER, List.of("GEN"), false, ContingencyContext.all());

    private final Network network = EurostagTutorialExample1Factory.createWithLFResults();

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

    private static double readVoltages(Network network) {
        return network.getBusView().getBusStream().mapToDouble(Bus::getV).sum();
    }

    @Test
    void storesTheResultWithItsMatricesAndModeAndKeepsTheNetworkReadable() throws TimeoutException {
        interact(() -> {
            SensitivityAnalysisParameters parameters = ApplicationParameters.defaultSensitivityAnalysisParameters();
            // OpenLoadFlow switches the network to thread-local variants for a multi-threaded analysis only
            parameters.getExtension(OpenSensitivityAnalysisParameters.class).setThreadCount(2);
            mainModel.getParametersModel().sensitivityAnalysisParametersProperty().set(parameters);
            mainModel.getParametersModel().sensitivityAnalysisModeProperty().set(SensitivityAnalysisMode.DC);
            mainModel.addNetwork(network);
            mainModel.setNetwork(network);
            NetworkStudy study = mainModel.getStudy(network);
            study.getContingencyLists().add(new DefaultContingencyList("lines", List.of(Contingency.line("NHV1_NHV2_2"))));
            study.getSensitivityFactorMatrices().add(LINES_TO_GENERATOR);
        });
        NetworkStudy study = mainModel.getStudy(network);
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> !mainModel.isBusy(network));

        interact(() -> {
            controller.onSensitivityAnalysis();

            assertTrue(network.getVariantManager().isVariantMultiThreadAccessAllowed());
            readVoltages(network);
        });

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> study.getSensitivityAnalysisResult() != null);
        interact(() -> {
            assertFalse(network.getVariantManager().isVariantMultiThreadAccessAllowed());
            readVoltages(network);
            assertEquals(List.of(LINES_TO_GENERATOR), study.getSensitivityAnalysisMatrices());
            assertTrue(study.isSensitivityAnalysisDc());
            // the mode overrides the run's copy only
            assertFalse(mainModel.getParametersModel().loadFlowParametersProperty().get().isDc());
            assertEquals(2, study.getSensitivityAnalysisResult().getValues(SensitivityState.postContingency("NHV1_NHV2_2")).size());
            Notification outcome = mainModel.getNotificationsModel().getNotifications().stream()
                    .filter(notification -> notification.messageKey().startsWith("main.sensitivityAnalysis.")).findFirst().orElseThrow();
            assertEquals(NotificationStatus.SUCCESS, outcome.status());
            assertEquals("main.sensitivityAnalysis.completed", outcome.messageKey());
        });
    }
}
