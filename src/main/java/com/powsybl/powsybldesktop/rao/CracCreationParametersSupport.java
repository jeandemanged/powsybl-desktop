/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.RaUsageLimits;
import com.powsybl.openrao.data.crac.api.parameters.AbstractAlignedRaCracCreationParameters;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.api.parameters.JsonCracCreationParameters;
import com.powsybl.openrao.data.crac.io.cim.parameters.CimCracCreationParameters;
import com.powsybl.openrao.data.crac.io.cim.parameters.RangeActionSpeed;
import com.powsybl.openrao.data.crac.io.cim.parameters.VoltageCnecsCreationParameters;
import com.powsybl.openrao.data.crac.io.cim.parameters.VoltageMonitoredContingenciesAndThresholds;
import com.powsybl.openrao.data.crac.io.cim.parameters.VoltageThreshold;
import com.powsybl.openrao.data.crac.io.cse.parameters.BusBarChangeSwitches;
import com.powsybl.openrao.data.crac.io.cse.parameters.CseCracCreationParameters;
import com.powsybl.openrao.data.crac.io.cse.parameters.SwitchPairId;
import com.powsybl.openrao.data.crac.io.fbconstraint.parameters.FbConstraintCracCreationParameters;
import com.powsybl.openrao.data.crac.io.nc.parameters.NcCracCreationParameters;
import com.powsybl.openrao.data.crac.io.nc.parameters.SweNcCracCreationParameters;
import com.powsybl.openrao.virtualhubs.HvdcConverter;
import com.powsybl.openrao.virtualhubs.HvdcLine;
import com.powsybl.openrao.virtualhubs.HvdcPole;
import com.powsybl.openrao.virtualhubs.InternalHvdc;
import com.powsybl.powsybldesktop.utils.Messages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * OpenRAO's {@link CracCreationParameters} as the CRAC Creation parameters tab edits them: every list, map or nested
 * structure of the formats' extensions as a table of text rows, nested ones flattened to a row per leaf (an internal
 * HVDC's converter repeats its HVDC and pole). {@code xxxRows} reads a table, {@code setXxx} parses rows back, throwing
 * an {@link IllegalArgumentException} telling the row and why when one is invalid, the parameters then unchanged. A
 * blank cell is an absent value. An extension is added once one of its values is set; a missing one reads as OpenRAO's
 * defaults. Kept toolkit-free for testing.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CracCreationParametersSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GROUP_SEPARATOR = " + ";

    private CracCreationParametersSupport() {
    }

    // ----- JSON (OpenRAO's format) -----

    public static JsonNode toJson(CracCreationParameters parameters) {
        ByteArrayOutputStream json = new ByteArrayOutputStream();
        JsonCracCreationParameters.write(parameters, json);
        try {
            return MAPPER.readTree(json.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static CracCreationParameters fromJson(JsonNode node) {
        try {
            return JsonCracCreationParameters.read(new ByteArrayInputStream(MAPPER.writeValueAsBytes(node)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ----- all formats: default monitored line side -----

    /**
     * The default monitored line side of {@code parameters}, which they only tell as a set of sides.
     */
    public static CracCreationParameters.MonitoredLineSide monitoredLineSide(CracCreationParameters parameters) {
        for (CracCreationParameters.MonitoredLineSide side : CracCreationParameters.MonitoredLineSide.values()) {
            CracCreationParameters probe = new CracCreationParameters();
            probe.setDefaultMonitoredLineSide(side);
            if (probe.getDefaultMonitoredSides().equals(parameters.getDefaultMonitoredSides())) {
                return side;
            }
        }
        return CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_BOTH_SIDES;
    }

    // ----- all formats: remedial action usage limits per instant -----

    /**
     * Rows: instant, max remedial actions.
     */
    public static List<List<String>> usageLimitsRows(CracCreationParameters parameters) {
        return new TreeMap<>(parameters.getRaUsageLimitsPerInstant()).entrySet().stream()
                .map(entry -> List.of(entry.getKey(), text(entry.getValue().getMaxRa())))
                .toList();
    }

    /**
     * Rows: instant, TSO, max topological actions, max PSTs, max remedial actions, max elementary actions.
     */
    public static List<List<String>> usageLimitsPerTsoRows(CracCreationParameters parameters) {
        List<List<String>> rows = new ArrayList<>();
        new TreeMap<>(parameters.getRaUsageLimitsPerInstant()).forEach((instant, limits) -> {
            Set<String> tsos = new TreeSet<>();
            tsos.addAll(limits.getMaxTopoPerTso().keySet());
            tsos.addAll(limits.getMaxPstPerTso().keySet());
            tsos.addAll(limits.getMaxRaPerTso().keySet());
            tsos.addAll(limits.getMaxElementaryActionsPerTso().keySet());
            tsos.forEach(tso -> rows.add(List.of(instant, tso, text(limits.getMaxTopoPerTso().get(tso)), text(limits.getMaxPstPerTso().get(tso)),
                    text(limits.getMaxRaPerTso().get(tso)), text(limits.getMaxElementaryActionsPerTso().get(tso)))));
        });
        return rows;
    }

    /**
     * Sets the usage limits of both tables at once, an instant being in either.
     */
    public static void setUsageLimits(CracCreationParameters parameters, List<List<String>> rows, List<List<String>> perTsoRows) {
        Map<String, RaUsageLimits> limitsPerInstant = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> maxTopo = new HashMap<>();
        Map<String, Map<String, Integer>> maxPst = new HashMap<>();
        Map<String, Map<String, Integer>> maxRa = new HashMap<>();
        Map<String, Map<String, Integer>> maxElementary = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String instant = required(row, 0, i);
            if (limitsPerInstant.containsKey(instant)) {
                throw invalid(i, Messages.get("cracCreation.row.duplicate", instant));
            }
            RaUsageLimits limits = new RaUsageLimits();
            limits.setMaxRa(integer(row, 1, i));
            limitsPerInstant.put(instant, limits);
        }
        for (int i = 0; i < perTsoRows.size(); i++) {
            List<String> row = perTsoRows.get(i);
            String instant = required(row, 0, i);
            String tso = required(row, 1, i);
            putIfPresent(maxTopo, instant, tso, integer(row, 2, i));
            putIfPresent(maxPst, instant, tso, integer(row, 3, i));
            putIfPresent(maxRa, instant, tso, integer(row, 4, i));
            putIfPresent(maxElementary, instant, tso, integer(row, 5, i));
            limitsPerInstant.computeIfAbsent(instant, _ -> new RaUsageLimits());
        }
        limitsPerInstant.forEach((instant, limits) -> {
            try {
                limits.setMaxTopoPerTso(maxTopo.getOrDefault(instant, new HashMap<>()));
                limits.setMaxPstPerTso(maxPst.getOrDefault(instant, new HashMap<>()));
                limits.setMaxRaPerTso(maxRa.getOrDefault(instant, new HashMap<>()));
                limits.setMaxElementaryActionsPerTso(maxElementary.getOrDefault(instant, new HashMap<>()));
            } catch (OpenRaoException e) {
                throw new IllegalArgumentException(Messages.get("cracCreation.usageLimits.inconsistent", instant, e.getMessage()), e);
            }
        });
        parameters.getRaUsageLimitsPerInstant().clear();
        limitsPerInstant.forEach(parameters::addRaUsageLimitsForInstant);
    }

    private static void putIfPresent(Map<String, Map<String, Integer>> map, String instant, String tso, Integer value) {
        if (value != null) {
            map.computeIfAbsent(instant, _ -> new HashMap<>()).put(tso, value);
        }
    }

    // ----- CSE and CIM: aligned range action groups -----

    /**
     * Rows: the ids of a group's range actions, separated by " + ".
     */
    public static List<List<String>> rangeActionGroupsRows(AbstractAlignedRaCracCreationParameters parameters) {
        return parameters.getRangeActionGroupsAsString().stream().map(List::of).toList();
    }

    public static void setRangeActionGroups(AbstractAlignedRaCracCreationParameters parameters, List<List<String>> rows) {
        List<String> groups = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> ids = Arrays.stream(required(rows.get(i), 0, i).split("\\+")).map(String::trim).filter(id -> !id.isEmpty()).toList();
            if (ids.size() < 2) {
                throw invalid(i, Messages.get("cracCreation.groups.tooSmall"));
            }
            groups.add(String.join(GROUP_SEPARATOR, ids));
        }
        parameters.setRangeActionGroupsAsString(groups);
    }

    public static CseCracCreationParameters cse(CracCreationParameters parameters) {
        return extension(parameters, CseCracCreationParameters.class, CseCracCreationParameters::new);
    }

    public static CimCracCreationParameters cim(CracCreationParameters parameters) {
        return extension(parameters, CimCracCreationParameters.class, CimCracCreationParameters::new);
    }

    public static NcCracCreationParameters nc(CracCreationParameters parameters) {
        return extension(parameters, NcCracCreationParameters.class, NcCracCreationParameters::new);
    }

    public static SweNcCracCreationParameters sweNc(CracCreationParameters parameters) {
        return extension(parameters, SweNcCracCreationParameters.class, SweNcCracCreationParameters::new);
    }

    public static FbConstraintCracCreationParameters flowBasedConstraint(CracCreationParameters parameters) {
        return extension(parameters, FbConstraintCracCreationParameters.class, FbConstraintCracCreationParameters::new);
    }

    /**
     * The extension of {@code type} if {@code parameters} have it, else a new one with OpenRAO's defaults, not added:
     * reading doesn't add extensions.
     */
    public static <E extends Extension<CracCreationParameters>> E read(CracCreationParameters parameters, Class<E> type, Supplier<E> creator) {
        E extension = parameters.getExtension(type);
        return extension == null ? creator.get() : extension;
    }

    // ----- CSE: busbar changes -----

    /**
     * Rows: remedial action id, switch to open, switch to close; a row per switch pair.
     */
    public static List<List<String>> busBarChangeSwitchesRows(CseCracCreationParameters parameters) {
        return parameters.getBusBarChangeSwitchesSet().stream()
                .sorted(Comparator.comparing(BusBarChangeSwitches::getRemedialActionId))
                .flatMap(switches -> switches.getSwitchPairs().stream()
                        .sorted(Comparator.comparing(SwitchPairId::getSwitchToOpenId).thenComparing(SwitchPairId::getSwitchToCloseId))
                        .map(pair -> List.of(switches.getRemedialActionId(), pair.getSwitchToOpenId(), pair.getSwitchToCloseId())))
                .toList();
    }

    public static void setBusBarChangeSwitches(CseCracCreationParameters parameters, List<List<String>> rows) {
        Map<String, Set<SwitchPairId>> pairs = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            pairs.computeIfAbsent(required(row, 0, i), _ -> new LinkedHashSet<>()).add(new SwitchPairId(required(row, 1, i), required(row, 2, i)));
        }
        parameters.setBusBarChangeSwitchesSet(pairs.entrySet().stream()
                .map(entry -> new BusBarChangeSwitches(entry.getKey(), entry.getValue()))
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    // ----- CIM -----

    /**
     * Rows: a time series mRID.
     */
    public static List<List<String>> timeseriesMridsRows(CimCracCreationParameters parameters) {
        return parameters.getTimeseriesMrids().stream().sorted().map(List::of).toList();
    }

    public static void setTimeseriesMrids(CimCracCreationParameters parameters, List<List<String>> rows) {
        Set<String> mrids = new LinkedHashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            mrids.add(required(rows.get(i), 0, i));
        }
        parameters.setTimeseriesMrids(mrids);
    }

    /**
     * Rows: range action id, speed.
     */
    public static List<List<String>> rangeActionSpeedsRows(CimCracCreationParameters parameters) {
        return parameters.getRangeActionSpeedSet().stream()
                .sorted(Comparator.comparing(RangeActionSpeed::getRangeActionId))
                .map(speed -> List.of(speed.getRangeActionId(), text(speed.getSpeed())))
                .toList();
    }

    public static void setRangeActionSpeeds(CimCracCreationParameters parameters, List<List<String>> rows) {
        Set<RangeActionSpeed> speeds = new LinkedHashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            Integer speed = integer(row, 1, i);
            if (speed == null) {
                throw invalid(i, Messages.get("cracCreation.row.required", 2));
            }
            speeds.add(new RangeActionSpeed(required(row, 0, i), speed));
        }
        try {
            parameters.setRemedialActionSpeed(speeds);
        } catch (OpenRaoException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Rows: a network element whose voltage is monitored.
     */
    public static List<List<String>> voltageMonitoredElementsRows(CimCracCreationParameters parameters) {
        VoltageCnecsCreationParameters voltage = parameters.getVoltageCnecsCreationParameters();
        return voltage == null ? List.of() : voltage.getMonitoredNetworkElements().stream().sorted().map(List::of).toList();
    }

    /**
     * Rows: instant, the names of the contingencies after which the voltage is monitored at it, separated by commas
     * (none for the preventive instant).
     */
    public static List<List<String>> voltageMonitoredStatesRows(CimCracCreationParameters parameters) {
        VoltageCnecsCreationParameters voltage = parameters.getVoltageCnecsCreationParameters();
        if (voltage == null) {
            return List.of();
        }
        return new TreeMap<>(voltage.getMonitoredStatesAndThresholds()).entrySet().stream()
                .map(entry -> List.of(entry.getKey(), entry.getValue().getContingencyNames() == null ? ""
                        : entry.getValue().getContingencyNames().stream().sorted().collect(Collectors.joining(", "))))
                .toList();
    }

    /**
     * Rows: instant, nominal voltage, min, max; in kV, voltage thresholds' only unit.
     */
    public static List<List<String>> voltageThresholdsRows(CimCracCreationParameters parameters) {
        VoltageCnecsCreationParameters voltage = parameters.getVoltageCnecsCreationParameters();
        if (voltage == null) {
            return List.of();
        }
        List<List<String>> rows = new ArrayList<>();
        new TreeMap<>(voltage.getMonitoredStatesAndThresholds()).forEach((instant, states) -> new TreeMap<>(states.getThresholdPerNominalV())
                .forEach((nominalV, threshold) -> rows.add(List.of(instant, text(nominalV), text(threshold.getMin()), text(threshold.getMax())))));
        return rows;
    }

    /**
     * Sets the voltage CNECs of the three tables at once: none when all are empty; otherwise each instant with
     * thresholds needs a monitored state row, and there must be monitored elements.
     */
    public static void setVoltageCnecs(CimCracCreationParameters parameters, List<List<String>> elementRows, List<List<String>> stateRows,
                                       List<List<String>> thresholdRows) {
        if (elementRows.isEmpty() && stateRows.isEmpty() && thresholdRows.isEmpty()) {
            parameters.setVoltageCnecsCreationParameters(null);
            return;
        }
        Set<String> elements = new LinkedHashSet<>();
        for (int i = 0; i < elementRows.size(); i++) {
            elements.add(required(elementRows.get(i), 0, i));
        }
        Map<String, Set<String>> contingenciesPerInstant = new LinkedHashMap<>();
        for (int i = 0; i < stateRows.size(); i++) {
            List<String> row = stateRows.get(i);
            Set<String> contingencies = Arrays.stream(cell(row, 1).split(",")).map(String::trim).filter(name -> !name.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            contingenciesPerInstant.put(required(row, 0, i), contingencies);
        }
        Map<String, Map<Double, VoltageThreshold>> thresholds = new LinkedHashMap<>();
        for (int i = 0; i < thresholdRows.size(); i++) {
            List<String> row = thresholdRows.get(i);
            String instant = required(row, 0, i);
            if (!contingenciesPerInstant.containsKey(instant)) {
                throw invalid(i, Messages.get("cracCreation.voltage.unknownInstant", instant));
            }
            Double nominalV = number(row, 1, i);
            if (nominalV == null) {
                throw invalid(i, Messages.get("cracCreation.row.required", 2));
            }
            thresholds.computeIfAbsent(instant, _ -> new LinkedHashMap<>())
                    .put(nominalV, new VoltageThreshold(Unit.KILOVOLT, number(row, 2, i), number(row, 3, i)));
        }
        Map<String, VoltageMonitoredContingenciesAndThresholds> states = new LinkedHashMap<>();
        try {
            contingenciesPerInstant.forEach((instant, contingencies) -> states.put(instant,
                    new VoltageMonitoredContingenciesAndThresholds(contingencies, thresholds.getOrDefault(instant, Map.of()))));
            parameters.setVoltageCnecsCreationParameters(new VoltageCnecsCreationParameters(states, elements));
        } catch (OpenRaoException e) {
            throw new IllegalArgumentException(Messages.get("cracCreation.voltage.incomplete"), e);
        }
    }

    // ----- NC -----

    /**
     * Rows: curative instant, application time (s).
     */
    public static List<List<String>> curativeInstantsRows(NcCracCreationParameters parameters) {
        return parameters.getCurativeInstants().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> List.of(entry.getKey(), text(entry.getValue())))
                .toList();
    }

    public static void setCurativeInstants(NcCracCreationParameters parameters, List<List<String>> rows) {
        Map<String, Integer> instants = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            Integer time = integer(row, 1, i);
            if (time == null) {
                throw invalid(i, Messages.get("cracCreation.row.required", 2));
            }
            instants.put(required(row, 0, i), time);
        }
        parameters.setCurativeInstants(instants);
    }

    /**
     * Rows: a TSO.
     */
    public static List<List<String>> tsosRows(Set<String> tsos) {
        return tsos.stream().sorted().map(List::of).toList();
    }

    public static Set<String> tsos(List<List<String>> rows) {
        Set<String> tsos = new LinkedHashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            tsos.add(required(rows.get(i), 0, i));
        }
        return tsos;
    }

    // ----- flow-based constraint document: internal HVDCs -----

    /**
     * Rows: HVDC EIC, HVDC code, pole id, converter node, converter station; a row per converter.
     */
    public static List<List<String>> hvdcConvertersRows(FbConstraintCracCreationParameters parameters) {
        List<List<String>> rows = new ArrayList<>();
        parameters.getInternalHvdcs().forEach(hvdc -> hvdc.poles().forEach(pole -> pole.converters()
                .forEach(converter -> rows.add(List.of(hvdc.eic(), hvdc.code(), pole.id(), converter.node(), converter.station())))));
        return rows;
    }

    /**
     * Rows: HVDC EIC, HVDC code, pole id, line id, from, to; a row per line.
     */
    public static List<List<String>> hvdcLinesRows(FbConstraintCracCreationParameters parameters) {
        List<List<String>> rows = new ArrayList<>();
        parameters.getInternalHvdcs().forEach(hvdc -> hvdc.poles().forEach(pole -> pole.lines()
                .forEach(line -> rows.add(List.of(hvdc.eic(), hvdc.code(), pole.id(), line.id(), line.from(), line.to())))));
        return rows;
    }

    private record PoleKey(String eic, String code, String pole) {
    }

    /**
     * Sets the internal HVDCs of both tables at once, grouped by HVDC (EIC and code) and pole, in the order they appear.
     */
    public static void setInternalHvdcs(FbConstraintCracCreationParameters parameters, List<List<String>> converterRows, List<List<String>> lineRows) {
        Map<PoleKey, List<HvdcConverter>> converters = new LinkedHashMap<>();
        Map<PoleKey, List<HvdcLine>> lines = new LinkedHashMap<>();
        for (int i = 0; i < converterRows.size(); i++) {
            List<String> row = converterRows.get(i);
            converters.computeIfAbsent(new PoleKey(required(row, 0, i), required(row, 1, i), required(row, 2, i)), _ -> new ArrayList<>())
                    .add(new HvdcConverter(required(row, 3, i), required(row, 4, i)));
        }
        for (int i = 0; i < lineRows.size(); i++) {
            List<String> row = lineRows.get(i);
            PoleKey key = new PoleKey(required(row, 0, i), required(row, 1, i), required(row, 2, i));
            converters.putIfAbsent(key, new ArrayList<>());
            lines.computeIfAbsent(key, _ -> new ArrayList<>()).add(new HvdcLine(required(row, 3, i), required(row, 4, i), required(row, 5, i)));
        }
        Map<List<String>, List<HvdcPole>> poles = new LinkedHashMap<>();
        converters.forEach((key, poleConverters) -> poles.computeIfAbsent(List.of(key.eic(), key.code()), _ -> new ArrayList<>())
                .add(new HvdcPole(key.pole(), poleConverters, lines.getOrDefault(key, List.of()))));
        parameters.setInternalHvdcs(poles.entrySet().stream()
                .map(entry -> new InternalHvdc(entry.getKey().get(0), entry.getKey().get(1), entry.getValue()))
                .toList());
    }

    // ----- cells -----

    private static String text(Object value) {
        return value == null ? "" : value instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString(d.longValue()) : value.toString();
    }

    private static String cell(List<String> row, int column) {
        return column < row.size() && row.get(column) != null ? row.get(column).trim() : "";
    }

    private static String required(List<String> row, int column, int rowIndex) {
        String value = cell(row, column);
        if (value.isEmpty()) {
            throw invalid(rowIndex, Messages.get("cracCreation.row.required", column + 1));
        }
        return value;
    }

    private static Integer integer(List<String> row, int column, int rowIndex) {
        String value = cell(row, column);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException _) {
            throw invalid(rowIndex, Messages.get("cracCreation.row.integer", column + 1, value));
        }
    }

    private static Double number(List<String> row, int column, int rowIndex) {
        String value = cell(row, column);
        if (value.isEmpty()) {
            return null;
        }
        try {
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number)) {
                throw new NumberFormatException(value);
            }
            return number;
        } catch (NumberFormatException _) {
            throw invalid(rowIndex, Messages.get("cracCreation.row.number", column + 1, value));
        }
    }

    private static IllegalArgumentException invalid(int rowIndex, String reason) {
        return new IllegalArgumentException(Messages.get("cracCreation.row.invalid", rowIndex + 1, reason));
    }

    private static <E extends Extension<CracCreationParameters>> E extension(CracCreationParameters parameters, Class<E> type, Supplier<E> creator) {
        E extension = parameters.getExtension(type);
        if (extension == null) {
            extension = Objects.requireNonNull(creator.get());
            parameters.addExtension(type, extension);
        }
        return extension;
    }
}
