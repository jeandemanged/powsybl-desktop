/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MapControllerEmptyStateTest extends AbstractHeadlessApplicationTest {

    private MapController controller;
    private AnchorPane mapContainer;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/map/map-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        mapContainer = (AnchorPane) root;

        controller.setMainModel(new MainModel());

        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @Test
    void noExceptionWhenNoNetworkIsSelected() {
        assertEquals(1, mapContainer.getChildren().size());
    }

    // leaflet.js/leaflet.css aren't checked in: they're unpacked from the org.webjars:leaflet artifact by
    // pom.xml's unpack-leaflet execution. Without this, breaking that wiring leaves the view silently
    // blank rather than failing the build - the test above still passes with the resources missing.
    @Test
    void leafletResourcesAreOnTheClasspath() {
        assertNotNull(MapController.class.getResource("leaflet.js"));
        assertNotNull(MapController.class.getResource("leaflet.css"));
    }
}
