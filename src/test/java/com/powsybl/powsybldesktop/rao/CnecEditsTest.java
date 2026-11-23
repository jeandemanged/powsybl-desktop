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
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.powsybldesktop.rao.CnecEdits.FlowCnecSpec;
import com.powsybl.powsybldesktop.rao.CnecEdits.StateRef;
import com.powsybl.powsybldesktop.rao.CnecEdits.ThresholdSpec;
import com.powsybl.powsybldesktop.utils.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CnecEditsTest {

    private final Network network = RaoTestCases.network();
    private final Crac crac = RaoTestCases.crac(network);
    private final StateRef preventive = new StateRef(null, "preventive");
    private final StateRef curative = new StateRef(RaoTestCases.CONTINGENCY, "curative");

    // the curative CNEC made a condition of a network action
    private void referCurativeCnec() {
        crac.newNetworkAction().withId("on constraint").newLoadAction().withNetworkElement("LD2").withActivePowerValue(50.).add()
                .newOnConstraintUsageRule().withCnec("L1 curative").withInstant("curative").add()
                .add();
    }

    @Test
    void listsTheStates() {
        assertEquals(List.of(preventive, new StateRef(RaoTestCases.CONTINGENCY, "outage"), curative), CnecEdits.states(crac));
    }

    @Test
    void makesDefaultCnecsFromTheLimits() {
        FlowCnecSpec spec = CnecEdits.defaultSpec(crac, network.getBranch("L1"), curative, Set.of(TwoSides.ONE));
        assertEquals("L1_" + RaoTestCases.CONTINGENCY + "_curative", spec.id());
        assertEquals(spec.id(), spec.name());
        assertEquals(List.of(new ThresholdSpec(TwoSides.ONE, Unit.AMPERE, -1000., 1000.)), spec.thresholds());
        assertEquals(1000., spec.iMax1());
        assertEquals(network.getVoltageLevel("VL1").getNominalV(), spec.nominalVoltage1());
        assertTrue(spec.optimized());
        assertFalse(spec.monitored());

        CnecEdits.Added added = CnecEdits.addFlowCnecs(crac, network, List.of("L1", "L2", "LD2"), List.of(preventive, curative),
                Set.of(TwoSides.ONE, TwoSides.TWO));
        assertEquals(List.of("L1_preventive", "L1_L2 out_curative", "L2_preventive", "L2_L2 out_curative"),
                added.added().stream().map(FlowCnec::getId).toList());
        assertEquals(List.of(Messages.get("cnecEdits.skipped", "LD2", Messages.get("cnecEdits.notABranch"))), added.skipped());
        assertEquals(2, crac.getFlowCnec("L1_preventive").getThresholds().size());
        assertSame(crac.getContingency(RaoTestCases.CONTINGENCY), crac.getFlowCnec("L2_L2 out_curative").getState().getContingency().orElseThrow());

        // again: already there
        assertEquals(4, CnecEdits.addFlowCnecs(crac, network, List.of("L1", "L2"), List.of(preventive, curative), Set.of(TwoSides.ONE)).skipped().size());
    }

    @Test
    void changesTheRoleInPlaceAndAnythingElseByReplacing() {
        FlowCnec cnec = crac.getFlowCnec("L1 curative");
        FlowCnecSpec spec = FlowCnecSpec.of(cnec);
        assertEquals(curative, spec.state());

        assertSame(cnec, CnecEdits.change(crac, cnec, spec.withRole(false, true)));
        assertFalse(cnec.isOptimized());
        assertTrue(cnec.isMonitored());

        FlowCnecSpec changed = new FlowCnecSpec("L1 curative 2", "", "L1", preventive, true, false, 5, "operator", " ", spec.nominalVoltage1(),
                spec.nominalVoltage2(), null, null, List.of(new ThresholdSpec(TwoSides.TWO, Unit.MEGAWATT, null, 100.)));
        FlowCnec replaced = CnecEdits.change(crac, cnec, changed);
        assertNotSame(cnec, replaced);
        assertNull(crac.getCnec("L1 curative"));
        assertSame(replaced, crac.getFlowCnec("L1 curative 2"));
        assertEquals(changed, FlowCnecSpec.of(replaced));
        assertTrue(replaced.getState().isPreventive());
        assertEquals("operator", replaced.getOperator());
    }

    @Test
    void putsTheCnecBackWhenOpenRaoRefusesTheChange() {
        FlowCnec cnec = crac.getFlowCnec("L1 curative");
        FlowCnecSpec spec = FlowCnecSpec.of(cnec);
        // a threshold in % of iMax needs the iMax
        FlowCnecSpec refused = new FlowCnecSpec(spec.id(), spec.name(), spec.branchId(), spec.state(), true, false, 0, null, null,
                spec.nominalVoltage1(), spec.nominalVoltage2(), null, null, List.of(new ThresholdSpec(TwoSides.ONE, Unit.PERCENT_IMAX, null, 1.)));

        assertThrows(IllegalArgumentException.class, () -> CnecEdits.change(crac, cnec, refused));
        assertEquals(spec, FlowCnecSpec.of(crac.getFlowCnec("L1 curative")));
        assertThrows(IllegalArgumentException.class, () -> CnecEdits.change(crac, crac.getFlowCnec("L1 curative"),
                new FlowCnecSpec("L1 preventive", null, "L1", curative, true, false, 0, null, null, 1., 1., null, null, spec.thresholds())));
    }

    @Test
    void keepsAReferencedCnec() {
        referCurativeCnec();
        FlowCnec cnec = crac.getFlowCnec("L1 curative");
        assertEquals(List.of("on constraint"), CnecEdits.references(crac, "L1 curative"));
        FlowCnecSpec spec = FlowCnecSpec.of(cnec);
        // its role can be changed
        assertSame(cnec, CnecEdits.change(crac, cnec, spec.withRole(true, true)));
        FlowCnecSpec renamed = new FlowCnecSpec("other", null, spec.branchId(), spec.state(), true, true, 0, null, null, spec.nominalVoltage1(),
                spec.nominalVoltage2(), null, null, spec.thresholds());
        assertEquals(Messages.get("cnecEdits.referenced", "L1 curative", "on constraint"),
                assertThrows(IllegalArgumentException.class, () -> CnecEdits.change(crac, cnec, renamed)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> CnecEdits.remove(crac, "L1 curative"));
        assertNotNull(crac.getCnec("L1 curative"));

        CnecEdits.remove(crac, "L1 preventive");
        assertNull(crac.getCnec("L1 preventive"));
    }

    @Test
    void readsAndWritesThresholdsAsRows() {
        List<ThresholdSpec> thresholds = List.of(new ThresholdSpec(TwoSides.ONE, Unit.AMPERE, -1000., 1000.5),
                new ThresholdSpec(TwoSides.TWO, Unit.PERCENT_IMAX, null, 0.9));
        List<List<String>> rows = CnecEdits.thresholdRows(thresholds);
        assertEquals(List.of(List.of("1", "A", "-1000", "1000.5"), List.of("2", "%", "", "0.9")), rows);
        assertEquals(thresholds, CnecEdits.parseThresholds(rows));
        assertEquals(List.of(new ThresholdSpec(TwoSides.TWO, Unit.MEGAWATT, 1., null)), CnecEdits.parseThresholds(List.of(List.of("2", "megawatt", "1", ""))));

        assertThrows(IllegalArgumentException.class, () -> CnecEdits.parseThresholds(List.of(List.of("3", "A", "1", ""))));
        assertThrows(IllegalArgumentException.class, () -> CnecEdits.parseThresholds(List.of(List.of("1", "kV", "1", ""))));
        assertThrows(IllegalArgumentException.class, () -> CnecEdits.parseThresholds(List.of(List.of("1", "A", "", ""))));
        assertThrows(IllegalArgumentException.class, () -> CnecEdits.parseThresholds(List.of(List.of("1", "A", "x", ""))));
    }
}
