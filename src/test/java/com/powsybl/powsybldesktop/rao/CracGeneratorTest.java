/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.CracCreationContext;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.api.rangeaction.InjectionRangeAction;
import com.powsybl.openrao.data.crac.io.network.parameters.CriticalElements;
import com.powsybl.powsybldesktop.utils.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CracGeneratorTest {

    private final Network network = RaoTestCases.network();
    private final CracGeneratorSettings settings = new CracGeneratorSettings();

    private Crac generate() {
        CracCreationContext context = CracGenerator.generate(network, settings, new CracCreationParameters());
        assertTrue(context.isCreationSuccessful(), String.join("\n", CracIO.report(context)));
        return context.getCrac();
    }

    // a contingency per branch, a flow CNEC per branch and state, a PST range action; no redispatching by default
    @Test
    void generatesFromTheDefaults() {
        Crac crac = generate();

        assertEquals(List.of("preventive", "outage", "curative"), crac.getSortedInstants().stream().map(instant -> instant.getId()).toList());
        assertEquals(Set.of("L1", "L2", "PS1"), crac.getContingencies().stream()
                .flatMap(contingency -> contingency.getElements().stream()).map(element -> element.getId()).collect(Collectors.toSet()));
        assertTrue(crac.getFlowCnecs().stream().anyMatch(cnec -> cnec.getNetworkElementId().equals("L1") && cnec.getState().isPreventive()));
        assertEquals(1, crac.getPstRangeActions().stream().map(pst -> pst.getNetworkElementsIds()).distinct().count());
        assertTrue(crac.getRangeActions().stream().noneMatch(InjectionRangeAction.class::isInstance));
    }

    @Test
    void appliesTheFilters() {
        settings.setExcludedContingencyBranches(List.of("PS1", "L2"));
        settings.setPstRangeActions(false);
        settings.setRedispatching(true);
        settings.setRedispatchingRangesAndCosts(List.of(List.of("preventive", "50", "150", "10", "1", "2")));

        Crac crac = generate();

        assertEquals(Set.of("L1"), crac.getContingencies().stream().flatMap(contingency -> contingency.getElements().stream()).map(element -> element.getId())
                .collect(Collectors.toSet()));
        assertTrue(crac.getPstRangeActions().isEmpty());
        InjectionRangeAction redispatching = crac.getRangeActions().stream()
                .filter(InjectionRangeAction.class::isInstance).map(InjectionRangeAction.class::cast)
                .filter(action -> action.getUsageRules().stream().anyMatch(rule -> rule.getInstant().isPreventive()))
                .findFirst().orElseThrow();
        assertTrue(redispatching.getNetworkElementsIds().contains("G1"));
        assertEquals(10., redispatching.getActivationCost().orElseThrow());
    }

    @Test
    void thresholdsFromThePermanentLimitsTimesAMultiplier() {
        settings.setThresholdDefinition(CriticalElements.ThresholdDefinition.PERM_LIMIT_MULTIPLIER);
        settings.setLimitMultipliers(List.of(List.of("curative", "1.2")));

        Crac crac = generate();

        FlowCnec preventive = crac.getFlowCnecs().stream()
                .filter(cnec -> cnec.getNetworkElementId().equals("L1") && cnec.getState().isPreventive()).findFirst().orElseThrow();
        FlowCnec curative = crac.getFlowCnecs().stream()
                .filter(cnec -> cnec.getNetworkElementId().equals("L1") && cnec.getState().getInstant().isCurative()).findFirst().orElseThrow();
        assertEquals(1000., preventive.getUpperBound(TwoSides.ONE, Unit.AMPERE).orElseThrow(), 1e-6);
        assertEquals(1200., curative.getUpperBound(TwoSides.ONE, Unit.AMPERE).orElseThrow(), 1e-6);
    }

    @Test
    void tellsEveryProblem() {
        settings.setCurativeInstants(List.of("curative", "outage"));
        settings.setLimitDurations(List.of(List.of("outage", "60")));
        settings.setPstTapRanges(List.of(List.of("tomorrow", "-1", "1", "ABSOLUTE"), List.of("preventive", "1", "-1", "ABSOLUTE")));
        settings.setCountertrading(List.of(List.of("XX", "preventive", "0", "100")));

        List<String> problems = CracGenerator.problems(settings);

        assertEquals(4, problems.size(), String.join("\n", problems));
        assertTrue(problems.getFirst().startsWith(Messages.get("cracGenerator.problem.instants", "preventive, outage, curative, outage")), problems.getFirst());
        assertTrue(problems.stream().anyMatch(problem -> problem.contains(Messages.get("cracGenerator.row.minAboveMax"))), String.join("\n", problems));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("XX")), String.join("\n", problems));
        assertTrue(problems.contains(Messages.get("cracGenerator.problem.durationsMissing", "curative")), String.join("\n", problems));
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> CracGenerator.parameters(settings, new CracCreationParameters()));
        assertEquals(String.join("\n", problems), refusal.getMessage());
    }

    // the tables' instants checked against all the instants, not only those before the duplicate
    @Test
    void aDuplicateInstantIsTheOnlyProblem() {
        settings.setCurativeInstants(List.of("curative", "curative"));
        settings.setThresholdDefinition(CriticalElements.ThresholdDefinition.PERM_LIMIT_MULTIPLIER);
        settings.setLimitMultipliers(List.of(List.of("curative", "1.2")));

        List<String> problems = CracGenerator.problems(settings);

        assertEquals(1, problems.size(), String.join("\n", problems));
        assertTrue(problems.getFirst().startsWith(Messages.get("cracGenerator.problem.instants", "preventive, outage, curative, curative")), problems.getFirst());
    }

    // a second row for the same key would silently replace the first
    @Test
    void duplicateRowsAreRefused() {
        IllegalArgumentException multipliers = assertThrows(IllegalArgumentException.class, () -> CracGenerator.parseInstantNominalVValues(
                List.of(List.of("curative", "400", "1.2"), List.of("curative", "400", "0.9"))));
        assertTrue(multipliers.getMessage().contains(Messages.get("cracCreation.row.duplicate", "curative, 400")), multipliers.getMessage());
        IllegalArgumentException countertrading = assertThrows(IllegalArgumentException.class, () -> CracGenerator.parseCountryRangesAndCosts(
                List.of(List.of("FR", "preventive", "0", "100"), List.of("FR", "preventive", "0", "50"))));
        assertTrue(countertrading.getMessage().contains(Messages.get("cracCreation.row.duplicate", "FR, preventive")), countertrading.getMessage());
        IllegalArgumentException taps = assertThrows(IllegalArgumentException.class, () -> CracGenerator.parseTapRanges(
                List.of(List.of("preventive", "-1", "1", "ABSOLUTE"), List.of("preventive", "-2", "2", "ABSOLUTE"))));
        assertTrue(taps.getMessage().contains(Messages.get("cracCreation.row.duplicate", "preventive")), taps.getMessage());
        IllegalArgumentException combinations = assertThrows(IllegalArgumentException.class, () -> CracGenerator.parseInjectionCombinations(
                List.of(List.of("c", "G1"), List.of("c", "G2"))));
        assertTrue(combinations.getMessage().contains(Messages.get("cracCreation.row.duplicate", "c")), combinations.getMessage());
    }

    // only from the operational limits
    @Test
    void durationsAreRequiredFromTheOperationalLimitsOnly() {
        settings.setLimitDurations(List.of());
        assertEquals(List.of(Messages.get("cracGenerator.problem.durationsMissing", "outage, curative")), CracGenerator.problems(settings));

        settings.setThresholdDefinition(CriticalElements.ThresholdDefinition.PERM_LIMIT_MULTIPLIER);
        assertEquals(List.of(), CracGenerator.problems(settings));
    }

    @Test
    void settingsRoundTripInJson() {
        settings.setContingencyCountries(List.of(Country.FR));
        settings.setOptimizedMinV(225.);
        settings.setCountertrading(List.of(List.of("FR", "preventive", "-100", "100", "", "1", "1")));

        CracGeneratorSettings read = CracGeneratorSettings.fromJson(CracGeneratorSettings.toJson(settings));

        assertEquals(List.of(Country.FR), read.getContingencyCountries());
        assertEquals(225., read.getOptimizedMinV());
        assertEquals(settings.getCountertrading(), read.getCountertrading());
        assertEquals(settings.getLimitDurations(), read.getLimitDurations());
        assertFalse(read.isRedispatching());
    }
}
