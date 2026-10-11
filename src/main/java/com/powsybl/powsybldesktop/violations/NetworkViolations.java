/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.violations;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.BoundaryLineFilter;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.DetectionKind;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.iidm.network.limitmodification.result.IdenticalLimitsContainer;
import com.powsybl.iidm.network.limitmodification.result.LimitsContainer;
import com.powsybl.iidm.network.util.LimitViolationUtils;
import com.powsybl.iidm.network.util.PermanentLimitCheckResult;
import com.powsybl.security.LimitViolationDetection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The current, active power, apparent power and voltage limit violations of a network's present state (voltage angle
 * limits aren't checked), computed after the network is loaded and after each load flow run, and cached in its
 * {@code NetworkStudy}. Immutable.
 * <p>
 * Flow violations follow PowSyBl's {@code LimitViolationDetection}: on every selected operational limits group, a
 * temporary limit violation when a temporary limit is crossed, a permanent limit violation otherwise. Boundary lines
 * aren't checked by PowSyBl, nor supported by its {@link LimitsComputer}s, so unpaired ones are checked here on their
 * unmodified limits; paired ones are covered by their tie line.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkViolations {

    public static final NetworkViolations EMPTY = new NetworkViolations(Map.of(), Map.of(), Map.of());

    private static final List<LimitType> FLOW_LIMIT_TYPES = List.of(LimitType.CURRENT, LimitType.ACTIVE_POWER, LimitType.APPARENT_POWER);

    private final Map<String, List<FlowViolation>> flowViolations;
    private final Map<String, VoltageViolation> voltageViolations;
    // highest loading percentage per equipment and side, whether violated or not
    private final Map<String, Map<ThreeSides, Double>> loadings;

    private NetworkViolations(Map<String, List<FlowViolation>> flowViolations, Map<String, VoltageViolation> voltageViolations,
                              Map<String, Map<ThreeSides, Double>> loadings) {
        this.flowViolations = flowViolations;
        this.voltageViolations = voltageViolations;
        this.loadings = loadings;
    }

    private interface LoadingConsumer {
        void accept(Identifiable<?> equipment, ThreeSides side, double percent);
    }

    /**
     * @param limitsComputer the limits to check against, e.g. {@link LimitsComputer#NO_MODIFICATIONS} or limits
     *                       reduced by a {@code DefaultLimitReductionsApplier}
     */
    public static NetworkViolations compute(Network network, LimitsComputer<Identifiable<?>, LoadingLimits> limitsComputer) {
        Objects.requireNonNull(limitsComputer);
        Map<String, List<FlowViolation>> flowViolations = new HashMap<>();
        Consumer<FlowViolation> flowConsumer = v -> flowViolations.computeIfAbsent(v.violation().getSubjectId(), id -> new ArrayList<>()).add(v);
        Map<String, Map<ThreeSides, Double>> loadings = new HashMap<>();
        LoadingConsumer loadingConsumer = (equipment, side, percent) -> loadings
                .computeIfAbsent(equipment.getId(), id -> new EnumMap<>(ThreeSides.class))
                .merge(side, percent, Math::max);
        network.getBranchStream().forEach(branch -> {
            for (TwoSides side : TwoSides.values()) {
                checkFlows(branch, side.toThreeSides(), branch.getTerminal(side),
                        type -> limitsComputer.computeLimits(branch, type, side.toThreeSides(), false), flowConsumer, loadingConsumer);
            }
        });
        network.getThreeWindingsTransformerStream().forEach(transformer -> {
            for (ThreeSides side : ThreeSides.values()) {
                checkFlows(transformer, side, transformer.getTerminal(side),
                        type -> limitsComputer.computeLimits(transformer, type, side, false), flowConsumer, loadingConsumer);
            }
        });
        network.getBoundaryLineStream(BoundaryLineFilter.UNPAIRED).forEach(boundaryLine ->
                checkFlows(boundaryLine, ThreeSides.ONE, boundaryLine.getTerminal(), type -> unmodifiedLimits(boundaryLine, type),
                        flowConsumer, loadingConsumer));
        flowViolations.replaceAll((id, violations) -> violations.stream().sorted(FlowViolation.SEVERITY).toList());

        Map<String, VoltageViolation> voltageViolations = new HashMap<>();
        network.getVoltageLevelStream()
                .flatMap(vl -> vl.getBusView().getBusStream())
                .forEach(bus -> checkVoltage(bus, v -> voltageViolations.put(v.busId(), v)));
        return new NetworkViolations(Map.copyOf(flowViolations), Map.copyOf(voltageViolations), Map.copyOf(loadings));
    }

    private interface LimitsGetter {
        Collection<LimitsContainer<LoadingLimits>> limits(LimitType type);
    }

    private static Collection<LimitsContainer<LoadingLimits>> unmodifiedLimits(BoundaryLine boundaryLine, LimitType type) {
        return boundaryLine.getAllSelectedOperationalLimitsGroups().stream()
                .<LimitsContainer<LoadingLimits>>mapMulti((group, downstream) -> group.getLoadingLimits(type)
                        .ifPresent(limits -> downstream.accept(new IdenticalLimitsContainer<>(limits, group.getId()))))
                .toList();
    }

    private static void checkFlows(Identifiable<?> equipment, ThreeSides side, Terminal terminal, LimitsGetter limitsGetter,
                                   Consumer<FlowViolation> consumer, LoadingConsumer loadingConsumer) {
        for (LimitType type : FLOW_LIMIT_TYPES) {
            double value = flowValue(terminal, type);
            if (!Double.isNaN(value)) {
                Collection<LimitsContainer<LoadingLimits>> limits = limitsGetter.limits(type);
                checkFlow(equipment, side, type, value, limits, consumer);
                for (LimitsContainer<LoadingLimits> container : limits) {
                    double percent = value / referenceLimit(container) * 100;
                    if (!Double.isNaN(percent)) {
                        loadingConsumer.accept(equipment, side, percent);
                    }
                }
            }
        }
    }

    // the permanent limit, or the lowest temporary limit of a LOW detection kind limit set, which has no permanent one
    private static double referenceLimit(LimitsContainer<LoadingLimits> limits) {
        LoadingLimits originalLimits = limits.getOriginalLimits();
        if (originalLimits.getDetectionKind() == DetectionKind.HIGH) {
            return originalLimits.getPermanentLimit();
        }
        return originalLimits.getTemporaryLimits().stream().findFirst().map(LoadingLimits.TemporaryLimit::getValue).orElse(Double.NaN);
    }

    /**
     * The value a {@code type} flow limit applies to: the current, |P| or the apparent power.
     */
    public static double flowValue(Terminal terminal, LimitType type) {
        return flowValue(terminal.getI(), terminal.getP(), terminal.getQ(), type);
    }

    /**
     * Like {@link #flowValue(Terminal, LimitType)}, from a computation's result, e.g. a security analysis'.
     */
    public static double flowValue(double i, double p, double q, LimitType type) {
        return switch (type) {
            case CURRENT -> i;
            case ACTIVE_POWER -> Math.abs(p);
            case APPARENT_POWER -> Math.hypot(p, q);
            default -> throw new IllegalArgumentException("Not a flow limit type: " + type);
        };
    }

    // Same logic as LimitViolationDetection.checkLimitViolation, which doesn't hand out the permanent limit
    private static void checkFlow(Identifiable<?> equipment, ThreeSides side, LimitType type, double value,
                                  Collection<LimitsContainer<LoadingLimits>> allLimits, Consumer<FlowViolation> consumer) {
        String name = equipment.getOptionalName().orElse(null);
        LimitViolationType violationType = LimitViolationDetection.toLimitViolationType(type);
        Set<String> temporaryOverloadGroupIds = new HashSet<>();
        for (LimitsContainer<LoadingLimits> limits : allLimits) {
            LimitViolationUtils.getOverload(limits, value).ifPresent(overload -> {
                consumer.accept(new FlowViolation(new LimitViolation(equipment.getId(), name, overload.getOperationalLimitsGroupId(),
                        violationType, overload.getPreviousLimitName(), overload.getTemporaryLimit().getAcceptableDuration(),
                        overload.getPreviousLimit(), overload.getLimitReductionCoefficient(), value, side, null),
                        permanentLimit(limits)));
                temporaryOverloadGroupIds.add(overload.getOperationalLimitsGroupId());
            });
        }
        for (LimitsContainer<LoadingLimits> limits : allLimits) {
            if (temporaryOverloadGroupIds.contains(limits.getOperationalLimitsGroupId())
                    || limits.getOriginalLimits().getDetectionKind() != DetectionKind.HIGH) {
                continue;
            }
            PermanentLimitCheckResult result = LimitViolationUtils.checkPermanentLimitIfAny(limits, value);
            if (result.isOverload()) {
                consumer.accept(new FlowViolation(new LimitViolation(equipment.getId(), name, result.operationalLimitsGroupId(),
                        violationType, result.permanentLimitName(), Integer.MAX_VALUE, result.permanentLimitValue(),
                        result.limitReductionValue(), value, side, null),
                        permanentLimit(limits)));
            }
        }
    }

    // a LOW detection kind limit set has no permanent limit: getPermanentLimit() throws
    private static double permanentLimit(LimitsContainer<LoadingLimits> limits) {
        LoadingLimits originalLimits = limits.getOriginalLimits();
        return originalLimits.getDetectionKind() == DetectionKind.HIGH ? originalLimits.getPermanentLimit() : Double.NaN;
    }

    // Same logic as LimitViolationDetection.checkVoltage (package-private), keeping the bus
    private static void checkVoltage(Bus bus, Consumer<VoltageViolation> consumer) {
        double v = bus.getV();
        VoltageLevel vl = bus.getVoltageLevel();
        if (!Double.isNaN(vl.getLowVoltageLimit()) && v <= vl.getLowVoltageLimit()) {
            consumer.accept(voltageViolation(bus, LimitViolationType.LOW_VOLTAGE, vl.getLowVoltageLimit()));
        } else if (!Double.isNaN(vl.getHighVoltageLimit()) && v >= vl.getHighVoltageLimit()) {
            consumer.accept(voltageViolation(bus, LimitViolationType.HIGH_VOLTAGE, vl.getHighVoltageLimit()));
        }
    }

    private static VoltageViolation voltageViolation(Bus bus, LimitViolationType type, double limit) {
        VoltageLevel vl = bus.getVoltageLevel();
        LimitViolation violation = LimitViolation.builder()
                .subject(vl.getId())
                .subjectName(vl.getOptionalName().orElse(null))
                .type(type)
                .limit(limit)
                .value(bus.getV())
                .violationLocation(LimitViolationDetection.createViolationLocation(bus))
                .build();
        return new VoltageViolation(violation, bus.getId(), vl.getNominalV());
    }

    /**
     * All flow violations, keyed by equipment id, each equipment's sorted by {@link FlowViolation#SEVERITY}.
     */
    public Map<String, List<FlowViolation>> getFlowViolations() {
        return flowViolations;
    }

    public List<FlowViolation> getFlowViolations(String equipmentId) {
        return flowViolations.getOrDefault(equipmentId, List.of());
    }

    /**
     * The equipment's most severe flow violation, or null if it has none.
     */
    public FlowViolation getWorstFlowViolation(String equipmentId) {
        List<FlowViolation> violations = flowViolations.get(equipmentId);
        return violations == null ? null : violations.getFirst();
    }

    /**
     * The equipment's loading: its most severe flow violation's if any, otherwise its highest loading percentage over
     * all its sides and limits. Null if it has no limits or no flow values.
     */
    public Loading getLoading(String equipmentId) {
        FlowViolation worst = getWorstFlowViolation(equipmentId);
        if (worst != null) {
            return new Loading(worst.percent(), worst);
        }
        Map<ThreeSides, Double> sideLoadings = loadings.get(equipmentId);
        return sideLoadings == null ? null : new Loading(sideLoadings.values().stream().max(Double::compare).orElseThrow(), null);
    }

    /**
     * Like {@link #getLoading(String)}, restricted to one side of the equipment.
     */
    public Loading getLoading(String equipmentId, ThreeSides side) {
        FlowViolation worst = getFlowViolations(equipmentId).stream().filter(v -> v.violation().getSide() == side).findFirst().orElse(null);
        if (worst != null) {
            return new Loading(worst.percent(), worst);
        }
        Double percent = loadings.getOrDefault(equipmentId, Map.of()).get(side);
        return percent == null ? null : new Loading(percent, null);
    }

    public Collection<VoltageViolation> getVoltageViolations() {
        return voltageViolations.values();
    }

    /**
     * The voltage violation of a bus of the bus view, or null if it has none.
     */
    public VoltageViolation getVoltageViolation(String busId) {
        return busId == null ? null : voltageViolations.get(busId);
    }
}
