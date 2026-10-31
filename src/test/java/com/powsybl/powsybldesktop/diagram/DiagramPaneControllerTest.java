/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.diagram;

import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.io.IOException;

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
                "/com/powsybl/powsybldesktop/diagram/diagram-pane.fxml"), Messages.bundle());
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
}
