/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The app's own view settings (as opposed to the PowSyBl parameters it passes to computations and diagrams), edited
 * both from the views they apply to and from the parameters view. The decimal places per {@link Quantity} are the
 * single source for every displayed value, single line and network area diagrams included. Setters throw {@link IllegalArgumentException} on
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

    /**
     * A kind of displayed value, with its own number of decimal places.
     */
    public enum Quantity {
        VOLTAGE(1),
        ANGLE(2),
        // active, reactive and apparent
        POWER(1),
        POWER_FACTOR(2),
        CURRENT(1),
        PERCENTAGE(1),
        IMPEDANCE(2),
        ADMITTANCE(6),
        RHO(6),
        LIMIT_SCALING(2),
        SENSITIVITY(5),
        // latitude and longitude, in degrees
        COORDINATE(6);

        private final int defaultDecimals;

        Quantity(int defaultDecimals) {
            this.defaultDecimals = defaultDecimals;
        }
    }

    public static final int MAX_DECIMALS = 10;

    // PowSyBl report severities, lowest first
    public static final List<String> REPORT_SEVERITIES = List.of("TRACE", "DEBUG", "INFO", "DETAIL", "WARN", "ERROR");

    private Basemap mapBasemap = Basemap.OFFLINE;
    // names of the base voltages (see BaseVoltagesConfig) whose substations and lines the map view hides
    private Set<String> mapHiddenBaseVoltages = Set.of();
    private Set<MapViolationLayer> mapShownViolations = Set.copyOf(EnumSet.allOf(MapViolationLayer.class));
    private boolean mapShowFlows = true;
    private LogLevel logsMinLevel = LogLevel.INFO;
    private boolean logsNewestOnTop = true;
    private String reportsMinSeverity = REPORT_SEVERITIES.getFirst();
    private boolean openDetailsAfterCreation = true;
    private boolean confirmExit = true;
    private boolean nadKeepPositionsWhenCentering = true;
    private final Map<Quantity, Integer> decimals = new EnumMap<>(Quantity.class);

    public GuiParameters() {
        for (Quantity quantity : Quantity.values()) {
            decimals.put(quantity, quantity.defaultDecimals);
        }
    }

    public Basemap getMapBasemap() {
        return mapBasemap;
    }

    public void setMapBasemap(Basemap mapBasemap) {
        this.mapBasemap = requireNonNull(mapBasemap, "mapBasemap");
    }

    public Set<String> getMapHiddenBaseVoltages() {
        return mapHiddenBaseVoltages;
    }

    public void setMapHiddenBaseVoltages(Set<String> mapHiddenBaseVoltages) {
        this.mapHiddenBaseVoltages = Set.copyOf(requireNonNull(mapHiddenBaseVoltages, "mapHiddenBaseVoltages"));
    }

    public Set<MapViolationLayer> getMapShownViolations() {
        return mapShownViolations;
    }

    public void setMapShownViolations(Set<MapViolationLayer> mapShownViolations) {
        this.mapShownViolations = Set.copyOf(requireNonNull(mapShownViolations, "mapShownViolations"));
    }

    public void setMapViolationsShown(MapViolationLayer layer, boolean shown) {
        Set<MapViolationLayer> layers = EnumSet.noneOf(MapViolationLayer.class);
        layers.addAll(mapShownViolations);
        if (shown) {
            layers.add(layer);
        } else {
            layers.remove(layer);
        }
        setMapShownViolations(layers);
    }

    public boolean isMapShowFlows() {
        return mapShowFlows;
    }

    public void setMapShowFlows(boolean mapShowFlows) {
        this.mapShowFlows = mapShowFlows;
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
        if (!REPORT_SEVERITIES.contains(reportsMinSeverity)) {
            throw new IllegalArgumentException("Unknown report severity: " + reportsMinSeverity);
        }
        this.reportsMinSeverity = reportsMinSeverity;
    }

    /**
     * Whether a creation dialog then opens the created object's detail view, to set what it doesn't ask for.
     */
    public boolean isOpenDetailsAfterCreation() {
        return openDetailsAfterCreation;
    }

    public void setOpenDetailsAfterCreation(boolean openDetailsAfterCreation) {
        this.openDetailsAfterCreation = openDetailsAfterCreation;
    }

    /**
     * Whether exiting the application with networks loaded first asks for confirmation, as their changes are lost.
     */
    public boolean isConfirmExit() {
        return confirmExit;
    }

    public void setConfirmExit(boolean confirmExit) {
        this.confirmExit = confirmExit;
    }

    /**
     * Whether a network area diagram centered on another voltage level keeps the voltage levels shown before and after
     * where they were, laying out only the others (force layouts).
     */
    public boolean isNadKeepPositionsWhenCentering() {
        return nadKeepPositionsWhenCentering;
    }

    public void setNadKeepPositionsWhenCentering(boolean nadKeepPositionsWhenCentering) {
        this.nadKeepPositionsWhenCentering = nadKeepPositionsWhenCentering;
    }

    public int getDecimals(Quantity quantity) {
        return decimals.get(quantity);
    }

    public void setDecimals(Quantity quantity, int decimals) {
        if (decimals < 0 || decimals > MAX_DECIMALS) {
            throw new IllegalArgumentException("Decimals out of [0, " + MAX_DECIMALS + "]: " + decimals);
        }
        this.decimals.put(requireNonNull(quantity, "quantity"), decimals);
    }

    public Map<Quantity, Integer> getDecimals() {
        return Map.copyOf(decimals);
    }

    public String format(Quantity quantity, double value) {
        String pattern = String.format(Locale.ROOT, "%%.%df", getDecimals(quantity));
        return String.format(Locale.ROOT, pattern, value);
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is null");
        }
        return value;
    }
}
