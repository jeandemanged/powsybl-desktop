/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.ConnectFeedersToBusbarSectionsBuilder;
import com.powsybl.iidm.modification.topology.CreateCouplingDeviceBuilder;
import com.powsybl.iidm.modification.topology.CreateVoltageLevelSectionsBuilder;
import com.powsybl.iidm.modification.topology.MoveFeederBayBuilder;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.BusbarSectionPositionAdder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Changing the topology of voltage levels: busbar sections, switches, couplings, buses, and the busbar sections or
 * buses feeders are connected to.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class Topologies {

    private Topologies() {
    }

    /**
     * Sections added next to a reference busbar section, on its busbar or all the parallel ones, with the switches
     * joining them.
     */
    public record SectionsSpec(boolean after, boolean allBusbars, SwitchKind leftSwitchKind, boolean leftSwitchOpen,
                               SwitchKind rightSwitchKind, boolean rightSwitchOpen, String busbarSectionPrefix, String switchPrefix) {
    }

    /**
     * Adds sections next to {@code reference}, which must have a {@code BusbarSectionPosition}.
     */
    public static void addBusbarSections(Network network, BusbarSection reference, SectionsSpec spec) {
        NetworkEdits.apply(network, new CreateVoltageLevelSectionsBuilder()
                .withReferenceBusbarSectionId(reference.getId())
                .withCreateTheBusbarSectionsAfterTheReferenceBusbarSection(spec.after())
                .withAllBusbars(spec.allBusbars())
                .withLeftSwitchKind(spec.leftSwitchKind())
                .withLeftSwitchOpen(spec.leftSwitchOpen())
                .withRightSwitchKind(spec.rightSwitchKind())
                .withRightSwitchOpen(spec.rightSwitchOpen())
                .withBusbarSectionPrefixId(spec.busbarSectionPrefix())
                .withSwitchPrefixId(spec.switchPrefix())
                .build());
    }

    /**
     * Adds a busbar section at {@code node} of a node breaker {@code voltageLevel}, positioned in its grid of busbars
     * and sections unless {@code busbarIndex} and {@code sectionIndex} are null.
     */
    public static BusbarSection addBusbarSection(VoltageLevel voltageLevel, String id, String name, int node, Integer busbarIndex, Integer sectionIndex) {
        BusbarSection busbarSection = voltageLevel.getNodeBreakerView().newBusbarSection().setId(id).setName(name).setNode(node).add();
        if (busbarIndex != null) {
            busbarSection.newExtension(BusbarSectionPositionAdder.class).withBusbarIndex(busbarIndex).withSectionIndex(sectionIndex).add();
        }
        return busbarSection;
    }

    /**
     * Adds a coupling device between two busbar sections or buses: a breaker with its disconnectors (node breaker), a
     * switch (bus breaker).
     *
     * @param switchPrefix the default one if null
     */
    public static void addCouplingDevice(Network network, Identifiable<?> busOrBusbarSection1, Identifiable<?> busOrBusbarSection2, String switchPrefix) {
        CreateCouplingDeviceBuilder builder = new CreateCouplingDeviceBuilder()
                .withBusOrBusbarSectionId1(busOrBusbarSection1.getId())
                .withBusOrBusbarSectionId2(busOrBusbarSection2.getId());
        if (switchPrefix != null) {
            builder.withSwitchPrefixId(switchPrefix);
        }
        NetworkEdits.apply(network, builder.build());
    }

    /**
     * {@code voltageLevel}'s busbar sections (node breaker) or buses (bus breaker), by id.
     */
    public static List<Identifiable<?>> busesOrBusbarSections(VoltageLevel voltageLevel) {
        List<Identifiable<?>> buses = new ArrayList<>();
        if (voltageLevel.getTopologyKind() == TopologyKind.NODE_BREAKER) {
            voltageLevel.getNodeBreakerView().getBusbarSectionStream().forEach(buses::add);
        } else {
            voltageLevel.getBusBreakerView().getBusStream().forEach(buses::add);
        }
        buses.sort(Comparator.comparing(Identifiable::getId));
        return buses;
    }

    public record SwitchSpec(String id, String name, SwitchKind kind, int node1, int node2, boolean open, boolean retained) {
    }

    /**
     * Adds a switch between two nodes of a node breaker {@code voltageLevel}.
     */
    public static Switch addSwitch(VoltageLevel voltageLevel, SwitchSpec spec) {
        return voltageLevel.getNodeBreakerView().newSwitch().setId(spec.id()).setName(spec.name()).setKind(spec.kind())
                .setNode1(spec.node1()).setNode2(spec.node2()).setOpen(spec.open()).setRetained(spec.retained()).add();
    }

    /**
     * Adds an internal connection (a zero impedance link, without switch) between two nodes of a node breaker
     * {@code voltageLevel}.
     */
    public static void addInternalConnection(VoltageLevel voltageLevel, int node1, int node2) {
        voltageLevel.getNodeBreakerView().newInternalConnection().setNode1(node1).setNode2(node2).add();
    }

    public static Bus addBus(VoltageLevel voltageLevel, String id, String name) {
        return voltageLevel.getBusBreakerView().newBus().setId(id).setName(name).add();
    }

    /**
     * Connects {@code feeders} of a node breaker voltage level to {@code busbarSections}, with disconnectors from their
     * bays, which their busbar sections need a {@code BusbarSectionPosition} for.
     */
    @SuppressWarnings("rawtypes")
    public static void connectFeeders(Network network, List<BusbarSection> busbarSections, List<Connectable> feeders, boolean couplingDevices) {
        NetworkEdits.apply(network, new ConnectFeedersToBusbarSectionsBuilder()
                .withBusbarSectionsToConnect(busbarSections)
                .withConnectablesToConnect(feeders)
                .withConnectCouplingDevices(couplingDevices)
                .build());
    }

    /**
     * Moves {@code terminal} of {@code connectable} to a busbar section or bus, possibly of another voltage level: its bay moves
     * with it in node breaker voltage levels.
     */
    public static void moveFeeder(Network network, Connectable<?> connectable, Terminal terminal, VoltageLevel targetVoltageLevel,
                                  String targetBusOrBusbarSectionId) {
        NetworkEdits.apply(network, new MoveFeederBayBuilder()
                .withConnectableId(connectable.getId())
                .withTerminal(terminal)
                .withTargetVoltageLevelId(targetVoltageLevel.getId())
                .withTargetBusOrBusBarSectionId(targetBusOrBusbarSectionId)
                .build());
    }
}
