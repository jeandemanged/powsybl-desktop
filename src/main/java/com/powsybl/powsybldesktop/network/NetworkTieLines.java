/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TieLine;

import java.util.stream.Stream;

/**
 * A subnetwork's own {@link Network#getTieLineStream()} misses a tie line whose two boundary lines belong to
 * two different subnetworks: {@code TieLine.getParentNetwork()} then resolves to the root network (neither
 * subnetwork), so the subnetwork-scoped stream (which filters by {@code getParentNetwork() == this}) excludes
 * it on both sides. This utility instead counts/lists a tie line as part of a (sub)network as soon as either
 * of its boundary lines is, which matches how a user thinks of "this subnetwork's tie lines". Shared by the
 * tie lines table and the networks view's equipment counts.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkTieLines {

    private NetworkTieLines() {
    }

    public static Stream<TieLine> of(Network network) {
        if (network.getNetwork() == network) {
            return network.getTieLineStream();
        }
        return network.getNetwork().getTieLineStream().filter(tieLine -> belongsTo(tieLine, network));
    }

    public static int countOf(Network network) {
        return (int) of(network).count();
    }

    private static boolean belongsTo(TieLine tieLine, Network network) {
        return tieLine.getBoundaryLine1().getParentNetwork() == network || tieLine.getBoundaryLine2().getParentNetwork() == network;
    }
}
