/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.search;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Searches a network by id/name across substations, voltage levels, buses, generators, shunt compensators, static
 * var compensators, loads, lines, transformers, tie lines, boundary lines and busbar sections, ranked by best
 * match via a {@link NetworkSearchIndex} built for the call. Shared by the substations, generators, shunt
 * compensators, static var compensators, loads, lines, transformers, tie lines, boundary lines, buses and busbar
 * sections views; each restricts matches to the equipment kinds relevant to it via the {@code kinds} parameter
 * (substations/voltage levels stay searchable everywhere so a container match can still be revealed). {@link Kind#BUS}
 * matches are the bus/branch view's merged buses ({@link VoltageLevel#getBusView()}); {@link Kind#CONFIGURED_BUS}
 * matches are the bus/breaker view's own buses ({@link VoltageLevel#getBusBreakerView()}) of a BUS_BREAKER-topology
 * voltage level - the two are indexed and searched independently, since only BUS_BREAKER voltage levels have a
 * configured bus set distinct from their (possibly merged) bus view. Add more equipment types here as they become
 * searchable.
 * <p>
 * This entry point builds an uncached, throwaway {@link NetworkSearchIndex} per call; {@link SearchBoxController}
 * queries a shared, per-network cached index instead (see {@code MainModel.getSearchIndex}) so repeated searches
 * don't rebuild it.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkSearch {

    public enum Kind {
        SUBSTATION, VOLTAGE_LEVEL, BUS, CONFIGURED_BUS, GENERATOR, SHUNT_COMPENSATOR, STATIC_VAR_COMPENSATOR, LOAD, LINE,
        TRANSFORMER, TIE_LINE, BOUNDARY_LINE, BUSBAR_SECTION
    }

    public static final Set<Kind> ALL_KINDS = EnumSet.allOf(Kind.class);
    private static final Set<Kind> EQUIPMENT_KINDS = EnumSet.of(Kind.BUS, Kind.CONFIGURED_BUS, Kind.GENERATOR,
            Kind.SHUNT_COMPENSATOR, Kind.STATIC_VAR_COMPENSATOR, Kind.LOAD, Kind.LINE, Kind.TRANSFORMER, Kind.TIE_LINE,
            Kind.BOUNDARY_LINE, Kind.BUSBAR_SECTION);

    private NetworkSearch() {
    }

    public static List<Identifiable<?>> search(Network network, String query) {
        return search(network, query, ALL_KINDS);
    }

    public static List<Identifiable<?>> search(Network network, String query, Set<Kind> kinds) {
        if (network == null || query == null || query.isBlank()) {
            return List.of();
        }
        try (NetworkSearchIndex index = NetworkSearchIndex.build(network)) {
            return index.search(query, kinds).matches();
        }
    }

    /**
     * A substation/voltage-level match is only useful to a view if it has something of a searched kind to show for
     * it, unless every equipment kind is being searched (the substations view's full tree, which shows every
     * container regardless of its equipment).
     */
    static boolean hasMatchingEquipment(Substation substation, Set<Kind> kinds) {
        return kinds.containsAll(EQUIPMENT_KINDS)
                || substation.getVoltageLevelStream().anyMatch(vl -> hasMatchingEquipment(vl, kinds));
    }

    static boolean hasMatchingEquipment(VoltageLevel voltageLevel, Set<Kind> kinds) {
        return kinds.containsAll(EQUIPMENT_KINDS)
                || kinds.contains(Kind.BUS) && voltageLevel.getBusView().getBusStream().findAny().isPresent()
                || kinds.contains(Kind.CONFIGURED_BUS) && voltageLevel.getTopologyKind() == TopologyKind.BUS_BREAKER
                        && voltageLevel.getBusBreakerView().getBusStream().findAny().isPresent()
                || kinds.contains(Kind.GENERATOR) && voltageLevel.getGeneratorCount() > 0
                || kinds.contains(Kind.SHUNT_COMPENSATOR) && voltageLevel.getShuntCompensatorCount() > 0
                || kinds.contains(Kind.STATIC_VAR_COMPENSATOR) && voltageLevel.getStaticVarCompensatorCount() > 0
                || kinds.contains(Kind.LOAD) && voltageLevel.getLoadCount() > 0
                || kinds.contains(Kind.LINE) && voltageLevel.getConnectableCount(Line.class) > 0
                || kinds.contains(Kind.TRANSFORMER) && (voltageLevel.getConnectableCount(TwoWindingsTransformer.class) > 0
                        || voltageLevel.getConnectableCount(ThreeWindingsTransformer.class) > 0)
                || kinds.contains(Kind.TIE_LINE) && voltageLevel.getConnectableStream(BoundaryLine.class).anyMatch(BoundaryLine::isPaired)
                || kinds.contains(Kind.BOUNDARY_LINE) && voltageLevel.getBoundaryLineCount() > 0
                || kinds.contains(Kind.BUSBAR_SECTION) && voltageLevel.getConnectableCount(BusbarSection.class) > 0;
    }

    public static Kind kindOf(Identifiable<?> identifiable) {
        if (identifiable instanceof Substation) {
            return Kind.SUBSTATION;
        }
        if (identifiable instanceof VoltageLevel) {
            return Kind.VOLTAGE_LEVEL;
        }
        if (identifiable instanceof Bus) {
            // a bus-view bus and a configured (bus-breaker-view) bus are both plain Bus instances - not
            // distinguishable by type, only by which stream produced them (see NetworkSearchIndex); this
            // classifies either as the more general Kind.BUS, which is fine for display purposes
            return Kind.BUS;
        }
        if (identifiable instanceof Generator) {
            return Kind.GENERATOR;
        }
        if (identifiable instanceof ShuntCompensator) {
            return Kind.SHUNT_COMPENSATOR;
        }
        if (identifiable instanceof StaticVarCompensator) {
            return Kind.STATIC_VAR_COMPENSATOR;
        }
        if (identifiable instanceof Load) {
            return Kind.LOAD;
        }
        if (identifiable instanceof Line) {
            return Kind.LINE;
        }
        if (identifiable instanceof TwoWindingsTransformer || identifiable instanceof ThreeWindingsTransformer) {
            return Kind.TRANSFORMER;
        }
        if (identifiable instanceof TieLine) {
            return Kind.TIE_LINE;
        }
        if (identifiable instanceof BoundaryLine) {
            return Kind.BOUNDARY_LINE;
        }
        if (identifiable instanceof BusbarSection) {
            return Kind.BUSBAR_SECTION;
        }
        throw new IllegalArgumentException("Unsupported searchable type: " + identifiable.getClass());
    }

    /**
     * The substation/voltage-level container to reveal for a match, for views that don't display the matched
     * item itself (e.g. a generator match in the substations tree, revealed as its voltage level).
     */
    public static Container<?> containerOf(Identifiable<?> identifiable) {
        if (identifiable instanceof Container<?> container) {
            return container;
        }
        if (identifiable instanceof Bus bus) {
            return bus.getVoltageLevel();
        }
        if (identifiable instanceof Injection<?> injection) {
            return injection.getTerminal().getVoltageLevel();
        }
        if (identifiable instanceof Line line) {
            return line.getTerminal1().getVoltageLevel();
        }
        if (identifiable instanceof TwoWindingsTransformer transformer) {
            return transformer.getTerminal1().getVoltageLevel();
        }
        if (identifiable instanceof ThreeWindingsTransformer transformer) {
            return transformer.getLeg1().getTerminal().getVoltageLevel();
        }
        if (identifiable instanceof TieLine tieLine) {
            return tieLine.getTerminal1().getVoltageLevel();
        }
        throw new IllegalArgumentException("Unsupported searchable type: " + identifiable.getClass());
    }
}
