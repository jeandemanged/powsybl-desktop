/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageAngleLimit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link Network#merge} checks each network only as it reaches it, after the previous ones were already moved into
 * the merged network: a refused merge leaves them emptied and unusable (merging them again throws an NPE in
 * {@code detachDcTopologyModel}). Everything it refuses is therefore checked beforehand, on all the networks.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkMerges {

    public static final int MAX_SHARED_IDS_LISTED = 20;

    public sealed interface Refusal permits SharedIds, MultipleVariants {
    }

    /**
     * @param ids at most {@link #MAX_SHARED_IDS_LISTED} of the shared ids
     * @param truncated whether more ids are shared
     */
    public record SharedIds(List<String> ids, boolean truncated) implements Refusal {
    }

    public record MultipleVariants(List<String> networkIds) implements Refusal {
    }

    private NetworkMerges() {
    }

    public static Optional<Refusal> refusal(List<Network> networks) {
        // powsybl actually checks its internal variant array size, which removed variants can leave above 1: not
        // visible through the API, and unreachable while the application doesn't create variants
        List<String> multiVariantNetworkIds = networks.stream()
                .filter(network -> network.getVariantManager().getVariantIds().size() > 1)
                .map(Network::getId)
                .toList();
        if (!multiVariantNetworkIds.isEmpty()) {
            return Optional.of(new MultipleVariants(multiVariantNetworkIds));
        }
        // one more than listed, to tell whether the list is truncated
        List<String> sharedIds = sharedIds(networks, MAX_SHARED_IDS_LISTED + 1);
        if (!sharedIds.isEmpty()) {
            return Optional.of(new SharedIds(sharedIds.subList(0, Math.min(sharedIds.size(), MAX_SHARED_IDS_LISTED)),
                    sharedIds.size() > MAX_SHARED_IDS_LISTED));
        }
        return Optional.empty();
    }

    static List<String> sharedIds(List<Network> networks, int limit) {
        // a network's index holds the network itself and keeps its ids and aliases unique, so a single set over all
        // networks only catches ids used by several of them; voltage angle limits have their own namespace
        Set<String> ids = new HashSet<>();
        Set<String> voltageAngleLimitIds = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (Network network : networks) {
            for (Identifiable<?> identifiable : network.getIdentifiables()) {
                if (addIfDuplicate(ids, identifiable.getId(), duplicates, limit)
                        || identifiable.getAliases().stream().anyMatch(alias -> addIfDuplicate(ids, alias, duplicates, limit))) {
                    return duplicates;
                }
            }
            for (VoltageAngleLimit voltageAngleLimit : network.getVoltageAngleLimits()) {
                if (addIfDuplicate(voltageAngleLimitIds, voltageAngleLimit.getId(), duplicates, limit)) {
                    return duplicates;
                }
            }
        }
        return duplicates;
    }

    private static boolean addIfDuplicate(Set<String> seenIds, String id, List<String> duplicates, int limit) {
        if (!seenIds.add(id)) {
            duplicates.add(id);
        }
        return duplicates.size() >= limit;
    }
}
