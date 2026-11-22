/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.BusbarSectionPosition;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.SubstationPosition;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ContainersTest {

    private final Network network = Network.create("test", Containers.SOURCE_FORMAT);

    private VoltageLevel newVoltageLevel(TopologyKind kind) {
        return network.newSubstation().setId("S").add().newVoltageLevel().setId("VL").setNominalV(400).setTopologyKind(kind).add();
    }

    @Test
    void nodeBreakerTopologyIsAGridOfBusbarSectionsWithCouplings() {
        VoltageLevel voltageLevel = newVoltageLevel(TopologyKind.NODE_BREAKER);

        Containers.createTopology(network, "VL", TopologyKind.NODE_BREAKER, 2, 3, SwitchKind.DISCONNECTOR, true);

        assertEquals(6, voltageLevel.getNodeBreakerView().getBusbarSectionCount());
        assertNotNull(network.getBusbarSection("VL_2_3").getExtension(BusbarSectionPosition.class));
        // a coupling per section, the sections being joined by disconnectors
        assertEquals(3, voltageLevel.getNodeBreakerView().getSwitchStream().filter(s -> s.getKind() == SwitchKind.BREAKER).count());
    }

    @Test
    void busBreakerTopologyIsAGridOfBuses() {
        VoltageLevel voltageLevel = newVoltageLevel(TopologyKind.BUS_BREAKER);

        Containers.createTopology(network, "VL", TopologyKind.BUS_BREAKER, 2, 2, SwitchKind.BREAKER, false);

        assertEquals(4, voltageLevel.getBusBreakerView().getBusStream().count());
        // two section switches, no couplings
        assertEquals(2, voltageLevel.getBusBreakerView().getSwitchCount());
    }

    @Test
    void uniqueNetworkIdSkipsTakenIds() {
        assertEquals("network1", Containers.uniqueNetworkId(List.of(network)));
        assertEquals("network2", Containers.uniqueNetworkId(List.of(Network.create("network1", Containers.SOURCE_FORMAT))));
    }

    @Test
    void newNetworkHasTheGivenIdentityAndCaseDate() {
        ZonedDateTime caseDate = ZonedDateTime.parse("2026-01-02T03:04:00Z");
        Network created = Containers.newNetwork("n", "My network", caseDate);
        assertEquals("n", created.getId());
        assertEquals("My network", created.getOptionalName().orElseThrow());
        assertEquals(caseDate.toInstant(), created.getCaseDate().toInstant());
    }

    @Test
    void defaultCountryIsTheNetworksOnlyCountry() {
        assertNull(Containers.defaultCountry(network));
        Containers.addSubstation(network, new Containers.SubstationSpec("S1", null, Country.FR, null, null));
        assertEquals(Country.FR, Containers.defaultCountry(network));
        Containers.addSubstation(network, new Containers.SubstationSpec("S2", null, Country.BE, null, null));
        assertNull(Containers.defaultCountry(network));
    }

    @Test
    void substationGetsItsCountryTsoAndPosition() {
        Substation substation = Containers.addSubstation(network, new Containers.SubstationSpec("S", "Name", Country.FR, "TSO",
                new Coordinate(48.8, 2.3)));
        assertEquals(Country.FR, substation.getCountry().orElseThrow());
        assertEquals("TSO", substation.getTso());
        assertEquals(new Coordinate(48.8, 2.3), substation.getExtension(SubstationPosition.class).getCoordinate());

        Substation bare = Containers.addSubstation(network, new Containers.SubstationSpec("S2", null, null, null, null));
        assertTrue(bare.getCountry().isEmpty());
        assertNull(bare.getExtension(SubstationPosition.class));
    }

    @Test
    void voltageLevelGetsItsLimitsAndTopology() {
        Substation substation = Containers.addSubstation(network, new Containers.SubstationSpec("S", null, null, null, null));
        VoltageLevel voltageLevel = Containers.addVoltageLevel(network, new Containers.VoltageLevelSpec("VL", null, substation, 225, 200, 245,
                TopologyKind.NODE_BREAKER, 2, 1, SwitchKind.BREAKER, true));

        assertSame(substation, voltageLevel.getSubstation().orElseThrow());
        assertEquals(225, voltageLevel.getNominalV());
        assertEquals(200, voltageLevel.getLowVoltageLimit());
        assertEquals(245, voltageLevel.getHighVoltageLimit());
        assertEquals(2, voltageLevel.getNodeBreakerView().getBusbarSectionCount());
    }

    @Test
    void voltageLevelWithoutBusbarsOrSubstationIsEmpty() {
        VoltageLevel voltageLevel = Containers.addVoltageLevel(network, new Containers.VoltageLevelSpec("VL", null, null, 400, Double.NaN, Double.NaN,
                TopologyKind.BUS_BREAKER, 0, 1, SwitchKind.BREAKER, true));

        assertTrue(voltageLevel.getSubstation().isEmpty());
        assertEquals(0, voltageLevel.getBusBreakerView().getBusStream().count());
    }
}
