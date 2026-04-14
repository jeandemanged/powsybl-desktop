/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.notification;

import com.powsybl.powsybldesktop.utils.Messages;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.RotateTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Renders a single {@link Notification}: a status indicator, its message (live elapsed time while
 * {@link NotificationStatus#RUNNING}), an icon-only close, a timestamp, and either a cancel button
 * (running) or the outcome's action links. Shared by the notifications history list and the corner
 * {@link NotificationOverlay} so a notification looks identical in both places.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NotificationView extends VBox {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault());
    private static final List<String> STATUS_STYLE_CLASSES = List.of(
            "notification-status-success", "notification-status-error", "notification-status-cancelled");

    private final Region statusMarker = new Region();
    private final Arc statusArc = new Arc(5, 5, 5, 5, 90, 270);
    private final RotateTransition statusArcRotation = new RotateTransition(Duration.seconds(1), statusArc);
    private final Timeline elapsedTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> this.refreshMessage()));
    private final StackPane statusIndicator = new StackPane(statusMarker, statusArc);
    private final Label messageLabel = new Label();
    private final Label timeLabel = new Label();
    private final Label closeIcon = new Label("✕");
    private final HBox actionsBox = new HBox(4);

    private Notification item;
    private Consumer<Notification> onClose;

    public NotificationView() {
        super(4);
        statusMarker.getStyleClass().add("notification-status");
        statusArc.setType(ArcType.OPEN);
        statusArc.getStyleClass().add("notification-status-arc");
        statusIndicator.setMinSize(10, 10);
        statusIndicator.setMaxSize(10, 10);
        statusArcRotation.setByAngle(360);
        statusArcRotation.setInterpolator(Interpolator.LINEAR);
        statusArcRotation.setCycleCount(Animation.INDEFINITE);
        elapsedTimeline.setCycleCount(Animation.INDEFINITE);

        messageLabel.setWrapText(true);
        HBox.setHgrow(messageLabel, Priority.ALWAYS);
        timeLabel.getStyleClass().add("notification-timestamp");
        closeIcon.getStyleClass().add("notification-close");
        closeIcon.setOnMouseClicked(e -> {
            if (onClose != null && item != null) {
                onClose.accept(item);
            }
        });

        HBox header = new HBox(8, statusIndicator, messageLabel, spacer(), closeIcon);
        HBox footer = new HBox(8, timeLabel, spacer(), actionsBox);
        header.setAlignment(Pos.CENTER_LEFT);
        footer.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(header, footer);
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    public void update(Notification notification, Consumer<Notification> onClose) {
        this.item = notification;
        this.onClose = onClose;

        boolean running = notification.status() == NotificationStatus.RUNNING;
        statusMarker.setVisible(!running);
        statusMarker.setManaged(!running);
        statusArc.setVisible(running);
        statusArc.setManaged(running);
        if (running) {
            if (statusArcRotation.getStatus() != Animation.Status.RUNNING) {
                statusArcRotation.playFromStart();
            }
            if (elapsedTimeline.getStatus() != Animation.Status.RUNNING) {
                elapsedTimeline.playFromStart();
            }
        } else {
            statusArcRotation.stop();
            elapsedTimeline.stop();
            statusMarker.getStyleClass().removeAll(STATUS_STYLE_CLASSES);
            statusMarker.getStyleClass().add("notification-status-" + notification.status().name().toLowerCase(Locale.ROOT));
        }

        refreshMessage();
        timeLabel.setText(TIME_FORMATTER.format(notification.timestamp()));
        actionsBox.getChildren().setAll(running
                ? cancelButtonOrNone(notification.onCancel())
                : notification.actions().stream().<Node>map(NotificationView::toHyperlink).toList());
    }

    public void dispose() {
        statusArcRotation.stop();
        elapsedTimeline.stop();
    }

    private void refreshMessage() {
        if (item != null) {
            messageLabel.setText(item.message());
        }
    }

    private static List<Node> cancelButtonOrNone(Runnable onCancel) {
        if (onCancel == null) {
            return List.of();
        }
        Button button = new Button(Messages.get("main.cancel"));
        button.setOnAction(e -> onCancel.run());
        return List.of(button);
    }

    private static Hyperlink toHyperlink(NotificationAction action) {
        Hyperlink link = new Hyperlink(Messages.get(action.messageKey()));
        link.setOnAction(event -> action.handler().accept(event));
        return link;
    }
}
