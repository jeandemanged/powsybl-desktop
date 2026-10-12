/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkTieLinesTest {

    private static Network networkWithOneBoundaryLine(String networkId, String pairingKey) {
        Network network = NetworkFactory.findDefault().createNetwork(networkId, "test");
        VoltageLevel voltageLevel = network.newSubstation().setId(networkId + "_S").add()
                .newVoltageLevel().setId(networkId + "_VL").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add();
        voltageLevel.getBusBreakerView().newBus().setId(networkId + "_B").add();
        voltageLevel.newBoundaryLine()
                .setId(networkId + "_BL")
                .setP0(0).setQ0(0).setR(1).setX(1).setG(0).setB(0)
                .setPairingKey(pairingKey)
                .setBus(networkId + "_B")
                .add();
        return network;
    }

    @Test
    void onARootNetworkReturnsAllTieLines() {
        Network network = EurostagTutorialExample1Factory.createWithTieLine();
        List<TieLine> expected = network.getTieLineStream().toList();

        assertEquals(expected, NetworkTieLines.of(network).toList());
        assertEquals(expected.size(), NetworkTieLines.countOf(network));
    }

    @Test
    void onASubnetworkIncludesATieLineWithOnlyOneBoundaryLineInIt() {
        Network network1 = networkWithOneBoundaryLine("N1", "PAIR");
        Network network2 = networkWithOneBoundaryLine("N2", "PAIR");
        Network merged = Network.merge("MERGED", network1, network2);
        TieLine straddlingTieLine = merged.getTieLineStream().findFirst().orElseThrow();
        Network subnetwork1 = merged.getSubnetwork("N1");
        Network subnetwork2 = merged.getSubnetwork("N2");

        // the plain IIDM stream misses it on both sides: the tie line's own parent network is the root,
        // since its two boundary lines belong to two different subnetworks
        assertEquals(List.of(), subnetwork1.getTieLineStream().toList());
        assertEquals(List.of(), subnetwork2.getTieLineStream().toList());

        assertTrue(NetworkTieLines.of(subnetwork1).toList().contains(straddlingTieLine));
        assertTrue(NetworkTieLines.of(subnetwork2).toList().contains(straddlingTieLine));
        assertEquals(1, NetworkTieLines.countOf(subnetwork1));
        assertEquals(1, NetworkTieLines.countOf(subnetwork2));
    }
}
