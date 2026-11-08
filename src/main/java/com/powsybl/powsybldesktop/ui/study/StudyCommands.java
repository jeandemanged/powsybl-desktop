/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.study;

import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.notification.NotificationAction;
import com.powsybl.powsybldesktop.study.Study;
import com.powsybl.powsybldesktop.study.StudyArchive;
import com.powsybl.powsybldesktop.ui.controls.FileChooserPreferences;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * File &gt; Save Study and Load Study: what to save, the file, and the write or read in the background behind an
 * application-modal progress dialog, the app being unusable meanwhile.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class StudyCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyCommands.class);

    private StudyCommands() {
    }

    public static void save(Window owner, SceneModel sceneModel) {
        save(owner, sceneModel, () -> { });
    }

    /**
     * @param onSaved run once the study is written, not if the user cancels or the write fails
     */
    private static void save(Window owner, SceneModel sceneModel, Runnable onSaved) {
        MainModel mainModel = sceneModel.getMainModel();
        Optional<SaveStudyDialog.Choice> choice = SaveStudyDialog.show(owner, List.copyOf(mainModel.getNetworks()));
        if (choice.isEmpty() || refuseIfBusy(mainModel, choice.get().networks())) {
            return;
        }
        File file = chooseFile(owner, true);
        if (file == null) {
            return;
        }
        Path path = withExtension(file);
        Study study = Study.capture(mainModel, choice.get().networks(), choice.get().parts());
        // released by the handlers below, the networks being written meanwhile
        List<Runnable> releases = choice.get().networks().stream().map(mainModel::markBusy).toList();
        Instant start = Instant.now();
        Service<Void> service = new Service<>() {
            @Override
            protected Task<Void> createTask() {
                return new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        StudyArchive.write(study, path, this::progress);
                        return null;
                    }

                    private void progress(int done, int total, String message) {
                        updateMessage(message);
                        updateProgress(done, total);
                    }
                };
            }
        };
        service.setOnSucceeded(_ -> {
            releases.forEach(Runnable::run);
            mainModel.getNotificationsModel().add(Notification.createSuccess(start, "study.save.completed").withMessageArgs(path.getFileName()));
            // after the progress dialog has closed, e.g. for the next file chooser
            Platform.runLater(onSaved);
        });
        service.setOnFailed(event -> {
            releases.forEach(Runnable::run);
            failed(sceneModel, start, "study.save.failed", event.getSource().getException(), path);
        });
        runWithProgress(owner, "study.save.title", service);
    }

    public static void load(Window owner, SceneModel sceneModel) {
        MainModel mainModel = sceneModel.getMainModel();
        if (mainModel.getNetworks().isEmpty()) {
            chooseAndLoad(owner, sceneModel);
            return;
        }
        if (refuseIfBusy(mainModel, mainModel.getNetworks())) {
            return;
        }
        ButtonType saveFirst = new ButtonType(Messages.get("study.load.saveFirst"), ButtonBar.ButtonData.OTHER);
        ButtonType replace = new ButtonType(Messages.get("study.load.replace"), ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Messages.get("study.load.confirmMessage"), saveFirst, replace, ButtonType.CANCEL);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("study.load.title"));
        alert.setHeaderText(null);
        Optional<ButtonType> answer = alert.showAndWait();
        if (answer.filter(saveFirst::equals).isPresent()) {
            save(owner, sceneModel, () -> chooseAndLoad(owner, sceneModel));
        } else if (answer.filter(replace::equals).isPresent()) {
            chooseAndLoad(owner, sceneModel);
        }
    }

    private static void chooseAndLoad(Window owner, SceneModel sceneModel) {
        File file = chooseFile(owner, false);
        if (file == null) {
            return;
        }
        MainModel mainModel = sceneModel.getMainModel();
        Path path = file.toPath();
        Instant start = Instant.now();
        // a plain Task: the study's networks aren't loaded yet
        Service<Study> service = new Service<>() {
            @Override
            protected Task<Study> createTask() {
                return new Task<>() {
                    @Override
                    protected Study call() throws Exception {
                        return StudyArchive.read(path, this::progress);
                    }

                    private void progress(int done, int total, String message) {
                        updateMessage(message);
                        updateProgress(done, total);
                    }
                };
            }
        };
        service.setOnSucceeded(event -> {
            ((Study) event.getSource().getValue()).restore(mainModel);
            mainModel.getNotificationsModel().add(Notification.createSuccess(start, "study.load.completed").withMessageArgs(path.getFileName()));
        });
        service.setOnFailed(event -> failed(sceneModel, start, "study.load.failed", event.getSource().getException(), path));
        runWithProgress(owner, "study.load.title", service);
    }

    private static boolean refuseIfBusy(MainModel mainModel, List<Network> networks) {
        if (networks.stream().anyMatch(mainModel::isBusy)) {
            mainModel.getNotificationsModel().add(Notification.createError(Instant.now(), "main.networkBusy"));
            return true;
        }
        return false;
    }

    private static void failed(SceneModel sceneModel, Instant start, String key, Throwable exception, Path path) {
        LOGGER.error(exception.toString(), exception);
        NotificationAction viewLogsAction = new NotificationAction("main.viewLogs", _ ->
                sceneModel.navigate(NavigationEvent.create(NavigationType.LOGS)));
        sceneModel.getMainModel().getNotificationsModel().add(Notification.createError(start, key, viewLogsAction).withMessageArgs(path.getFileName()));
    }

    private static File chooseFile(Window owner, boolean save) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(Messages.get(save ? "study.save.title" : "study.load.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("study.file.filter", StudyArchive.EXTENSION), "*." + StudyArchive.EXTENSION));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File file = save ? fileChooser.showSaveDialog(owner) : fileChooser.showOpenDialog(owner);
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
        }
        return file;
    }

    // some platforms' save dialogs don't append the filter's extension
    private static Path withExtension(File file) {
        String suffix = "." + StudyArchive.EXTENSION;
        return file.getName().toLowerCase(Locale.ROOT).endsWith(suffix) ? file.toPath() : file.toPath().resolveSibling(file.getName() + suffix);
    }

    private static void runWithProgress(Window owner, String titleKey, Service<?> service) {
        Label message = new Label();
        message.textProperty().bind(service.messageProperty());
        ProgressBar progressBar = new ProgressBar();
        progressBar.setPrefWidth(420);
        progressBar.progressProperty().bind(service.progressProperty());
        VBox content = new VBox(8, message, progressBar);

        // without buttons: the user can't close it, it closes when the service is over
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(Messages.get(titleKey));
        dialog.setHeaderText(Messages.get(titleKey));
        dialog.getDialogPane().setContent(content);
        service.stateProperty().addListener((_, _, state) -> {
            if (state == Worker.State.SUCCEEDED || state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                // a dialog without a cancel button only closes once it has a result
                dialog.setResult(Boolean.TRUE);
                dialog.close();
            }
        });
        dialog.show();
        service.start();
    }
}
