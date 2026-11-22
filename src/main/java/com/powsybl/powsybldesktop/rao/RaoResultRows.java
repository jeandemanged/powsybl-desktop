/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.openrao.data.crac.api.rangeaction.PstRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.RangeAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * The rows of the RAO Results view, from a {@link RaoResult} and the CRAC it refers to: its flow CNECs (the RAO computes
 * neither angles nor voltages), the remedial actions it activated, state by state, and its costs, instant by instant.
 * A value the result doesn't hold (a state whose computation failed, an instant not optimized) is NaN.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class RaoResultRows {

    /**
     * The preventive state first, then by contingency and instant.
     */
    public static final Comparator<State> STATE_ORDER = Comparator.<State, Boolean>comparing(state -> !state.isPreventive())
            .thenComparing(state -> state.getContingency().map(Contingency::getId).orElse(""))
            .thenComparing(State::getInstant);

    /**
     * A flow CNEC on {@code side} (the first it monitors), its thresholds there, then its flows and margins (over all
     * its monitored sides) before optimization, after the preventive one and after the optimization of its own instant
     * ({@code preventive*} are NaN for a preventive CNEC, the same as the last ones).
     */
    public record CnecRow(FlowCnec cnec, TwoSides side, double lowerBound, double upperBound,
                          double initialFlow, double preventiveFlow, double flow,
                          double initialMargin, double preventiveMargin, double margin) {
    }

    /**
     * A remedial action activated in {@code state}: for a range action, its set point (and tap, for a PST) before and
     * after the optimization of the state, null for a network action.
     */
    public record ActionRow(State state, RemedialAction<?> action, Double initialSetPoint, Double setPoint, Integer initialTap, Integer tap) {
    }

    /**
     * The costs after the optimization of {@code instant}, null for the initial situation.
     */
    public record CostRow(Instant instant, double functionalCost, double virtualCost, Map<String, Double> virtualCosts) {
    }

    private RaoResultRows() {
    }

    /**
     * Every flow CNEC of {@code crac}, by state then id, in {@code unit} (MEGAWATT or AMPERE).
     */
    public static List<CnecRow> cnecRows(RaoResult result, Crac crac, Unit unit) {
        Instant preventive = crac.getPreventiveInstant();
        return crac.getFlowCnecs().stream()
                .sorted(Comparator.comparing(FlowCnec::getState, STATE_ORDER).thenComparing(FlowCnec::getId))
                .map(cnec -> {
                    TwoSides side = cnec.getMonitoredSides().stream().sorted().findFirst().orElse(TwoSides.ONE);
                    Instant instant = cnec.getState().getInstant();
                    boolean afterPreventive = instant.comesAfter(preventive);
                    return new CnecRow(cnec, side,
                            cnec.getLowerBound(side, unit).orElse(Double.NaN), cnec.getUpperBound(side, unit).orElse(Double.NaN),
                            value(() -> result.getFlow(null, cnec, side, unit)),
                            afterPreventive ? value(() -> result.getFlow(preventive, cnec, side, unit)) : Double.NaN,
                            value(() -> result.getFlow(instant, cnec, side, unit)),
                            value(() -> result.getMargin(null, cnec, unit)),
                            afterPreventive ? value(() -> result.getMargin(preventive, cnec, unit)) : Double.NaN,
                            value(() -> result.getMargin(instant, cnec, unit)));
                })
                .toList();
    }

    /**
     * The remedial actions activated in each state of {@code crac}, by state then id.
     */
    public static List<ActionRow> actionRows(RaoResult result, Crac crac) {
        List<ActionRow> rows = new ArrayList<>();
        crac.getStates().stream().sorted(STATE_ORDER).forEach(state -> {
            List<ActionRow> stateRows = new ArrayList<>();
            activated(() -> result.getActivatedNetworkActionsDuringState(state)).forEach(action ->
                    stateRows.add(new ActionRow(state, action, null, null, null, null)));
            activated(() -> result.getActivatedRangeActionsDuringState(state)).forEach(action -> {
                RangeAction<?> rangeAction = (RangeAction<?>) action;
                boolean pst = rangeAction instanceof PstRangeAction;
                stateRows.add(new ActionRow(state, rangeAction,
                        orNull(() -> result.getPreOptimizationSetPointOnState(state, rangeAction)), orNull(() -> result.getOptimizedSetPointOnState(state, rangeAction)),
                        pst ? orNull(() -> result.getPreOptimizationTapOnState(state, (PstRangeAction) rangeAction)) : null,
                        pst ? orNull(() -> result.getOptimizedTapOnState(state, (PstRangeAction) rangeAction)) : null));
            });
            stateRows.sort(Comparator.comparing(row -> row.action().getId()));
            rows.addAll(stateRows);
        });
        return rows;
    }

    /**
     * The costs of the initial situation, then after the optimization of each instant of {@code crac}.
     */
    public static List<CostRow> costRows(RaoResult result, Crac crac) {
        List<Instant> instants = new ArrayList<>();
        instants.add(null);
        instants.addAll(crac.getSortedInstants());
        return instants.stream().map(instant -> {
            Map<String, Double> virtualCosts = new LinkedHashMap<>();
            result.getVirtualCostNames().stream().sorted()
                    .forEach(name -> virtualCosts.put(name, value(() -> result.getVirtualCost(instant, name))));
            return new CostRow(instant, value(() -> result.getFunctionalCost(instant)), value(() -> result.getVirtualCost(instant)), virtualCosts);
        }).toList();
    }

    private static double value(DoubleSupplier supplier) {
        try {
            return supplier.getAsDouble();
        } catch (OpenRaoException _) {
            return Double.NaN;
        }
    }

    // null when the result has no such value (e.g. a partial failure)
    private static <T> T orNull(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (OpenRaoException _) {
            return null;
        }
    }

    private static List<? extends RemedialAction<?>> activated(Supplier<? extends Collection<? extends RemedialAction<?>>> actions) {
        try {
            return List.copyOf(actions.get());
        } catch (OpenRaoException _) {
            return List.of();
        }
    }
}
