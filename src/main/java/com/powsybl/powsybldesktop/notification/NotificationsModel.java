/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.notification;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NotificationsModel {
    private final ObservableList<Notification> modifiableNotifications = FXCollections.observableArrayList();
    // cached, see NavigationHistory's unmodifiable views
    private final ObservableList<Notification> notifications = FXCollections.unmodifiableObservableList(modifiableNotifications);
    private final BooleanProperty panelOpen = new SimpleBooleanProperty();

    public void add(Notification notification) {
        Objects.requireNonNull(notification);
        modifiableNotifications.add(notification);
    }

    public void remove(Notification notification) {
        Objects.requireNonNull(notification);
        modifiableNotifications.remove(notification);
    }

    public void replace(Notification oldNotification, Notification newNotification) {
        Objects.requireNonNull(oldNotification);
        Objects.requireNonNull(newNotification);
        int index = modifiableNotifications.indexOf(oldNotification);
        if (index >= 0) {
            modifiableNotifications.set(index, newNotification);
        } else {
            modifiableNotifications.add(newNotification);
        }
    }

    // running ones are kept: they carry the only way to cancel their operation, and are replaced by its outcome
    public void clear() {
        modifiableNotifications.removeIf(notification -> notification.status() != NotificationStatus.RUNNING);
    }

    public ObservableList<Notification> getNotifications() {
        return notifications;
    }

    public BooleanProperty panelOpenProperty() {
        return panelOpen;
    }

    public boolean isPanelOpen() {
        return panelOpen.get();
    }

    public void setPanelOpen(boolean open) {
        panelOpen.set(open);
    }
}
