/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OverloadManagementSystem;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.VoltageAngleLimit;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkObjectsTest {

    @Test
    void areaGroupsItsVoltageLevels() {
        Network network = EurostagTutorialExample1Factory.create();

        Area area = NetworkObjects.addArea(network, new NetworkObjects.AreaSpec("A", "Area", "ControlArea", 12,
                List.of(network.getVoltageLevel("VLGEN"), network.getVoltageLevel("VLHV1"))));
        Area noTarget = NetworkObjects.addArea(network, new NetworkObjects.AreaSpec("B", null, "ControlArea", Double.NaN, List.of()));

        assertEquals("ControlArea", area.getAreaType());
        assertEquals(12, area.getInterchangeTarget().orElseThrow());
        assertEquals(2, area.getVoltageLevelStream().count());
        assertTrue(noTarget.getInterchangeTarget().isEmpty());
    }

    @Test
    void overloadManagementSystemTripsSwitchesAndBranches() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        Substation substation = network.getSubstation("S1");
        List<Identifiable<?>> branches = NetworkObjects.branchesOf(substation);
        List<Switch> switches = NetworkObjects.switchesOf(substation);
        Identifiable<?> monitored = branches.getFirst();
        Identifiable<?> branch = branches.stream().filter(candidate -> !(candidate instanceof ThreeWindingsTransformer))
                .findFirst().orElseThrow();

        OverloadManagementSystem system = NetworkObjects.addOverloadManagementSystem(substation, new NetworkObjects.OverloadManagementSystemSpec(
                "OMS", null, false, monitored, ThreeSides.ONE, List.of(
                        new NetworkObjects.Tripping(OverloadManagementSystem.Tripping.Type.SWITCH_TRIPPING, switches.getFirst(), null, "T1", 1000, true),
                        new NetworkObjects.Tripping(OverloadManagementSystem.Tripping.Type.BRANCH_TRIPPING, branch, TwoSides.TWO, "T2", 900, false))));

        assertFalse(system.isEnabled());
        assertEquals(monitored.getId(), system.getMonitoredElementId());
        assertEquals(2, system.getTrippings().size());
        assertEquals(900, system.getTrippings().get(1).getCurrentLimit());
        assertFalse(system.getTrippings().get(1).isOpenAction());
    }

    @Test
    void trippableElementsAreTheSubstationsOwn() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        Substation substation = network.getSubstation("S1");

        assertEquals(substation.getVoltageLevelStream().flatMap(voltageLevel -> voltageLevel.getLineStream()).distinct().count()
                + substation.getTwoWindingsTransformerCount() + substation.getThreeWindingsTransformerCount(), NetworkObjects.branchesOf(substation).size());
        assertEquals(substation.getVoltageLevelStream().mapToLong(voltageLevel -> voltageLevel.getNodeBreakerView().getSwitchCount()).sum(),
                NetworkObjects.switchesOf(substation).size());
    }

    @Test
    void voltageAngleLimitGetsTheLimitsGivenAndAnIdOfItsOwn() {
        Network network = EurostagTutorialExample1Factory.create();
        assertEquals("VAL1", NetworkObjects.uniqueVoltageAngleLimitId(network));

        VoltageAngleLimit limit = NetworkObjects.addVoltageAngleLimit(network, new NetworkObjects.VoltageAngleLimitSpec("VAL1",
                network.getLine("NHV1_NHV2_1").getTerminal1(), network.getLine("NHV1_NHV2_1").getTerminal2(), Double.NaN, 30));

        assertTrue(limit.getLowLimit().isEmpty());
        assertEquals(30, limit.getHighLimit().orElseThrow());
        assertEquals("VAL2", NetworkObjects.uniqueVoltageAngleLimitId(network));
    }
}
