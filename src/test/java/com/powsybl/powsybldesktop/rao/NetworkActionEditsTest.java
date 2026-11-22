/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.networkaction.ActionType;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.api.usagerule.UsageRule;
import com.powsybl.powsybldesktop.rao.NetworkActionEdits.ElementaryActionSpec;
import com.powsybl.powsybldesktop.rao.NetworkActionEdits.Kind;
import com.powsybl.powsybldesktop.rao.NetworkActionEdits.NetworkActionSpec;
import com.powsybl.powsybldesktop.utils.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class NetworkActionEditsTest {

    private final Network network = RaoTestCases.network();
    private final Crac crac = RaoTestCases.crac(network);

    // two switches in VL1, the first closed, the second open
    @BeforeEach
    void addSwitches() {
        VoltageLevel vl1 = network.getVoltageLevel("VL1");
        vl1.getBusBreakerView().newBus().setId("B1b").add();
        vl1.getBusBreakerView().newSwitch().setId("SW1").setBus1("B1").setBus2("B1b").setOpen(false).add();
        vl1.getBusBreakerView().newSwitch().setId("SW2").setBus1("B1").setBus2("B1b").setOpen(true).add();
    }

    private static NetworkActionSpec spec(String id, ElementaryActionSpec... actions) {
        return new NetworkActionSpec(id, null, null, null, null, List.of(actions));
    }

    @Test
    void readsTheActionsOpenRaoMakes() {
        crac.newNetworkAction().withId("all").withName("All kinds").withOperator("RTE").withSpeed(10).withActivationCost(5.)
                .newSwitchAction().withNetworkElement("SW1").withActionType(ActionType.OPEN).add()
                .newTerminalsConnectionAction().withNetworkElement("L2").withActionType(ActionType.CLOSE).add()
                .newPhaseTapChangerTapPositionAction().withNetworkElement("PS1").withTapPosition(2).add()
                .newGeneratorAction().withNetworkElement("G1").withActivePowerValue(80.).add()
                .newShuntCompensatorPositionAction().withNetworkElement("SH").withSectionCount(1).add()
                .newSwitchPair().withSwitchToOpen("SW3").withSwitchToClose("SW4").add()
                .newAcEmulationDeactivationAction().withNetworkElement("HVDC").add()
                .newBoundaryLineAction().withNetworkElement("BL").withActivePowerValue(-5.).add()
                .add();
        NetworkActionSpec spec = NetworkActionSpec.of(crac.getNetworkAction("all")).orElseThrow();
        assertEquals(new NetworkActionSpec("all", "All kinds", "RTE", 10, 5., List.of(ElementaryActionSpec.switchAction("SW1", true),
                ElementaryActionSpec.terminals("L2", false), ElementaryActionSpec.pstTap("PS1", 2), ElementaryActionSpec.setPoint(Kind.GENERATOR, "G1", 80.),
                ElementaryActionSpec.setPoint(Kind.BOUNDARY_LINE, "BL", -5.), ElementaryActionSpec.shunt("SH", 1),
                ElementaryActionSpec.switchPair("SW3", "SW4"), ElementaryActionSpec.acEmulationOff("HVDC"))), spec);

        List<List<String>> rows = NetworkActionEdits.actionRows(spec.actions());
        assertEquals(List.of(List.of("switch", "SW1", "open"), List.of("terminals", "L2", "close"), List.of("pstTap", "PS1", "2"),
                List.of("generator", "G1", "80"), List.of("boundaryLine", "BL", "-5"), List.of("shunt", "SH", "1"), List.of("switchPair", "SW3", "SW4"),
                List.of("acEmulationOff", "HVDC", "")), rows);
        assertEquals(spec.actions(), NetworkActionEdits.parseActions(rows));
    }

    @Test
    void parsesTheRowsByCodeOrLabel() {
        assertEquals(List.of(ElementaryActionSpec.switchAction("SW1", false), ElementaryActionSpec.setPoint(Kind.LOAD, "LD2", 12.5)),
                NetworkActionEdits.parseActions(List.of(List.of("SWITCH", "SW1", "Close"), List.of(Kind.LOAD.label(), "LD2", "12.5"))));
        assertThrows(IllegalArgumentException.class, () -> NetworkActionEdits.parseActions(List.of(List.of("teleport", "SW1", ""))));
        assertThrows(IllegalArgumentException.class, () -> NetworkActionEdits.parseActions(List.of(List.of("switch", "SW1", "ajar"))));
        assertThrows(IllegalArgumentException.class, () -> NetworkActionEdits.parseActions(List.of(List.of("pstTap", "PS1", "1.5"))));
        assertThrows(IllegalArgumentException.class, () -> NetworkActionEdits.parseActions(List.of(List.of("load", "", "1"))));
        assertThrows(IllegalArgumentException.class, () -> NetworkActionEdits.parseActions(List.of(List.of("switchPair", "SW1", ""))));
    }

    @Test
    void startsFromTheElementsState() {
        assertEquals(ElementaryActionSpec.switchAction("SW1", true), NetworkActionEdits.defaultAction(network.getSwitch("SW1")).orElseThrow());
        assertEquals(ElementaryActionSpec.switchAction("SW2", false), NetworkActionEdits.defaultAction(network.getSwitch("SW2")).orElseThrow());
        assertEquals(ElementaryActionSpec.pstTap("PS1", 1), NetworkActionEdits.defaultAction(network.getTwoWindingsTransformer("PS1")).orElseThrow());
        assertEquals(ElementaryActionSpec.setPoint(Kind.GENERATOR, "G1", 100.), NetworkActionEdits.defaultAction(network.getGenerator("G1")).orElseThrow());
        assertEquals(ElementaryActionSpec.setPoint(Kind.LOAD, "LD2", 100.), NetworkActionEdits.defaultAction(network.getLoad("LD2")).orElseThrow());
        assertEquals(ElementaryActionSpec.terminals("L1", true), NetworkActionEdits.defaultAction(network.getLine("L1")).orElseThrow());
        assertTrue(NetworkActionEdits.defaultAction(network.getVoltageLevel("VL1")).isEmpty());
        assertTrue(NetworkActionEdits.elementCandidates(network).anyMatch(identifiable -> identifiable.getId().equals("SW1")));
        assertEquals("Network action 1", NetworkActionEdits.newId(crac, "Network action"));
    }

    @Test
    void addsCheckingTheElements() {
        NetworkAction added = NetworkActionEdits.add(crac, network, spec("topo", ElementaryActionSpec.switchAction("SW1", true),
                ElementaryActionSpec.pstTap("PS1", 0)), List.of());
        assertSame(added, crac.getNetworkAction("topo"));
        assertEquals("topo", added.getName());
        assertEquals(Set.of("SW1", "PS1"), added.getNetworkElementsIds());
        // an element missing from the network is kept
        NetworkActionEdits.add(crac, network, spec("elsewhere", ElementaryActionSpec.switchAction("not here", true)), List.of());

        assertEquals(Messages.get("networkActionEdits.exists", RaoTestCases.PST), assertThrows(IllegalArgumentException.class,
                () -> NetworkActionEdits.add(crac, network, spec(RaoTestCases.PST, ElementaryActionSpec.switchAction("SW1", true)), List.of())).getMessage());
        assertEquals(Messages.get("networkActionEdits.noAction"),
                assertThrows(IllegalArgumentException.class, () -> NetworkActionEdits.add(crac, network, spec("none"), List.of())).getMessage());
        assertEquals(Messages.get("networkActionEdits.wrongElement", "L1", Kind.SWITCH.label()), assertThrows(IllegalArgumentException.class,
                () -> NetworkActionEdits.add(crac, network, spec("wrong", ElementaryActionSpec.switchAction("L1", true)), List.of())).getMessage());
        assertEquals(Messages.get("networkActionEdits.twice", "SW1"), assertThrows(IllegalArgumentException.class,
                () -> NetworkActionEdits.add(crac, network, spec("twice", ElementaryActionSpec.switchAction("SW1", true),
                        ElementaryActionSpec.switchPair("SW2", "SW1")), List.of())).getMessage());
        assertThrows(IllegalArgumentException.class, () -> NetworkActionEdits.add(crac, network, spec("tap", ElementaryActionSpec.pstTap("PS1", 50)), List.of()));
        assertThrows(IllegalArgumentException.class, () -> NetworkActionEdits.add(crac, network, spec("pair", ElementaryActionSpec.switchPair("SW1", "SW1")), List.of()));
        assertNull(crac.getRemedialAction("tap"));
    }

    @Test
    void replacesKeepingTheUsageRules() {
        NetworkAction loadShedding = crac.getNetworkAction(RaoTestCases.LOAD_SHEDDING);
        crac.newNetworkAction().withId("rules").newLoadAction().withNetworkElement("LD2").withActivePowerValue(1.).add()
                .newOnInstantUsageRule().withInstant("preventive").add()
                .newOnConstraintUsageRule().withCnec("L1 curative").withInstant("curative").add()
                .newOnFlowConstraintInCountryUsageRule().withCountry(Country.FR).withInstant("curative").withContingency(RaoTestCases.CONTINGENCY).add()
                .newOnContingencyStateUsageRule().withContingency(RaoTestCases.CONTINGENCY).withInstant("curative").add()
                .add();
        NetworkAction rules = crac.getNetworkAction("rules");
        Set<UsageRule> usageRules = new HashSet<>(rules.getUsageRules());

        // unchanged: the same
        NetworkActionSpec spec = NetworkActionSpec.of(rules).orElseThrow();
        assertSame(rules, NetworkActionEdits.change(crac, network, rules, spec));

        NetworkActionSpec renamed = new NetworkActionSpec("rules 2", "Rules", "operator", 3, null, List.of(ElementaryActionSpec.switchAction("SW2", false)));
        NetworkAction replaced = NetworkActionEdits.change(crac, network, rules, renamed);
        assertNotSame(rules, replaced);
        assertNull(crac.getRemedialAction("rules"));
        assertEquals(renamed, NetworkActionSpec.of(replaced).orElseThrow());
        assertEquals(usageRules, new HashSet<>(replaced.getUsageRules()));

        // refused: put back as it was
        assertThrows(IllegalArgumentException.class, () -> NetworkActionEdits.change(crac, network, replaced,
                new NetworkActionSpec("rules 2", null, null, null, null, List.of(ElementaryActionSpec.pstTap("PS1", 50)))));
        NetworkAction putBack = crac.getNetworkAction("rules 2");
        assertEquals(renamed, NetworkActionSpec.of(putBack).orElseThrow());
        assertEquals(usageRules, new HashSet<>(putBack.getUsageRules()));
        assertThrows(IllegalArgumentException.class, () -> NetworkActionEdits.change(crac, network, putBack,
                new NetworkActionSpec(RaoTestCases.LOAD_SHEDDING, null, null, null, null, renamed.actions())));
        assertSame(loadShedding, crac.getNetworkAction(RaoTestCases.LOAD_SHEDDING));

        NetworkActionEdits.remove(crac, "rules 2");
        assertNull(crac.getRemedialAction("rules 2"));
    }
}
