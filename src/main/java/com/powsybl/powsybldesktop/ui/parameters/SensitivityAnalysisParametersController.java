/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.parameters;

import com.powsybl.commons.PowsyblException;
import com.powsybl.openloadflow.sensi.OpenSensitivityAnalysisParameters;
import com.powsybl.powsybldesktop.parameters.ParametersModel;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisMode;
import com.powsybl.powsybldesktop.ui.controls.FileChooserPreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.json.JsonSensitivityAnalysisParameters;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Edits {@link SensitivityAnalysisParameters} plus its {@link OpenSensitivityAnalysisParameters} extension, and the
 * app's own {@link SensitivityAnalysisMode}. As for the security analysis, the embedded load flow parameters are not
 * shown: {@link ParametersModel} keeps them wired to the app's single {@code LoadFlowParameters} instance. Operator
 * strategies aren't supported by the app, nor their calculation mode shown.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SensitivityAnalysisParametersController extends AbstractDiagramParametersController<SensitivityAnalysisParameters> {

    private static final String CAT_GENERAL = "general";
    private static final String CAT_THRESHOLDS = "thresholds";
    private static final String CAT_MISCELLANEOUS = "miscellaneous";

    private final ChoiceBox<SensitivityAnalysisMode> modeChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(SensitivityAnalysisMode.values()));
    private ObjectProperty<SensitivityAnalysisMode> modeProperty;
    private BiConsumer<Path, RuntimeException> onImportFailed = (_, _) -> { };

    @FXML
    private void initialize() {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put(CAT_GENERAL, Messages.get("sensitivityAnalysis.category.general"));
        titles.put(CAT_THRESHOLDS, Messages.get("sensitivityAnalysis.category.thresholds"));
        titles.put(CAT_MISCELLANEOUS, Messages.get("sensitivityAnalysis.category.miscellaneous"));
        buildCategoryList(titles);
    }

    public void setParametersModel(ParametersModel parametersModel) {
        modeProperty = parametersModel.sensitivityAnalysisModeProperty();
        setParametersProperty(parametersModel.sensitivityAnalysisParametersProperty());
        setOnChange(parametersModel::parametersChanged);
        listenerManager.listen(modeProperty, (_, _, _) -> refresh());
        // the "same as load flow" choice tells whether that is AC or DC
        listenerManager.listen(parametersModel.parametersRevisionProperty(), (_, _, _) -> refreshModeLabels(parametersModel));
        refreshModeLabels(parametersModel);
    }

    public void setOnImportFailed(BiConsumer<Path, RuntimeException> onImportFailed) {
        this.onImportFailed = Objects.requireNonNull(onImportFailed);
    }

    // extension is self-healing, as in SecurityAnalysisParametersController
    private static OpenSensitivityAnalysisParameters ext(SensitivityAnalysisParameters parameters) {
        OpenSensitivityAnalysisParameters extension = parameters.getExtension(OpenSensitivityAnalysisParameters.class);
        if (extension == null) {
            extension = new OpenSensitivityAnalysisParameters();
            parameters.addExtension(OpenSensitivityAnalysisParameters.class, extension);
        }
        return extension;
    }

    @Override
    protected void buildFields() {
        refreshers.add(() -> modeChoiceBox.setValue(modeProperty.getValue()));
        modeChoiceBox.valueProperty().addListener((_, _, newValue) -> {
            if (!refreshing && newValue != null) {
                modeProperty.setValue(newValue);
            }
        });
        addRow(CAT_GENERAL, label("mode"), tooltip("mode"), modeChoiceBox);
        addIntField(CAT_GENERAL, label("threadCount"), tooltip("threadCount"),
                p -> ext(p).getThreadCount(), (p, v) -> ext(p).setThreadCount(v));
        addBooleanField(CAT_GENERAL, label("startWithFrozenACEmulation"), tooltip("startWithFrozenACEmulation"),
                p -> ext(p).isStartWithFrozenACEmulation(), (p, v) -> ext(p).setStartWithFrozenACEmulation(v));

        addDoubleField(CAT_THRESHOLDS, label("flowFlowThreshold"), tooltip("flowFlowThreshold"),
                SensitivityAnalysisParameters::getFlowFlowSensitivityValueThreshold, SensitivityAnalysisParameters::setFlowFlowSensitivityValueThreshold);
        addDoubleField(CAT_THRESHOLDS, label("voltageVoltageThreshold"), tooltip("voltageVoltageThreshold"),
                SensitivityAnalysisParameters::getVoltageVoltageSensitivityValueThreshold, SensitivityAnalysisParameters::setVoltageVoltageSensitivityValueThreshold);
        addDoubleField(CAT_THRESHOLDS, label("flowVoltageThreshold"), tooltip("flowVoltageThreshold"),
                SensitivityAnalysisParameters::getFlowVoltageSensitivityValueThreshold, SensitivityAnalysisParameters::setFlowVoltageSensitivityValueThreshold);
        addDoubleField(CAT_THRESHOLDS, label("angleFlowThreshold"), tooltip("angleFlowThreshold"),
                SensitivityAnalysisParameters::getAngleFlowSensitivityValueThreshold, SensitivityAnalysisParameters::setAngleFlowSensitivityValueThreshold);

        // Open Load Flow reads its own extension's debug directory, not SensitivityAnalysisParameters'
        addStringField(CAT_MISCELLANEOUS, Messages.get("parameters.debugDir.label"), Messages.get("parameters.debugDir.tooltip"),
                p -> ext(p).getDebugDir(), (p, v) -> ext(p).setDebugDir(v.isEmpty() ? null : v));
    }

    private void refreshModeLabels(ParametersModel parametersModel) {
        boolean loadFlowDc = parametersModel.loadFlowParametersProperty().getValue().isDc();
        modeChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(SensitivityAnalysisMode mode) {
                if (mode == null) {
                    return "";
                }
                return mode == SensitivityAnalysisMode.LOAD_FLOW
                        ? Messages.get("sensitivityAnalysis.mode.loadFlow", Messages.get(loadFlowDc ? "sensitivityAnalysis.mode.dc" : "sensitivityAnalysis.mode.ac"))
                        : Messages.get("sensitivityAnalysis.mode." + mode.name().toLowerCase(Locale.ROOT));
            }

            @Override
            public SensitivityAnalysisMode fromString(String string) {
                throw new UnsupportedOperationException();
            }
        });
    }

    private static String label(String param) {
        return Messages.get("sensitivityAnalysis.param." + param + ".label");
    }

    private static String tooltip(String param) {
        return Messages.get("sensitivityAnalysis.param." + param + ".tooltip");
    }

    @FXML
    private void onImport() {
        File file = createFileChooser().showOpenDialog(splitPane.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            importFrom(file.toPath());
        }
    }

    @FXML
    private void onExport() {
        File file = createFileChooser().showSaveDialog(splitPane.getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            exportTo(file.toPath());
        }
    }

    private static FileChooser createFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*"));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        return fileChooser;
    }

    // PowSyBl's format: the mode, the app's own, is neither imported nor exported. The imported embedded load flow
    // parameters are replaced by the app's own, as for the security analysis
    void importFrom(Path path) {
        SensitivityAnalysisParameters parameters;
        try {
            parameters = JsonSensitivityAnalysisParameters.read(path);
        } catch (PowsyblException | UncheckedIOException e) {
            onImportFailed.accept(path, e);
            return;
        }
        ext(parameters);
        parametersProperty.setValue(parameters);
    }

    void exportTo(Path path) {
        try {
            JsonSensitivityAnalysisParameters.createObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(), parametersProperty.getValue());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
