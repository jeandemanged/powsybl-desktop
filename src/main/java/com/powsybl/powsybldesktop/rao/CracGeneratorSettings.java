/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Country;
import com.powsybl.openrao.data.crac.io.network.parameters.CriticalElements;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings of the CRAC generator (OpenRAO's CRAC creation from the network, see {@link CracGenerator}), as data the
 * application edits and saves: OpenRAO's {@code NetworkCracCreationParameters} hold lambdas (branch filters, cost and
 * range providers) and have no JSON serializer. Countries empty means all of them, except for counter-trading; a blank
 * voltage bound is none. The tables are text rows, as the CRAC Generator tab edits them, parsed by
 * {@link CracGenerator}. Mutable, edited in place like the other parameters.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class CracGeneratorSettings {

    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // instants, besides "preventive" and "outage"
    private List<String> autoInstants = new ArrayList<>();
    private List<String> curativeInstants = new ArrayList<>(List.of("curative"));
    private List<String> rangeActionGroups = new ArrayList<>();

    // contingencies: one per branch
    private List<Country> contingencyCountries = new ArrayList<>();
    private Double contingencyMinV;
    private Double contingencyMaxV;
    private List<String> excludedContingencyBranches = new ArrayList<>();

    // CNECs: a flow CNEC per branch and state
    private List<Country> cnecCountries = new ArrayList<>();
    private Double optimizedMinV;
    private Double optimizedMaxV;
    private boolean monitoredElements;
    private Double monitoredMinV;
    private Double monitoredMaxV;
    private CriticalElements.ThresholdDefinition thresholdDefinition = CriticalElements.ThresholdDefinition.FROM_OPERATIONAL_LIMITS;
    // instant, multiplier
    private List<List<String>> limitMultipliers = new ArrayList<>();
    // instant, nominal voltage, multiplier
    private List<List<String>> limitMultipliersPerNominalV = new ArrayList<>();
    // instant, duration (s, blank for the permanent limit)
    private List<List<String>> limitDurations = new ArrayList<>(List.of(List.of("outage", "60"), List.of("curative", "")));
    // instant, nominal voltage, duration
    private List<List<String>> limitDurationsPerNominalV = new ArrayList<>();

    // PST range actions
    private boolean pstRangeActions = true;
    private List<Country> pstCountries = new ArrayList<>();
    private List<String> excludedPsts = new ArrayList<>();
    // instant, min tap, max tap, range type
    private List<List<String>> pstTapRanges = new ArrayList<>();

    // redispatching: generators, not on by default unlike OpenRAO's, which creates one per generator and instant
    private boolean redispatching;
    private List<Country> redispatchingCountries = new ArrayList<>();
    private List<String> excludedRedispatchingGenerators = new ArrayList<>();
    // instant, min, max, activation cost, up variation cost, down variation cost
    private List<List<String>> redispatchingRangesAndCosts = new ArrayList<>();
    // combination id, injections separated by " + "
    private List<List<String>> injectionCombinations = new ArrayList<>();

    // country, instant, min, max, activation cost, up variation cost, down variation cost
    private List<List<String>> countertrading = new ArrayList<>();
    // instant, min, max, activation cost, up variation cost, down variation cost
    private List<List<String>> balancing = new ArrayList<>();

    public static JsonNode toJson(CracGeneratorSettings settings) {
        return MAPPER.valueToTree(settings);
    }

    /**
     * @throws IllegalArgumentException when {@code node} is not settings
     */
    public static CracGeneratorSettings fromJson(JsonNode node) {
        try {
            return MAPPER.treeToValue(node, CracGeneratorSettings.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    public CracGeneratorSettings copy() {
        try {
            return MAPPER.treeToValue(toJson(this), CracGeneratorSettings.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<String> getAutoInstants() {
        return autoInstants;
    }

    public void setAutoInstants(List<String> autoInstants) {
        this.autoInstants = new ArrayList<>(autoInstants);
    }

    public List<String> getCurativeInstants() {
        return curativeInstants;
    }

    public void setCurativeInstants(List<String> curativeInstants) {
        this.curativeInstants = new ArrayList<>(curativeInstants);
    }

    public List<String> getRangeActionGroups() {
        return rangeActionGroups;
    }

    public void setRangeActionGroups(List<String> rangeActionGroups) {
        this.rangeActionGroups = new ArrayList<>(rangeActionGroups);
    }

    public List<Country> getContingencyCountries() {
        return contingencyCountries;
    }

    public void setContingencyCountries(List<Country> contingencyCountries) {
        this.contingencyCountries = new ArrayList<>(contingencyCountries);
    }

    public Double getContingencyMinV() {
        return contingencyMinV;
    }

    public void setContingencyMinV(Double contingencyMinV) {
        this.contingencyMinV = contingencyMinV;
    }

    public Double getContingencyMaxV() {
        return contingencyMaxV;
    }

    public void setContingencyMaxV(Double contingencyMaxV) {
        this.contingencyMaxV = contingencyMaxV;
    }

    public List<String> getExcludedContingencyBranches() {
        return excludedContingencyBranches;
    }

    public void setExcludedContingencyBranches(List<String> excludedContingencyBranches) {
        this.excludedContingencyBranches = new ArrayList<>(excludedContingencyBranches);
    }

    public List<Country> getCnecCountries() {
        return cnecCountries;
    }

    public void setCnecCountries(List<Country> cnecCountries) {
        this.cnecCountries = new ArrayList<>(cnecCountries);
    }

    public Double getOptimizedMinV() {
        return optimizedMinV;
    }

    public void setOptimizedMinV(Double optimizedMinV) {
        this.optimizedMinV = optimizedMinV;
    }

    public Double getOptimizedMaxV() {
        return optimizedMaxV;
    }

    public void setOptimizedMaxV(Double optimizedMaxV) {
        this.optimizedMaxV = optimizedMaxV;
    }

    public boolean isMonitoredElements() {
        return monitoredElements;
    }

    public void setMonitoredElements(boolean monitoredElements) {
        this.monitoredElements = monitoredElements;
    }

    public Double getMonitoredMinV() {
        return monitoredMinV;
    }

    public void setMonitoredMinV(Double monitoredMinV) {
        this.monitoredMinV = monitoredMinV;
    }

    public Double getMonitoredMaxV() {
        return monitoredMaxV;
    }

    public void setMonitoredMaxV(Double monitoredMaxV) {
        this.monitoredMaxV = monitoredMaxV;
    }

    public CriticalElements.ThresholdDefinition getThresholdDefinition() {
        return thresholdDefinition;
    }

    public void setThresholdDefinition(CriticalElements.ThresholdDefinition thresholdDefinition) {
        this.thresholdDefinition = thresholdDefinition;
    }

    public List<List<String>> getLimitMultipliers() {
        return limitMultipliers;
    }

    public void setLimitMultipliers(List<List<String>> limitMultipliers) {
        this.limitMultipliers = rows(limitMultipliers);
    }

    public List<List<String>> getLimitMultipliersPerNominalV() {
        return limitMultipliersPerNominalV;
    }

    public void setLimitMultipliersPerNominalV(List<List<String>> limitMultipliersPerNominalV) {
        this.limitMultipliersPerNominalV = rows(limitMultipliersPerNominalV);
    }

    public List<List<String>> getLimitDurations() {
        return limitDurations;
    }

    public void setLimitDurations(List<List<String>> limitDurations) {
        this.limitDurations = rows(limitDurations);
    }

    public List<List<String>> getLimitDurationsPerNominalV() {
        return limitDurationsPerNominalV;
    }

    public void setLimitDurationsPerNominalV(List<List<String>> limitDurationsPerNominalV) {
        this.limitDurationsPerNominalV = rows(limitDurationsPerNominalV);
    }

    public boolean isPstRangeActions() {
        return pstRangeActions;
    }

    public void setPstRangeActions(boolean pstRangeActions) {
        this.pstRangeActions = pstRangeActions;
    }

    public List<Country> getPstCountries() {
        return pstCountries;
    }

    public void setPstCountries(List<Country> pstCountries) {
        this.pstCountries = new ArrayList<>(pstCountries);
    }

    public List<String> getExcludedPsts() {
        return excludedPsts;
    }

    public void setExcludedPsts(List<String> excludedPsts) {
        this.excludedPsts = new ArrayList<>(excludedPsts);
    }

    public List<List<String>> getPstTapRanges() {
        return pstTapRanges;
    }

    public void setPstTapRanges(List<List<String>> pstTapRanges) {
        this.pstTapRanges = rows(pstTapRanges);
    }

    public boolean isRedispatching() {
        return redispatching;
    }

    public void setRedispatching(boolean redispatching) {
        this.redispatching = redispatching;
    }

    public List<Country> getRedispatchingCountries() {
        return redispatchingCountries;
    }

    public void setRedispatchingCountries(List<Country> redispatchingCountries) {
        this.redispatchingCountries = new ArrayList<>(redispatchingCountries);
    }

    public List<String> getExcludedRedispatchingGenerators() {
        return excludedRedispatchingGenerators;
    }

    public void setExcludedRedispatchingGenerators(List<String> excludedRedispatchingGenerators) {
        this.excludedRedispatchingGenerators = new ArrayList<>(excludedRedispatchingGenerators);
    }

    public List<List<String>> getRedispatchingRangesAndCosts() {
        return redispatchingRangesAndCosts;
    }

    public void setRedispatchingRangesAndCosts(List<List<String>> redispatchingRangesAndCosts) {
        this.redispatchingRangesAndCosts = rows(redispatchingRangesAndCosts);
    }

    public List<List<String>> getInjectionCombinations() {
        return injectionCombinations;
    }

    public void setInjectionCombinations(List<List<String>> injectionCombinations) {
        this.injectionCombinations = rows(injectionCombinations);
    }

    public List<List<String>> getCountertrading() {
        return countertrading;
    }

    public void setCountertrading(List<List<String>> countertrading) {
        this.countertrading = rows(countertrading);
    }

    public List<List<String>> getBalancing() {
        return balancing;
    }

    public void setBalancing(List<List<String>> balancing) {
        this.balancing = rows(balancing);
    }

    private static List<List<String>> rows(List<List<String>> rows) {
        return new ArrayList<>(rows.stream().<List<String>>map(ArrayList::new).toList());
    }
}
