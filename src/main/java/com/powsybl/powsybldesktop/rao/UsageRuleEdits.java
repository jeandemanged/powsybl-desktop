/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.RemedialActionAdder;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.RangeAction;
import com.powsybl.openrao.data.crac.api.usagerule.OnConstraint;
import com.powsybl.openrao.data.crac.api.usagerule.OnContingencyState;
import com.powsybl.openrao.data.crac.api.usagerule.OnFlowConstraintInCountry;
import com.powsybl.openrao.data.crac.api.usagerule.OnFlowConstraintInCountryAdder;
import com.powsybl.openrao.data.crac.api.usagerule.OnInstant;
import com.powsybl.openrao.data.crac.api.usagerule.UsageRule;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The edits of a remedial action's usage rules, which tell when the RAO may use it: at an instant, after a contingency,
 * on a CNEC's constraint, on a flow constraint in a country. OpenRAO can't change the rules of a remedial action: a
 * change replaces the action by a new one, as {@link NetworkActionEdits} and {@link RangeActionEdits} do for their
 * other changes, so that OpenRAO keeps the CRAC's states consistent; a counter-trade, which OpenRAO 7.5.1 can't remove,
 * keeps its rules. The rules are checked beforehand as OpenRAO's adders check them, with messages of their own.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class UsageRuleEdits {

    private UsageRuleEdits() {
    }

    /**
     * The kinds of usage rules OpenRAO's adders make.
     */
    public enum Kind {
        ON_INSTANT("onInstant"),
        ON_CONTINGENCY_STATE("onState"),
        ON_CONSTRAINT("onConstraint"),
        ON_FLOW_CONSTRAINT_IN_COUNTRY("onCountry");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        public String label() {
            return Messages.get("usageRuleEdits.kind." + code);
        }
    }

    /**
     * A usage rule: its kind, its instant, and as its kind needs the contingency (required after a contingency,
     * optional on a flow constraint in a country), the CNEC or the country.
     */
    public record UsageRuleSpec(Kind kind, String instantId, String contingencyId, String cnecId, Country country) {

        public UsageRuleSpec {
            Objects.requireNonNull(kind);
        }

        public static UsageRuleSpec onInstant(String instantId) {
            return new UsageRuleSpec(Kind.ON_INSTANT, instantId, null, null, null);
        }

        public static UsageRuleSpec onState(String contingencyId, String instantId) {
            return new UsageRuleSpec(Kind.ON_CONTINGENCY_STATE, instantId, contingencyId, null, null);
        }

        public static UsageRuleSpec onConstraint(String cnecId, String instantId) {
            return new UsageRuleSpec(Kind.ON_CONSTRAINT, instantId, null, cnecId, null);
        }

        /**
         * @param contingencyId the contingency after which the rule holds, null for any state of the instant
         */
        public static UsageRuleSpec onCountry(Country country, String contingencyId, String instantId) {
            return new UsageRuleSpec(Kind.ON_FLOW_CONSTRAINT_IN_COUNTRY, instantId, contingencyId, null, country);
        }

        public static UsageRuleSpec of(UsageRule rule) {
            String instant = rule.getInstant().getId();
            return switch (rule) {
                case OnContingencyState onState -> onState(onState.getContingency().getId(), instant);
                case OnConstraint<?> onConstraint -> onConstraint(onConstraint.getCnec().getId(), instant);
                case OnFlowConstraintInCountry onCountry -> onCountry(onCountry.getCountry(), onCountry.getContingency().map(Contingency::getId).orElse(null), instant);
                case OnInstant _ -> onInstant(instant);
                default -> throw new IllegalArgumentException(rule.getClass().getSimpleName());
            };
        }
    }

    private static final Comparator<String> NULLS_FIRST = Comparator.nullsFirst(Comparator.naturalOrder());

    private static final Comparator<UsageRuleSpec> ORDER = Comparator.comparing(UsageRuleSpec::kind)
            .thenComparing(UsageRuleSpec::instantId, NULLS_FIRST)
            .thenComparing(UsageRuleSpec::contingencyId, NULLS_FIRST)
            .thenComparing(UsageRuleSpec::cnecId, NULLS_FIRST)
            .thenComparing(rule -> rule.country() == null ? null : rule.country().name(), NULLS_FIRST);

    /**
     * The usage rules of {@code action}, in a stable order: by kind, instant, contingency, CNEC and country.
     */
    public static List<UsageRuleSpec> of(RemedialAction<?> action) {
        return action.getUsageRules().stream().map(UsageRuleSpec::of).sorted(ORDER).toList();
    }

    /**
     * The instants a rule of {@code kind} can be at: any but the outage one, and not the preventive one after a
     * contingency.
     */
    public static List<Instant> instants(Crac crac, Kind kind) {
        return crac.getSortedInstants().stream()
                .filter(instant -> !instant.isOutage() && !(kind == Kind.ON_CONTINGENCY_STATE && instant.isPreventive()))
                .toList();
    }

    /**
     * Checks {@code rule} against {@code crac}, as OpenRAO's adders do.
     */
    public static void check(Crac crac, UsageRuleSpec rule) {
        if (rule.instantId() == null || rule.instantId().isBlank()) {
            throw new IllegalArgumentException(Messages.get("usageRuleEdits.instantRequired"));
        }
        Instant instant = crac.getSortedInstants().stream().filter(i -> i.getId().equals(rule.instantId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(Messages.get("usageRuleEdits.unknownInstant", rule.instantId())));
        if (instant.isOutage()) {
            throw new IllegalArgumentException(Messages.get("usageRuleEdits.outage", instant.getId()));
        }
        switch (rule.kind()) {
            case ON_INSTANT -> {
                // at the instant alone
            }
            case ON_CONTINGENCY_STATE -> {
                if (instant.isPreventive()) {
                    throw new IllegalArgumentException(Messages.get("usageRuleEdits.onStatePreventive", instant.getId()));
                }
                if (rule.contingencyId() == null || rule.contingencyId().isBlank()) {
                    throw new IllegalArgumentException(Messages.get("usageRuleEdits.contingencyRequired"));
                }
                checkContingency(crac, rule.contingencyId());
            }
            case ON_CONSTRAINT -> {
                if (rule.cnecId() == null || rule.cnecId().isBlank()) {
                    throw new IllegalArgumentException(Messages.get("usageRuleEdits.cnecRequired"));
                }
                Cnec<?> cnec = crac.getCnec(rule.cnecId());
                if (cnec == null) {
                    throw new IllegalArgumentException(Messages.get("usageRuleEdits.unknownCnec", rule.cnecId()));
                }
                Instant cnecInstant = cnec.getState().getInstant();
                if (cnecInstant.comesBefore(instant)) {
                    throw new IllegalArgumentException(Messages.get("usageRuleEdits.cnecBefore", cnec.getName(), cnecInstant.getId(), instant.getId()));
                }
            }
            case ON_FLOW_CONSTRAINT_IN_COUNTRY -> {
                if (rule.country() == null) {
                    throw new IllegalArgumentException(Messages.get("usageRuleEdits.countryRequired"));
                }
                if (rule.contingencyId() != null) {
                    checkContingency(crac, rule.contingencyId());
                }
            }
        }
    }

    private static void checkContingency(Crac crac, String contingencyId) {
        if (crac.getContingency(contingencyId) == null) {
            throw new IllegalArgumentException(Messages.get("usageRuleEdits.unknownContingency", contingencyId));
        }
    }

    /**
     * Checks each of {@code rules}, and that none is there twice.
     */
    static void checkAll(Crac crac, Collection<UsageRuleSpec> rules) {
        Set<UsageRuleSpec> seen = new HashSet<>();
        for (UsageRuleSpec rule : rules) {
            check(crac, rule);
            if (!seen.add(rule)) {
                throw new IllegalArgumentException(Messages.get("usageRuleEdits.duplicate"));
            }
        }
    }

    /**
     * Adds {@code rules} to {@code adder}.
     */
    static <T extends RemedialActionAdder<T>> void addTo(T adder, Collection<UsageRuleSpec> rules) {
        for (UsageRuleSpec rule : rules) {
            switch (rule.kind()) {
                case ON_INSTANT -> adder.newOnInstantUsageRule().withInstant(rule.instantId()).add();
                case ON_CONTINGENCY_STATE -> adder.newOnContingencyStateUsageRule().withContingency(rule.contingencyId()).withInstant(rule.instantId()).add();
                case ON_CONSTRAINT -> adder.newOnConstraintUsageRule().withCnec(rule.cnecId()).withInstant(rule.instantId()).add();
                case ON_FLOW_CONSTRAINT_IN_COUNTRY -> {
                    OnFlowConstraintInCountryAdder<T> ruleAdder = adder.newOnFlowConstraintInCountryUsageRule().withCountry(rule.country()).withInstant(rule.instantId());
                    if (rule.contingencyId() != null) {
                        ruleAdder.withContingency(rule.contingencyId());
                    }
                    ruleAdder.add();
                }
            }
        }
    }

    /**
     * Replaces {@code action} by the same one with {@code rules} for usage rules, putting it back when they are refused;
     * {@code action} itself when they are its own.
     */
    public static RemedialAction<?> set(Crac crac, Network network, RemedialAction<?> action, List<UsageRuleSpec> rules) {
        if (action instanceof CounterTradeRangeAction) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.counterTradeFixed"));
        }
        checkAll(crac, rules);
        return switch (action) {
            case NetworkAction networkAction -> NetworkActionEdits.setUsageRules(crac, network, networkAction, rules);
            case RangeAction<?> rangeAction -> RangeActionEdits.setUsageRules(crac, network, rangeAction, rules);
            default -> throw new IllegalArgumentException(Messages.get("networkActionEdits.unsupported", action.getId()));
        };
    }

    /**
     * Adds {@code rule} to the usage rules of {@code action}, as {@link #set} does.
     */
    public static RemedialAction<?> add(Crac crac, Network network, RemedialAction<?> action, UsageRuleSpec rule) {
        List<UsageRuleSpec> rules = new ArrayList<>(of(action));
        if (rules.contains(rule)) {
            throw new IllegalArgumentException(Messages.get("usageRuleEdits.duplicate"));
        }
        rules.add(rule);
        return set(crac, network, action, rules);
    }

    /**
     * Removes {@code rule} from the usage rules of {@code action}, as {@link #set} does.
     */
    public static RemedialAction<?> remove(Crac crac, Network network, RemedialAction<?> action, UsageRuleSpec rule) {
        List<UsageRuleSpec> rules = new ArrayList<>(of(action));
        rules.remove(rule);
        return set(crac, network, action, rules);
    }
}
