/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;

/**
 * Borderless, always-on-top window showing the app logo, meant to be displayed while the main
 * view loads.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class SplashScreen {
    private final Stage stage = new Stage(StageStyle.UNDECORATED);

    SplashScreen() {
        ImageView imageView = new ImageView(new Image(
                Objects.requireNonNull(MainApplication.class.getResourceAsStream("logo_lfe_powsybl.png"))));
        imageView.setFitWidth(450);
        imageView.setPreserveRatio(true);

        StackPane root = new StackPane(imageView);
        root.setStyle("-fx-background-color: white; -fx-padding: 20;");

        stage.setScene(new Scene(root, Color.WHITE));
        stage.getIcons().add(new Image(Objects.requireNonNull(MainApplication.class.getResourceAsStream("logo.png"))));
        stage.setAlwaysOnTop(true);
        stage.centerOnScreen();
    }

    void show() {
        stage.show();
    }

    void close() {
        stage.close();
    }
}
