/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.list.LineCriterionContingencyList;
import com.powsybl.ieeecdf.converter.IeeeCdfNetworkFactory;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ContingencyNamesTest {

    private final Network network = IeeeCdfNetworkFactory.create14();

    @Test
    void elementsSummaryShowsNamesAndFallsBackToIdWhenMissing() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        line.setName("Named line");

        Contingency contingency = Contingency.builder("c").addLine(line.getId()).addLine("does-not-exist").build();

        assertEquals("LINE:Named line, LINE:does-not-exist", ContingencyNames.elementsSummary(contingency, network));
    }

    @Test
    void criterionContingencyDisplaysEquipmentNameAndFallsBackToIdOnceRemoved() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        line.setName("Named line");
        Contingency contingency = new LineCriterionContingencyList("all-lines", null, null, List.of(), null)
                .getContingencies(network).stream()
                .filter(c -> c.getId().equals(line.getId()))
                .findFirst().orElseThrow();

        assertEquals("Named line", ContingencyNames.displayName(contingency, network));

        String lineId = line.getId();
        line.remove();
        assertEquals(lineId, ContingencyNames.displayName(contingency, network));
    }

    @Test
    void explicitContingencyDisplaysOwnNameOrId() {
        Line line = network.getLineStream().findFirst().orElseThrow();
        line.setName("Named line");

        assertEquals("c", ContingencyNames.displayName(new Contingency("c", Contingency.line(line.getId()).getElements()), network));
        assertEquals("My contingency", ContingencyNames.displayName(new Contingency("c", "My contingency", Contingency.line(line.getId()).getElements()), network));
    }

    @Test
    void deduplicateDropsSameIdAndElementsRegardlessOfOrder() {
        Contingency first = Contingency.builder("c").addLine("L1").addLine("L2").build();
        Contingency sameElementsReordered = Contingency.builder("c").addLine("L2").addLine("L1").build();

        assertEquals(List.of(first), ContingencyNames.deduplicate(List.of(first, sameElementsReordered)));
    }

    @Test
    void deduplicateSuffixesSameIdWithDifferentElements() {
        Contingency first = Contingency.builder("c").addLine("L1").build();
        Contingency second = new Contingency("c", "Named", Contingency.line("L2").getElements());
        Contingency third = Contingency.builder("c").addLine("L3").build();
        Contingency alreadyTaken = Contingency.builder("c (2)").addLine("L4").build();

        List<Contingency> unique = ContingencyNames.deduplicate(List.of(first, second, third, alreadyTaken));

        assertEquals(List.of("c", "c (1)", "c (3)", "c (2)"), unique.stream().map(Contingency::getId).toList());
        assertEquals("Named", unique.get(1).getName().orElseThrow());
        assertEquals(second.getElements(), unique.get(1).getElements());
        assertTrue(unique.get(2).getName().isEmpty());
    }
}
