/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.BusbarSectionPosition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ContainerCreationsTest {

    private final Network network = Network.create("test", ContainerCreations.SOURCE_FORMAT);

    private VoltageLevel newVoltageLevel(TopologyKind kind) {
        return network.newSubstation().setId("S").add().newVoltageLevel().setId("VL").setNominalV(400).setTopologyKind(kind).add();
    }

    @Test
    void nodeBreakerTopologyIsAGridOfBusbarSectionsWithCouplings() {
        VoltageLevel voltageLevel = newVoltageLevel(TopologyKind.NODE_BREAKER);

        ContainerCreations.createTopology(network, "VL", TopologyKind.NODE_BREAKER, 2, 3, SwitchKind.DISCONNECTOR, true);

        assertEquals(6, voltageLevel.getNodeBreakerView().getBusbarSectionCount());
        assertNotNull(network.getBusbarSection("VL_2_3").getExtension(BusbarSectionPosition.class));
        // a coupling per section, the sections being joined by disconnectors
        assertEquals(3, voltageLevel.getNodeBreakerView().getSwitchStream().filter(s -> s.getKind() == SwitchKind.BREAKER).count());
    }

    @Test
    void busBreakerTopologyIsAGridOfBuses() {
        VoltageLevel voltageLevel = newVoltageLevel(TopologyKind.BUS_BREAKER);

        ContainerCreations.createTopology(network, "VL", TopologyKind.BUS_BREAKER, 2, 2, SwitchKind.BREAKER, false);

        assertEquals(4, voltageLevel.getBusBreakerView().getBusStream().count());
        // two section switches, no couplings
        assertEquals(2, voltageLevel.getBusBreakerView().getSwitchCount());
    }
}
