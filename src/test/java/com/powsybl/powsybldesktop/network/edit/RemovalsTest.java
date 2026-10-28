/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit: the removals themselves, without their confirmation.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class RemovalsTest {

    @Test
    void substationGoesWithItsVoltageLevelsAndTheirBranches() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        List<String> dependents = Removals.dependents(network.getSubstation("S1"));

        assertTrue(dependents.stream().anyMatch(dependent -> dependent.contains("S1VL1")));
        assertTrue(dependents.stream().anyMatch(dependent -> dependent.contains("LINE_S2S3") || dependent.contains("TWT")));

        Removals.remove(network, network.getSubstation("S1"));

        assertNull(network.getSubstation("S1"));
        assertNull(network.getVoltageLevel("S1VL1"));
        assertNull(network.getTwoWindingsTransformer("TWT"));
        // HVDC1 links a station of S1 to one of S3
        assertNull(network.getHvdcLine("HVDC1"));
    }

    @Test
    void feederGoesWithItsBay() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        int switchCount = network.getSwitchCount();

        Removals.remove(network, network.getLoad("LD1"));

        assertNull(network.getLoad("LD1"));
        assertTrue(network.getSwitchCount() < switchCount);
        assertNull(network.getSwitch("S1VL1_LD1_BREAKER"));
    }

    @Test
    void busbarSectionGoesWithItsSwitches() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        List<String> dependents = Removals.dependents(network.getBusbarSection("S1VL2_BBS2"));
        assertFalse(dependents.isEmpty());

        Removals.remove(network, network.getBusbarSection("S1VL2_BBS2"));

        assertNull(network.getBusbarSection("S1VL2_BBS2"));
        assertEquals(dependents.size(), dependents.stream().distinct().count());
        assertTrue(network.getVoltageLevel("S1VL2").getNodeBreakerView().getSwitchStream()
                .noneMatch(aSwitch -> aSwitch.getId().contains("BBS2")));
    }

    @Test
    void converterStationGoesWithItsHvdcLine() {
        Network network = HvdcTestNetwork.createVsc();
        assertEquals(1, Removals.dependents(network.getVscConverterStation("C1")).size());

        Removals.remove(network, network.getVscConverterStation("C1"));

        assertNull(network.getVscConverterStation("C1"));
        assertNull(network.getHvdcLine("L"));
        assertNotNull(network.getVscConverterStation("C2"));
    }

    @Test
    void boundaryLineGoesWithItsTieLineButNotTheOtherHalf() {
        Network network = EurostagTutorialExample1Factory.createWithTieLine();
        TieLine tieLine = network.getTieLine("NHV1_NHV2_1");
        BoundaryLine removed = tieLine.getBoundaryLine1();
        String otherId = tieLine.getBoundaryLine2().getId();
        assertEquals(2, Removals.dependents(removed).size());

        Removals.remove(network, removed);

        assertNull(network.getTieLine("NHV1_NHV2_1"));
        assertNull(network.getBoundaryLine(removed.getId()));
        assertFalse(network.getBoundaryLine(otherId).isPaired());
    }

    @Test
    void tieLineKeepsItsBoundaryLines() {
        Network network = EurostagTutorialExample1Factory.createWithTieLine();
        TieLine tieLine = network.getTieLine("NHV1_NHV2_1");
        String id1 = tieLine.getBoundaryLine1().getId();

        Removals.remove(network, tieLine);

        assertNull(network.getTieLine("NHV1_NHV2_1"));
        assertFalse(network.getBoundaryLine(id1).isPaired());
    }

    @Test
    void configuredBusIsRemovable() {
        Network network = EurostagTutorialExample1Factory.create();
        network.getVoltageLevel("VLGEN").getBusBreakerView().newBus().setId("SPARE").add();

        assertTrue(Removals.supports(network.getBusBreakerView().getBus("SPARE")));
        Removals.remove(network, network.getBusBreakerView().getBus("SPARE"));

        assertNull(network.getBusBreakerView().getBus("SPARE"));
    }
}
