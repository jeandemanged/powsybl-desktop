/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.cgmes.conformity.CgmesConformity1Catalog;
import com.powsybl.cgmes.conversion.CgmesImport;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Map toolbar button is only offered for networks that actually carry geographic positions.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MainControllerMapButtonTest extends AbstractHeadlessApplicationTest {

    private final Network withoutPositions = IeeeCdfNetworkFactory.create14();
    private Network withPositions;

    private MainModel mainModel;
    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        Properties parameters = new Properties();
        parameters.setProperty(CgmesImport.POST_PROCESSORS, "cgmesGLImport");
        withPositions = Network.read(CgmesConformity1Catalog.microGridBaseCaseBE().dataSource(), parameters);

        mainModel = new MainModel();
        controller = new MainController(mainModel);

        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/main-view.fxml"), Messages.bundle());
        loader.setControllerFactory(type -> controller);
        Parent root = loader.load();

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @Test
    void mapButtonIsHiddenWhenNoNetworkIsSelected() {
        assertFalse(controller.mapButton.isVisible());
        assertFalse(controller.mapButton.isManaged());
    }

    @Test
    void mapButtonIsHiddenForANetworkWithoutPositions() {
        interact(() -> {
            mainModel.addNetwork(withoutPositions);
            mainModel.setNetwork(withoutPositions);
        });

        assertFalse(controller.mapButton.isVisible());
        assertFalse(controller.mapButton.isManaged());
    }

    @Test
    void mapButtonIsShownForANetworkWithPositions() {
        interact(() -> {
            mainModel.addNetwork(withPositions);
            mainModel.setNetwork(withPositions);
        });

        assertTrue(controller.mapButton.isVisible());
        assertTrue(controller.mapButton.isManaged());
    }

    @Test
    void mapButtonFollowsTheSelectedNetwork() {
        interact(() -> {
            mainModel.addNetwork(withPositions);
            mainModel.addNetwork(withoutPositions);
            mainModel.setNetwork(withPositions);
        });
        assertTrue(controller.mapButton.isVisible());

        interact(() -> mainModel.setNetwork(withoutPositions));
        assertFalse(controller.mapButton.isVisible());

        interact(() -> mainModel.setNetwork(withPositions));
        assertTrue(controller.mapButton.isVisible());
    }
}
