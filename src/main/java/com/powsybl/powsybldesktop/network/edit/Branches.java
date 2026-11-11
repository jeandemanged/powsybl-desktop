/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.modification.topology.CreateBranchFeederBaysBuilder;
import com.powsybl.iidm.modification.topology.DefaultNamingStrategy;
import com.powsybl.iidm.modification.topology.TopologyModificationUtils;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.BranchAdder;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.ThreeWindingsTransformerAdder;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePositionAdder;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.SubstationPosition;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creating branches, with their bays in node breaker voltage levels, and HVDC lines; and the likeliest ends proposed
 * for them.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class Branches {

    private Branches() {
    }

    public record LineSpec(String id, String name, double r, double x, double g1, double b1, double g2, double b2) {
    }

    /**
     * @param ratedU1 the nominal voltage of side 1's voltage level if NaN
     * @param ratedU2 the nominal voltage of side 2's voltage level if NaN
     */
    public record TwoWindingsTransformerSpec(String id, String name, double r, double x, double g, double b, double ratedU1, double ratedU2,
                                             double ratedS) {
    }

    /**
     * @param ratedU the nominal voltage of its voltage level if NaN
     */
    public record Leg(double r, double x, double g, double b, double ratedU, Connection connection) {
    }

    /**
     * @param ratedU0 the nominal voltage of leg 1's voltage level if NaN
     */
    public record ThreeWindingsTransformerSpec(String id, String name, double ratedU0, List<Leg> legs) {
    }

    public record HvdcLineSpec(String id, String name, HvdcConverterStation<?> station1, HvdcConverterStation<?> station2, double r,
                               double nominalV, HvdcLine.ConvertersMode convertersMode, double activePowerSetpoint, double maxP) {
    }

    public static Line addLine(Network network, LineSpec spec, Connection side1, Connection side2) {
        addBranch(network, network.newLine().setId(spec.id()).setName(spec.name())
                .setR(spec.r()).setX(spec.x()).setG1(spec.g1()).setB1(spec.b1()).setG2(spec.g2()).setB2(spec.b2()), side1, side2);
        return network.getLine(spec.id());
    }

    public static TwoWindingsTransformer addTwoWindingsTransformer(Substation substation, TwoWindingsTransformerSpec spec,
                                                                   Connection side1, Connection side2) {
        Network network = substation.getParentNetwork();
        addBranch(network, substation.newTwoWindingsTransformer().setId(spec.id()).setName(spec.name())
                .setR(spec.r()).setX(spec.x()).setG(spec.g()).setB(spec.b())
                .setRatedU1(orNominal(spec.ratedU1(), side1.voltageLevel())).setRatedU2(orNominal(spec.ratedU2(), side2.voltageLevel()))
                .setRatedS(spec.ratedS()), side1, side2);
        return network.getTwoWindingsTransformer(spec.id());
    }

    private static void addBranch(Network network, BranchAdder<?, ?> adder, Connection side1, Connection side2) {
        CreateBranchFeederBaysBuilder builder = new CreateBranchFeederBaysBuilder()
                .withBranchAdder(adder)
                .withBusOrBusbarSectionId1(side1.busOrBusbarSectionId())
                .withBusOrBusbarSectionId2(side2.busOrBusbarSectionId())
                .withLogOrThrowIfIncorrectPositionOrder1(true)
                .withLogOrThrowIfIncorrectPositionOrder2(true);
        FeederOrders.reserving(orders -> {
            FeederOrders.Slot slot1 = side1.isNodeBreaker() ? side1.reserve(orders, 1) : null;
            FeederOrders.Slot slot2 = side2.isNodeBreaker() ? side2.reserve(orders, 1) : null;
            // read once both are reserved: the second may have moved the first
            if (slot1 != null) {
                builder.withPositionOrder1(slot1.order()).withDirection1(side1.direction());
            }
            if (slot2 != null) {
                builder.withPositionOrder2(slot2.order()).withDirection2(side2.direction());
            }
            orders.apply(network, builder.build());
        });
    }

    private static double orNominal(double ratedU, VoltageLevel voltageLevel) {
        return Double.isNaN(ratedU) ? voltageLevel.getNominalV() : ratedU;
    }

    public static ThreeWindingsTransformer addThreeWindingsTransformer(Substation substation, ThreeWindingsTransformerSpec spec) {
        Network network = substation.getParentNetwork();
        List<Leg> legs = spec.legs();
        ThreeWindingsTransformerAdder adder = substation.newThreeWindingsTransformer().setId(spec.id()).setName(spec.name())
                .setRatedU0(orNominal(spec.ratedU0(), legs.getFirst().connection().voltageLevel()));
        // the node of each node breaker leg, the next free ones of its voltage level
        Map<VoltageLevel, Integer> nextNodes = new HashMap<>();
        int[] nodes = new int[3];
        for (int i = 0; i < 3; i++) {
            Leg leg = legs.get(i);
            Connection connection = leg.connection();
            VoltageLevel voltageLevel = connection.voltageLevel();
            ThreeWindingsTransformerAdder.LegAdder legAdder = (i == 0 ? adder.newLeg1() : i == 1 ? adder.newLeg2() : adder.newLeg3())
                    .setR(leg.r()).setX(leg.x()).setG(leg.g()).setB(leg.b())
                    .setRatedU(orNominal(leg.ratedU(), voltageLevel))
                    .setVoltageLevel(voltageLevel.getId());
            if (connection.isNodeBreaker()) {
                nodes[i] = nextNodes.merge(voltageLevel, voltageLevel.getNodeBreakerView().getMaximumNodeIndex() + 1, (previous, _) -> previous + 1);
                legAdder.setNode(nodes[i]);
            } else {
                legAdder.setBus(connection.busOrBusbarSectionId()).setConnectableBus(connection.busOrBusbarSectionId());
            }
            legAdder.add();
        }
        FeederOrders.reserving(orders -> {
            FeederOrders.Slot[] slots = new FeederOrders.Slot[3];
            for (int i = 0; i < 3; i++) {
                Connection connection = legs.get(i).connection();
                slots[i] = connection.isNodeBreaker() ? connection.reserve(orders, 1) : null;
            }
            ThreeWindingsTransformer transformer = adder.add();
            addBays(network, transformer, legs, nodes, slots);
        });
        return network.getThreeWindingsTransformer(spec.id());
    }

    // as CreateBranchFeederBays does for each side of a two-winding transformer
    @SuppressWarnings("unchecked")
    private static void addBays(Network network, ThreeWindingsTransformer transformer, List<Leg> legs, int[] nodes, FeederOrders.Slot[] slots) {
        ConnectablePositionAdder<ThreeWindingsTransformer> positions = null;
        for (int i = 0; i < 3; i++) {
            Connection connection = legs.get(i).connection();
            if (!connection.isNodeBreaker()) {
                continue;
            }
            VoltageLevel voltageLevel = connection.voltageLevel();
            TopologyModificationUtils.createTopologyWithConnectableNode(i + 1, connection.busOrBusbarSectionId(), network, voltageLevel,
                    nodes[i], transformer, new DefaultNamingStrategy(), ReportNode.NO_OP);
            if (Connection.usesPositions(voltageLevel, transformer)) {
                if (positions == null) {
                    positions = transformer.newExtension(ConnectablePositionAdder.class);
                }
                (i == 0 ? positions.newFeeder1() : i == 1 ? positions.newFeeder2() : positions.newFeeder3())
                        .withName(transformer.getId()).withOrder(slots[i].order()).withDirection(connection.direction()).add();
            }
        }
        if (positions != null) {
            positions.add();
        }
    }

    public static TieLine addTieLine(Network network, String id, String name, BoundaryLine boundaryLine1, BoundaryLine boundaryLine2) {
        return network.newTieLine().setId(id).setName(name).setBoundaryLine1(boundaryLine1.getId()).setBoundaryLine2(boundaryLine2.getId()).add();
    }

    public static HvdcLine addHvdcLine(Network network, HvdcLineSpec spec) {
        return network.newHvdcLine().setId(spec.id()).setName(spec.name())
                .setConverterStationId1(spec.station1().getId()).setConverterStationId2(spec.station2().getId())
                .setR(spec.r()).setNominalV(spec.nominalV()).setConvertersMode(spec.convertersMode())
                .setActivePowerSetpoint(spec.activePowerSetpoint()).setMaxP(spec.maxP()).add();
    }

    public static List<VoltageLevel> byDecreasingVoltage(Substation substation) {
        return substation.getVoltageLevelStream().sorted(Comparator.comparingDouble(VoltageLevel::getNominalV).reversed()).toList();
    }

    /**
     * The likeliest other end of a line from {@code voltageLevel}: a voltage level of another substation, of the
     * closest nominal voltage, in the nearest substation (from their positions, unplaced ones last).
     */
    public static VoltageLevel otherEnd(List<VoltageLevel> voltageLevels, VoltageLevel voltageLevel) {
        if (voltageLevel == null) {
            return otherThan(voltageLevels, null);
        }
        Substation substation = voltageLevel.getSubstation().orElse(null);
        Coordinate position = coordinate(substation);
        return voltageLevels.stream()
                .filter(other -> other != voltageLevel && (substation == null || other.getSubstation().orElse(null) != substation))
                .min(Comparator.<VoltageLevel>comparingDouble(other -> Math.abs(other.getNominalV() - voltageLevel.getNominalV()))
                        .thenComparingDouble(other -> squaredDistance(position, coordinate(other.getSubstation().orElse(null)))))
                .orElseGet(() -> otherThan(voltageLevels, voltageLevel));
    }

    private static Coordinate coordinate(Substation substation) {
        SubstationPosition position = substation == null ? null : substation.getExtension(SubstationPosition.class);
        return position == null ? null : position.getCoordinate();
    }

    // equirectangular, enough to rank substations by distance
    private static double squaredDistance(Coordinate a, Coordinate b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        double dx = (b.getLongitude() - a.getLongitude()) * Math.cos(Math.toRadians((a.getLatitude() + b.getLatitude()) / 2));
        double dy = b.getLatitude() - a.getLatitude();
        return dx * dx + dy * dy;
    }

    /**
     * The first of {@code voltageLevels} other than {@code voltageLevel}, or {@code voltageLevel} if none.
     */
    public static VoltageLevel otherThan(List<VoltageLevel> voltageLevels, VoltageLevel voltageLevel) {
        return voltageLevels.stream().filter(other -> other != voltageLevel).findFirst().orElse(voltageLevel);
    }

    /**
     * The other unpaired boundary line with the same pairing key as {@code boundaryLine}, or else the first other one.
     */
    public static BoundaryLine matchingBoundaryLine(List<BoundaryLine> unpaired, BoundaryLine boundaryLine) {
        return unpaired.stream()
                .filter(other -> other != boundaryLine && boundaryLine != null && boundaryLine.getPairingKey() != null
                        && boundaryLine.getPairingKey().equals(other.getPairingKey()))
                .findFirst()
                .orElse(unpaired.stream().filter(other -> other != boundaryLine).findFirst().orElse(null));
    }
}
