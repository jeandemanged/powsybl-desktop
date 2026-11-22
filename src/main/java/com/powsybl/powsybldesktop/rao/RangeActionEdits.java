/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.ConnectedArea;
import com.powsybl.openrao.data.crac.api.ConnectedAreaAdder;
import com.powsybl.openrao.data.crac.api.ConnectedAreaBorderRangeAdder;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.RemedialActionAdder;
import com.powsybl.openrao.data.crac.api.range.RangeType;
import com.powsybl.openrao.data.crac.api.range.StandardRange;
import com.powsybl.openrao.data.crac.api.range.StandardRangeAdder;
import com.powsybl.openrao.data.crac.api.range.TapRange;
import com.powsybl.openrao.data.crac.api.range.TapRangeAdder;
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeActionAdder;
import com.powsybl.openrao.data.crac.api.rangeaction.HvdcRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.HvdcRangeActionAdder;
import com.powsybl.openrao.data.crac.api.rangeaction.InjectionRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.InjectionRangeActionAdder;
import com.powsybl.openrao.data.crac.api.rangeaction.PstRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.PstRangeActionAdder;
import com.powsybl.openrao.data.crac.api.rangeaction.RangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.StandardRangeActionAdder;
import com.powsybl.openrao.data.crac.api.rangeaction.VariationDirection;
import com.powsybl.openrao.data.crac.io.commons.iidm.IidmCounterTradeHelper;
import com.powsybl.openrao.data.crac.io.commons.iidm.IidmHvdcHelper;
import com.powsybl.openrao.data.crac.io.commons.iidm.IidmPstHelper;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits.UsageRuleSpec;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The edits of a CRAC's range actions: on a PST's tap, an HVDC line's set point, injections' set point (distributed
 * along keys) and a counter-trade between an area and its connected areas. OpenRAO's range actions are immutable: a
 * change replaces the action by a new one, with the same usage rules. The elements are checked against the network when
 * it has them; one it lacks is kept, the view marking it.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class RangeActionEdits {

    private RangeActionEdits() {
    }

    /**
     * The types of range actions.
     */
    public enum Type {
        PST("pst"),
        HVDC("hvdc"),
        INJECTION("injection"),
        COUNTER_TRADE("counterTrade");

        private final String code;

        Type(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public String label() {
            return Messages.get("rangeActionEdits.type." + code);
        }
    }

    private static final Map<RangeType, String> RANGE_TYPE_CODES = Map.of(RangeType.ABSOLUTE, "absolute",
            RangeType.RELATIVE_TO_INITIAL_NETWORK, "relativeToInitialNetwork", RangeType.RELATIVE_TO_PREVIOUS_INSTANT, "relativeToPreviousInstant",
            RangeType.RELATIVE_TO_PREVIOUS_TIME_STEP, "relativeToPreviousTimeStep");

    /**
     * The code of a range type in the tables of ranges.
     */
    public static String code(RangeType type) {
        return RANGE_TYPE_CODES.get(type);
    }

    public static String label(RangeType type) {
        return Messages.get("rangeActionEdits.rangeType." + code(type));
    }

    static Optional<RangeType> rangeType(String text) {
        return Stream.of(RangeType.values()).filter(type -> code(type).equalsIgnoreCase(text) || label(type).equalsIgnoreCase(text)).findFirst();
    }

    /**
     * A range of {@code type}: taps for a PST, MW otherwise; a {@code null} bound is none.
     */
    public record RangeSpec(RangeType type, Double min, Double max) {
        public RangeSpec {
            Objects.requireNonNull(type);
        }
    }

    /**
     * A network element of a range action, with its distribution key: 1 but for an injection range action's.
     */
    public record KeyedElement(String id, double key) {
        public KeyedElement {
            id = id == null ? "" : id.trim();
        }
    }

    /**
     * An area connected to a counter-trade's, with the ranges of the exchange on their border.
     */
    public record ConnectedAreaSpec(String areaId, List<RangeSpec> borderRanges) {
        public ConnectedAreaSpec {
            areaId = areaId == null ? "" : areaId.trim();
            borderRanges = List.copyOf(borderRanges);
        }
    }

    /**
     * A PST range action's initial tap and tap-to-angle conversion map, taken from the network when the action is made.
     */
    public record PstTaps(int initialTap, Map<Integer, Double> tapToAngle) {
        public PstTaps {
            tapToAngle = Map.copyOf(tapToAngle);
        }

        static Optional<PstTaps> of(Network network, String transformerId) {
            IidmPstHelper helper = new IidmPstHelper(transformerId, network);
            return helper.isValid() ? Optional.of(new PstTaps(helper.getInitialTap(), helper.getTapToAngleConversionMap())) : Optional.empty();
        }
    }

    /**
     * What a range action is made of, its usage rules apart. A blank name is the id; a blank operator or group is none.
     * {@code elements} is a PST's or an HVDC line's single element, or an injection range action's injections and keys,
     * none for a counter-trade; {@code initialSetpoint} is none for a PST, whose {@code pstTaps} are none for another type
     * (and for a PST whose taps are to be taken from the network); {@code areaId} and {@code connectedAreas} are a
     * counter-trade's.
     */
    public record RangeActionSpec(Type type, String id, String name, String operator, Integer speed, Double activationCost, String groupId,
                                  Double upVariationCost, Double downVariationCost, List<KeyedElement> elements, Double initialSetpoint, PstTaps pstTaps,
                                  String areaId, List<ConnectedAreaSpec> connectedAreas, List<RangeSpec> ranges) {

        public RangeActionSpec {
            Objects.requireNonNull(type);
            id = id == null ? "" : id.trim();
            name = name == null || name.isBlank() ? id : name.trim();
            operator = blankToNull(operator);
            groupId = blankToNull(groupId);
            areaId = blankToNull(areaId);
            elements = elements.stream().sorted(Comparator.comparing(KeyedElement::id)).toList();
            connectedAreas = List.copyOf(connectedAreas);
            ranges = List.copyOf(ranges);
        }

        /**
         * {@code action}'s, or empty when it is of none of the types.
         */
        public static Optional<RangeActionSpec> of(RangeAction<?> action) {
            Double up = action.getVariationCost(VariationDirection.UP).orElse(null);
            Double down = action.getVariationCost(VariationDirection.DOWN).orElse(null);
            String operator = action.getOperator();
            Integer speed = action.getSpeed().orElse(null);
            Double cost = action.getActivationCost().orElse(null);
            String group = action.getGroupId().orElse(null);
            return Optional.ofNullable(switch (action) {
                case PstRangeAction pst -> new RangeActionSpec(Type.PST, pst.getId(), pst.getName(), operator, speed, cost, group, up, down,
                        List.of(new KeyedElement(pst.getNetworkElementId(), 1)), null, new PstTaps(pst.getInitialTap(), pst.getTapToAngleConversionMap()),
                        null, List.of(), pst.getRanges().stream().map(RangeActionEdits::rangeSpec).toList());
                case HvdcRangeAction hvdc -> new RangeActionSpec(Type.HVDC, hvdc.getId(), hvdc.getName(), operator, speed, cost, group, up, down,
                        List.of(new KeyedElement(hvdc.getNetworkElementId(), 1)), hvdc.getInitialSetpoint(), null, null, List.of(), standardRanges(hvdc.getRanges()));
                case InjectionRangeAction injection -> new RangeActionSpec(Type.INJECTION, injection.getId(), injection.getName(), operator, speed, cost, group, up,
                        down, injection.getInjectionDistributionKeysIds().entrySet().stream().map(e -> new KeyedElement(e.getKey(), e.getValue())).toList(),
                        injection.getInitialSetpoint(), null, null, List.of(), standardRanges(injection.getRanges()));
                case CounterTradeRangeAction counterTrade -> new RangeActionSpec(Type.COUNTER_TRADE, counterTrade.getId(), counterTrade.getName(), operator, speed,
                        cost, group, up, down, List.of(), counterTrade.getInitialSetpoint(), null, counterTrade.getAreaId(),
                        counterTrade.getConnectedAreas().stream().map(RangeActionEdits::connectedAreaSpec).toList(), standardRanges(counterTrade.getRanges()));
                default -> null;
            });
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    // the unbounded limits OpenRAO's adders set when none is given, as none
    private static RangeSpec rangeSpec(TapRange range) {
        return new RangeSpec(range.getRangeType(), range.getMinTap() == Integer.MIN_VALUE ? null : (double) range.getMinTap(),
                range.getMaxTap() == Integer.MAX_VALUE ? null : (double) range.getMaxTap());
    }

    private static RangeSpec rangeSpec(StandardRange range) {
        return new RangeSpec(range.getRangeType(), range.getMin() <= -Double.MAX_VALUE ? null : range.getMin(), range.getMax() >= Double.MAX_VALUE ? null : range.getMax());
    }

    private static List<RangeSpec> standardRanges(List<StandardRange> ranges) {
        return ranges.stream().map(RangeActionEdits::rangeSpec).toList();
    }

    private static ConnectedAreaSpec connectedAreaSpec(ConnectedArea area) {
        return new ConnectedAreaSpec(area.getAreaId(), standardRanges(area.getBorderRanges()));
    }

    // ----- creation -----

    /**
     * The network's objects a range action of {@code type} can be on: the PSTs, HVDC lines, generators and loads.
     */
    public static Stream<Identifiable<?>> elementCandidates(Network network, Type type) {
        return switch (type) {
            case PST -> network.getTwoWindingsTransformerStream().filter(transformer -> transformer.getPhaseTapChanger() != null).map(transformer -> transformer);
            case HVDC -> network.getHvdcLineStream().map(line -> line);
            case INJECTION -> Stream.concat(network.getGeneratorStream(), network.getLoadStream());
            case COUNTER_TRADE -> Stream.empty();
        };
    }

    /**
     * The countries of the network's substations, the areas a counter-trade can be between.
     */
    public static List<Country> countries(Network network) {
        return network.getSubstationStream().map(Substation::getNullableCountry).filter(Objects::nonNull).distinct().sorted().toList();
    }

    /**
     * A new PST range action on {@code transformerId}: its taps and range the network's.
     */
    public static RangeActionSpec pst(Network network, String id, String transformerId) {
        IidmPstHelper helper = new IidmPstHelper(transformerId, network);
        if (!helper.isValid()) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.notInNetwork", transformerId, Type.PST.label()));
        }
        return new RangeActionSpec(Type.PST, id, null, null, null, null, null, null, null, List.of(new KeyedElement(transformerId, 1)), null,
                new PstTaps(helper.getInitialTap(), helper.getTapToAngleConversionMap()), null, List.of(),
                List.of(new RangeSpec(RangeType.ABSOLUTE, (double) helper.getLowTapPosition(), (double) helper.getHighTapPosition())));
    }

    /**
     * A new HVDC range action on {@code lineId}: from its current set point, within its maximum active power both ways.
     */
    public static RangeActionSpec hvdc(Network network, String id, String lineId) {
        HvdcLine line = network.getHvdcLine(lineId);
        if (line == null) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.notInNetwork", lineId, Type.HVDC.label()));
        }
        return new RangeActionSpec(Type.HVDC, id, null, null, null, null, null, null, null, List.of(new KeyedElement(lineId, 1)),
                IidmHvdcHelper.getCurrentSetpoint(network, lineId), null, null, List.of(), List.of(new RangeSpec(RangeType.ABSOLUTE, -line.getMaxP(), line.getMaxP())));
    }

    /**
     * A new injection range action on the generators and loads {@code injectionIds}, as OpenRAO's CRAC generator makes
     * them: keyed by their share of the total set point (generation minus consumption), or 1 when alone; a single
     * generator within its active power limits, else 100 MW around the initial set point.
     */
    public static RangeActionSpec injection(Network network, String id, List<String> injectionIds) {
        List<Identifiable<?>> injections = injectionIds.stream().<Identifiable<?>>map(network::getIdentifiable)
                .filter(injection -> injection instanceof Generator || injection instanceof Load).toList();
        if (injections.isEmpty()) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.injectionRequired"));
        }
        double total = injections.stream().mapToDouble(RangeActionEdits::setpoint).sum();
        List<KeyedElement> keys = injections.stream().map(injection -> new KeyedElement(injection.getId(),
                injections.size() == 1 || Math.abs(total) < 1 ? 1 : setpoint(injection) / total)).toList();
        double initial = injections.size() == 1 || Math.abs(total) >= 1 ? total : 0;
        RangeSpec range = injections.size() == 1 && injections.getFirst() instanceof Generator generator
                ? new RangeSpec(RangeType.ABSOLUTE, Math.min(generator.getMinP(), initial), Math.max(generator.getMaxP(), initial))
                : new RangeSpec(RangeType.RELATIVE_TO_INITIAL_NETWORK, -DEFAULT_VARIATION, DEFAULT_VARIATION);
        return new RangeActionSpec(Type.INJECTION, id, null, null, null, null, null, null, null, keys, initial, null, null, List.of(), List.of(range));
    }

    private static final double DEFAULT_VARIATION = 100;

    // a generator's target, a load's consumption counted negatively, as OpenRAO's injection range actions apply them
    private static double setpoint(Identifiable<?> injection) {
        return injection instanceof Generator generator ? generator.getTargetP() : -((Load) injection).getP0();
    }

    /**
     * A new counter-trade from {@code country} to its neighbours in the network, from 0 by up to 100 MW both ways.
     */
    public static RangeActionSpec counterTrade(Network network, String id, Country country) {
        List<ConnectedAreaSpec> neighbours = new IidmCounterTradeHelper(country.name(), network).getNeighbors().stream()
                .map(neighbour -> new ConnectedAreaSpec(neighbour, List.of())).toList();
        return new RangeActionSpec(Type.COUNTER_TRADE, id, null, null, null, null, null, null, null, List.of(), 0., null, country.name(), neighbours,
                List.of(new RangeSpec(RangeType.ABSOLUTE, -DEFAULT_VARIATION, DEFAULT_VARIATION)));
    }

    /**
     * Adds a range action, available as {@code usageRules} tell.
     */
    public static RangeAction<?> add(Crac crac, Network network, RangeActionSpec spec, Collection<UsageRuleSpec> usageRules) {
        if (spec.id().isEmpty()) {
            throw new IllegalArgumentException(Messages.get("cracEdits.idRequired"));
        }
        if (crac.getRemedialAction(spec.id()) != null) {
            throw new IllegalArgumentException(Messages.get("networkActionEdits.exists", spec.id()));
        }
        UsageRuleEdits.checkAll(crac, usageRules);
        RangeActionSpec checked = check(crac, network, spec, usageRules);
        try {
            return build(crac, checked, usageRules);
        } catch (OpenRaoException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    // the spec, with a PST's taps from the network when it has none
    private static RangeActionSpec check(Crac crac, Network network, RangeActionSpec spec, Collection<UsageRuleSpec> usageRules) {
        RangeActionSpec checked = spec;
        switch (spec.type()) {
            case PST, HVDC -> {
                if (spec.elements().size() != 1 || spec.elements().getFirst().id().isEmpty()) {
                    throw new IllegalArgumentException(Messages.get("rangeActionEdits.elementRequired", spec.type().label()));
                }
                String elementId = spec.elements().getFirst().id();
                Identifiable<?> element = network.getIdentifiable(elementId);
                if (element != null && !fits(spec.type(), element)) {
                    throw new IllegalArgumentException(Messages.get("rangeActionEdits.wrongElement", elementId, spec.type().label()));
                }
                if (spec.type() == Type.PST && spec.pstTaps() == null) {
                    PstTaps taps = PstTaps.of(network, elementId)
                            .orElseThrow(() -> new IllegalArgumentException(Messages.get("rangeActionEdits.notInNetwork", elementId, Type.PST.label())));
                    checked = new RangeActionSpec(spec.type(), spec.id(), spec.name(), spec.operator(), spec.speed(), spec.activationCost(), spec.groupId(),
                            spec.upVariationCost(), spec.downVariationCost(), spec.elements(), null, taps, null, List.of(), spec.ranges());
                }
            }
            case INJECTION -> checkInjections(network, spec.elements());
            case COUNTER_TRADE -> checkAreas(spec);
        }
        checkRanges(crac, spec, usageRules);
        return checked;
    }

    private static boolean fits(Type type, Identifiable<?> element) {
        return switch (type) {
            case PST -> element instanceof TwoWindingsTransformer transformer && transformer.getPhaseTapChanger() != null;
            case HVDC -> element instanceof HvdcLine;
            case INJECTION -> element instanceof Generator || element instanceof Load;
            case COUNTER_TRADE -> false;
        };
    }

    private static void checkInjections(Network network, List<KeyedElement> elements) {
        if (elements.isEmpty()) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.injectionRequired"));
        }
        Set<String> ids = new HashSet<>();
        for (KeyedElement element : elements) {
            if (element.id().isEmpty()) {
                throw new IllegalArgumentException(Messages.get("rangeActionEdits.elementRequired", Type.INJECTION.label()));
            }
            if (!ids.add(element.id())) {
                throw new IllegalArgumentException(Messages.get("rangeActionEdits.twice", element.id()));
            }
            // OpenRAO drops the injections of a key this small
            if (Math.abs(element.key()) <= 1e-3) {
                throw new IllegalArgumentException(Messages.get("rangeActionEdits.zeroKey", element.id()));
            }
            Identifiable<?> injection = network.getIdentifiable(element.id());
            if (injection != null && !fits(Type.INJECTION, injection)) {
                throw new IllegalArgumentException(Messages.get("rangeActionEdits.wrongElement", element.id(), Type.INJECTION.label()));
            }
        }
    }

    private static void checkAreas(RangeActionSpec spec) {
        if (spec.areaId() == null) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.areaRequired"));
        }
        Set<String> areas = new HashSet<>();
        for (ConnectedAreaSpec area : spec.connectedAreas()) {
            if (area.areaId().isEmpty()) {
                throw new IllegalArgumentException(Messages.get("rangeActionEdits.areaRequired"));
            }
            if (area.areaId().equals(spec.areaId())) {
                throw new IllegalArgumentException(Messages.get("rangeActionEdits.ownArea", area.areaId()));
            }
            if (!areas.add(area.areaId())) {
                throw new IllegalArgumentException(Messages.get("rangeActionEdits.areaTwice", area.areaId()));
            }
            for (RangeSpec range : area.borderRanges()) {
                if (range.min() == null && range.max() == null) {
                    throw new IllegalArgumentException(Messages.get("rangeActionEdits.borderBounds", area.areaId()));
                }
                checkMinMax(range);
            }
        }
    }

    private static void checkRanges(Crac crac, RangeActionSpec spec, Collection<UsageRuleSpec> usageRules) {
        if (spec.type() != Type.PST && spec.ranges().isEmpty()) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.rangeRequired"));
        }
        for (RangeSpec range : spec.ranges()) {
            if (spec.type() == Type.PST) {
                for (Double tap : Stream.of(range.min(), range.max()).filter(Objects::nonNull).toList()) {
                    if (tap != Math.rint(tap) || Math.abs(tap) > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException(Messages.get("rangeActionEdits.tapNotInteger", TableRows.text(tap)));
                    }
                }
            } else if (range.type() == RangeType.ABSOLUTE && (range.min() == null || range.max() == null)) {
                throw new IllegalArgumentException(Messages.get("rangeActionEdits.absoluteBounds"));
            }
            checkMinMax(range);
        }
        checkPreviousInstantRanges(crac, spec, usageRules);
    }

    // OpenRAO drops the ranges relative to the previous instant of a PST range action only available at the preventive instant
    private static void checkPreviousInstantRanges(Crac crac, RangeActionSpec spec, Collection<UsageRuleSpec> usageRules) {
        if (spec.type() == Type.PST && spec.ranges().stream().anyMatch(range -> range.type() == RangeType.RELATIVE_TO_PREVIOUS_INSTANT)
                && usageRules.stream().allMatch(rule -> crac.getInstant(rule.instantId()).isPreventive())) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.previousInstantPreventive"));
        }
    }

    private static void checkMinMax(RangeSpec range) {
        if (range.min() != null && range.max() != null && range.min() > range.max()) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.minAboveMax", TableRows.text(range.min()), TableRows.text(range.max())));
        }
    }

    // unchecked: OpenRAO's adders' own checks apart
    private static RangeAction<?> build(Crac crac, RangeActionSpec spec, Collection<UsageRuleSpec> usageRules) {
        return switch (spec.type()) {
            case PST -> {
                PstRangeActionAdder adder = common(crac.newPstRangeAction(), spec, usageRules).withGroupId(spec.groupId())
                        .withNetworkElement(spec.elements().getFirst().id())
                        .withInitialTap(spec.pstTaps().initialTap()).withTapToAngleConversionMap(spec.pstTaps().tapToAngle());
                variationCosts(spec, adder::withVariationCost);
                for (RangeSpec range : spec.ranges()) {
                    TapRangeAdder rangeAdder = adder.newTapRange().withRangeType(range.type());
                    if (range.min() != null) {
                        rangeAdder.withMinTap(range.min().intValue());
                    }
                    if (range.max() != null) {
                        rangeAdder.withMaxTap(range.max().intValue());
                    }
                    rangeAdder.add();
                }
                yield adder.add();
            }
            case HVDC -> {
                HvdcRangeActionAdder adder = standard(crac.newHvdcRangeAction(), spec, usageRules).withNetworkElement(spec.elements().getFirst().id());
                yield adder.add();
            }
            case INJECTION -> {
                InjectionRangeActionAdder adder = standard(crac.newInjectionRangeAction(), spec, usageRules);
                spec.elements().forEach(element -> adder.withNetworkElementAndKey(element.key(), element.id()));
                yield adder.add();
            }
            case COUNTER_TRADE -> {
                CounterTradeRangeActionAdder adder = standard(crac.newCounterTradeRangeAction(), spec, usageRules).withAreaId(spec.areaId());
                for (ConnectedAreaSpec area : spec.connectedAreas()) {
                    ConnectedAreaAdder areaAdder = adder.newConnectedArea().withAreaId(area.areaId());
                    for (RangeSpec range : area.borderRanges()) {
                        ConnectedAreaBorderRangeAdder rangeAdder = areaAdder.newBorderRange().withRangeType(range.type());
                        if (range.min() != null) {
                            rangeAdder.withMin(range.min());
                        }
                        if (range.max() != null) {
                            rangeAdder.withMax(range.max());
                        }
                        rangeAdder.add();
                    }
                    areaAdder.add();
                }
                yield adder.add();
            }
        };
    }

    private static <T extends RemedialActionAdder<T>> T common(T adder, RangeActionSpec spec, Collection<UsageRuleSpec> usageRules) {
        adder.withId(spec.id()).withName(spec.name()).withOperator(spec.operator()).withSpeed(spec.speed()).withActivationCost(spec.activationCost());
        UsageRuleEdits.addTo(adder, usageRules);
        return adder;
    }

    private interface VariationCostSetter {
        void set(Double cost, VariationDirection direction);
    }

    private static void variationCosts(RangeActionSpec spec, VariationCostSetter setter) {
        if (spec.upVariationCost() != null) {
            setter.set(spec.upVariationCost(), VariationDirection.UP);
        }
        if (spec.downVariationCost() != null) {
            setter.set(spec.downVariationCost(), VariationDirection.DOWN);
        }
    }

    // a missing bound: unbounded, rather than the adder's default (Double.MIN_VALUE, a bound of about 0, for a minimum)
    private static <T extends StandardRangeActionAdder<T>> T standard(T adder, RangeActionSpec spec, Collection<UsageRuleSpec> usageRules) {
        common(adder, spec, usageRules).withGroupId(spec.groupId()).withInitialSetpoint(spec.initialSetpoint());
        variationCosts(spec, adder::withVariationCost);
        for (RangeSpec range : spec.ranges()) {
            StandardRangeAdder<T> rangeAdder = adder.newRange().withRangeType(range.type())
                    .withMin(range.min() == null ? -Double.MAX_VALUE : range.min());
            if (range.max() != null) {
                rangeAdder.withMax(range.max());
            }
            rangeAdder.add();
        }
        return adder;
    }

    // ----- change -----

    /**
     * Replaces {@code action} by one as {@code spec} tells, with the same usage rules, putting it back when the new one is
     * refused; {@code action} itself when nothing changes.
     */
    public static RangeAction<?> change(Crac crac, Network network, RangeAction<?> action, RangeActionSpec spec) {
        RangeActionSpec current = current(action);
        if (current.equals(spec)) {
            return action;
        }
        if (spec.type() != current.type()) {
            throw new IllegalArgumentException(spec.type().name());
        }
        if (!spec.id().equals(action.getId()) && crac.getRemedialAction(spec.id()) != null) {
            throw new IllegalArgumentException(Messages.get("networkActionEdits.exists", spec.id()));
        }
        List<UsageRuleSpec> usageRules = UsageRuleEdits.of(action);
        return replace(crac, action, current, usageRules, () -> add(crac, network, spec, usageRules));
    }

    /**
     * Replaces {@code action} by the same one with {@code usageRules}, putting it back when they are refused; its
     * elements are not checked again (the network may have changed since), its ranges are, against the new rules. See
     * {@link UsageRuleEdits#set}.
     */
    static RangeAction<?> setUsageRules(Crac crac, Network network, RangeAction<?> action, List<UsageRuleSpec> usageRules) {
        RangeActionSpec current = current(action);
        List<UsageRuleSpec> currentRules = UsageRuleEdits.of(action);
        if (Set.copyOf(currentRules).equals(Set.copyOf(usageRules))) {
            return action;
        }
        UsageRuleEdits.checkAll(crac, usageRules);
        checkPreviousInstantRanges(crac, current, usageRules);
        return replace(crac, action, current, currentRules, () -> {
            try {
                return build(crac, current, usageRules);
            } catch (OpenRaoException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        });
    }

    // a counter-trade, which OpenRAO 7.5.1 can't remove, can't be replaced
    private static RangeActionSpec current(RangeAction<?> action) {
        if (action instanceof CounterTradeRangeAction) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.counterTradeFixed"));
        }
        return RangeActionSpec.of(action).orElseThrow(() -> new IllegalArgumentException(Messages.get("networkActionEdits.unsupported", action.getId())));
    }

    // {@code action} removed, then its replacement added, or else {@code action} put back as it was, unchecked
    private static RangeAction<?> replace(Crac crac, RangeAction<?> action, RangeActionSpec current, List<UsageRuleSpec> currentRules,
                                          Supplier<RangeAction<?>> replacement) {
        crac.removeRemedialAction(action.getId());
        try {
            return replacement.get();
        } catch (IllegalArgumentException e) {
            build(crac, current, currentRules);
            throw e;
        }
    }

    /**
     * Removes the range action {@code id}: refused for a counter-trade, which OpenRAO 7.5.1 can't remove.
     */
    public static void remove(Crac crac, String id) {
        if (crac.getCounterTradeRangeAction(id) != null) {
            throw new IllegalArgumentException(Messages.get("rangeActionEdits.counterTradeFixed"));
        }
        crac.removeRemedialAction(id);
    }

    // ----- tables -----

    /**
     * The rows of ranges: type, min, max.
     */
    public static List<List<String>> rangeRows(List<RangeSpec> ranges) {
        return ranges.stream().map(range -> List.of(code(range.type()), TableRows.text(range.min()), TableRows.text(range.max()))).toList();
    }

    /**
     * The rows' ranges: the type, by code or label; the min and max, blank for none.
     */
    public static List<RangeSpec> parseRanges(List<List<String>> rows) {
        List<RangeSpec> ranges = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ranges.add(new RangeSpec(rangeType(rows.get(i), 0, i), TableRows.number(rows.get(i), 1, i), TableRows.number(rows.get(i), 2, i)));
        }
        return ranges;
    }

    private static RangeType rangeType(List<String> row, int column, int rowIndex) {
        String text = TableRows.required(row, column, rowIndex);
        return rangeType(text).orElseThrow(() -> TableRows.invalid(rowIndex, Messages.get("rangeActionEdits.unknownRangeType", text)));
    }

    /**
     * The rows of an injection range action's injections: element, key.
     */
    public static List<List<String>> keyRows(List<KeyedElement> elements) {
        return elements.stream().map(element -> List.of(element.id(), TableRows.text(element.key()))).toList();
    }

    public static List<KeyedElement> parseKeys(List<List<String>> rows) {
        List<KeyedElement> elements = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            elements.add(new KeyedElement(TableRows.required(rows.get(i), 0, i), TableRows.requiredNumber(rows.get(i), 1, i)));
        }
        return elements;
    }

    /**
     * The rows of a counter-trade's connected areas: area, range type, min, max; one per border range, an area without
     * any on a row of its own, its range blank.
     */
    public static List<List<String>> connectedAreaRows(List<ConnectedAreaSpec> areas) {
        List<List<String>> rows = new ArrayList<>();
        for (ConnectedAreaSpec area : areas) {
            if (area.borderRanges().isEmpty()) {
                rows.add(List.of(area.areaId(), "", "", ""));
            }
            area.borderRanges().forEach(range -> rows.add(List.of(area.areaId(), code(range.type()), TableRows.text(range.min()), TableRows.text(range.max()))));
        }
        return rows;
    }

    /**
     * The rows' connected areas, in order of first appearance: the rows of an area are its border ranges, but for a row
     * whose range is blank.
     */
    public static List<ConnectedAreaSpec> parseConnectedAreas(List<List<String>> rows) {
        Map<String, List<RangeSpec>> areas = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            List<RangeSpec> ranges = areas.computeIfAbsent(TableRows.required(row, 0, i), _ -> new ArrayList<>());
            if (!(TableRows.cell(row, 1).isEmpty() && TableRows.cell(row, 2).isEmpty() && TableRows.cell(row, 3).isEmpty())) {
                ranges.add(new RangeSpec(rangeType(row, 1, i), TableRows.number(row, 2, i), TableRows.number(row, 3, i)));
            }
        }
        return areas.entrySet().stream().map(entry -> new ConnectedAreaSpec(entry.getKey(), entry.getValue())).toList();
    }
}
