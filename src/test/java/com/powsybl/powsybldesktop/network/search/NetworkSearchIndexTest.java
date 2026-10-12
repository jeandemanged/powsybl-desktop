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
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain JUnit — no JavaFX toolkit needed, since {@link NetworkSearchIndex} is pure Java.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkSearchIndexTest {

    private final Network network = IeeeCdfNetworkFactory.create14();
    private final NetworkSearchIndex index = NetworkSearchIndex.build(network);

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void typoTolerantQueryStillFindsTheEquipmentAndIsFlaggedApproximate() {
        // "B10-L" typo'd as "B19-L" - one substitution away, and no precise (prefix/substring) match exists for
        // "b19", so the fuzzy fallback kicks in
        Load load = network.getLoad("B10-L");

        NetworkSearchIndex.Result result = index.search("B19-L", EnumSet.of(NetworkSearch.Kind.LOAD));

        assertTrue(result.matches().contains(load));
        assertTrue(result.approximate());
    }

    @Test
    void preciseMatchSuppressesFuzzyFallback() {
        // "B10-L" is a precise (substring) match for itself only - "B11-L".."B14-L" would only fuzzy-match, but
        // since a precise match exists, the fuzzy fallback never runs and they're excluded
        Load exactMatch = network.getLoad("B10-L");

        NetworkSearchIndex.Result result = index.search("B10-L", EnumSet.of(NetworkSearch.Kind.LOAD));

        assertEquals(List.of(exactMatch), result.matches());
        assertFalse(result.approximate());
    }

    @Test
    void kindFilterExcludesOtherKinds() {
        NetworkSearchIndex.Result result = index.search("B1-G", EnumSet.of(NetworkSearch.Kind.LOAD));

        assertTrue(result.matches().isEmpty());
    }

    @Test
    void substationOnlyReturnedWhenItHasMatchingEquipment() {
        // mirrors NetworkSearchTest.searchForSubstationOnlyReturnedWhenItHasMatchingEquipment
        Substation substation = network.getSubstationStream().findFirst().orElseThrow();

        List<Identifiable<?>> genOnly = index.search(substation.getId(), EnumSet.of(NetworkSearch.Kind.SUBSTATION, NetworkSearch.Kind.GENERATOR)).matches();
        boolean hasGenerator = substation.getVoltageLevelStream().anyMatch(vl -> vl.getGeneratorCount() > 0);
        assertEquals(hasGenerator, genOnly.contains(substation));
    }

    @Test
    void transformerAppearsExactlyOnce() {
        // a transformer touches two voltage levels; flat per-kind indexing (unlike the old hierarchy walk)
        // has no terminal-side dedup to get wrong, but this is worth asserting explicitly
        TwoWindingsTransformer transformer = network.getTwoWindingsTransformerStream().findFirst().orElseThrow();

        List<Identifiable<?>> result = index.search(transformer.getId(), EnumSet.of(NetworkSearch.Kind.TRANSFORMER)).matches();

        assertEquals(1, result.stream().filter(transformer::equals).count());
    }

    @Test
    void blankQueryReturnsNoResult() {
        assertTrue(index.search(null, NetworkSearch.ALL_KINDS).matches().isEmpty());
        assertTrue(index.search("  ", NetworkSearch.ALL_KINDS).matches().isEmpty());
    }

    @Test
    void configuredBusIsSearchableUnderItsOwnKindOnly() {
        // IeeeCdfNetworkFactory's voltage levels are BUS_BREAKER-topology; "B1" is VL1's configured bus,
        // distinct from its bus-view merged bus "VL1_0"
        Bus configuredBus = network.getVoltageLevel("VL1").getBusBreakerView().getBus("B1");

        assertTrue(index.search("B1", EnumSet.of(NetworkSearch.Kind.CONFIGURED_BUS)).matches().contains(configuredBus));
        assertTrue(index.search("B1", EnumSet.of(NetworkSearch.Kind.BUS)).matches().isEmpty(),
                "a configured bus shouldn't be found under Kind.BUS");
    }

    @Test
    void busViewBusIsSearchableUnderItsOwnKindOnly() {
        Bus mergedBus = network.getVoltageLevel("VL1").getBusView().getBus("VL1_0");

        assertTrue(index.search("VL1_0", EnumSet.of(NetworkSearch.Kind.BUS)).matches().contains(mergedBus));
        assertTrue(index.search("VL1_0", EnumSet.of(NetworkSearch.Kind.CONFIGURED_BUS)).matches().isEmpty(),
                "a bus-view bus shouldn't be found under Kind.CONFIGURED_BUS");
    }

    @Test
    void refreshBusesPicksUpABusViewSplitFromOpeningACoupler() {
        // S1VL2's bus view is a single merged bus (S1VL2_0) until its coupler is opened, splitting it into two
        Network nodeBreakerNetwork = FourSubstationsNodeBreakerFactory.create();
        try (NetworkSearchIndex busIndex = NetworkSearchIndex.build(nodeBreakerNetwork)) {
            Switch coupler = nodeBreakerNetwork.getSwitch("S1VL2_COUPLER");
            coupler.setOpen(true);
            Bus splitBus = nodeBreakerNetwork.getVoltageLevel("S1VL2").getBusView().getBus("S1VL2_1");

            assertFalse(busIndex.search("S1VL2_1", EnumSet.of(NetworkSearch.Kind.BUS)).matches().contains(splitBus),
                    "the index was built before the coupler opened, so it shouldn't know about the split-off bus yet");

            busIndex.refreshBuses(nodeBreakerNetwork);

            assertTrue(busIndex.search("S1VL2_1", EnumSet.of(NetworkSearch.Kind.BUS)).matches().contains(splitBus));
        }
    }
}
