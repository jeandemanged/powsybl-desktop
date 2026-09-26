/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.openloadflow.sa.ContingencyActivePowerLossDistribution;
import com.powsybl.openloadflow.sa.OpenSecurityAnalysisParameters;
import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import com.powsybl.powsybldesktop.utils.FileChooserPreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.security.json.JsonSecurityAnalysisParameters;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Edits {@link SecurityAnalysisParameters} plus its {@link OpenSecurityAnalysisParameters} extension, hand-wired
 * field by field like {@link com.powsybl.powsybldesktop.parameters.LoadFlowParametersController}'s base
 * fields (no {@code Parameter} metadata is exposed here to drive fields generically). The embedded
 * {@link SecurityAnalysisParameters#getLoadFlowParameters()} is not shown - {@code MainModel} keeps it wired to
 * the app's single {@code LoadFlowParameters} instance, edited from the Load Flow tab instead.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SecurityAnalysisParametersController extends AbstractDisposableController {

    private static final String GENERAL_CATEGORY = "general";
    private static final String INCREASED_VIOLATIONS_CATEGORY = "increasedViolations";
    private static final String MODIFIED_MONITORED_ELEMENTS_CATEGORY = "modifiedMonitoredElements";
    private static final String MISCELLANEOUS_CATEGORY = "miscellaneous";

    @FXML
    public SplitPane splitPane;

    private ObjectProperty<SecurityAnalysisParameters> securityAnalysisParametersProperty;
    private final Map<String, GridPane> categoryGrids = new LinkedHashMap<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private boolean refreshing;

    public void setSecurityAnalysisParametersProperty(ObjectProperty<SecurityAnalysisParameters> securityAnalysisParametersProperty) {
        this.securityAnalysisParametersProperty = Objects.requireNonNull(securityAnalysisParametersProperty);
        refresh();
        listenerManager.listen(securityAnalysisParametersProperty, (observable, oldValue, newValue) -> refresh());
    }

    private void refresh() {
        refreshing = true;
        try {
            refreshers.forEach(Runnable::run);
        } finally {
            refreshing = false;
        }
    }

    @FXML
    private void initialize() {
        Map<String, String> categoryTitles = new LinkedHashMap<>();
        categoryTitles.put(GENERAL_CATEGORY, Messages.get("securityAnalysis.category.general"));
        categoryTitles.put(INCREASED_VIOLATIONS_CATEGORY, Messages.get("securityAnalysis.category.increasedViolations"));
        categoryTitles.put(MODIFIED_MONITORED_ELEMENTS_CATEGORY, Messages.get("securityAnalysis.category.modifiedMonitoredElements"));
        categoryTitles.put(MISCELLANEOUS_CATEGORY, Messages.get("securityAnalysis.category.miscellaneous"));
        categoryTitles.keySet().forEach(category -> categoryGrids.put(category, createCategoryGrid()));

        addGeneralFields();
        addIncreasedViolationsFields();
        addModifiedMonitoredElementsFields();
        addMiscellaneousFields();

        ListView<String> categoryListView = new ListView<>();
        Map<String, ScrollPane> categoryPanes = new LinkedHashMap<>();
        categoryTitles.forEach((category, title) -> {
            ScrollPane scrollPane = new ScrollPane(categoryGrids.get(category));
            scrollPane.setFitToWidth(true);
            categoryPanes.put(title, scrollPane);
            categoryListView.getItems().add(title);
        });

        StackPane detailPane = new StackPane();
        categoryListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                detailPane.getChildren().setAll(categoryPanes.get(newValue));
            }
        });
        categoryListView.setPrefWidth(220);
        categoryListView.getSelectionModel().selectFirst();

        splitPane.getItems().addAll(categoryListView, detailPane);
        splitPane.setDividerPositions(0.18);
    }

    private static GridPane createCategoryGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setHalignment(HPos.RIGHT);
        labelColumn.setPrefWidth(320);
        labelColumn.setMaxWidth(320);
        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setHalignment(HPos.LEFT);
        controlColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, controlColumn);
        return grid;
    }

    // extension is self-healing: a SecurityAnalysisParameters read back without it (e.g. imported from a JSON
    // file that predates it) would otherwise silently lose every edit made through this extension's fields
    private static OpenSecurityAnalysisParameters ext(SecurityAnalysisParameters parameters) {
        OpenSecurityAnalysisParameters extension = parameters.getExtension(OpenSecurityAnalysisParameters.class);
        if (extension == null) {
            extension = new OpenSecurityAnalysisParameters();
            parameters.addExtension(OpenSecurityAnalysisParameters.class, extension);
        }
        return extension;
    }

    private void addRow(String category, String labelText, String tooltipText, Control control) {
        GridPane grid = categoryGrids.get(category);
        Label label = new Label(labelText);
        label.setWrapText(true);
        Tooltip tooltip = new Tooltip(tooltipText);
        label.setTooltip(tooltip);
        control.setTooltip(tooltip);
        grid.addRow(grid.getRowCount(), label, control);
    }

    private static void bindCommit(TextField textField, Runnable commit) {
        textField.setOnAction(event -> commit.run());
        textField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (Boolean.TRUE.equals(wasFocused) && Boolean.FALSE.equals(isFocused)) {
                commit.run();
            }
        });
    }

    private void addGeneralFields() {
        addBooleanField(GENERAL_CATEGORY, Messages.get("securityAnalysis.param.createResultExtension.label"),
                Messages.get("securityAnalysis.param.createResultExtension.tooltip"),
                p -> ext(p).isCreateResultExtension(), (p, v) -> ext(p).setCreateResultExtension(v));
        addBooleanField(GENERAL_CATEGORY, Messages.get("securityAnalysis.param.contingencyPropagation.label"),
                Messages.get("securityAnalysis.param.contingencyPropagation.tooltip"),
                p -> ext(p).isContingencyPropagation(), (p, v) -> ext(p).setContingencyPropagation(v));
        addIntField(GENERAL_CATEGORY, Messages.get("securityAnalysis.param.threadCount.label"),
                Messages.get("securityAnalysis.param.threadCount.tooltip"),
                p -> ext(p).getThreadCount(), (p, v) -> ext(p).setThreadCount(v));
        addBooleanField(GENERAL_CATEGORY, Messages.get("securityAnalysis.param.dcFastMode.label"),
                Messages.get("securityAnalysis.param.dcFastMode.tooltip"),
                p -> ext(p).isDcFastMode(), (p, v) -> ext(p).setDcFastMode(v));
        addContingencyActivePowerLossDistributionField(GENERAL_CATEGORY);
        addBooleanField(GENERAL_CATEGORY, Messages.get("securityAnalysis.param.startWithFrozenACEmulation.label"),
                Messages.get("securityAnalysis.param.startWithFrozenACEmulation.tooltip"),
                p -> ext(p).isStartWithFrozenACEmulation(), (p, v) -> ext(p).setStartWithFrozenACEmulation(v));
    }

    private void addIncreasedViolationsFields() {
        addDoubleField(INCREASED_VIOLATIONS_CATEGORY, Messages.get("securityAnalysis.param.flowProportionalThreshold.label"),
                Messages.get("securityAnalysis.param.flowProportionalThreshold.tooltip"),
                p -> p.getIncreasedViolationsParameters().getFlowProportionalThreshold(),
                (p, v) -> p.getIncreasedViolationsParameters().setFlowProportionalThreshold(v));
        addDoubleField(INCREASED_VIOLATIONS_CATEGORY, Messages.get("securityAnalysis.param.lowVoltageProportionalThreshold.label"),
                Messages.get("securityAnalysis.param.lowVoltageProportionalThreshold.tooltip"),
                p -> p.getIncreasedViolationsParameters().getLowVoltageProportionalThreshold(),
                (p, v) -> p.getIncreasedViolationsParameters().setLowVoltageProportionalThreshold(v));
        addDoubleField(INCREASED_VIOLATIONS_CATEGORY, Messages.get("securityAnalysis.param.lowVoltageAbsoluteThreshold.label"),
                Messages.get("securityAnalysis.param.lowVoltageAbsoluteThreshold.tooltip"),
                p -> p.getIncreasedViolationsParameters().getLowVoltageAbsoluteThreshold(),
                (p, v) -> p.getIncreasedViolationsParameters().setLowVoltageAbsoluteThreshold(v));
        addDoubleField(INCREASED_VIOLATIONS_CATEGORY, Messages.get("securityAnalysis.param.highVoltageProportionalThreshold.label"),
                Messages.get("securityAnalysis.param.highVoltageProportionalThreshold.tooltip"),
                p -> p.getIncreasedViolationsParameters().getHighVoltageProportionalThreshold(),
                (p, v) -> p.getIncreasedViolationsParameters().setHighVoltageProportionalThreshold(v));
        addDoubleField(INCREASED_VIOLATIONS_CATEGORY, Messages.get("securityAnalysis.param.highVoltageAbsoluteThreshold.label"),
                Messages.get("securityAnalysis.param.highVoltageAbsoluteThreshold.tooltip"),
                p -> p.getIncreasedViolationsParameters().getHighVoltageAbsoluteThreshold(),
                (p, v) -> p.getIncreasedViolationsParameters().setHighVoltageAbsoluteThreshold(v));
    }

    private void addModifiedMonitoredElementsFields() {
        addDoubleField(MODIFIED_MONITORED_ELEMENTS_CATEGORY, Messages.get("securityAnalysis.param.powerModificationThreshold.label"),
                Messages.get("securityAnalysis.param.powerModificationThreshold.tooltip"),
                p -> p.getModifiedMonitoredElementsParameters().getPowerModificationThreshold(),
                (p, v) -> p.getModifiedMonitoredElementsParameters().setPowerModificationThreshold(v));
        addDoubleField(MODIFIED_MONITORED_ELEMENTS_CATEGORY, Messages.get("securityAnalysis.param.voltageModificationProportionalThreshold.label"),
                Messages.get("securityAnalysis.param.voltageModificationProportionalThreshold.tooltip"),
                p -> p.getModifiedMonitoredElementsParameters().getVoltageModificationProportionalThreshold(),
                (p, v) -> p.getModifiedMonitoredElementsParameters().setVoltageModificationProportionalThreshold(v));
        addDoubleField(MODIFIED_MONITORED_ELEMENTS_CATEGORY, Messages.get("securityAnalysis.param.voltageModificationAbsoluteThreshold.label"),
                Messages.get("securityAnalysis.param.voltageModificationAbsoluteThreshold.tooltip"),
                p -> p.getModifiedMonitoredElementsParameters().getVoltageModificationAbsoluteThreshold(),
                (p, v) -> p.getModifiedMonitoredElementsParameters().setVoltageModificationAbsoluteThreshold(v));
    }

    private void addMiscellaneousFields() {
        addBooleanField(MISCELLANEOUS_CATEGORY, Messages.get("securityAnalysis.param.intermediateResultsInOperatorStrategy.label"),
                Messages.get("securityAnalysis.param.intermediateResultsInOperatorStrategy.tooltip"),
                SecurityAnalysisParameters::getIntermediateResultsInOperatorStrategy, SecurityAnalysisParameters::setIntermediateResultsInOperatorStrategy);
        addDebugDirField(MISCELLANEOUS_CATEGORY);
    }

    private void addBooleanField(String category, String label, String tooltip,
                                  Predicate<SecurityAnalysisParameters> getter, BiConsumer<SecurityAnalysisParameters, Boolean> setter) {
        CheckBox checkBox = new CheckBox();
        refreshers.add(() -> checkBox.setSelected(getter.test(securityAnalysisParametersProperty.getValue())));
        checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing) {
                setter.accept(securityAnalysisParametersProperty.getValue(), newValue);
            }
        });
        addRow(category, label, tooltip, checkBox);
    }

    private void addDoubleField(String category, String label, String tooltip,
                                 ToDoubleFunction<SecurityAnalysisParameters> getter, BiConsumer<SecurityAnalysisParameters, Double> setter) {
        TextField textField = new TextField();
        refreshers.add(() -> textField.setText(Double.toString(getter.applyAsDouble(securityAnalysisParametersProperty.getValue()))));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            try {
                setter.accept(securityAnalysisParametersProperty.getValue(), Double.parseDouble(textField.getText()));
            } catch (NumberFormatException e) {
                refresh();
            }
        };
        bindCommit(textField, commit);
        addRow(category, label, tooltip, textField);
    }

    private void addIntField(String category, String label, String tooltip,
                              ToIntFunction<SecurityAnalysisParameters> getter, BiConsumer<SecurityAnalysisParameters, Integer> setter) {
        TextField textField = new TextField();
        refreshers.add(() -> textField.setText(Integer.toString(getter.applyAsInt(securityAnalysisParametersProperty.getValue()))));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            try {
                setter.accept(securityAnalysisParametersProperty.getValue(), Integer.parseInt(textField.getText()));
            } catch (IllegalArgumentException e) {
                refresh();
            }
        };
        bindCommit(textField, commit);
        addRow(category, label, tooltip, textField);
    }

    private void addDebugDirField(String category) {
        TextField textField = new TextField();
        refreshers.add(() -> textField.setText(Objects.toString(securityAnalysisParametersProperty.getValue().getDebugDir(), "")));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            String text = textField.getText();
            securityAnalysisParametersProperty.getValue().setDebugDir(text.isEmpty() ? null : text);
        };
        bindCommit(textField, commit);
        addRow(category, Messages.get("securityAnalysis.param.debugDir.label"), Messages.get("securityAnalysis.param.debugDir.tooltip"), textField);
    }

    private void addContingencyActivePowerLossDistributionField(String category) {
        ChoiceBox<String> choiceBox = new ChoiceBox<>(FXCollections.observableArrayList(
                ContingencyActivePowerLossDistribution.findAll().stream().map(ContingencyActivePowerLossDistribution::getName).toList()));
        refreshers.add(() -> choiceBox.setValue(ext(securityAnalysisParametersProperty.getValue()).getContingencyActivePowerLossDistribution()));
        choiceBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                ext(securityAnalysisParametersProperty.getValue()).setContingencyActivePowerLossDistribution(newValue);
            }
        });
        addRow(category, Messages.get("securityAnalysis.param.contingencyActivePowerLossDistribution.label"),
                Messages.get("securityAnalysis.param.contingencyActivePowerLossDistribution.tooltip"), choiceBox);
    }

    public void onImport(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        addJsonExtensionFilters(fileChooser);
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File selectedFile = fileChooser.showOpenDialog(this.splitPane.getScene().getWindow());
        if (selectedFile != null) {
            FileChooserPreferences.saveLastDirectory(selectedFile);
            importFrom(selectedFile.toPath());
        }
    }

    public void onExport(ActionEvent actionEvent) {
        FileChooser fileChooser = new FileChooser();
        addJsonExtensionFilters(fileChooser);
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File selectedFile = fileChooser.showSaveDialog(this.splitPane.getScene().getWindow());
        if (selectedFile != null) {
            FileChooserPreferences.saveLastDirectory(selectedFile);
            exportTo(selectedFile.toPath());
        }
    }

    private static void addJsonExtensionFilters(FileChooser fileChooser) {
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*"));
    }

    // the imported parameters' own embedded LoadFlowParameters is immediately overridden by MainModel with the
    // app's single authoritative instance - see MainModel.syncSecurityAnalysisLoadFlowParameters()
    public void importFrom(Path path) {
        securityAnalysisParametersProperty.setValue(JsonSecurityAnalysisParameters.read(path));
    }

    public void exportTo(Path path) {
        JsonSecurityAnalysisParameters.write(securityAnalysisParametersProperty.getValue(), path);
    }
}
