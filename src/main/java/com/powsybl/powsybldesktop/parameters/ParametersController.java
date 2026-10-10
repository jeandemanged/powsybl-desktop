/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.commons.PowsyblException;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.FileChooserPreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Tab host for the app's parameter screens: network import/export parameters per format
 * ({@link NetworkFormatParametersController}), single line and network area diagram parameters
 * ({@link SldParametersController}, {@link NadParametersController}), load flow parameters (existing
 * {@link LoadFlowParametersController}, embedded unchanged), security analysis parameters
 * ({@link SecurityAnalysisParametersController}) and the app's own {@link GuiParameters}
 * ({@link GuiParametersController}). Its toolbar saves all of them to the configuration file restored on startup
 * ({@link ParametersConfigFile}), resets them to defaults, or imports/exports them from/to any JSON file.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ParametersController extends AbstractDisposableController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParametersController.class);

    @FXML
    private Button saveButton;
    @FXML
    private Tooltip saveTooltip;
    @FXML
    private TabPane tabPane;
    @FXML
    private NetworkFormatParametersController networkImportEmbeddedController;
    @FXML
    private NetworkFormatParametersController networkExportEmbeddedController;
    @FXML
    private SldParametersController sldEmbeddedController;
    @FXML
    private NadParametersController nadEmbeddedController;
    @FXML
    private LoadFlowParametersController loadFlowEmbeddedController;
    @FXML
    private SecurityAnalysisParametersController securityAnalysisEmbeddedController;
    @FXML
    private GuiParametersController guiEmbeddedController;

    private MainModel mainModel;
    private Path configPath = ParametersConfigFile.defaultPath();

    public void setMainModel(MainModel mainModel) {
        this.mainModel = Objects.requireNonNull(mainModel);
        networkImportEmbeddedController.setImportParameters(mainModel);
        networkExportEmbeddedController.setExportParameters(mainModel);
        sldEmbeddedController.setParametersProperty(mainModel.getParametersModel().sldParametersProperty());
        sldEmbeddedController.setOnChange(mainModel.getParametersModel()::sldParametersChanged);
        nadEmbeddedController.setParametersProperty(mainModel.getParametersModel().nadParametersProperty());
        nadEmbeddedController.setOnChange(mainModel.getParametersModel()::nadParametersChanged);
        loadFlowEmbeddedController.setLoadFlowParametersProperty(mainModel.getParametersModel().loadFlowParametersProperty());
        loadFlowEmbeddedController.setOnChange(mainModel.getParametersModel()::parametersChanged);
        loadFlowEmbeddedController.setOnImportFailed((path, e) -> importFailed(Instant.now(), path, e));
        securityAnalysisEmbeddedController.setSecurityAnalysisParametersProperty(mainModel.getParametersModel().securityAnalysisParametersProperty());
        securityAnalysisEmbeddedController.setOnChange(mainModel.getParametersModel()::parametersChanged);
        securityAnalysisEmbeddedController.setOnImportFailed((path, e) -> importFailed(Instant.now(), path, e));
        guiEmbeddedController.setParametersModel(mainModel.getParametersModel());
        listenerManager.listen(mainModel.getParametersModel().parametersRevisionProperty(), (observable, oldValue, newValue) -> updateSaveButton());
        setConfigPath(configPath);
    }

    void setConfigPath(Path configPath) {
        this.configPath = Objects.requireNonNull(configPath);
        saveTooltip.setText(Messages.get("parameters.toolbar.save", configPath));
        updateSaveButton();
    }

    private void updateSaveButton() {
        saveButton.setDisable(ParametersConfigFile.isSaved(mainModel.getParametersModel()));
    }

    @FXML
    private void onSave() {
        Instant start = Instant.now();
        try {
            ParametersConfigFile.save(mainModel.getParametersModel(), configPath);
        } catch (PowsyblException | UncheckedIOException e) {
            LOGGER.error("Failed to save parameters to {}", configPath, e);
            mainModel.getNotificationsModel().add(Notification.createError(start, "parameters.config.saveFailed").withMessageArgs(configPath));
        }
        updateSaveButton();
    }

    @FXML
    private void onReset() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, Messages.get("parameters.reset.confirm"), ButtonType.YES, ButtonType.NO);
        alert.initOwner(tabPane.getScene().getWindow());
        alert.setHeaderText(null);
        if (alert.showAndWait().filter(ButtonType.YES::equals).isPresent()) {
            setParameters(ApplicationParameters.createDefault());
        }
    }

    @FXML
    private void onImport() {
        File file = createFileChooser().showOpenDialog(tabPane.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            importFrom(file.toPath());
        }
    }

    @FXML
    private void onExport() {
        File file = createFileChooser().showSaveDialog(tabPane.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            exportTo(file.toPath());
        }
    }

    // all or nothing: a file that fails to read leaves the current parameters untouched
    void importFrom(Path path) {
        Instant start = Instant.now();
        ApplicationParameters parameters;
        try {
            parameters = ApplicationParametersJson.read(path);
        } catch (PowsyblException | UncheckedIOException e) {
            importFailed(start, path, e);
            return;
        }
        setParameters(parameters);
    }

    private void importFailed(Instant start, Path path, RuntimeException e) {
        LOGGER.error("Failed to import parameters from {}", path, e);
        mainModel.getNotificationsModel().add(Notification.createError(start, "parameters.import.failed").withMessageArgs(path));
    }

    void exportTo(Path path) {
        Instant start = Instant.now();
        try {
            ApplicationParametersJson.write(mainModel.getParametersModel().getParameters(), path);
        } catch (PowsyblException | UncheckedIOException e) {
            LOGGER.error("Failed to export parameters to {}", path, e);
            mainModel.getNotificationsModel().add(Notification.createError(start, "parameters.export.failed").withMessageArgs(path));
        }
    }

    private void setParameters(ApplicationParameters parameters) {
        mainModel.getParametersModel().setParameters(parameters);
        networkImportEmbeddedController.refresh();
        networkExportEmbeddedController.refresh();
    }

    private static FileChooser createFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*"));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        return fileChooser;
    }

    @Override
    public void dispose() {
        networkImportEmbeddedController.dispose();
        networkExportEmbeddedController.dispose();
        sldEmbeddedController.dispose();
        nadEmbeddedController.dispose();
        loadFlowEmbeddedController.dispose();
        securityAnalysisEmbeddedController.dispose();
        guiEmbeddedController.dispose();
        super.dispose();
    }
}
