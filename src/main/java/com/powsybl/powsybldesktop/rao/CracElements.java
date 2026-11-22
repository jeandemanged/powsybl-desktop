/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;

import java.util.List;
import java.util.stream.Stream;

/**
 * The network elements of a CRAC's objects missing from a network: a CRAC is checked against the network when it is
 * imported, but the network may be edited since.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CracElements {

    private CracElements() {
    }

    /**
     * The ids of the elements of {@code contingency} missing from {@code network}, sorted.
     */
    public static List<String> missing(Network network, Contingency contingency) {
        return missing(network, contingency.getElements().stream().map(ContingencyElement::getId));
    }

    public static List<String> missing(Network network, Cnec<?> cnec) {
        return missing(network, cnec.getNetworkElementsIds().stream());
    }

    public static List<String> missing(Network network, RemedialAction<?> remedialAction) {
        return missing(network, remedialAction.getNetworkElementsIds().stream());
    }

    private static List<String> missing(Network network, Stream<String> ids) {
        return ids.filter(id -> network.getIdentifiable(id) == null).distinct().sorted().toList();
    }
}
