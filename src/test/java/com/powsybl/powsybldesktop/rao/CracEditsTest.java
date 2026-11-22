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
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.contingency.LineContingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.InstantKind;
import com.powsybl.powsybldesktop.utils.Messages;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CracEditsTest {

    private final Network network = RaoTestCases.network();

    private static List<String> instants(Crac crac) {
        return crac.getSortedInstants().stream().map(Instant::getId).toList();
    }

    @Test
    void createsAnEmptyCracWithItsInstants() {
        Crac crac = CracEdits.newCrac(" new ", "auto", List.of("curative 1", "curative 2"));
        assertEquals("new", crac.getId());
        assertEquals(List.of("preventive", "outage", "auto", "curative 1", "curative 2"), instants(crac));
        assertTrue(crac.getContingencies().isEmpty());

        assertEquals(List.of("preventive", "outage"), instants(CracEdits.newCrac("c", null, List.of())));
        assertEquals(Messages.get("cracEdits.idRequired"),
                assertThrows(IllegalArgumentException.class, () -> CracEdits.newCrac(" ", null, List.of())).getMessage());
        // OpenRAO refuses two instants of the same id
        assertThrows(IllegalArgumentException.class, () -> CracEdits.newCrac("c", null, List.of("curative", "curative")));
    }

    @Test
    void addsInstantsInTheirKindsOrder() {
        Crac crac = CracEdits.newCrac("c", null, List.of());
        assertEquals(List.of(InstantKind.AUTO, InstantKind.CURATIVE), CracEdits.addableInstantKinds(crac));

        CracEdits.addInstant(crac, "curative", InstantKind.CURATIVE);
        // no auto instant after a curative one
        assertEquals(List.of(InstantKind.CURATIVE), CracEdits.addableInstantKinds(crac));
        assertThrows(IllegalArgumentException.class, () -> CracEdits.addInstant(crac, "auto", InstantKind.AUTO));
        assertThrows(IllegalArgumentException.class, () -> CracEdits.addInstant(crac, "curative", InstantKind.CURATIVE));
        assertThrows(IllegalArgumentException.class, () -> CracEdits.addInstant(crac, "", InstantKind.CURATIVE));
        CracEdits.addInstant(crac, "curative 2", InstantKind.CURATIVE);
        assertEquals(List.of("preventive", "outage", "curative", "curative 2"), instants(crac));

        Crac withAuto = CracEdits.newCrac("c", "auto", List.of());
        assertEquals(List.of(InstantKind.CURATIVE), CracEdits.addableInstantKinds(withAuto));
    }

    @Test
    void addsContingencies() {
        Crac crac = CracEdits.newCrac("c", null, List.of("curative"));
        assertEquals("Contingency 1", CracEdits.newContingencyId(crac, "Contingency"));
        Contingency contingency = CracEdits.addContingency(crac, "Contingency 1", " ", List.of(CracEdits.element(network.getLine("L1"))));
        // OpenRAO names it by its id
        assertEquals(Optional.of("Contingency 1"), contingency.getName());
        assertEquals(ContingencyElementType.LINE, contingency.getElements().getFirst().getType());
        assertEquals("Contingency 2", CracEdits.newContingencyId(crac, "Contingency"));
        assertEquals(Messages.get("cracEdits.contingency.exists", "Contingency 1"),
                assertThrows(IllegalArgumentException.class, () -> CracEdits.addContingency(crac, "Contingency 1", null, List.of())).getMessage());
        // a voltage level can't be a contingency element
        assertThrows(IllegalArgumentException.class, () -> CracEdits.element(network.getVoltageLevel("VL1")));
    }

    @Test
    void changesTheElementsInPlace() {
        Crac crac = RaoTestCases.crac(network);
        Contingency contingency = crac.getContingency(RaoTestCases.CONTINGENCY);
        CracEdits.setElements(contingency, List.of(CracEdits.element(network.getLine("L1")), CracEdits.element(network.getLoad("LD2"))));

        assertSame(contingency, crac.getContingency(RaoTestCases.CONTINGENCY));
        assertSame(contingency, crac.getFlowCnec("L1 curative").getState().getContingency().orElseThrow());
        assertEquals(List.of("L1", "LD2"), contingency.getElements().stream().map(ContingencyElement::getId).toList());
    }

    @Test
    void renamesAndRemovesOnlyWhenNotReferenced() {
        Crac crac = RaoTestCases.crac(network);
        Contingency referenced = crac.getContingency(RaoTestCases.CONTINGENCY);
        assertEquals(List.of("L1 curative", RaoTestCases.LOAD_SHEDDING), CracEdits.references(crac, RaoTestCases.CONTINGENCY));
        assertThrows(IllegalArgumentException.class, () -> CracEdits.rename(crac, referenced, "other", null));
        assertThrows(IllegalArgumentException.class, () -> CracEdits.removeContingency(crac, RaoTestCases.CONTINGENCY));
        // unchanged: no rename
        assertSame(referenced, CracEdits.rename(crac, referenced, RaoTestCases.CONTINGENCY + " ", ""));

        Contingency free = CracEdits.addContingency(crac, "free", null, List.of(CracEdits.element(network.getLine("L1"))));
        assertTrue(CracEdits.references(crac, "free").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> CracEdits.rename(crac, free, RaoTestCases.CONTINGENCY, null));
        Contingency renamed = CracEdits.rename(crac, free, "renamed", "Renamed");
        assertNull(crac.getContingency("free"));
        assertSame(renamed, crac.getContingency("renamed"));
        assertEquals(Optional.of("Renamed"), renamed.getName());
        assertEquals("L1", renamed.getElements().getFirst().getId());

        CracEdits.removeContingency(crac, "renamed");
        assertNull(crac.getContingency("renamed"));
    }

    @Test
    void replaceRefusesWithIllegalArgumentsTellingAnObjectNotPutBack() {
        List<String> steps = new ArrayList<>();
        // OpenRAO's refusal as an IllegalArgumentException, the object put back
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> CracEdits.replace("x", () -> steps.add("remove"),
                () -> {
                    throw new OpenRaoException("refused");
                }, () -> steps.add("putBack")));
        assertEquals("refused", refused.getMessage());
        assertEquals(List.of("remove", "putBack"), steps);

        IllegalArgumentException lost = assertThrows(IllegalArgumentException.class, () -> CracEdits.replace("x", () -> { },
                () -> {
                    throw new IllegalArgumentException("refused");
                }, () -> {
                    throw new OpenRaoException("no way");
                }));
        assertEquals(Messages.get("cracEdits.lost", "x", "refused", "no way"), lost.getMessage());
        assertEquals(1, lost.getSuppressed().length);
    }

    @Test
    void addsTheValidNewContingenciesOfLists() {
        Crac crac = RaoTestCases.crac(network);
        List<Contingency> contingencies = List.of(Contingency.line("L1"), Contingency.line(RaoTestCases.CONTINGENCY.replace(" out", "")),
                new Contingency(RaoTestCases.CONTINGENCY, new LineContingency("L1")), Contingency.line("unknown"));

        CracEdits.Added added = CracEdits.addContingencies(crac, CracEdits.resolve(network, contingencies));

        assertEquals(List.of("L1", "L2"), added.added().stream().map(Contingency::getId).toList());
        assertEquals(List.of(RaoTestCases.CONTINGENCY, "unknown"), added.skipped());
        assertNotNull(crac.getContingency("L2"));
    }

    @Test
    void tellsTheProblemsOfAContingency() {
        Crac crac = CracEdits.newCrac("c", null, List.of());
        Contingency contingency = CracEdits.addContingency(crac, "c", null, List.of());
        assertEquals(Optional.of(Messages.get("cracEdits.contingency.empty")), CracEdits.contingencyProblem(network, contingency));
        CracEdits.setElements(contingency, List.of(CracEdits.element(network.getLine("L1"))));
        assertEquals(Optional.empty(), CracEdits.contingencyProblem(network, contingency));
        network.getLine("L1").remove();
        assertEquals(Optional.of(Messages.get("crac.missingElements", "L1")), CracEdits.contingencyProblem(network, contingency));
    }
}
