/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.notification;

import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NotificationsController extends AbstractDisposableController {

    @FXML
    private ListView<Notification> notificationsListView;

    @FXML
    private Label noNotificationsLabel;

    private MainModel mainModel;

    @FXML
    private void initialize() {
        notificationsListView.setCellFactory(lv -> new NotificationCell());
    }

    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        notificationsListView.setItems(mainModel.getNotifications());
        noNotificationsLabel.setVisible(mainModel.getNotifications().isEmpty());
        listenerManager.listen(mainModel.getNotifications(), (ListChangeListener<Notification>) change ->
                noNotificationsLabel.setVisible(mainModel.getNotifications().isEmpty()));
    }

    @FXML
    private void onClearAll() {
        mainModel.clearNotifications();
    }

    private final class NotificationCell extends ListCell<Notification> {
        private final NotificationView view = new NotificationView();

        @Override
        protected void updateItem(Notification item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                view.dispose();
                setGraphic(null);
                return;
            }
            view.update(item, mainModel::removeNotification);
            setGraphic(view);
            setText(null);
        }
    }
}
