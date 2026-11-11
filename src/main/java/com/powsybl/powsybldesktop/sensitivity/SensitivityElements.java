/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.IdBasedBusRef;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.RatioTapChanger;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TopologyLevel;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.iidm.network.extensions.ControlZone;
import com.powsybl.iidm.network.extensions.HvdcAngleDroopActivePowerControl;
import com.powsybl.iidm.network.extensions.SecondaryVoltageControl;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableSet;
import com.powsybl.sensitivity.SensitivityVariableType;
import com.powsybl.sensitivity.WeightedSensitivityVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The network elements a sensitivity function or variable can be, as Open Load Flow 2.4.0 looks them up
 * ({@code AbstractSensitivityAnalysis.readAndCheck} and the lookups it calls, after PowSyBl's
 * {@code SensitivityFactor.resolveBusId}); {@code SensitivityElementsTest} checks them against Open Load Flow: {@link #candidates} offers the ones
 * that make sense for a type, {@link #isEligible} accepts whatever Open Load Flow does not fail on (e.g. a bus
 * breaker view bus as an injection). Elements Open Load Flow merely skips (NaN values), such as an unknown branch
 * parameter variable or pilot point zone, are refused too: such a factor is surely a mistake.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SensitivityElements {

    // shown at most per kind of problem, then "and N more"
    private static final int MAX_LISTED = 5;

    /**
     * An element to pick: its id, displayed name and kind.
     */
    public record Candidate(String id, String name, String kind) {
    }

    private SensitivityElements() {
    }

    public static List<Candidate> candidates(Network network, SensitivityFunctionType type) {
        return switch (type) {
            case BRANCH_ACTIVE_POWER_3, BRANCH_CURRENT_3, BRANCH_REACTIVE_POWER_3 -> identifiables(network.getThreeWindingsTransformerStream());
            case BRANCH_ACTIVE_POWER_1, BRANCH_ACTIVE_POWER_2, BRANCH_CURRENT_1, BRANCH_CURRENT_2, BRANCH_REACTIVE_POWER_1, BRANCH_REACTIVE_POWER_2 -> identifiables(Stream.of(
                    network.getLineStream(), network.getTieLineStream(), network.getTwoWindingsTransformerStream(),
                    network.getThreeWindingsTransformerStream(), network.getBoundaryLineStream().filter(line -> !line.isPaired())).flatMap(s -> s));
            case BUS_VOLTAGE -> identifiables(network.getBusView().getBusStream());
            case BUS_REACTIVE_POWER -> injections(network, true);
        };
    }

    public static List<Candidate> candidates(Network network, SensitivityVariableType type) {
        return switch (type) {
            case INJECTION_ACTIVE_POWER -> injections(network, false);
            case INJECTION_REACTIVE_POWER -> injections(network, true);
            case SHUNT_COMPENSATOR_SUSCEPTANCE -> identifiables(network.getShuntCompensatorStream());
            case TRANSFORMER_PHASE -> identifiables(network.getTwoWindingsTransformerStream().filter(t -> t.getPhaseTapChanger() != null));
            case TRANSFORMER_PHASE_1, TRANSFORMER_PHASE_2, TRANSFORMER_PHASE_3 -> identifiables(network.getThreeWindingsTransformerStream()
                    .filter(t -> t.getLegs().get(leg(type)).getPhaseTapChanger() != null));
            case BUS_TARGET_VOLTAGE -> identifiables(Stream.of(network.getGeneratorStream(), network.getBatteryStream(), network.getShuntCompensatorStream(),
                    network.getStaticVarCompensatorStream(), network.getVscConverterStationStream(),
                    network.getTwoWindingsTransformerStream(), network.getThreeWindingsTransformerStream()).flatMap(s -> s)
                    .filter(identifiable -> isEligible(network, type, identifiable.getId())));
            case HVDC_LINE_ACTIVE_POWER -> identifiables(network.getHvdcLineStream());
            case BRANCH_RESISTANCE, BRANCH_REACTANCE, BRANCH_ADMITTANCE -> identifiables(Stream.of(
                    network.getLineStream(), network.getTieLineStream(), network.getTwoWindingsTransformerStream()).flatMap(s -> s));
            case SVC_PILOT_POINT_TARGET_VOLTAGE -> controlZones(network).stream()
                    .map(zone -> new Candidate(zone.getName(), zone.getName(), Messages.get("sensitivity.element.controlZone"))).toList();
        };
    }

    /**
     * The injections a variable set is made of.
     */
    public static List<Candidate> variableSetCandidates(Network network) {
        return injections(network, false);
    }

    public static boolean isEligible(Network network, SensitivityFunctionType type, String id) {
        return switch (type) {
            // SensitivityFactor.resolveBusId, before Open Load Flow sees it: a bus of the bus view, or what resolves to one
            case BUS_VOLTAGE -> network.getBusView().getBus(id) != null || resolvesToBus(network, id);
            case BUS_REACTIVE_POWER -> isInjection(network, id);
            default -> {
                BoundaryLine boundaryLine = network.getBoundaryLine(id);
                if (boundaryLine != null && boundaryLine.isPaired()) {
                    // computed on its tie line's side
                    yield type.getSide().orElseThrow() == 1;
                }
                yield network.getBranch(id) != null || boundaryLine != null || network.getThreeWindingsTransformer(id) != null;
            }
        };
    }

    public static boolean isEligible(Network network, SensitivityVariableType type, String id) {
        return switch (type) {
            case INJECTION_ACTIVE_POWER, INJECTION_REACTIVE_POWER, SHUNT_COMPENSATOR_SUSCEPTANCE -> isInjection(network, id);
            case TRANSFORMER_PHASE -> {
                TwoWindingsTransformer transformer = network.getTwoWindingsTransformer(id);
                yield transformer != null && transformer.getPhaseTapChanger() != null;
            }
            case TRANSFORMER_PHASE_1, TRANSFORMER_PHASE_2, TRANSFORMER_PHASE_3 -> {
                ThreeWindingsTransformer transformer = network.getThreeWindingsTransformer(id);
                yield transformer != null && transformer.getLegs().get(leg(type)).getPhaseTapChanger() != null;
            }
            case BUS_TARGET_VOLTAGE -> hasRegulatingTerminal(network.getIdentifiable(id));
            case HVDC_LINE_ACTIVE_POWER -> network.getHvdcLine(id) != null;
            case BRANCH_RESISTANCE, BRANCH_REACTANCE, BRANCH_ADMITTANCE -> network.getBranch(id) != null;
            case SVC_PILOT_POINT_TARGET_VOLTAGE -> controlZones(network).stream().anyMatch(zone -> zone.getName().equals(id));
        };
    }

    /**
     * Why Open Load Flow would fail on, or skip, the elements of {@code matrix}: empty when they are all fine.
     * {@code hvdcAcEmulation} is the load flow parameter of the same name.
     */
    public static List<String> problems(Network network, SensitivityFactorMatrix matrix, List<SensitivityVariableSet> variableSets,
                                        List<String> contingencyIds, boolean hvdcAcEmulation) {
        List<String> problems = new ArrayList<>();
        addProblem(problems, "sensitivity.check.elements", SensitivityTexts.function(matrix.functionType()),
                matrix.functionIds(), id -> !isEligible(network, matrix.functionType(), id));
        if (matrix.variableSet()) {
            List<String> variableSetIds = variableSets.stream().map(SensitivityVariableSet::getId).toList();
            addProblem(problems, "sensitivity.check.variableSets", null, matrix.variableIds(), id -> !variableSetIds.contains(id));
        } else {
            addProblem(problems, "sensitivity.check.elements", SensitivityTexts.variable(matrix.variableType()),
                    matrix.variableIds(), id -> !isEligible(network, matrix.variableType(), id));
            if (matrix.variableType() == SensitivityVariableType.HVDC_LINE_ACTIVE_POWER && hvdcAcEmulation) {
                addProblem(problems, "sensitivity.check.hvdcAcEmulation", null, matrix.variableIds(), id -> isInAcEmulation(network.getHvdcLine(id)));
            }
        }
        String contingencyId = matrix.contingencyContext().getContingencyId();
        if (contingencyId != null && !contingencyIds.contains(contingencyId)) {
            problems.add(Messages.get("sensitivity.check.contingency", contingencyId));
        }
        return problems;
    }

    /**
     * Why Open Load Flow would fail on {@code variableSet}: empty when all its variables are injections.
     */
    public static List<String> problems(Network network, SensitivityVariableSet variableSet) {
        List<String> problems = new ArrayList<>();
        addProblem(problems, "sensitivity.check.variableSetVariables", null,
                variableSet.getVariables().stream().map(WeightedSensitivityVariable::getId).toList(), id -> !isInjection(network, id));
        return problems;
    }

    private static void addProblem(List<String> problems, String key, String typeLabel, List<String> ids, Predicate<String> wrong) {
        List<String> wrongIds = ids.stream().filter(wrong).toList();
        if (wrongIds.isEmpty()) {
            return;
        }
        String listed = String.join(", ", wrongIds.subList(0, Math.min(MAX_LISTED, wrongIds.size())));
        if (wrongIds.size() > MAX_LISTED) {
            listed = Messages.get("sensitivity.check.more", listed, wrongIds.size() - MAX_LISTED);
        }
        problems.add(typeLabel == null ? Messages.get(key, listed) : Messages.get(key, listed, typeLabel));
    }

    private static boolean resolvesToBus(Network network, String id) {
        try {
            return new IdBasedBusRef(id).resolve(network, TopologyLevel.BUS_BRANCH).isPresent();
        } catch (PowsyblException _) {
            // neither a bus nor an injection
            return false;
        }
    }

    // SensitivityFactorHolder.getInjectionBusId: an injection, a bus breaker view bus or a busbar section
    private static boolean isInjection(Network network, String id) {
        BoundaryLine boundaryLine = network.getBoundaryLine(id);
        if (boundaryLine != null) {
            return !boundaryLine.isPaired();
        }
        return network.getGenerator(id) != null || network.getLoad(id) != null || network.getLccConverterStation(id) != null
                || network.getVscConverterStation(id) != null || network.getBattery(id) != null || network.getShuntCompensator(id) != null
                || network.getBusBreakerView().getBus(id) != null || network.getBusbarSection(id) != null;
    }

    // Networks.getEquipmentRegulatingTerminal
    private static boolean hasRegulatingTerminal(Identifiable<?> identifiable) {
        if (identifiable == null) {
            return false;
        }
        return switch (identifiable.getType()) {
            case TWO_WINDINGS_TRANSFORMER -> isRegulating(((TwoWindingsTransformer) identifiable).getRatioTapChanger());
            case THREE_WINDINGS_TRANSFORMER -> ((ThreeWindingsTransformer) identifiable).getLegs().stream().anyMatch(leg -> isRegulating(leg.getRatioTapChanger()));
            case GENERATOR, SHUNT_COMPENSATOR, STATIC_VAR_COMPENSATOR -> true;
            case BATTERY -> ((Battery) identifiable).getRegulatingTerminal() != null;
            case HVDC_CONVERTER_STATION -> identifiable instanceof VscConverterStation;
            default -> false;
        };
    }

    private static boolean isRegulating(RatioTapChanger tapChanger) {
        return tapChanger != null && tapChanger.isRegulating() && tapChanger.getRegulationTerminal() != null;
    }

    private static boolean isInAcEmulation(HvdcLine hvdcLine) {
        HvdcAngleDroopActivePowerControl control = hvdcLine == null ? null : hvdcLine.getExtension(HvdcAngleDroopActivePowerControl.class);
        return control != null && control.isEnabled();
    }

    private static List<ControlZone> controlZones(Network network) {
        SecondaryVoltageControl control = network.getExtension(SecondaryVoltageControl.class);
        return control == null ? List.of() : control.getControlZones();
    }

    private static int leg(SensitivityVariableType type) {
        return type.getSide().orElseThrow() - 1;
    }

    private static List<Candidate> injections(Network network, boolean withShunts) {
        return identifiables(Stream.of(network.getGeneratorStream(), network.getLoadStream(), network.getBatteryStream(),
                        network.getVscConverterStationStream(), network.getLccConverterStationStream(),
                        network.getBoundaryLineStream().filter(line -> !line.isPaired()),
                        withShunts ? network.getShuntCompensatorStream() : Stream.<Identifiable<?>>empty())
                .flatMap(s -> s));
    }

    private static List<Candidate> identifiables(Stream<? extends Identifiable<?>> identifiables) {
        return identifiables.map(identifiable -> new Candidate(identifiable.getId(), identifiable.getNameOrId(), kind(identifiable))).toList();
    }

    private static String kind(Identifiable<?> identifiable) {
        String key = switch (identifiable.getType()) {
            case LINE -> "line";
            case TIE_LINE -> "tieLine";
            case TWO_WINDINGS_TRANSFORMER -> "twoWindingsTransformer";
            case THREE_WINDINGS_TRANSFORMER -> "threeWindingsTransformer";
            case BOUNDARY_LINE -> "boundaryLine";
            case GENERATOR -> "generator";
            case LOAD -> "load";
            case BATTERY -> "battery";
            case SHUNT_COMPENSATOR -> "shuntCompensator";
            case STATIC_VAR_COMPENSATOR -> "staticVarCompensator";
            case HVDC_CONVERTER_STATION -> identifiable instanceof VscConverterStation ? "vscConverterStation" : "lccConverterStation";
            case HVDC_LINE -> "hvdcLine";
            case BUS -> "bus";
            default -> null;
        };
        return key == null ? Objects.toString(identifiable.getType()) : Messages.get("desktop.common.equipment." + key);
    }
}
