/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.modification.NetworkModification;
import com.powsybl.iidm.network.Network;

/**
 * Applying powsybl-iidm-modification modifications, and the ids proposed for new objects. Changes made from the UI go
 * through {@link com.powsybl.powsybldesktop.core.NetworkChanges}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class NetworkEdits {

    private NetworkEdits() {
    }

    /**
     * The first of {@code <prefix>1}, {@code <prefix>2}... not taken by any of {@code network}'s objects.
     */
    public static String uniqueId(Network network, String prefix) {
        int n = 1;
        while (network.getNetwork().getIdentifiable(prefix + n) != null) {
            n++;
        }
        return prefix + n;
    }

    /**
     * Applies {@code modification}, throwing on any error instead of logging it as modifications do by default.
     */
    public static void apply(Network network, NetworkModification modification) {
        modification.apply(network, true, ReportNode.NO_OP);
    }
}
