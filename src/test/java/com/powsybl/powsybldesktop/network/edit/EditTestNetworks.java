/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class EditTestNetworks {

    private EditTestNetworks() {
    }

    /**
     * Substation S: VL400 and VL225 node breaker, with two busbars; VL63 bus breaker, with one bus.
     */
    static Network network() {
        Network network = Network.create("test", Containers.SOURCE_FORMAT);
        Substation substation = network.newSubstation().setId("S").add();
        for (String id : new String[] {"VL400", "VL225"}) {
            substation.newVoltageLevel().setId(id).setNominalV("VL400".equals(id) ? 400 : 225).setTopologyKind(TopologyKind.NODE_BREAKER).add();
            Containers.createTopology(network, id, TopologyKind.NODE_BREAKER, 2, 1, SwitchKind.BREAKER, true);
        }
        substation.newVoltageLevel().setId("VL63").setNominalV(63).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        Containers.createTopology(network, "VL63", TopologyKind.BUS_BREAKER, 1, 1, SwitchKind.BREAKER, false);
        return network;
    }

    /**
     * A connection to the first busbar section or bus of {@code voltageLevelId}, after the busbar section's feeders.
     */
    static Connection at(Network network, String voltageLevelId, ConnectablePosition.Direction direction) {
        VoltageLevel voltageLevel = network.getVoltageLevel(voltageLevelId);
        Identifiable<?> bus = Topologies.busesOrBusbarSections(voltageLevel).getFirst();
        int order = bus instanceof BusbarSection busbarSection ? FeederOrders.endOf(busbarSection) : 0;
        return new Connection(voltageLevel, bus, order, direction);
    }
}
