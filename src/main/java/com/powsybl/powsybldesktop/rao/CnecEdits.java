/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OperationalLimitsGroup;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnecAdder;
import com.powsybl.openrao.data.crac.api.usagerule.OnConstraint;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Edits of the CNECs of a CRAC within what OpenRAO's API allows (7.5.1). A flow CNEC is immutable but for its role
 * (optimized, monitored): any other change replaces it by a new one, described by a {@link FlowCnecSpec}, the old one
 * put back when OpenRAO refuses the new one. OpenRAO removes a CNEC even when an on-constraint usage rule refers to it,
 * leaving the rule refer to a CNEC no longer in the CRAC: a referenced CNEC is neither replaced nor removed here. A
 * refused edit throws an {@link IllegalArgumentException} telling why. Toolkit-free for testing.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CnecEdits {

    private CnecEdits() {
    }

    /**
     * A state a CNEC can be monitored at: the preventive one ({@code contingencyId} null), or a contingency and a
     * post-contingency instant.
     */
    public record StateRef(String contingencyId, String instantId) {

        public static StateRef of(State state) {
            return new StateRef(state.getContingency().map(Contingency::getId).orElse(null), state.getInstant().getId());
        }

        public boolean isPreventive() {
            return contingencyId == null;
        }
    }

    /**
     * A flow CNEC threshold: {@code min} or {@code max} may be null, not both.
     */
    public record ThresholdSpec(TwoSides side, Unit unit, Double min, Double max) {
    }

    /**
     * What a flow CNEC is made of. A blank name is the id; a blank operator or border is none.
     */
    public record FlowCnecSpec(String id, String name, String branchId, StateRef state, boolean optimized, boolean monitored,
                               double reliabilityMargin, String operator, String border, Double nominalVoltage1, Double nominalVoltage2,
                               Double iMax1, Double iMax2, List<ThresholdSpec> thresholds) {

        public FlowCnecSpec {
            id = id == null ? "" : id.trim();
            name = name == null || name.isBlank() ? id : name.trim();
            operator = operator == null || operator.isBlank() ? null : operator.trim();
            border = border == null || border.isBlank() ? null : border.trim();
            thresholds = List.copyOf(thresholds);
        }

        public static FlowCnecSpec of(FlowCnec cnec) {
            List<ThresholdSpec> thresholds = cnec.getThresholds().stream()
                    .map(threshold -> new ThresholdSpec(threshold.getSide(), threshold.getUnit(), threshold.min().orElse(null), threshold.max().orElse(null)))
                    .sorted(THRESHOLD_ORDER).toList();
            return new FlowCnecSpec(cnec.getId(), cnec.getName(), cnec.getNetworkElementId(), StateRef.of(cnec.getState()), cnec.isOptimized(),
                    cnec.isMonitored(), cnec.getReliabilityMargin(), cnec.getOperator(), cnec.getBorder(), cnec.getNominalVoltage(TwoSides.ONE),
                    cnec.getNominalVoltage(TwoSides.TWO), cnec.getIMax(TwoSides.ONE).orElse(null), cnec.getIMax(TwoSides.TWO).orElse(null), thresholds);
        }

        public FlowCnecSpec withRole(boolean newOptimized, boolean newMonitored) {
            return new FlowCnecSpec(id, name, branchId, state, newOptimized, newMonitored, reliabilityMargin, operator, border, nominalVoltage1,
                    nominalVoltage2, iMax1, iMax2, thresholds);
        }
    }

    private static final Comparator<ThresholdSpec> THRESHOLD_ORDER = Comparator.comparing(ThresholdSpec::side).thenComparing(ThresholdSpec::unit)
            .thenComparing(ThresholdSpec::min, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ThresholdSpec::max, Comparator.nullsFirst(Comparator.naturalOrder()));

    /**
     * The states of {@code crac} a CNEC can be monitored at: the preventive one, then each contingency (by id) at each
     * post-contingency instant (in order).
     */
    public static List<StateRef> states(Crac crac) {
        List<StateRef> states = new ArrayList<>();
        states.add(new StateRef(null, crac.getPreventiveInstant().getId()));
        List<Instant> instants = crac.getSortedInstants().stream().filter(instant -> !instant.isPreventive()).toList();
        crac.getContingencies().stream().map(Contingency::getId).sorted()
                .forEach(contingency -> instants.forEach(instant -> states.add(new StateRef(contingency, instant.getId()))));
        return states;
    }

    // ----- creation -----

    /**
     * A flow CNEC on {@code branch} at {@code state}, as OpenRAO's CRAC generator makes one: its id the branch's name and
     * the state's, optimized, the nominal voltages of the branch's ends, and on each of {@code sides} a threshold of ±
     * the permanent current limit (A), or else of ± the permanent active power limit (MW); the permanent current limits
     * are the iMax.
     */
    public static FlowCnecSpec defaultSpec(Crac crac, Branch<?> branch, StateRef state, Set<TwoSides> sides) {
        String stateName = state.isPreventive() ? state.instantId()
                : Optional.ofNullable(crac.getContingency(state.contingencyId())).flatMap(Contingency::getName).orElse(state.contingencyId()) + "_" + state.instantId();
        List<ThresholdSpec> thresholds = new ArrayList<>();
        for (TwoSides side : List.of(TwoSides.ONE, TwoSides.TWO)) {
            if (sides.contains(side)) {
                permanentLimit(branch, side, true).map(limit -> new ThresholdSpec(side, Unit.AMPERE, -limit, limit))
                        .or(() -> permanentLimit(branch, side, false).map(limit -> new ThresholdSpec(side, Unit.MEGAWATT, -limit, limit)))
                        .ifPresent(thresholds::add);
            }
        }
        return new FlowCnecSpec(branch.getNameOrId() + "_" + stateName, null, branch.getId(), state, true, false, 0, null, null,
                branch.getTerminal(TwoSides.ONE).getVoltageLevel().getNominalV(), branch.getTerminal(TwoSides.TWO).getVoltageLevel().getNominalV(),
                permanentLimit(branch, TwoSides.ONE, true).orElse(null), permanentLimit(branch, TwoSides.TWO, true).orElse(null), thresholds);
    }

    private static Optional<Double> permanentLimit(Branch<?> branch, TwoSides side, boolean current) {
        Optional<OperationalLimitsGroup> group = side == TwoSides.ONE ? branch.getSelectedOperationalLimitsGroup1() : branch.getSelectedOperationalLimitsGroup2();
        return group.flatMap(limits -> current ? limits.getCurrentLimits().map(LoadingLimits.class::cast) : limits.getActivePowerLimits().map(LoadingLimits.class::cast))
                .map(LoadingLimits::getPermanentLimit).filter(limit -> !Double.isNaN(limit));
    }

    public static FlowCnec addFlowCnec(Crac crac, FlowCnecSpec spec) {
        if (spec.id().isEmpty()) {
            throw new IllegalArgumentException(Messages.get("cracEdits.idRequired"));
        }
        if (crac.getCnec(spec.id()) != null) {
            throw new IllegalArgumentException(Messages.get("cnecEdits.exists", spec.id()));
        }
        if (crac.getSortedInstants().stream().noneMatch(instant -> instant.getId().equals(spec.state().instantId()))) {
            throw new IllegalArgumentException(Messages.get("cnecEdits.unknownInstant", spec.state().instantId()));
        }
        for (ThresholdSpec threshold : spec.thresholds()) {
            if (threshold.min() == null && threshold.max() == null) {
                throw new IllegalArgumentException(Messages.get("cnecEdits.threshold.noBound"));
            }
        }
        FlowCnecAdder adder = crac.newFlowCnec().withId(spec.id()).withName(spec.name()).withNetworkElement(spec.branchId())
                .withInstant(spec.state().instantId()).withOptimized(spec.optimized()).withMonitored(spec.monitored())
                .withReliabilityMargin(spec.reliabilityMargin());
        if (!spec.state().isPreventive()) {
            adder.withContingency(spec.state().contingencyId());
        }
        if (spec.operator() != null) {
            adder.withOperator(spec.operator());
        }
        if (spec.border() != null) {
            adder.withBorder(spec.border());
        }
        if (spec.nominalVoltage1() != null) {
            adder.withNominalVoltage(spec.nominalVoltage1(), TwoSides.ONE);
        }
        if (spec.nominalVoltage2() != null) {
            adder.withNominalVoltage(spec.nominalVoltage2(), TwoSides.TWO);
        }
        if (spec.iMax1() != null) {
            adder.withIMax(spec.iMax1(), TwoSides.ONE);
        }
        if (spec.iMax2() != null) {
            adder.withIMax(spec.iMax2(), TwoSides.TWO);
        }
        spec.thresholds().forEach(threshold -> adder.newThreshold().withSide(threshold.side()).withUnit(threshold.unit())
                .withMin(threshold.min()).withMax(threshold.max()).add());
        try {
            return adder.add();
        } catch (OpenRaoException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * The result of {@link #addFlowCnecs}: the CNECs added, and those skipped with why.
     */
    public record Added(List<FlowCnec> added, List<String> skipped) {
    }

    /**
     * Adds the {@link #defaultSpec default} flow CNEC of each branch of {@code branchIds} at each of {@code states},
     * skipping those whose id the CRAC already has and those OpenRAO refuses (no limit on the sides...).
     */
    public static Added addFlowCnecs(Crac crac, Network network, List<String> branchIds, List<StateRef> states, Set<TwoSides> sides) {
        List<FlowCnec> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String branchId : branchIds) {
            Branch<?> branch = network.getBranch(branchId);
            if (branch == null) {
                skipped.add(Messages.get("cnecEdits.skipped", branchId, Messages.get("cnecEdits.notABranch")));
                continue;
            }
            for (StateRef state : states) {
                FlowCnecSpec spec = defaultSpec(crac, branch, state, sides);
                try {
                    added.add(addFlowCnec(crac, spec));
                } catch (IllegalArgumentException e) {
                    skipped.add(Messages.get("cnecEdits.skipped", spec.id(), e.getMessage()));
                }
            }
        }
        return new Added(added, skipped);
    }

    // ----- changes -----

    /**
     * The remedial actions with an on-constraint usage rule on the CNEC {@code id}, by name.
     */
    public static List<String> references(Crac crac, String id) {
        return CracElements.remedialActions(crac)
                .filter(action -> action.getUsageRules().stream().anyMatch(rule -> rule instanceof OnConstraint<?> onConstraint && onConstraint.getCnec().getId().equals(id)))
                .map(RemedialAction::getName).sorted().toList();
    }

    /**
     * Makes {@code cnec} as {@code spec} says: its role in place, anything else by replacing it, only when no usage
     * rule refers to it. When OpenRAO refuses {@code spec}, {@code cnec} is put back (a new object of the same id).
     *
     * @return the CNEC as edited: {@code cnec} itself unless replaced
     */
    public static FlowCnec change(Crac crac, FlowCnec cnec, FlowCnecSpec spec) {
        FlowCnecSpec current = FlowCnecSpec.of(cnec);
        FlowCnecSpec sorted = new FlowCnecSpec(spec.id(), spec.name(), spec.branchId(), spec.state(), spec.optimized(), spec.monitored(),
                spec.reliabilityMargin(), spec.operator(), spec.border(), spec.nominalVoltage1(), spec.nominalVoltage2(), spec.iMax1(), spec.iMax2(),
                spec.thresholds().stream().sorted(THRESHOLD_ORDER).toList());
        if (current.withRole(sorted.optimized(), sorted.monitored()).equals(sorted)) {
            cnec.setOptimized(sorted.optimized());
            cnec.setMonitored(sorted.monitored());
            return cnec;
        }
        List<String> references = references(crac, cnec.getId());
        if (!references.isEmpty()) {
            throw new IllegalArgumentException(Messages.get("cnecEdits.referenced", cnec.getId(), String.join(", ", references)));
        }
        if (!sorted.id().equals(cnec.getId()) && crac.getCnec(sorted.id()) != null) {
            throw new IllegalArgumentException(Messages.get("cnecEdits.exists", sorted.id()));
        }
        crac.removeFlowCnec(cnec.getId());
        try {
            return addFlowCnec(crac, sorted);
        } catch (IllegalArgumentException e) {
            addFlowCnec(crac, current);
            throw e;
        }
    }

    /**
     * Removes the CNEC {@code id} (of any kind), only when no usage rule refers to it.
     */
    public static void remove(Crac crac, String id) {
        List<String> references = references(crac, id);
        if (!references.isEmpty()) {
            throw new IllegalArgumentException(Messages.get("cnecEdits.referenced", id, String.join(", ", references)));
        }
        crac.removeCnec(id);
    }

    // ----- thresholds as table rows: side (1 or 2), unit, min, max -----

    public static List<List<String>> thresholdRows(List<ThresholdSpec> thresholds) {
        return thresholds.stream().map(threshold -> List.of(Integer.toString(threshold.side().getNum()), threshold.unit().toString(),
                TableRows.text(threshold.min()), TableRows.text(threshold.max()))).toList();
    }

    public static List<ThresholdSpec> parseThresholds(List<List<String>> rows) {
        List<ThresholdSpec> thresholds = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String sideText = TableRows.required(row, 0, i);
            TwoSides side = switch (sideText) {
                case "1" -> TwoSides.ONE;
                case "2" -> TwoSides.TWO;
                default -> throw TableRows.invalid(i, Messages.get("cnecEdits.threshold.side", sideText));
            };
            String unitText = TableRows.required(row, 1, i);
            int rowIndex = i;
            Unit unit = flowUnit(unitText).orElseThrow(() -> TableRows.invalid(rowIndex, Messages.get("cnecEdits.threshold.unit", unitText)));
            Double min = TableRows.number(row, 2, i);
            Double max = TableRows.number(row, 3, i);
            if (min == null && max == null) {
                throw TableRows.invalid(i, Messages.get("cnecEdits.threshold.noBound"));
            }
            thresholds.add(new ThresholdSpec(side, unit, min, max));
        }
        return thresholds;
    }

    /**
     * The flow units, as {@link Unit#toString()} writes them ("A", "MW", "%"), or by name.
     */
    public static final List<Unit> FLOW_UNITS = List.of(Unit.AMPERE, Unit.MEGAWATT, Unit.PERCENT_IMAX);

    private static Optional<Unit> flowUnit(String text) {
        return FLOW_UNITS.stream().filter(unit -> unit.toString().equalsIgnoreCase(text) || unit.name().equalsIgnoreCase(text))
                .findFirst();
    }
}
