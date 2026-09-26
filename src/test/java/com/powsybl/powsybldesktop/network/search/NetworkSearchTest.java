/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.search;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.VoltageLevel;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkSearchTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    @Test
    void nullNetworkOrBlankQueryReturnsNoResult() {
        Generator generator = network.getGeneratorStream().findFirst().orElseThrow();

        assertTrue(NetworkSearch.search(null, generator.getId()).isEmpty());
        assertTrue(NetworkSearch.search(network, null).isEmpty());
        assertTrue(NetworkSearch.search(network, "  ").isEmpty());
    }

    @Test
    void searchIsCaseInsensitiveAndMatchesById() {
        Generator generator = network.getGeneratorStream().findFirst().orElseThrow();

        List<Identifiable<?>> result = NetworkSearch.search(network, generator.getId().toUpperCase());

        assertTrue(result.contains(generator));
    }

    @Test
    void searchMatchesBusById() {
        Bus bus = network.getBusView().getBusStream().findFirst().orElseThrow();

        List<Identifiable<?>> result = NetworkSearch.search(network, bus.getId(), EnumSet.of(NetworkSearch.Kind.BUS));

        assertTrue(result.contains(bus));
    }

    @Test
    void searchRestrictedToKindExcludesOtherKinds() {
        Generator generator = network.getGeneratorStream().findFirst().orElseThrow();

        List<Identifiable<?>> result = NetworkSearch.search(network, generator.getId(), EnumSet.of(NetworkSearch.Kind.LOAD));

        assertTrue(result.isEmpty());
    }

    @Test
    void searchForSubstationOnlyReturnedWhenItHasMatchingEquipment() {
        Substation substation = network.getSubstationStream().findFirst().orElseThrow();

        // a substation match is only useful for a restricted kind search if it has equipment of that kind
        List<Identifiable<?>> genOnly = NetworkSearch.search(network, substation.getId(), EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.GENERATOR));
        boolean hasGenerator = substation.getVoltageLevelStream().anyMatch(vl -> vl.getGeneratorCount() > 0);
        assertEquals(hasGenerator, genOnly.contains(substation));

        // searching every kind always reveals the substation itself, regardless of its equipment
        List<Identifiable<?>> allKinds = NetworkSearch.search(network, substation.getId());
        assertTrue(allKinds.contains(substation));
    }

    @Test
    void kindOfMapsEachSupportedEquipmentType() {
        Generator generator = network.getGeneratorStream().findFirst().orElseThrow();
        VoltageLevel voltageLevel = network.getVoltageLevelStream().findFirst().orElseThrow();
        Substation substation = network.getSubstationStream().findFirst().orElseThrow();

        assertEquals(NetworkSearch.Kind.GENERATOR, NetworkSearch.kindOf(generator));
        assertEquals(NetworkSearch.Kind.VOLTAGE_LEVEL, NetworkSearch.kindOf(voltageLevel));
        assertEquals(NetworkSearch.Kind.SUBSTATION, NetworkSearch.kindOf(substation));

        Bus bus = network.getBusView().getBusStream().findFirst().orElseThrow();
        assertEquals(NetworkSearch.Kind.BUS, NetworkSearch.kindOf(bus));
    }

    @Test
    void containerOfResolvesGeneratorVoltageLevel() {
        Generator generator = network.getGeneratorStream().findFirst().orElseThrow();

        assertEquals(generator.getTerminal().getVoltageLevel(), NetworkSearch.containerOf(generator));
    }

    @Test
    void containerOfResolvesBusVoltageLevel() {
        Bus bus = network.getBusView().getBusStream().findFirst().orElseThrow();

        assertEquals(bus.getVoltageLevel(), NetworkSearch.containerOf(bus));
    }

    @Test
    void searchWithNoMatchReturnsEmptyResult() {
        assertTrue(NetworkSearch.search(network, "no-such-equipment-id").isEmpty());
    }
}
