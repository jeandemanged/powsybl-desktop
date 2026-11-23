/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class RaoResultRowsTest {

    private static RaoResultAndReport resultAndReport;

    @BeforeAll
    static void run() throws Exception {
        Network network = RaoTestCases.network();
        resultAndReport = RaoRun.prepare(network, RaoTestCases.crac(network), RaoTestCases.parameters(1), false).compute();
    }

    @Test
    void cnecsByStateWithTheirFlowsAndMarginsBeforeAndAfter() {
        Crac crac = resultAndReport.crac();
        List<RaoResultRows.CnecRow> rows = RaoResultRows.cnecRows(resultAndReport.raoResult(), crac, Unit.AMPERE);

        assertEquals(List.of("L1 preventive", "L1 curative"), rows.stream().map(row -> row.cnec().getId()).toList());
        RaoResultRows.CnecRow preventive = rows.getFirst();
        assertEquals(TwoSides.ONE, preventive.side());
        assertEquals(-200., preventive.lowerBound());
        assertEquals(45., preventive.upperBound());
        assertEquals(84., preventive.initialFlow(), 1.);
        assertEquals(-135., preventive.flow(), 1.);
        assertTrue(preventive.initialMargin() < 0);
        assertTrue(preventive.margin() > 0);
        assertTrue(Double.isNaN(preventive.preventiveFlow()));
        assertTrue(Double.isNaN(preventive.preventiveMargin()));

        // after the loss of L2, the PST no longer acts on L1: only the curative load shedding does
        RaoResultRows.CnecRow curative = rows.getLast();
        assertEquals(175., curative.initialFlow(), 1.);
        assertEquals(175., curative.preventiveFlow(), 1.);
        assertEquals(134., curative.flow(), 1.);
        assertTrue(curative.preventiveMargin() < 0);
        assertTrue(curative.margin() > 0);

        RaoResultRows.CnecRow inMegawatts = RaoResultRows.cnecRows(resultAndReport.raoResult(), crac, Unit.MEGAWATT).getLast();
        assertEquals(100., inMegawatts.initialFlow(), 1.);
    }

    @Test
    void activatedActionsByState() {
        Crac crac = resultAndReport.crac();
        List<RaoResultRows.ActionRow> rows = RaoResultRows.actionRows(resultAndReport.raoResult(), crac);

        assertEquals(2, rows.size());
        RaoResultRows.ActionRow pst = rows.getFirst();
        assertEquals(crac.getPreventiveState(), pst.state());
        assertEquals(RaoTestCases.PST, pst.action().getId());
        assertEquals(1, pst.initialTap());
        assertEquals(2, pst.tap());
        assertEquals(0., pst.initialSetPoint(), 1e-6);
        assertEquals(20., pst.setPoint(), 1e-6);
        RaoResultRows.ActionRow loadShedding = rows.getLast();
        assertEquals(crac.getState(RaoTestCases.CONTINGENCY, crac.getInstant("curative")), loadShedding.state());
        assertEquals(RaoTestCases.LOAD_SHEDDING, loadShedding.action().getId());
        assertNull(loadShedding.setPoint());
        assertNull(loadShedding.tap());
    }

    @Test
    void costsOfTheInitialSituationThenByInstant() {
        Crac crac = resultAndReport.crac();
        List<RaoResultRows.CostRow> rows = RaoResultRows.costRows(resultAndReport.raoResult(), crac);

        assertNull(rows.getFirst().instant());
        assertEquals(crac.getSortedInstants(), rows.subList(1, rows.size()).stream().map(RaoResultRows.CostRow::instant).toList());
        // SECURE_FLOW: the cost is the opposite of the worst margin
        assertTrue(rows.getFirst().functionalCost() > 0);
        assertTrue(rows.getLast().functionalCost() < 0);
    }
}
