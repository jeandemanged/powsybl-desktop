/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.powsybldesktop.utils.LanguagePreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class MainApplication extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainApplication.class);
    private static final Duration MIN_SPLASH_DURATION = Duration.seconds(1);
    public static final String APP_TITLE = "PowSyBl Desktop";

    static {
        // Default JDK JAXP entity-size limits can reject legitimately large CGMES/IIDM network files; 0 disables them.
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0");
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
    }

    @Override
    public void start(Stage stage) {
        LanguagePreferences.applyPersisted();

        SplashScreen splash = new SplashScreen();
        splash.show();
        long splashShownAt = System.currentTimeMillis();

        Task<Parent> loadMainView = new Task<>() {
            @Override
            protected Parent call() throws Exception {
                FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("main-view.fxml"), Messages.bundle());
                loader.setControllerFactory(type -> new MainController(new MainModel()));
                return loader.load();
            }
        };
        loadMainView.setOnSucceeded(event -> {
            Runnable showMainView = () -> {
                Scene scene = new Scene(loadMainView.getValue(), 900, 600);
                scene.getStylesheets().add(MainApplication.class.getResource("styles.css").toExternalForm());
                stage.setTitle(APP_TITLE);
                stage.getIcons().add(new Image(Objects.requireNonNull(MainApplication.class.getResourceAsStream("logo.png"))));
                stage.setScene(scene);
                stage.show();
                splash.close();
            };
            Duration elapsed = Duration.millis(System.currentTimeMillis() - splashShownAt);
            Duration remaining = MIN_SPLASH_DURATION.subtract(elapsed);
            if (remaining.greaterThan(Duration.ZERO)) {
                PauseTransition delay = new PauseTransition(remaining);
                delay.setOnFinished(e -> showMainView.run());
                delay.play();
            } else {
                showMainView.run();
            }
        });
        loadMainView.setOnFailed(event -> {
            LOGGER.error("Failed to load main view", loadMainView.getException());
            splash.close();
            Platform.exit();
        });

        Thread thread = new Thread(loadMainView, "main-view-loader");
        thread.setDaemon(true);
        thread.start();
    }
}
