/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop;

import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.parameters.ParametersConfigFile;
import com.powsybl.powsybldesktop.utils.LanguagePreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Optional;

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
        MainModel mainModel = new MainModel();
        Optional<Notification> parametersLoadError = ParametersConfigFile.load(mainModel.getParametersModel(), ParametersConfigFile.defaultPath());
        // only once the main view listens to notifications, for the error to pop up
        stage.setOnShown(event -> parametersLoadError.ifPresent(mainModel.getNotificationsModel()::add));

        SplashScreen splash = new SplashScreen();
        splash.show();
        long splashShownAt = System.currentTimeMillis();

        // Loaded on the FX thread, since MainController.initialize() builds controls and drives the first navigation;
        // the short pause lets the splash screen be painted first, as this blocks the FX thread while loading.
        PauseTransition splashPainted = new PauseTransition(Duration.millis(50));
        splashPainted.setOnFinished(event -> {
            Parent mainView = loadMainView(mainModel, splash);
            Runnable showMainView = () -> showMainView(stage, mainView, splash);
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
        splashPainted.play();
    }

    private static void showMainView(Stage stage, Parent mainView, SplashScreen splash) {
        Scene scene = new Scene(mainView, 900, 600);
        scene.getStylesheets().add(MainApplication.class.getResource("styles.css").toExternalForm());
        stage.setTitle(APP_TITLE);
        stage.getIcons().add(new Image(Objects.requireNonNull(MainApplication.class.getResourceAsStream("logo.png"))));
        stage.setScene(scene);
        stage.show();
        splash.close();
    }

    private static Parent loadMainView(MainModel mainModel, SplashScreen splash) {
        Parent root = null;
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource("main-view.fxml"), Messages.bundle());
            loader.setControllerFactory(type -> new MainController(mainModel));
            root = loader.load();
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            // whatever the failure, don't leave the splash screen up forever
            if (root == null) {
                LOGGER.error("Failed to load main view");
                splash.close();
                Platform.exit();
            }
        }
    }
}
