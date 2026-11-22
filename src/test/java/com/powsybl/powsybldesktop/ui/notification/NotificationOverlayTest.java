/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.notification;

import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The notification overlay's popup is never placed outside its owner's corner, not even while being shown: the headless
 * platform can't draw a window off-screen.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NotificationOverlayTest extends AbstractHeadlessApplicationTest {

    private Stage owner;
    private NotificationOverlay overlay;

    @Override
    public void start(Stage stage) {
        owner = stage;
        stage.setScene(new Scene(new StackPane(), 800, 600));
        stage.show();
        overlay = new NotificationOverlay(stage, _ -> {
        });
    }

    @AfterEach
    void tearDown() {
        interact(overlay::dispose);
    }

    @Test
    void isNeverShownOutsideItsOwnersCorner() {
        List<String> outside = new ArrayList<>();
        interact(() -> Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getAddedSubList().stream().filter(Popup.class::isInstance).forEach(popup -> {
                    InvalidationListener check = _ -> {
                        if (!inside(popup)) {
                            outside.add(popup.getX() + "," + popup.getY());
                        }
                    };
                    popup.xProperty().addListener(check);
                    popup.yProperty().addListener(check);
                });
            }
        }));

        interact(() -> overlay.show(Notification.createError(Instant.now(), "main.unexpectedError")));

        interact(() -> {
            Optional<Popup> popup = popup();
            assertTrue(popup.isPresent());
            assertTrue(inside(popup.get()), popup.get().getX() + "," + popup.get().getY());
        });
        assertEquals(List.of(), outside);
    }

    private static Optional<Popup> popup() {
        return Window.getWindows().stream().filter(Popup.class::isInstance).map(Popup.class::cast).filter(Window::isShowing).findFirst();
    }

    private boolean inside(Window popup) {
        return popup.getX() >= owner.getX() && popup.getY() >= owner.getY()
                && popup.getX() + popup.getWidth() <= owner.getX() + owner.getWidth()
                && popup.getY() + popup.getHeight() <= owner.getY() + owner.getHeight();
    }
}
