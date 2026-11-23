/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.powsybldesktop.rao.CracGeneratorSettings;
import com.powsybl.powsybldesktop.rao.RaoParametersSupport;
import com.powsybl.powsybldesktop.sensitivity.SensitivityAnalysisMode;
import com.powsybl.security.SecurityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ParametersModel {
    private final ObjectProperty<LoadFlowParameters> loadFlowParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<SecurityAnalysisParameters> securityAnalysisParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<SensitivityAnalysisParameters> sensitivityAnalysisParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<SensitivityAnalysisMode> sensitivityAnalysisMode = new SimpleObjectProperty<>();
    private final ObjectProperty<RaoParameters> raoParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<SensitivityAnalysisMode> raoMode = new SimpleObjectProperty<>();
    private final BooleanProperty fastRao = new SimpleBooleanProperty();
    private final ObjectProperty<CracCreationParameters> cracCreationParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<CracGeneratorSettings> cracGeneratorSettings = new SimpleObjectProperty<>();
    private final ObjectProperty<DesktopSldParameters> sldParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<DesktopNadParameters> nadParameters = new SimpleObjectProperty<>();
    private final ObjectProperty<GuiParameters> guiParameters = new SimpleObjectProperty<>();
    // the diagram and GUI parameters are edited in place, so their properties don't fire: these counters are bumped
    // instead, for the displayed diagrams to re-render and the views to apply their settings
    private final LongProperty sldParametersRevision = new SimpleLongProperty();
    private final LongProperty nadParametersRevision = new SimpleLongProperty();
    private final LongProperty guiParametersRevision = new SimpleLongProperty();
    // bumped on any parameter edit or replacement, for the parameters view to compare them against the saved file
    private final LongProperty parametersRevision = new SimpleLongProperty();
    // JSON of the parameters as last saved to / restored from the configuration file, null if there's none
    private JsonNode savedParameters;
    // keyed by format as listed in the parameters view / import-export menus (e.g. "IIDM" for all IIDM importers),
    // holding only the values the user edited so the importer/exporter falls back to its own defaults otherwise
    private final Map<String, Properties> networkImportParameters = new HashMap<>();
    private final Map<String, Properties> networkExportParameters = new HashMap<>();

    public ParametersModel() {
        ApplicationParameters defaults = ApplicationParameters.createDefault();
        loadFlowParameters.setValue(defaults.loadFlow());
        securityAnalysisParameters.setValue(defaults.securityAnalysis());
        sensitivityAnalysisParameters.setValue(defaults.sensitivityAnalysis());
        sensitivityAnalysisMode.setValue(defaults.sensitivityAnalysisMode());
        raoParameters.setValue(defaults.rao());
        raoMode.setValue(defaults.raoMode());
        fastRao.set(defaults.fastRao());
        cracCreationParameters.setValue(defaults.cracCreation());
        cracGeneratorSettings.setValue(defaults.cracGenerator());
        syncEmbeddedLoadFlowParameters();
        // Security and sensitivity analysis and RAO parameters embed a LoadFlowParameters, but this app has a single
        // authoritative LoadFlowParameters instance (loadFlowParameters above); keep it wired into whichever
        // parameters are current rather than letting them diverge (e.g. after importing a security analysis JSON
        // that carries its own, stale load flow section).
        loadFlowParameters.addListener((_, _, _) -> syncEmbeddedLoadFlowParameters());
        securityAnalysisParameters.addListener((_, _, _) -> syncEmbeddedLoadFlowParameters());
        sensitivityAnalysisParameters.addListener((_, _, _) -> syncEmbeddedLoadFlowParameters());
        raoParameters.addListener((_, _, _) -> syncEmbeddedLoadFlowParameters());
        sldParameters.setValue(defaults.sld());
        nadParameters.setValue(defaults.nad());
        guiParameters.setValue(defaults.gui());
        loadFlowParameters.addListener((_, _, _) -> parametersChanged());
        securityAnalysisParameters.addListener((_, _, _) -> parametersChanged());
        sensitivityAnalysisParameters.addListener((_, _, _) -> parametersChanged());
        sensitivityAnalysisMode.addListener((_, _, _) -> parametersChanged());
        raoParameters.addListener((_, _, _) -> parametersChanged());
        raoMode.addListener((_, _, _) -> parametersChanged());
        fastRao.addListener((_, _, _) -> parametersChanged());
        cracCreationParameters.addListener((_, _, _) -> parametersChanged());
        cracGeneratorSettings.addListener((_, _, _) -> parametersChanged());
        sldParameters.addListener((_, _, _) -> parametersChanged());
        nadParameters.addListener((_, _, _) -> parametersChanged());
        guiParameters.addListener((_, _, _) -> parametersChanged());
    }

    private void syncEmbeddedLoadFlowParameters() {
        securityAnalysisParameters.getValue().setLoadFlowParameters(loadFlowParameters.getValue());
        sensitivityAnalysisParameters.getValue().setLoadFlowParameters(loadFlowParameters.getValue());
        RaoParametersSupport.sensitivityParameters(raoParameters.getValue()).setLoadFlowParameters(loadFlowParameters.getValue());
    }

    public void setLoadFlowParameters(LoadFlowParameters loadFlowParameters) {
        Objects.requireNonNull(loadFlowParameters);
        this.loadFlowParameters.setValue(loadFlowParameters);
    }

    public ObjectProperty<LoadFlowParameters> loadFlowParametersProperty() {
        return loadFlowParameters;
    }

    public ObjectProperty<SecurityAnalysisParameters> securityAnalysisParametersProperty() {
        return securityAnalysisParameters;
    }

    public ObjectProperty<SensitivityAnalysisParameters> sensitivityAnalysisParametersProperty() {
        return sensitivityAnalysisParameters;
    }

    public ObjectProperty<SensitivityAnalysisMode> sensitivityAnalysisModeProperty() {
        return sensitivityAnalysisMode;
    }

    public ObjectProperty<RaoParameters> raoParametersProperty() {
        return raoParameters;
    }

    public ObjectProperty<SensitivityAnalysisMode> raoModeProperty() {
        return raoMode;
    }

    public BooleanProperty fastRaoProperty() {
        return fastRao;
    }

    public ObjectProperty<CracCreationParameters> cracCreationParametersProperty() {
        return cracCreationParameters;
    }

    public ObjectProperty<CracGeneratorSettings> cracGeneratorSettingsProperty() {
        return cracGeneratorSettings;
    }

    public ObjectProperty<DesktopSldParameters> sldParametersProperty() {
        return sldParameters;
    }

    public ObjectProperty<DesktopNadParameters> nadParametersProperty() {
        return nadParameters;
    }

    public ReadOnlyLongProperty sldParametersRevisionProperty() {
        return sldParametersRevision;
    }

    public void sldParametersChanged() {
        sldParametersRevision.set(sldParametersRevision.get() + 1);
        parametersChanged();
    }

    public ReadOnlyLongProperty nadParametersRevisionProperty() {
        return nadParametersRevision;
    }

    public void nadParametersChanged() {
        nadParametersRevision.set(nadParametersRevision.get() + 1);
        parametersChanged();
    }

    public ObjectProperty<GuiParameters> guiParametersProperty() {
        return guiParameters;
    }

    public GuiParameters getGuiParameters() {
        return guiParameters.getValue();
    }

    public ReadOnlyLongProperty guiParametersRevisionProperty() {
        return guiParametersRevision;
    }

    public void guiParametersChanged() {
        guiParametersRevision.set(guiParametersRevision.get() + 1);
        parametersChanged();
    }

    public ReadOnlyLongProperty parametersRevisionProperty() {
        return parametersRevision;
    }

    public void parametersChanged() {
        parametersRevision.set(parametersRevision.get() + 1);
    }

    // the returned objects are the live ones, edited in place by the parameters view
    public ApplicationParameters getParameters() {
        return new ApplicationParameters(Map.copyOf(networkImportParameters), Map.copyOf(networkExportParameters),
                sldParameters.getValue(), nadParameters.getValue(), loadFlowParameters.getValue(), securityAnalysisParameters.getValue(),
                sensitivityAnalysisParameters.getValue(), sensitivityAnalysisMode.getValue(), raoParameters.getValue(), raoMode.getValue(),
                fastRao.get(), cracCreationParameters.getValue(), cracGeneratorSettings.getValue(),
                guiParameters.getValue());
    }

    public void setParameters(ApplicationParameters parameters) {
        Objects.requireNonNull(parameters);
        networkImportParameters.clear();
        networkImportParameters.putAll(copy(parameters.networkImport()));
        networkExportParameters.clear();
        networkExportParameters.putAll(copy(parameters.networkExport()));
        securityAnalysisParameters.setValue(parameters.securityAnalysis());
        sensitivityAnalysisParameters.setValue(parameters.sensitivityAnalysis());
        sensitivityAnalysisMode.setValue(parameters.sensitivityAnalysisMode());
        raoParameters.setValue(parameters.rao());
        raoMode.setValue(parameters.raoMode());
        fastRao.set(parameters.fastRao());
        cracCreationParameters.setValue(parameters.cracCreation());
        cracGeneratorSettings.setValue(parameters.cracGenerator());
        loadFlowParameters.setValue(parameters.loadFlow());
        sldParameters.setValue(parameters.sld());
        nadParameters.setValue(parameters.nad());
        guiParameters.setValue(parameters.gui());
        sldParametersChanged();
        nadParametersChanged();
        guiParametersChanged();
    }

    private static Map<String, Properties> copy(Map<String, Properties> parameters) {
        return parameters.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
            Properties properties = new Properties();
            properties.putAll(e.getValue());
            return properties;
        }));
    }

    public JsonNode getSavedParameters() {
        return savedParameters;
    }

    public void setSavedParameters(JsonNode savedParameters) {
        this.savedParameters = savedParameters;
    }

    public Properties getNetworkImportParameters(String format) {
        return networkImportParameters.computeIfAbsent(format, _ -> new Properties());
    }

    public Properties getNetworkExportParameters(String format) {
        return networkExportParameters.computeIfAbsent(format, _ -> new Properties());
    }
}
