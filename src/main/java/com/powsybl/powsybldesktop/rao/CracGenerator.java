/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.CracCreationContext;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.api.range.RangeType;
import com.powsybl.openrao.data.crac.io.network.NetworkCracCreator;
import com.powsybl.openrao.data.crac.io.network.parameters.CriticalElements;
import com.powsybl.openrao.data.crac.io.network.parameters.InjectionRangeActionCosts;
import com.powsybl.openrao.data.crac.io.network.parameters.MinAndMax;
import com.powsybl.openrao.data.crac.io.network.parameters.NetworkCracCreationParameters;
import com.powsybl.openrao.data.crac.io.network.parameters.PstRangeActions;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.powsybl.powsybldesktop.rao.TableRows.invalid;
import static com.powsybl.powsybldesktop.rao.TableRows.number;
import static com.powsybl.powsybldesktop.rao.TableRows.required;
import static com.powsybl.powsybldesktop.rao.TableRows.requiredInteger;
import static com.powsybl.powsybldesktop.rao.TableRows.requiredNumber;

/**
 * OpenRAO's CRAC creation from the network ({@link NetworkCracCreator}, OpenRAO 7.5.1) with the application's
 * {@link CracGeneratorSettings}, turned into {@link NetworkCracCreationParameters} and their lambdas on top of the
 * CRAC Creation parameters (default monitored line side, usage limits): a contingency per branch and a flow CNEC per
 * branch and state, filtered by country and nominal voltage; thresholds from the operational limits (an acceptable
 * duration being then required for every post-contingency instant) or the permanent limits, times a multiplier per
 * instant and nominal voltage; PST range actions; redispatching on generators; counter-trading and balancing where a
 * range is given. The {@code parseXxx} methods parse a table of the settings (the CRAC Generator tab checks an edit
 * with them), {@link #problems} what needs the instants too.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CracGenerator {

    static final String PREVENTIVE = "preventive";
    static final String OUTAGE = "outage";
    // OpenRAO's acceptable duration of a permanent limit
    private static final double PERMANENT = Double.MAX_VALUE;
    private static final InjectionRangeActionCosts NO_COSTS = new InjectionRangeActionCosts(0, 0, 0);

    /**
     * A range, with blank bounds, and the costs of a range action.
     */
    public record RangeAndCosts(Double min, Double max, InjectionRangeActionCosts costs) {
        MinAndMax<Double> range() {
            return new MinAndMax<>(min, max);
        }
    }

    private CracGenerator() {
    }

    /**
     * The instants of the CRAC to generate, in order.
     */
    public static List<String> instants(CracGeneratorSettings settings) {
        return Stream.of(Stream.of(PREVENTIVE, OUTAGE), settings.getAutoInstants().stream(), settings.getCurativeInstants().stream())
                .flatMap(Function.identity()).toList();
    }

    // ----- tables -----

    /**
     * Rows: a value, required; for the single-column tables (instants, excluded elements).
     */
    public static List<String> parseValues(List<List<String>> rows) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            values.add(required(rows.get(i), 0, i));
        }
        return values;
    }

    /**
     * Rows: the ids of a group's range actions, separated by " + ", at least two.
     */
    public static List<String> parseRangeActionGroups(List<List<String>> rows) {
        List<String> groups = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> ids = Arrays.stream(required(rows.get(i), 0, i).split("\\+")).map(String::trim).filter(id -> !id.isEmpty()).toList();
            if (ids.size() < 2) {
                throw invalid(i, Messages.get("cracCreation.groups.tooSmall"));
            }
            groups.add(String.join(" + ", ids));
        }
        return groups;
    }

    /**
     * Rows: instant, value (blank only when {@code blankAllowed}, read as {@code blank}).
     */
    public static Map<String, Double> parseInstantValues(List<List<String>> rows, boolean blankAllowed, double blank) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String instant = required(row, 0, i);
            Double value = blankAllowed ? number(row, 1, i) : Double.valueOf(requiredNumber(row, 1, i));
            if (values.put(instant, value == null ? blank : value) != null) {
                throw invalid(i, Messages.get("cracCreation.row.duplicate", instant));
            }
        }
        return values;
    }

    /**
     * Rows: instant, nominal voltage, value.
     */
    public static Map<String, Map<Double, Double>> parseInstantNominalVValues(List<List<String>> rows) {
        Map<String, Map<Double, Double>> values = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            values.computeIfAbsent(required(row, 0, i), _ -> new HashMap<>()).put(requiredNumber(row, 1, i), requiredNumber(row, 2, i));
        }
        return values;
    }

    /**
     * Rows: instant, min tap, max tap, range type.
     */
    public static Map<String, PstRangeActions.TapRange> parseTapRanges(List<List<String>> rows) {
        Map<String, PstRangeActions.TapRange> ranges = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String instant = required(row, 0, i);
            int min = requiredInteger(row, 1, i);
            int max = requiredInteger(row, 2, i);
            if (min > max) {
                throw invalid(i, Messages.get("cracGenerator.row.minAboveMax"));
            }
            ranges.put(instant, new PstRangeActions.TapRange(min, max, choice(RangeType.class, row, 3, i)));
        }
        return ranges;
    }

    /**
     * Rows: instant, min, max, activation cost, up variation cost, down variation cost; blank costs are 0.
     */
    public static Map<String, RangeAndCosts> parseInstantRangesAndCosts(List<List<String>> rows) {
        Map<String, RangeAndCosts> values = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String instant = required(row, 0, i);
            if (values.put(instant, rangeAndCosts(row, 1, i)) != null) {
                throw invalid(i, Messages.get("cracCreation.row.duplicate", instant));
            }
        }
        return values;
    }

    /**
     * Rows: country code, instant, min, max, activation cost, up variation cost, down variation cost.
     */
    public static Map<Country, Map<String, RangeAndCosts>> parseCountryRangesAndCosts(List<List<String>> rows) {
        Map<Country, Map<String, RangeAndCosts>> values = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            values.computeIfAbsent(choice(Country.class, row, 0, i), _ -> new LinkedHashMap<>()).put(required(row, 1, i), rangeAndCosts(row, 2, i));
        }
        return values;
    }

    /**
     * Rows: combination id, injection ids separated by " + ", an injection in one combination at most.
     */
    public static Map<String, Set<String>> parseInjectionCombinations(List<List<String>> rows) {
        Map<String, Set<String>> combinations = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            Set<String> injections = Arrays.stream(required(row, 1, i).split("\\+")).map(String::trim).filter(id -> !id.isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (String injection : injections) {
                if (!used.add(injection)) {
                    throw invalid(i, Messages.get("cracGenerator.row.injectionTwice", injection));
                }
            }
            combinations.put(required(row, 0, i), injections);
        }
        return combinations;
    }

    private static RangeAndCosts rangeAndCosts(List<String> row, int column, int rowIndex) {
        Double min = number(row, column, rowIndex);
        Double max = number(row, column + 1, rowIndex);
        if (min != null && max != null && min > max) {
            throw invalid(rowIndex, Messages.get("cracGenerator.row.minAboveMax"));
        }
        return new RangeAndCosts(min, max, new InjectionRangeActionCosts(zeroIfBlank(row, column + 2, rowIndex), zeroIfBlank(row, column + 3, rowIndex),
                zeroIfBlank(row, column + 4, rowIndex)));
    }

    private static double zeroIfBlank(List<String> row, int column, int rowIndex) {
        Double value = number(row, column, rowIndex);
        return value == null ? 0 : value;
    }

    private static <E extends Enum<E>> E choice(Class<E> type, List<String> row, int column, int rowIndex) {
        String value = required(row, column, rowIndex);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException _) {
            throw invalid(rowIndex, Messages.get("cracCreation.row.choice", column + 1, value,
                    Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "))));
        }
    }

    // ----- checks -----

    /**
     * Why the settings can't generate a CRAC: invalid tables, invalid instant names, instants of the tables unknown, and
     * the acceptable durations missing for post-contingency instants when the thresholds come from the operational
     * limits. Empty when they can.
     */
    public static List<String> problems(CracGeneratorSettings settings) {
        List<String> problems = new ArrayList<>();
        List<String> instants = instants(settings);
        Set<String> seen = new HashSet<>();
        for (String instant : instants) {
            if (instant == null || instant.isBlank() || !seen.add(instant)) {
                problems.add(Messages.get("cracGenerator.problem.instants", String.join(", ", instants)));
                break;
            }
        }
        Set<String> postContingency = new LinkedHashSet<>(instants.subList(1, instants.size()));
        checkInstants(problems, "limitMultipliers", () -> parseInstantValues(settings.getLimitMultipliers(), false, 1).keySet(), seen);
        checkInstants(problems, "limitMultipliersPerNominalV", () -> parseInstantNominalVValues(settings.getLimitMultipliersPerNominalV()).keySet(), seen);
        checkInstants(problems, "limitDurations", () -> parseInstantValues(settings.getLimitDurations(), true, PERMANENT).keySet(), postContingency);
        checkInstants(problems, "limitDurationsPerNominalV", () -> parseInstantNominalVValues(settings.getLimitDurationsPerNominalV()).keySet(), postContingency);
        checkInstants(problems, "pstTapRanges", () -> parseTapRanges(settings.getPstTapRanges()).keySet(), seen);
        checkInstants(problems, "redispatchingRangesAndCosts", () -> parseInstantRangesAndCosts(settings.getRedispatchingRangesAndCosts()).keySet(), seen);
        checkInstants(problems, "balancing", () -> parseInstantRangesAndCosts(settings.getBalancing()).keySet(), seen);
        checkInstants(problems, "countertrading", () -> parseCountryRangesAndCosts(settings.getCountertrading()).values().stream()
                .flatMap(perInstant -> perInstant.keySet().stream()).collect(Collectors.toSet()), seen);
        check(problems, "injectionCombinations", () -> parseInjectionCombinations(settings.getInjectionCombinations()).keySet());
        if (settings.getThresholdDefinition() == CriticalElements.ThresholdDefinition.FROM_OPERATIONAL_LIMITS) {
            try {
                Set<String> missing = new LinkedHashSet<>(postContingency);
                missing.removeAll(parseInstantValues(settings.getLimitDurations(), true, PERMANENT).keySet());
                if (!missing.isEmpty()) {
                    problems.add(Messages.get("cracGenerator.problem.durationsMissing", String.join(", ", missing)));
                }
            } catch (IllegalArgumentException _) {
                // told by checkInstants
            }
        }
        return problems;
    }

    private static void checkInstants(List<String> problems, String table, Supplier<Set<String>> instants, Set<String> known) {
        check(problems, table, () -> {
            Set<String> unknown = new LinkedHashSet<>(instants.get());
            unknown.removeAll(known);
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException(Messages.get("cracGenerator.problem.unknownInstants", String.join(", ", unknown)));
            }
            return unknown;
        });
    }

    private static void check(List<String> problems, String table, Supplier<?> parse) {
        try {
            parse.get();
        } catch (IllegalArgumentException e) {
            problems.add(Messages.get("cracGenerator.problem.table", Messages.get("cracGenerator.param." + table + ".label"), e.getMessage()));
        }
    }

    // ----- generation -----

    /**
     * A copy of {@code base} (the CRAC Creation parameters) with the generator's extension from {@code settings}.
     *
     * @throws IllegalArgumentException with every reason, when {@link #problems} finds some
     */
    public static CracCreationParameters parameters(CracGeneratorSettings settings, CracCreationParameters base) {
        List<String> problems = problems(settings);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", problems));
        }
        List<String> instants = instants(settings);
        List<String> postContingency = instants.subList(1, instants.size());
        NetworkCracCreationParameters network = new NetworkCracCreationParameters(
                settings.getAutoInstants().isEmpty() ? null : settings.getAutoInstants(),
                settings.getCurativeInstants().isEmpty() ? null : settings.getCurativeInstants());
        network.setRangeActionGroupsAsString(settings.getRangeActionGroups());

        network.getContingencies().setCountryFilter(countries(settings.getContingencyCountries()));
        network.getContingencies().setMinAndMaxV(settings.getContingencyMinV(), settings.getContingencyMaxV());
        Set<String> excludedBranches = Set.copyOf(settings.getExcludedContingencyBranches());
        network.getContingencies().setBranchFilter(branch -> !excludedBranches.contains(branch.getId()));

        CriticalElements cnecs = network.getCriticalElements();
        cnecs.setCountryFilter(countries(settings.getCnecCountries()));
        cnecs.setOptimizedMinMaxV(settings.getOptimizedMinV(), settings.getOptimizedMaxV());
        cnecs.setMonitoredMinMaxV(settings.isMonitoredElements() ? new MinAndMax<>(settings.getMonitoredMinV(), settings.getMonitoredMaxV()) : null);
        cnecs.setThresholdDefinition(settings.getThresholdDefinition());
        Map<String, Double> multipliers = parseInstantValues(settings.getLimitMultipliers(), false, 1);
        Map<String, Map<Double, Double>> multipliersPerNominalV = parseInstantNominalVValues(settings.getLimitMultipliersPerNominalV());
        if (!multipliers.isEmpty() || !multipliersPerNominalV.isEmpty()) {
            // OpenRAO requires every instant listed; missing ones keep the limits as they are
            cnecs.setLimitMultiplierPerInstant(complete(instants, multipliers, _ -> 1.));
            if (!multipliersPerNominalV.isEmpty()) {
                cnecs.setLimitMultiplierPerInstantPerNominalV(complete(instants, multipliersPerNominalV, _ -> new HashMap<>()));
            }
        }
        if (settings.getThresholdDefinition() == CriticalElements.ThresholdDefinition.FROM_OPERATIONAL_LIMITS) {
            cnecs.setApplicableLimitDurationPerInstant(complete(postContingency, parseInstantValues(settings.getLimitDurations(), true, PERMANENT), _ -> PERMANENT));
            Map<String, Map<Double, Double>> durationsPerNominalV = parseInstantNominalVValues(settings.getLimitDurationsPerNominalV());
            if (!durationsPerNominalV.isEmpty()) {
                cnecs.setApplicableLimitDurationPerInstantPerNominalV(complete(postContingency, durationsPerNominalV, _ -> new HashMap<>()));
            }
        }

        boolean psts = settings.isPstRangeActions();
        Set<String> excludedPsts = Set.copyOf(settings.getExcludedPsts());
        network.getPstRangeActions().setCountryFilter(countries(settings.getPstCountries()));
        network.getPstRangeActions().setPstRaPredicate((pst, _, _) -> psts && !excludedPsts.contains(pst.getId()));
        network.getPstRangeActions().setAvailableTapRangesAtInstants(new HashMap<>(parseTapRanges(settings.getPstTapRanges())));

        Set<String> excludedGenerators = Set.copyOf(settings.getExcludedRedispatchingGenerators());
        Map<String, RangeAndCosts> redispatching = parseInstantRangesAndCosts(settings.getRedispatchingRangesAndCosts());
        network.getRedispatchingRangeActions().setIncludeAllInjections(settings.isRedispatching());
        network.getRedispatchingRangeActions().setCountryFilter(countries(settings.getRedispatchingCountries()));
        network.getRedispatchingRangeActions().setRdRaPredicate((injection, _, _) ->
                injection.getType() == IdentifiableType.GENERATOR && !excludedGenerators.contains(injection.getId()));
        network.getRedispatchingRangeActions().setRaRangeProvider((_, instant) -> rangeAt(redispatching, instant, new MinAndMax<>(null, null)));
        network.getRedispatchingRangeActions().setRaCostsProvider((_, instant) -> costsAt(redispatching, instant));
        network.getRedispatchingRangeActions().setCombinationCostsProvider((_, instant) -> costsAt(redispatching, instant));
        try {
            network.getRedispatchingRangeActions().setInjectionCombinations(parseInjectionCombinations(settings.getInjectionCombinations()));
        } catch (OpenRaoException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        Map<Country, Map<String, RangeAndCosts>> countertrading = parseCountryRangesAndCosts(settings.getCountertrading());
        network.getCountertradingRangeActions().setCountryFilter(Set.copyOf(countertrading.keySet()));
        network.getCountertradingRangeActions().setRaRangeProvider((country, instant) ->
                rangeAt(countertrading.getOrDefault(country, Map.of()), instant, new MinAndMax<>(0., 0.)));
        network.getCountertradingRangeActions().setRaCostsProvider((country, instant) -> costsAt(countertrading.getOrDefault(country, Map.of()), instant));

        Map<String, RangeAndCosts> balancing = parseInstantRangesAndCosts(settings.getBalancing());
        network.getBalancingRangeAction().setRaRangeProvider(instant -> rangeAt(balancing, instant, new MinAndMax<>(0., 0.)));
        network.getBalancingRangeAction().setRaCostsProvider(instant -> costsAt(balancing, instant));

        CracCreationParameters parameters = CracImportParameters.copy(base);
        parameters.addExtension(NetworkCracCreationParameters.class, network);
        return parameters;
    }

    /**
     * Generates a CRAC from {@code network} with {@code settings} and the CRAC Creation parameters {@code base}.
     *
     * @throws IllegalArgumentException with every reason, when {@link #problems} finds some
     */
    public static CracCreationContext generate(Network network, CracGeneratorSettings settings, CracCreationParameters base) {
        return NetworkCracCreator.createCrac(network, parameters(settings, base));
    }

    private static Set<Country> countries(List<Country> countries) {
        return countries.isEmpty() ? null : Set.copyOf(countries);
    }

    private static <V> Map<String, V> complete(List<String> instants, Map<String, V> values, Function<String, V> missing) {
        Map<String, V> complete = new HashMap<>();
        instants.forEach(instant -> complete.put(instant, values.containsKey(instant) ? values.get(instant) : missing.apply(instant)));
        return complete;
    }

    private static MinAndMax<Double> rangeAt(Map<String, RangeAndCosts> values, Instant instant, MinAndMax<Double> missing) {
        RangeAndCosts value = values.get(instant.getId());
        return value == null ? missing : value.range();
    }

    private static InjectionRangeActionCosts costsAt(Map<String, RangeAndCosts> values, Instant instant) {
        RangeAndCosts value = values.get(instant.getId());
        return value == null ? NO_COSTS : value.costs();
    }
}
