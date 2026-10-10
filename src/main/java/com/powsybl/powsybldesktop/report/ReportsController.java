/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.report;

import com.powsybl.commons.report.ReportConstants;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.commons.report.ReportNodeSerializer;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.SceneView;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.FileChooserPreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ReportsController extends AbstractDisposableController implements SceneView {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportsController.class);

    @FXML
    private ListView<ReportNode> reportsListView;

    @FXML
    private Label noReportsLabel;

    @FXML
    private Button exportButton;

    @FXML
    private ToggleGroup severityToggleGroup;

    @FXML
    private ReportNodeViewController reportDetailController;

    private SceneModel sceneModel;
    private MainModel mainModel;

    @FXML
    private void initialize() {
        reportsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ReportNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String timestamp = timestampOf(item);
                setText(timestamp == null ? item.getMessage() : timestamp + "  " + item.getMessage());
            }
        });
        exportButton.disableProperty().bind(reportsListView.getSelectionModel().selectedItemProperty().isNull());
        reportsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) ->
                reportDetailController.setRootReportNode(newItem));
        severityToggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            // clicking the selected toggle would otherwise leave none selected
            if (newToggle == null) {
                oldToggle.setSelected(true);
            } else {
                String severity = (String) newToggle.getUserData();
                if (!gui().getReportsMinSeverity().equals(severity)) {
                    gui().setReportsMinSeverity(severity);
                    mainModel.getParametersModel().guiParametersChanged();
                }
                reportDetailController.setMinSeverity(severity);
            }
        });
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        reportsListView.setItems(mainModel.getReports());
        noReportsLabel.setVisible(mainModel.getReports().isEmpty());
        listenerManager.listen(mainModel.getReports(), change -> noReportsLabel.setVisible(mainModel.getReports().isEmpty()));
        if (!mainModel.getReports().isEmpty()) {
            reportsListView.getSelectionModel().select(mainModel.getReports().size() - 1);
        }
        applyGuiParameters();
        // edited from this view or the parameters view, or replaced by a parameters reset or import
        listenerManager.listen(mainModel.getParametersModel().guiParametersRevisionProperty(), (observable, oldValue, newValue) -> applyGuiParameters());
    }

    private GuiParameters gui() {
        return mainModel.getParametersModel().getGuiParameters();
    }

    private void applyGuiParameters() {
        severityToggleGroup.getToggles().stream()
                .filter(toggle -> gui().getReportsMinSeverity().equals(toggle.getUserData()))
                .findFirst()
                .ifPresent(severityToggleGroup::selectToggle);
    }

    @FXML
    private void onClearAll() {
        mainModel.clearReports();
    }

    @FXML
    private void onExport() {
        ReportNode selected = reportsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        FileChooser fileChooser = new FileChooser();
        FileChooser.ExtensionFilter jsonFilter = new FileChooser.ExtensionFilter(
                Messages.get("networks.file.supportedFiles", "json"), "*.json");
        fileChooser.getExtensionFilters().add(jsonFilter);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("networks.file.supportedFiles", "txt"), "*.txt"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*"));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File selectedFile = fileChooser.showSaveDialog(reportsListView.getScene().getWindow());
        if (selectedFile == null) {
            return;
        }
        FileChooserPreferences.saveLastDirectory(selectedFile);
        try {
            if (isJson(selectedFile, fileChooser.getSelectedExtensionFilter() == jsonFilter)) {
                ReportNodeSerializer.write(selected, selectedFile.toPath());
            } else {
                selected.print(selectedFile.toPath());
            }
        } catch (IOException e) {
            LOGGER.error(e.toString(), e);
        }
    }

    private static boolean isJson(File file, boolean jsonFilterSelected) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".json")) {
            return true;
        }
        if (name.endsWith(".txt")) {
            return false;
        }
        return jsonFilterSelected;
    }

    public void selectReport(ReportNode reportNode) {
        Objects.requireNonNull(reportNode);
        reportsListView.getSelectionModel().select(reportNode);
        reportsListView.scrollTo(reportNode);
    }

    private static String timestampOf(ReportNode node) {
        return node.getValue(ReportConstants.TIMESTAMP_KEY)
                .map(typedValue -> String.valueOf(typedValue.getValue()))
                .orElse(null);
    }
}
