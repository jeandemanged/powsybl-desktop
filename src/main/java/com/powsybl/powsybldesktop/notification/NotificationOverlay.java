/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.notification;

import javafx.animation.PauseTransition;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Corner popup stacking one card per currently-relevant notification: every {@link NotificationStatus#RUNNING}
 * one stays until it completes, then its card shows the outcome for 5s. Each card is a
 * {@link NotificationView} - the same component used in the notifications history list - so a
 * notification looks identical in both places.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NotificationOverlay {

    private static final double MARGIN = 16;
    private static final Duration AUTO_HIDE_DELAY = Duration.seconds(5);

    private final Window owner;
    private final Consumer<Notification> onDismiss;
    private final Popup popup = new Popup();
    private final VBox stack = new VBox(8);
    private final Map<Notification, Entry> entries = new LinkedHashMap<>();

    private record Entry(NotificationView view, PauseTransition autoHide) {
    }

    public NotificationOverlay(Window owner, Consumer<Notification> onDismiss) {
        this.owner = Objects.requireNonNull(owner);
        this.onDismiss = Objects.requireNonNull(onDismiss);

        stack.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/com/powsybl/powsybldesktop/styles.css")).toExternalForm());

        // Reposition only on an actual size change (a card added/removed, or one wrapping to another line
        // count) - not on every bounds-changed pulse, which would otherwise nudge the popup around every
        // second as a running card's elapsed-time text ticks.
        stack.boundsInLocalProperty().addListener((obs, oldBounds, newBounds) -> {
            if (popup.isShowing()
                    && (oldBounds.getWidth() != newBounds.getWidth() || oldBounds.getHeight() != newBounds.getHeight())) {
                reposition();
            }
        });

        popup.getContent().add(stack);
        popup.setAutoHide(false);
    }

    public void show(Notification notification) {
        NotificationView view = new NotificationView();
        view.getStyleClass().add("notification-overlay");
        Entry entry = new Entry(view, new PauseTransition(AUTO_HIDE_DELAY));
        entries.put(notification, entry);
        wire(entry, notification);
        stack.getChildren().add(view);

        if (!popup.isShowing()) {
            // Shown off-screen first; the bounds listener above moves it into the corner once laid out.
            popup.show(owner, -10_000, -10_000);
        }
        if (notification.status() != NotificationStatus.RUNNING) {
            entry.autoHide().playFromStart();
        }
    }

    public void replace(Notification previous, Notification current) {
        Entry entry = entries.remove(previous);
        if (entry == null) {
            show(current);
            return;
        }
        entry.autoHide().stop();
        entries.put(current, entry);
        wire(entry, current);
        if (current.status() != NotificationStatus.RUNNING) {
            entry.autoHide().playFromStart();
        }
    }

    public void remove(Notification notification) {
        detach(notification);
    }

    private void wire(Entry entry, Notification notification) {
        entry.view().update(notification, n -> {
            onDismiss.accept(n);
            detach(n);
        });
        entry.autoHide().setOnFinished(e -> detach(notification));
    }

    private void detach(Notification notification) {
        Entry entry = entries.remove(notification);
        if (entry == null) {
            return;
        }
        entry.autoHide().stop();
        entry.view().dispose();
        stack.getChildren().remove(entry.view());
        if (entries.isEmpty()) {
            popup.hide();
        }
    }

    private void reposition() {
        popup.setX(owner.getX() + owner.getWidth() - stack.getWidth() - MARGIN);
        popup.setY(owner.getY() + owner.getHeight() - stack.getHeight() - MARGIN);
    }
}
