/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.diagram;

import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class DiagramPaneControllerTest extends AbstractHeadlessApplicationTest {

    private DiagramPaneController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/ui/diagram/diagram-pane.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root));
        stage.show();
    }

    @Test
    void untickingARestoredFitToScreenNotifiesListeners() {
        ToggleButton fitToScreenButton = lookup("#fitToScreenToggleButton").queryAs(ToggleButton.class);
        interact(() -> controller.restoreZoom(1.0, true));
        assertTrue(controller.fitToScreenProperty().get());

        clickOn(fitToScreenButton);

        // SubstationsController persists this property to MainModel: it must change for the untick to be kept
        assertFalse(controller.fitToScreenProperty().get());
    }

    @Test
    void pngIsTheWholeDiagramAtTwiceItsSizeWhateverTheViewport() throws Exception {
        // wider than the viewport, zoomed out: a snapshot of the view would be cropped or low resolution
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="3000" height="400" viewBox="0 0 3000 400">
                    <rect x="2900" y="300" width="100" height="100" fill="red"/>
                </svg>""";
        interact(() -> {
            controller.showDiagram(svg);
            controller.restoreZoom(0.25, false);
        });
        CompletableFuture<byte[]> png = new CompletableFuture<>();

        interact(() -> controller.rasterize(png::complete));

        Image image = new Image(new ByteArrayInputStream(png.get(10, TimeUnit.SECONDS)));
        assertEquals(6000, image.getWidth());
        assertEquals(800, image.getHeight());
        // the bottom right corner, far outside the viewport, is drawn
        assertEquals(1.0, image.getPixelReader().getColor(5990, 790).getRed(), 0.01);
        assertEquals(0.0, image.getPixelReader().getColor(5990, 790).getGreen(), 0.01);
    }
}
