/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.action.Action;
import com.powsybl.action.BoundaryLineAction;
import com.powsybl.action.GeneratorAction;
import com.powsybl.action.HvdcAction;
import com.powsybl.action.LoadAction;
import com.powsybl.action.PhaseTapChangerTapPositionAction;
import com.powsybl.action.ShuntCompensatorPositionAction;
import com.powsybl.action.SwitchAction;
import com.powsybl.action.TerminalsConnectionAction;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.RemedialActionAdder;
import com.powsybl.openrao.data.crac.api.networkaction.ActionType;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkActionAdder;
import com.powsybl.openrao.data.crac.api.networkaction.SwitchPair;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The edits of a CRAC's network actions, made of the elementary actions OpenRAO's adders make: a switch or terminals
 * connection opened or closed, a PST's tap, a generator's, load's or boundary line's active power set point, a shunt
 * compensator's section count, a switch pair, an HVDC line's AC emulation deactivated. OpenRAO's network actions are
 * immutable: a change replaces the action by a new one, with the same usage rules. The elements are checked against the
 * network when it has them; one it lacks (the CRAC made for another network) is kept, the view marking it.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkActionEdits {

    private NetworkActionEdits() {
    }

    /**
     * The kinds of elementary actions, with their code in the table of a network action's actions.
     */
    public enum Kind {
        SWITCH("switch"),
        TERMINALS("terminals"),
        PST_TAP("pstTap"),
        GENERATOR("generator"),
        LOAD("load"),
        BOUNDARY_LINE("boundaryLine"),
        SHUNT("shunt"),
        SWITCH_PAIR("switchPair"),
        AC_EMULATION_OFF("acEmulationOff");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public String label() {
            return Messages.get("networkActionEdits.kind." + code);
        }

        static Optional<Kind> of(String text) {
            return Stream.of(values()).filter(kind -> kind.code.equalsIgnoreCase(text) || kind.label().equalsIgnoreCase(text)).findFirst();
        }
    }

    /**
     * An elementary action on {@code elementId}, of {@code kind}: {@code open} for a switch or terminals connection,
     * {@code value} the tap, set point (MW) or section count, {@code otherElementId} a switch pair's switch to close (the
     * element the one to open). {@link Kind}-specific factories make them.
     */
    public record ElementaryActionSpec(Kind kind, String elementId, boolean open, double value, String otherElementId) {

        public ElementaryActionSpec {
            Objects.requireNonNull(kind);
            elementId = elementId == null ? "" : elementId.trim();
            otherElementId = otherElementId == null ? null : otherElementId.trim();
        }

        public static ElementaryActionSpec switchAction(String switchId, boolean open) {
            return new ElementaryActionSpec(Kind.SWITCH, switchId, open, 0, null);
        }

        public static ElementaryActionSpec terminals(String elementId, boolean open) {
            return new ElementaryActionSpec(Kind.TERMINALS, elementId, open, 0, null);
        }

        public static ElementaryActionSpec pstTap(String transformerId, int tap) {
            return new ElementaryActionSpec(Kind.PST_TAP, transformerId, false, tap, null);
        }

        public static ElementaryActionSpec setPoint(Kind kind, String elementId, double activePower) {
            if (kind != Kind.GENERATOR && kind != Kind.LOAD && kind != Kind.BOUNDARY_LINE) {
                throw new IllegalArgumentException(kind.name());
            }
            return new ElementaryActionSpec(kind, elementId, false, activePower, null);
        }

        public static ElementaryActionSpec shunt(String shuntId, int sectionCount) {
            return new ElementaryActionSpec(Kind.SHUNT, shuntId, false, sectionCount, null);
        }

        public static ElementaryActionSpec switchPair(String switchToOpenId, String switchToCloseId) {
            return new ElementaryActionSpec(Kind.SWITCH_PAIR, switchToOpenId, false, 0, switchToCloseId);
        }

        public static ElementaryActionSpec acEmulationOff(String hvdcLineId) {
            return new ElementaryActionSpec(Kind.AC_EMULATION_OFF, hvdcLineId, false, 0, null);
        }

        /**
         * The ids of the network elements it acts on.
         */
        public List<String> elementIds() {
            return otherElementId == null ? List.of(elementId) : List.of(elementId, otherElementId);
        }
    }

    /**
     * What a network action is made of, its usage rules apart. A blank name is the id; a blank operator is none.
     */
    public record NetworkActionSpec(String id, String name, String operator, Integer speed, Double activationCost, List<ElementaryActionSpec> actions) {

        public NetworkActionSpec {
            id = id == null ? "" : id.trim();
            name = name == null || name.isBlank() ? id : name.trim();
            operator = operator == null || operator.isBlank() ? null : operator.trim();
            actions = actions.stream().sorted(ACTION_ORDER).toList();
        }

        /**
         * {@code action}'s, or empty when one of its elementary actions is none OpenRAO's adders make.
         */
        public static Optional<NetworkActionSpec> of(NetworkAction action) {
            List<ElementaryActionSpec> actions = new ArrayList<>();
            for (Action elementary : action.getElementaryActions()) {
                Optional<ElementaryActionSpec> spec = elementary(elementary);
                if (spec.isEmpty()) {
                    return Optional.empty();
                }
                actions.add(spec.get());
            }
            return Optional.of(new NetworkActionSpec(action.getId(), action.getName(), action.getOperator(), action.getSpeed().orElse(null),
                    action.getActivationCost().orElse(null), actions));
        }
    }

    private static final Comparator<ElementaryActionSpec> ACTION_ORDER = Comparator.comparing(ElementaryActionSpec::kind)
            .thenComparing(ElementaryActionSpec::elementId).thenComparing(ElementaryActionSpec::otherElementId, Comparator.nullsFirst(Comparator.naturalOrder()));

    // the absolute values OpenRAO's adders set
    private static Optional<ElementaryActionSpec> elementary(Action action) {
        return Optional.ofNullable(switch (action) {
            case SwitchPair pair -> ElementaryActionSpec.switchPair(pair.getSwitchToOpenId(), pair.getSwitchToCloseId());
            case SwitchAction switchAction -> ElementaryActionSpec.switchAction(switchAction.getSwitchId(), switchAction.isOpen());
            case TerminalsConnectionAction terminals when terminals.getSide().isEmpty() -> ElementaryActionSpec.terminals(terminals.getElementId(), terminals.isOpen());
            case PhaseTapChangerTapPositionAction tap when !tap.isRelativeValue() && tap.getSide().isEmpty() -> ElementaryActionSpec.pstTap(tap.getTransformerId(), tap.getTapPosition());
            case GeneratorAction generator -> setPoint(Kind.GENERATOR, generator.getGeneratorId(), generator.getActivePowerValue(),
                    generator.isActivePowerRelativeValue().orElse(false));
            case LoadAction load -> setPoint(Kind.LOAD, load.getLoadId(), load.getActivePowerValue(), load.isRelativeValue());
            case BoundaryLineAction line -> setPoint(Kind.BOUNDARY_LINE, line.getBoundaryLineId(), line.getActivePowerValue(), line.isRelativeValue());
            case ShuntCompensatorPositionAction shunt -> ElementaryActionSpec.shunt(shunt.getShuntCompensatorId(), shunt.getSectionCount());
            case HvdcAction hvdc when hvdc.isAcEmulationEnabled().filter(enabled -> !enabled).isPresent() -> ElementaryActionSpec.acEmulationOff(hvdc.getHvdcId());
            default -> null;
        });
    }

    private static ElementaryActionSpec setPoint(Kind kind, String elementId, OptionalDouble activePower, boolean relative) {
        return activePower.isPresent() && !relative ? ElementaryActionSpec.setPoint(kind, elementId, activePower.getAsDouble()) : null;
    }

    // ----- creation -----

    /**
     * The network's objects a network action can act on: switches, branches, three-winding transformers, injections,
     * unpaired boundary lines and HVDC lines.
     */
    public static Stream<Identifiable<?>> elementCandidates(Network network) {
        return Stream.<Stream<? extends Identifiable<?>>>of(network.getSwitchStream(), network.getLineStream(), network.getTieLineStream(),
                network.getTwoWindingsTransformerStream(), network.getThreeWindingsTransformerStream(), network.getGeneratorStream(), network.getLoadStream(),
                network.getBatteryStream(), network.getShuntCompensatorStream(), network.getStaticVarCompensatorStream(),
                network.getBoundaryLineStream().filter(line -> !line.isPaired()), network.getHvdcLineStream()).flatMap(stream -> stream.map(identifiable -> identifiable));
    }

    /**
     * The elementary action on {@code identifiable} a new network action starts with: a switch toggled, a PST at its
     * current tap, a generator, load or boundary line at its current set point, a shunt compensator's sections
     * toggled (all or none), an HVDC line's AC emulation deactivated, another element's terminals toggled.
     */
    public static Optional<ElementaryActionSpec> defaultAction(Identifiable<?> identifiable) {
        return Optional.ofNullable(switch (identifiable) {
            case Switch switchElement -> ElementaryActionSpec.switchAction(switchElement.getId(), !switchElement.isOpen());
            case TwoWindingsTransformer pst when pst.getPhaseTapChanger() != null -> ElementaryActionSpec.pstTap(pst.getId(), pst.getPhaseTapChanger().getTapPosition());
            case Generator generator -> ElementaryActionSpec.setPoint(Kind.GENERATOR, generator.getId(), generator.getTargetP());
            case Load load -> ElementaryActionSpec.setPoint(Kind.LOAD, load.getId(), load.getP0());
            case BoundaryLine line -> ElementaryActionSpec.setPoint(Kind.BOUNDARY_LINE, line.getId(), line.getP0());
            case ShuntCompensator shunt -> ElementaryActionSpec.shunt(shunt.getId(), shunt.getSectionCount() == 0 ? shunt.getMaximumSectionCount() : 0);
            case HvdcLine line -> ElementaryActionSpec.acEmulationOff(line.getId());
            case Connectable<?> connectable -> ElementaryActionSpec.terminals(connectable.getId(),
                    connectable.getTerminals().stream().allMatch(Terminal::isConnected));
            default -> null;
        });
    }

    /**
     * An id not used by any remedial action of {@code crac}: {@code prefix} and a number.
     */
    public static String newId(Crac crac, String prefix) {
        int i = 1;
        while (crac.getRemedialAction(prefix + " " + i) != null) {
            i++;
        }
        return prefix + " " + i;
    }

    /**
     * Adds a network action, available as {@code usageRules} tell.
     */
    public static NetworkAction add(Crac crac, Network network, NetworkActionSpec spec, Collection<UsageRule> usageRules) {
        if (spec.id().isEmpty()) {
            throw new IllegalArgumentException(Messages.get("cracEdits.idRequired"));
        }
        if (crac.getRemedialAction(spec.id()) != null) {
            throw new IllegalArgumentException(Messages.get("networkActionEdits.exists", spec.id()));
        }
        if (spec.actions().isEmpty()) {
            throw new IllegalArgumentException(Messages.get("networkActionEdits.noAction"));
        }
        Set<String> elements = new HashSet<>();
        for (ElementaryActionSpec action : spec.actions()) {
            check(network, action);
            for (String element : action.elementIds()) {
                if (!elements.add(element)) {
                    throw new IllegalArgumentException(Messages.get("networkActionEdits.twice", element));
                }
            }
        }
        NetworkActionAdder adder = crac.newNetworkAction().withId(spec.id()).withName(spec.name()).withOperator(spec.operator())
                .withSpeed(spec.speed()).withActivationCost(spec.activationCost());
        for (ElementaryActionSpec action : spec.actions()) {
            addTo(adder, action);
        }
        copyUsageRules(adder, usageRules);
        try {
            return adder.add();
        } catch (OpenRaoException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static void check(Network network, ElementaryActionSpec action) {
        if (action.elementId().isEmpty() || action.kind() == Kind.SWITCH_PAIR && (action.otherElementId() == null || action.otherElementId().isEmpty())) {
            throw new IllegalArgumentException(Messages.get("networkActionEdits.elementRequired", action.kind().label()));
        }
        if (action.kind() == Kind.SWITCH_PAIR && action.elementId().equals(action.otherElementId())) {
            throw new IllegalArgumentException(Messages.get("networkActionEdits.samePair", action.elementId()));
        }
        if (action.kind() == Kind.SHUNT && action.value() < 0) {
            throw new IllegalArgumentException(Messages.get("networkActionEdits.negativeSections", action.elementId()));
        }
        for (String elementId : action.elementIds()) {
            Identifiable<?> element = network.getIdentifiable(elementId);
            // missing from the network: kept, the view marking it
            if (element != null && !fits(action.kind(), element)) {
                throw new IllegalArgumentException(Messages.get("networkActionEdits.wrongElement", elementId, action.kind().label()));
            }
        }
        if (action.kind() == Kind.PST_TAP && network.getIdentifiable(action.elementId()) instanceof TwoWindingsTransformer transformer) {
            PhaseTapChanger tapChanger = transformer.getPhaseTapChanger();
            if (action.value() < tapChanger.getLowTapPosition() || action.value() > tapChanger.getHighTapPosition()) {
                throw new IllegalArgumentException(Messages.get("networkActionEdits.tapOutOfRange", (int) action.value(), action.elementId(),
                        tapChanger.getLowTapPosition(), tapChanger.getHighTapPosition()));
            }
        }
        if (action.kind() == Kind.SHUNT && network.getIdentifiable(action.elementId()) instanceof ShuntCompensator shunt
                && action.value() > shunt.getMaximumSectionCount()) {
            throw new IllegalArgumentException(Messages.get("networkActionEdits.tooManySections", (int) action.value(), action.elementId(),
                    shunt.getMaximumSectionCount()));
        }
    }

    private static boolean fits(Kind kind, Identifiable<?> element) {
        return switch (kind) {
            case SWITCH, SWITCH_PAIR -> element instanceof Switch;
            case TERMINALS -> element instanceof Connectable<?>;
            case PST_TAP -> element instanceof TwoWindingsTransformer transformer && transformer.getPhaseTapChanger() != null;
            case GENERATOR -> element instanceof Generator;
            case LOAD -> element instanceof Load;
            case BOUNDARY_LINE -> element instanceof BoundaryLine;
            case SHUNT -> element instanceof ShuntCompensator;
            case AC_EMULATION_OFF -> element instanceof HvdcLine;
        };
    }

    private static void addTo(NetworkActionAdder adder, ElementaryActionSpec action) {
        String element = action.elementId();
        ActionType type = action.open() ? ActionType.OPEN : ActionType.CLOSE;
        switch (action.kind()) {
            case SWITCH -> adder.newSwitchAction().withNetworkElement(element).withActionType(type).add();
            case TERMINALS -> adder.newTerminalsConnectionAction().withNetworkElement(element).withActionType(type).add();
            case PST_TAP -> adder.newPhaseTapChangerTapPositionAction().withNetworkElement(element).withTapPosition((int) action.value()).add();
            case GENERATOR -> adder.newGeneratorAction().withNetworkElement(element).withActivePowerValue(action.value()).add();
            case LOAD -> adder.newLoadAction().withNetworkElement(element).withActivePowerValue(action.value()).add();
            case BOUNDARY_LINE -> adder.newBoundaryLineAction().withNetworkElement(element).withActivePowerValue(action.value()).add();
            case SHUNT -> adder.newShuntCompensatorPositionAction().withNetworkElement(element).withSectionCount((int) action.value()).add();
            case SWITCH_PAIR -> adder.newSwitchPair().withSwitchToOpen(element).withSwitchToClose(action.otherElementId()).add();
            case AC_EMULATION_OFF -> adder.newAcEmulationDeactivationAction().withNetworkElement(element).add();
        }
    }

    /**
     * Adds to {@code adder} usage rules like {@code usageRules}, those of another remedial action of the same CRAC.
     */
    public static <T extends RemedialActionAdder<T>> void copyUsageRules(T adder, Collection<UsageRule> usageRules) {
        for (UsageRule rule : usageRules) {
            String instant = rule.getInstant().getId();
            switch (rule) {
                case OnContingencyState onState -> adder.newOnContingencyStateUsageRule().withContingency(onState.getContingency().getId()).withInstant(instant).add();
                case OnConstraint<?> onConstraint -> adder.newOnConstraintUsageRule().withCnec(onConstraint.getCnec().getId()).withInstant(instant).add();
                case OnFlowConstraintInCountry onCountry -> {
                    OnFlowConstraintInCountryAdder<T> ruleAdder = adder.newOnFlowConstraintInCountryUsageRule().withCountry(onCountry.getCountry()).withInstant(instant);
                    onCountry.getContingency().ifPresent(contingency -> ruleAdder.withContingency(contingency.getId()));
                    ruleAdder.add();
                }
                case OnInstant _ -> adder.newOnInstantUsageRule().withInstant(instant).add();
                default -> throw new IllegalArgumentException(rule.getClass().getSimpleName());
            }
        }
    }

    // ----- change, removal -----

    /**
     * Replaces {@code action} by one as {@code spec} tells, with the same usage rules, putting it back when the new one is
     * refused; {@code action} itself when nothing changes.
     */
    public static NetworkAction change(Crac crac, Network network, NetworkAction action, NetworkActionSpec spec) {
        Optional<NetworkActionSpec> current = NetworkActionSpec.of(action);
        if (current.isEmpty()) {
            throw new IllegalArgumentException(Messages.get("networkActionEdits.unsupported", action.getId()));
        }
        if (current.get().equals(spec)) {
            return action;
        }
        if (!spec.id().equals(action.getId()) && crac.getRemedialAction(spec.id()) != null) {
            throw new IllegalArgumentException(Messages.get("networkActionEdits.exists", spec.id()));
        }
        List<UsageRule> usageRules = List.copyOf(action.getUsageRules());
        crac.removeNetworkAction(action.getId());
        try {
            return add(crac, network, spec, usageRules);
        } catch (IllegalArgumentException e) {
            // as it was: its elements are not checked again, the network may have changed since
            NetworkActionAdder adder = crac.newNetworkAction().withId(current.get().id()).withName(current.get().name())
                    .withOperator(current.get().operator()).withSpeed(current.get().speed()).withActivationCost(current.get().activationCost());
            current.get().actions().forEach(elementary -> addTo(adder, elementary));
            copyUsageRules(adder, usageRules);
            adder.add();
            throw e;
        }
    }

    public static void remove(Crac crac, String id) {
        crac.removeNetworkAction(id);
    }

    // ----- elementary actions as table rows: kind, element, value -----

    private static final String OPEN = "open";
    private static final String CLOSE = "close";

    public static List<List<String>> actionRows(List<ElementaryActionSpec> actions) {
        return actions.stream().map(action -> List.of(action.kind().code(), action.elementId(), switch (action.kind()) {
            case SWITCH, TERMINALS -> action.open() ? OPEN : CLOSE;
            case PST_TAP, SHUNT -> Integer.toString((int) action.value());
            case GENERATOR, LOAD, BOUNDARY_LINE -> TableRows.text(action.value());
            case SWITCH_PAIR -> action.otherElementId();
            case AC_EMULATION_OFF -> "";
        })).toList();
    }

    /**
     * The rows' elementary actions: the kind, by code or label; the element; the value: open or close, the tap, the set
     * point (MW), the section count, or a switch pair's switch to close (the element the one to open).
     */
    public static List<ElementaryActionSpec> parseActions(List<List<String>> rows) {
        List<ElementaryActionSpec> actions = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int rowIndex = i;
            String kindText = TableRows.required(row, 0, i);
            Kind kind = Kind.of(kindText).orElseThrow(() -> TableRows.invalid(rowIndex, Messages.get("networkActionEdits.unknownKind", kindText)));
            String element = TableRows.required(row, 1, i);
            actions.add(switch (kind) {
                case SWITCH, TERMINALS -> {
                    String value = TableRows.required(row, 2, i).toLowerCase(Locale.ROOT);
                    if (!value.equals(OPEN) && !value.equals(CLOSE)) {
                        throw TableRows.invalid(i, Messages.get("networkActionEdits.openOrClose", value));
                    }
                    yield kind == Kind.SWITCH ? ElementaryActionSpec.switchAction(element, value.equals(OPEN)) : ElementaryActionSpec.terminals(element, value.equals(OPEN));
                }
                case PST_TAP -> ElementaryActionSpec.pstTap(element, TableRows.requiredInteger(row, 2, i));
                case SHUNT -> ElementaryActionSpec.shunt(element, TableRows.requiredInteger(row, 2, i));
                case GENERATOR, LOAD, BOUNDARY_LINE -> ElementaryActionSpec.setPoint(kind, element, TableRows.requiredNumber(row, 2, i));
                case SWITCH_PAIR -> ElementaryActionSpec.switchPair(element, TableRows.required(row, 2, i));
                case AC_EMULATION_OFF -> ElementaryActionSpec.acEmulationOff(element);
            });
        }
        return actions;
    }
}
