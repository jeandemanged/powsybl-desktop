/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.NetworkFactory;
import com.powsybl.iidm.network.VariantManagerConstants;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkMergesTest {

    private static Network network(String id) {
        return NetworkFactory.findDefault().createNetwork(id, "test");
    }

    private static Network networkWithSubstation(String networkId, String substationId) {
        Network network = network(networkId);
        network.newSubstation().setId(substationId).add();
        return network;
    }

    @Test
    void networksWithDistinctIdsCanBeMerged() {
        assertEquals(Optional.empty(), NetworkMerges.refusal(List.of(
                networkWithSubstation("N1", "S1"), networkWithSubstation("N2", "S2"))));
    }

    @Test
    void sharedNetworkIdIsRefused() {
        assertEquals(Optional.of(new NetworkMerges.SharedIds(List.of("N"), false)),
                NetworkMerges.refusal(List.of(network("N"), network("N"))));
    }

    @Test
    void aliasMatchingAnotherNetworksIdIsRefused() {
        Network network2 = networkWithSubstation("N2", "S2");
        network2.getSubstation("S2").addAlias("S1");

        assertEquals(Optional.of(new NetworkMerges.SharedIds(List.of("S1"), false)),
                NetworkMerges.refusal(List.of(networkWithSubstation("N1", "S1"), network2)));
    }

    @Test
    void manySharedIdsAreTruncated() {
        List<Network> networks = List.of(IeeeCdfNetworkFactory.create14(), IeeeCdfNetworkFactory.create118());

        NetworkMerges.SharedIds sharedIds = (NetworkMerges.SharedIds) NetworkMerges.refusal(networks).orElseThrow();
        assertEquals(NetworkMerges.MAX_SHARED_IDS_LISTED, sharedIds.ids().size());
        assertTrue(sharedIds.truncated());
        assertEquals(3, NetworkMerges.sharedIds(networks, 3).size(), "stops at the limit");
        assertTrue(NetworkMerges.sharedIds(networks, Integer.MAX_VALUE).contains("B1"));
    }

    @Test
    void multiVariantNetworkIsRefused() {
        Network ieee14 = IeeeCdfNetworkFactory.create14();
        ieee14.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, "other");

        assertEquals(Optional.of(new NetworkMerges.MultipleVariants(List.of(ieee14.getId()))),
                NetworkMerges.refusal(List.of(ieee14, IeeeCdfNetworkFactory.create14())));
    }
}
