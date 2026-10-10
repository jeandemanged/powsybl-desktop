/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.powsybldesktop.map.MapController;
import com.powsybl.powsybldesktop.report.ReportNodeTreeCell;

import java.util.Set;

/**
 * The app's own view settings (as opposed to the PowSyBl parameters it passes to computations and diagrams), edited
 * both from the views they apply to and from the parameters view. Setters throw {@link IllegalArgumentException} on
 * invalid values, for {@link ApplicationParametersJson} to reject a malformed file.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class GuiParameters {

    /**
     * The lowest level the logs view shows.
     */
    public enum LogLevel {
        INFO,
        WARN,
        ERROR
    }

    private MapController.Basemap mapBasemap = MapController.Basemap.OFFLINE;
    // names of the base voltages (see BaseVoltagesConfig) whose substations and lines the map view hides
    private Set<String> mapHiddenBaseVoltages = Set.of();
    private LogLevel logsMinLevel = LogLevel.INFO;
    private boolean logsNewestOnTop = true;
    private String reportsMinSeverity = ReportNodeTreeCell.SEVERITIES.getFirst();

    public MapController.Basemap getMapBasemap() {
        return mapBasemap;
    }

    public void setMapBasemap(MapController.Basemap mapBasemap) {
        this.mapBasemap = requireNonNull(mapBasemap, "mapBasemap");
    }

    public Set<String> getMapHiddenBaseVoltages() {
        return mapHiddenBaseVoltages;
    }

    public void setMapHiddenBaseVoltages(Set<String> mapHiddenBaseVoltages) {
        this.mapHiddenBaseVoltages = Set.copyOf(requireNonNull(mapHiddenBaseVoltages, "mapHiddenBaseVoltages"));
    }

    public LogLevel getLogsMinLevel() {
        return logsMinLevel;
    }

    public void setLogsMinLevel(LogLevel logsMinLevel) {
        this.logsMinLevel = requireNonNull(logsMinLevel, "logsMinLevel");
    }

    public boolean isLogsNewestOnTop() {
        return logsNewestOnTop;
    }

    public void setLogsNewestOnTop(boolean logsNewestOnTop) {
        this.logsNewestOnTop = logsNewestOnTop;
    }

    public String getReportsMinSeverity() {
        return reportsMinSeverity;
    }

    public void setReportsMinSeverity(String reportsMinSeverity) {
        if (!ReportNodeTreeCell.SEVERITIES.contains(reportsMinSeverity)) {
            throw new IllegalArgumentException("Unknown report severity: " + reportsMinSeverity);
        }
        this.reportsMinSeverity = reportsMinSeverity;
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is null");
        }
        return value;
    }
}
