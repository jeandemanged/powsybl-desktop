/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.list.DefaultContingencyList;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.security.SecurityAnalysisParameters;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MainControllerSecurityAnalysisTest extends AbstractHeadlessApplicationTest {

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
    void networkStaysReadableFromTheFxThreadDuringAndAfterAMultiThreadedSecurityAnalysis() throws TimeoutException {
        interact(() -> {
            SecurityAnalysisParameters parameters = new SecurityAnalysisParameters();
            // OpenLoadFlow switches the network to thread-local variants for a multi-threaded analysis only
            parameters.addExtension(OpenSecurityAnalysisParameters.class, new OpenSecurityAnalysisParameters().setThreadCount(2));
            mainModel.getParametersModel().securityAnalysisParametersProperty().set(parameters);
            mainModel.addNetwork(network);
            mainModel.setNetwork(network);
            mainModel.getStudy(network).getContingencyLists().add(new DefaultContingencyList("lines",
                    List.of(Contingency.line("NHV1_NHV2_1"), Contingency.line("NHV1_NHV2_2"))));
        });
        // e.g. the search index being built
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> !mainModel.isBusy(network));

        interact(() -> {
            controller.onSecurityAnalysis();

            assertTrue(network.getVariantManager().isVariantMultiThreadAccessAllowed());
            readVoltages(network);
        });

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> mainModel.getStudy(network).getSecurityAnalysisResult() != null);
        interact(() -> {
            assertFalse(network.getVariantManager().isVariantMultiThreadAccessAllowed());
            readVoltages(network);
        });
    }
}
