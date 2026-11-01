/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.CreateFeederBayBuilder;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.BusbarSectionPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class TopologiesTest {

    private final Network network = EditTestNetworks.network();

    @Test
    void sectionsAreAddedOnEveryParallelBusbar() {
        Topologies.addBusbarSections(network, network.getBusbarSection("VL400_1_1"), new Topologies.SectionsSpec(true, true,
                SwitchKind.DISCONNECTOR, false, SwitchKind.DISCONNECTOR, false, "VL400", "VL400"));

        VoltageLevel voltageLevel = network.getVoltageLevel("VL400");
        assertEquals(4, voltageLevel.getNodeBreakerView().getBusbarSectionCount());
        assertTrue(voltageLevel.getNodeBreakerView().getBusbarSectionStream()
                .allMatch(busbarSection -> busbarSection.getExtension(BusbarSectionPosition.class) != null));
    }

    @Test
    void busbarSectionIsPositionedOnlyWithBothIndexes() {
        VoltageLevel voltageLevel = network.getVoltageLevel("VL400");
        int node = voltageLevel.getNodeBreakerView().getMaximumNodeIndex() + 1;

        BusbarSection positioned = Topologies.addBusbarSection(voltageLevel, "BBS3", "Third", node, 3, 1);
        BusbarSection unpositioned = Topologies.addBusbarSection(voltageLevel, "BBS4", null, node + 1, null, null);

        assertEquals(3, positioned.getExtension(BusbarSectionPosition.class).getBusbarIndex());
        assertEquals(1, positioned.getExtension(BusbarSectionPosition.class).getSectionIndex());
        assertNull(unpositioned.getExtension(BusbarSectionPosition.class));
    }

    @Test
    void couplingDeviceBetweenTwoBuses() {
        VoltageLevel voltageLevel = network.getVoltageLevel("VL63");
        Bus bus = Topologies.addBus(voltageLevel, "B2", "Bus 2");

        Identifiable<?> other = Topologies.busesOrBusbarSections(voltageLevel).stream().filter(candidate -> candidate != bus).findFirst().orElseThrow();
        Topologies.addCouplingDevice(network, other, bus, null);

        assertEquals("Bus 2", bus.getOptionalName().orElseThrow());
        assertEquals(1, voltageLevel.getBusBreakerView().getSwitchCount());
    }

    @Test
    void busesOrBusbarSectionsFollowTheTopologyKind() {
        assertEquals(List.of("VL400_1_1", "VL400_2_1"),
                Topologies.busesOrBusbarSections(network.getVoltageLevel("VL400")).stream().map(identifiable -> identifiable.getId()).toList());
        assertEquals(1, Topologies.busesOrBusbarSections(network.getVoltageLevel("VL63")).size());
    }

    @Test
    void switchAndInternalConnectionBetweenNodes() {
        VoltageLevel voltageLevel = network.getVoltageLevel("VL225");
        int node = network.getBusbarSection("VL225_1_1").getTerminal().getNodeBreakerView().getNode();
        int next = voltageLevel.getNodeBreakerView().getMaximumNodeIndex() + 1;

        Switch aSwitch = Topologies.addSwitch(voltageLevel, new Topologies.SwitchSpec("SW", null, SwitchKind.BREAKER, node, next, true, false));
        Topologies.addInternalConnection(voltageLevel, next, next + 1);

        assertEquals(SwitchKind.BREAKER, aSwitch.getKind());
        assertTrue(aSwitch.isOpen());
        assertEquals(node, voltageLevel.getNodeBreakerView().getNode1(aSwitch.getId()));
        assertEquals(1, voltageLevel.getNodeBreakerView().getInternalConnectionCount());
    }

    @Test
    void feederMovesToAnotherVoltageLevel() {
        NetworkEdits.apply(network, new CreateFeederBayBuilder()
                .withInjectionAdder(network.getVoltageLevel("VL400").newLoad().setId("LD").setP0(1).setQ0(0))
                .withBusOrBusbarSectionId("VL400_1_1").withInjectionPositionOrder(1).build());
        Load load = network.getLoad("LD");
        VoltageLevel target = network.getVoltageLevel("VL63");

        Topologies.moveFeeder(network, load, load.getTerminal(), target, Topologies.busesOrBusbarSections(target).getFirst().getId());

        assertEquals("VL63", load.getTerminal().getVoltageLevel().getId());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void feedersAreConnectedToTheCheckedBusbarSections() {
        NetworkEdits.apply(network, new CreateFeederBayBuilder()
                .withInjectionAdder(network.getVoltageLevel("VL400").newLoad().setId("LD").setP0(1).setQ0(0))
                .withBusOrBusbarSectionId("VL400_1_1").withInjectionPositionOrder(1).build());
        VoltageLevel voltageLevel = network.getVoltageLevel("VL400");
        BusbarSection third = Topologies.addBusbarSection(voltageLevel, "BBS3", null, voltageLevel.getNodeBreakerView().getMaximumNodeIndex() + 1, 3, 1);
        int switchCount = voltageLevel.getNodeBreakerView().getSwitchCount();

        Topologies.connectFeeders(network, List.of(third), List.<Connectable>of(network.getLoad("LD")), false);

        // a disconnector from the load's bay to the new busbar
        assertEquals(switchCount + 1, voltageLevel.getNodeBreakerView().getSwitchCount());
    }
}
