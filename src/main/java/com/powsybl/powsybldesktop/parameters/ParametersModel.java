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
import com.powsybl.security.SecurityAnalysisParameters;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyLongProperty;
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
        syncSecurityAnalysisLoadFlowParameters();
        // SecurityAnalysisParameters embeds a LoadFlowParameters, but this app has a single authoritative
        // LoadFlowParameters instance (loadFlowParameters above); keep it wired into whichever
        // SecurityAnalysisParameters is current rather than letting the two diverge (e.g. after importing a
        // security analysis JSON that carries its own, stale load flow section).
        loadFlowParameters.addListener((observable, oldValue, newValue) -> syncSecurityAnalysisLoadFlowParameters());
        securityAnalysisParameters.addListener((observable, oldValue, newValue) -> syncSecurityAnalysisLoadFlowParameters());
        sldParameters.setValue(defaults.sld());
        nadParameters.setValue(defaults.nad());
        guiParameters.setValue(defaults.gui());
        loadFlowParameters.addListener((observable, oldValue, newValue) -> parametersChanged());
        securityAnalysisParameters.addListener((observable, oldValue, newValue) -> parametersChanged());
        sldParameters.addListener((observable, oldValue, newValue) -> parametersChanged());
        nadParameters.addListener((observable, oldValue, newValue) -> parametersChanged());
        guiParameters.addListener((observable, oldValue, newValue) -> parametersChanged());
    }

    private void syncSecurityAnalysisLoadFlowParameters() {
        securityAnalysisParameters.getValue().setLoadFlowParameters(loadFlowParameters.getValue());
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
                guiParameters.getValue());
    }

    public void setParameters(ApplicationParameters parameters) {
        Objects.requireNonNull(parameters);
        networkImportParameters.clear();
        networkImportParameters.putAll(copy(parameters.networkImport()));
        networkExportParameters.clear();
        networkExportParameters.putAll(copy(parameters.networkExport()));
        securityAnalysisParameters.setValue(parameters.securityAnalysis());
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
        return networkImportParameters.computeIfAbsent(format, f -> new Properties());
    }

    public Properties getNetworkExportParameters(String format) {
        return networkExportParameters.computeIfAbsent(format, f -> new Properties());
    }
}
