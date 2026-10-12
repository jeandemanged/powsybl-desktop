/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.memory;

import com.powsybl.powsybldesktop.testutil.AbstractHeadlessApplicationTest;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MemoryControllerTest extends AbstractHeadlessApplicationTest {

    private MemoryController controller;
    private ComboBox<Integer> maxHeapCombo;

    @Override
    @SuppressWarnings("unchecked")
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/com/powsybl/powsybldesktop/memory/memory-view.fxml"), Messages.bundle());
        Parent root = loader.load();
        controller = loader.getController();
        maxHeapCombo = (ComboBox<Integer>) root.lookup("#maxHeapCombo");
        stage.setScene(new Scene(root));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        interact(controller::dispose);
    }

    @Test
    void maxHeapChoicesAreLabelled() {
        assertEquals(MaxHeapConfig.availableChoices(), maxHeapCombo.getItems());
        maxHeapCombo.getItems().forEach(gb -> assertFalse(maxHeapCombo.getConverter().toString(gb).isEmpty()));
    }

    @Test
    void maxHeapIsReadOnlyOutsidePackagedApplication() {
        assertTrue(maxHeapCombo.isDisabled());
        assertEquals(MaxHeapConfig.running(), maxHeapCombo.getValue());
    }
}
