/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.AreaAdder;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OverloadManagementSystem;
import com.powsybl.iidm.network.OverloadManagementSystemAdder;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.VoltageAngleLimit;
import com.powsybl.iidm.network.VoltageAngleLimitAdder;
import com.powsybl.iidm.network.VoltageLevel;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Creating the network objects that aren't equipment: areas, overload management systems, voltage angle limits.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkObjects {

    private NetworkObjects() {
    }

    /**
     * @param interchangeTarget none if NaN
     */
    public record AreaSpec(String id, String name, String areaType, double interchangeTarget, List<VoltageLevel> voltageLevels) {
    }

    public static Area addArea(Network network, AreaSpec spec) {
        AreaAdder adder = network.newArea().setId(spec.id()).setName(spec.name()).setAreaType(spec.areaType());
        if (!Double.isNaN(spec.interchangeTarget())) {
            adder.setInterchangeTarget(spec.interchangeTarget());
        }
        spec.voltageLevels().forEach(adder::addVoltageLevel);
        return adder.add();
    }

    /**
     * What an overload management system operates above {@code currentLimit}: a switch, or a side of a branch
     * ({@link TwoSides}) or of a three-winding transformer ({@link ThreeSides}).
     *
     * @param side null for a switch
     */
    public record Tripping(OverloadManagementSystem.Tripping.Type type, Identifiable<?> element, Object side, String key, double currentLimit,
                           boolean open) {
    }

    public record OverloadManagementSystemSpec(String id, String name, boolean enabled, Identifiable<?> monitoredElement, ThreeSides monitoredSide,
                                               List<Tripping> trippings) {
    }

    public static OverloadManagementSystem addOverloadManagementSystem(Substation substation, OverloadManagementSystemSpec spec) {
        OverloadManagementSystemAdder adder = substation.newOverloadManagementSystem().setId(spec.id()).setName(spec.name())
                .setEnabled(spec.enabled()).setMonitoredElementId(spec.monitoredElement().getId()).setMonitoredElementSide(spec.monitoredSide());
        spec.trippings().forEach(tripping -> addTripping(adder, tripping));
        return adder.add();
    }

    private static void addTripping(OverloadManagementSystemAdder adder, Tripping tripping) {
        switch (tripping.type()) {
            case SWITCH_TRIPPING -> adder.newSwitchTripping().setKey(tripping.key()).setCurrentLimit(tripping.currentLimit())
                    .setOpenAction(tripping.open()).setSwitchToOperateId(tripping.element().getId()).add();
            case BRANCH_TRIPPING -> adder.newBranchTripping().setKey(tripping.key()).setCurrentLimit(tripping.currentLimit())
                    .setOpenAction(tripping.open()).setBranchToOperateId(tripping.element().getId()).setSideToOperate((TwoSides) tripping.side()).add();
            case THREE_WINDINGS_TRANSFORMER_TRIPPING -> adder.newThreeWindingsTransformerTripping().setKey(tripping.key())
                    .setCurrentLimit(tripping.currentLimit()).setOpenAction(tripping.open())
                    .setThreeWindingsTransformerToOperateId(tripping.element().getId()).setSideToOperate((ThreeSides) tripping.side()).add();
        }
    }

    /**
     * The lines and transformers of {@code substation}, by name, that an overload management system can monitor or trip.
     */
    public static List<Identifiable<?>> branchesOf(Substation substation) {
        return Stream.concat(substation.getVoltageLevelStream().flatMap(VoltageLevel::getLineStream).distinct().map(line -> (Identifiable<?>) line),
                        Stream.concat(substation.getTwoWindingsTransformerStream(), substation.getThreeWindingsTransformerStream()))
                .sorted(Comparator.comparing(Identifiable::getNameOrId))
                .toList();
    }

    /**
     * The switches of {@code substation}, by name, that an overload management system can trip.
     */
    public static List<Switch> switchesOf(Substation substation) {
        return substation.getVoltageLevelStream().flatMap(voltageLevel -> voltageLevel.getTopologyKind() == TopologyKind.NODE_BREAKER
                        ? voltageLevel.getNodeBreakerView().getSwitchStream() : voltageLevel.getBusBreakerView().getSwitchStream())
                .sorted(Comparator.comparing(Switch::getNameOrId)).toList();
    }

    /**
     * @param lowLimit  none if NaN
     * @param highLimit none if NaN
     */
    public record VoltageAngleLimitSpec(String id, Terminal from, Terminal to, double lowLimit, double highLimit) {
    }

    public static VoltageAngleLimit addVoltageAngleLimit(Network network, VoltageAngleLimitSpec spec) {
        VoltageAngleLimitAdder adder = network.newVoltageAngleLimit().setId(spec.id()).from(spec.from()).to(spec.to());
        if (!Double.isNaN(spec.lowLimit())) {
            adder.setLowLimit(spec.lowLimit());
        }
        if (!Double.isNaN(spec.highLimit())) {
            adder.setHighLimit(spec.highLimit());
        }
        return adder.add();
    }

    /**
     * The first of VAL1, VAL2... not taken: voltage angle limits have ids of their own, apart from the network's objects.
     */
    public static String uniqueVoltageAngleLimitId(Network network) {
        int n = 1;
        while (network.getVoltageAngleLimit("VAL" + n) != null) {
            n++;
        }
        return "VAL" + n;
    }
}
