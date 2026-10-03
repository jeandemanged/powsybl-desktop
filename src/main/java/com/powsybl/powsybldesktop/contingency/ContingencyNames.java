/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class ContingencyNames {

    private ContingencyNames() {
    }

    // TODO: improve in powsybl-core - AbstractEquipmentCriterionContingencyList.getContingencies(Network) (and
    // IdentifierContingencyList) build each Contingency as new Contingency(equipmentId, element) without a name, so
    // the equipment's name is recovered here instead. Restricted to that single-element, id == element-id shape
    // so an explicit unnamed contingency keeps showing its own id; the lookup is done against the current network
    // state since the equipment may have been removed since the list was defined.
    public static String displayName(Contingency contingency, Network network) {
        return contingency.getName().orElseGet(() -> {
            List<ContingencyElement> elements = contingency.getElements();
            if (elements.size() == 1 && elements.get(0).getId().equals(contingency.getId())) {
                Identifiable<?> identifiable = network.getIdentifiable(contingency.getId());
                if (identifiable != null) {
                    return identifiable.getNameOrId();
                }
            }
            return contingency.getId();
        });
    }

    // Falls back to the raw id for elements missing from the network, so invalid entries stay identifiable.
    public static String elementsSummary(Contingency contingency, Network network) {
        return contingency.getElements().stream()
                .map(element -> {
                    Identifiable<?> identifiable = network.getIdentifiable(element.getId());
                    return element.getType() + ":" + (identifiable == null ? element.getId() : identifiable.getNameOrId());
                })
                .collect(Collectors.joining(", "));
    }
}
