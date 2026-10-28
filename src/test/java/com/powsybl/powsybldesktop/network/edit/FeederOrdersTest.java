/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.CreateFeederBayBuilder;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A voltage level with one busbar in two sections, loads at orders 0, 1, 2 on the first section and 3, 4 on the
 * second.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class FeederOrdersTest {

    private Network network;

    @BeforeEach
    void setUp() {
        network = Network.create("test", ContainerCreations.SOURCE_FORMAT);
        network.newVoltageLevel().setId("VL").setNominalV(225).setTopologyKind(TopologyKind.NODE_BREAKER).add();
        ContainerCreations.createTopology(network, "VL", TopologyKind.NODE_BREAKER, 1, 2, SwitchKind.BREAKER, false);
        for (int order = 0; order < 5; order++) {
            addLoad("L" + order, order < 3 ? "VL_1_1" : "VL_1_2", order);
        }
    }

    private void addLoad(String id, String busbarSectionId, int order) {
        VoltageLevel voltageLevel = network.getVoltageLevel("VL");
        NetworkEdits.apply(network, new CreateFeederBayBuilder()
                .withInjectionAdder(voltageLevel.newLoad().setId(id).setP0(0).setQ0(0))
                .withBusOrBusbarSectionId(busbarSectionId)
                .withInjectionPositionOrder(order)
                .withInjectionDirection(ConnectablePosition.Direction.BOTTOM)
                .withLogOrThrowIfIncorrectPositionOrder(true)
                .build());
    }

    private int reserve(String busbarSectionId, int requested) {
        AtomicReference<FeederOrders.Slot> slot = new AtomicReference<>();
        FeederOrders.reserving(orders -> slot.set(orders.reserve(network.getBusbarSection(busbarSectionId), requested, 1)));
        return slot.get().order();
    }

    @SuppressWarnings("unchecked")
    private List<Integer> orders() {
        return List.of("L0", "L1", "L2", "L3", "L4").stream()
                .map(id -> ((ConnectablePosition<?>) network.getLoad(id).getExtension(ConnectablePosition.class)).getFeeder().getOrder().orElseThrow())
                .toList();
    }

    @Test
    void takenOrderMovesTheFollowingFeedersUp() {
        assertEquals(1, reserve("VL_1_1", 1));
        assertEquals(List.of(0, 2, 3, 4, 5), orders());

        addLoad("NEW", "VL_1_1", 1);
        assertEquals(6, network.getLoadCount());
    }

    @Test
    void tooHighIsTheEndOfTheSection() {
        assertEquals(3, reserve("VL_1_1", 100));
        assertEquals(List.of(0, 1, 2, 4, 5), orders());
    }

    @Test
    void tooHighOnTheLastSectionIsAfterEveryFeeder() {
        assertEquals(5, reserve("VL_1_2", 100));
        assertEquals(List.of(0, 1, 2, 3, 4), orders());
    }

    @Test
    void tooLowIsTheStartOfTheSection() {
        assertEquals(3, reserve("VL_1_2", 0));
        assertEquals(List.of(0, 1, 2, 4, 5), orders());
    }

    @Test
    void suggestionIsTheEndOfTheSection() {
        assertEquals(3, FeederOrders.endOf(network.getBusbarSection("VL_1_1")));
        assertEquals(5, FeederOrders.endOf(network.getBusbarSection("VL_1_2")));
    }

    @Test
    void laterReservationsMoveEarlierOnes() {
        FeederOrders.reserving(orders -> {
            FeederOrders.Slot first = orders.reserve(network.getBusbarSection("VL_1_1"), 1, 1);
            FeederOrders.Slot second = orders.reserve(network.getBusbarSection("VL_1_1"), 1, 1);
            assertEquals(2, first.order());
            assertEquals(1, second.order());
        });
        assertEquals(List.of(0, 3, 4, 5, 6), orders());
    }

    @Test
    void movedFeedersAreMovedBackOnFailure() {
        assertThrows(IllegalStateException.class, () -> FeederOrders.reserving(orders -> {
            orders.reserve(network.getBusbarSection("VL_1_1"), 0, 2);
            throw new IllegalStateException();
        }));
        assertEquals(List.of(0, 1, 2, 3, 4), orders());
    }
}
