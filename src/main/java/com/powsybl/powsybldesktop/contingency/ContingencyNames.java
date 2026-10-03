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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Enabled lists are independent of each other, so the same contingency can come out of several of them (e.g. a
    // line both in an explicit list and matched by a criterion list), and two lists can also reuse an id for different
    // elements - security analysis results are keyed by contingency id, so ids have to be unique. Elements are
    // compared as a set since their order doesn't change the contingency; generated ids skip any id already in use.
    public static List<Contingency> deduplicate(List<Contingency> contingencies) {
        Set<String> usedIds = contingencies.stream().map(Contingency::getId).collect(Collectors.toCollection(HashSet::new));
        Map<String, List<Set<ContingencyElement>>> elementSetsById = new HashMap<>();
        List<Contingency> unique = new ArrayList<>();
        for (Contingency contingency : contingencies) {
            Set<ContingencyElement> elements = new HashSet<>(contingency.getElements());
            List<Set<ContingencyElement>> elementSets = elementSetsById.computeIfAbsent(contingency.getId(), id -> new ArrayList<>());
            if (elementSets.contains(elements)) {
                continue;
            }
            elementSets.add(elements);
            if (elementSets.size() == 1) {
                unique.add(contingency);
            } else {
                int suffix = elementSets.size() - 1;
                String id = contingency.getId() + " (" + suffix + ")";
                while (!usedIds.add(id)) {
                    id = contingency.getId() + " (" + ++suffix + ")";
                }
                unique.add(new Contingency(id, contingency.getName().orElse(null), contingency.getElements()));
            }
        }
        return unique;
    }
}
