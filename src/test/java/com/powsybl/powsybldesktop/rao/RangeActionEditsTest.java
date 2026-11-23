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
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.range.RangeType;
import com.powsybl.openrao.data.crac.api.rangeaction.PstRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.RangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.VariationDirection;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.ConnectedAreaSpec;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.KeyedElement;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.PstTaps;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.RangeActionSpec;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.RangeSpec;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.Type;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits.UsageRuleSpec;
import com.powsybl.powsybldesktop.utils.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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
class RangeActionEditsTest {

    private static final Map<Integer, Double> PS1_ANGLES = Map.of(0, -20., 1, 0., 2, 20.);

    private final Network network = RaoTestCases.network();
    private final Crac crac = RaoTestCases.crac(network);

    private static RangeActionSpec injection(String id, List<KeyedElement> keys, List<RangeSpec> ranges) {
        return injection(id, keys, null, ranges);
    }

    private static RangeActionSpec injection(String id, List<KeyedElement> keys, Double initialSetpoint, List<RangeSpec> ranges) {
        return new RangeActionSpec(Type.INJECTION, id, null, null, null, null, null, null, null, keys, initialSetpoint, null, null, List.of(), ranges);
    }

    private static RangeActionSpec pst(String id, String transformerId, List<RangeSpec> ranges) {
        return new RangeActionSpec(Type.PST, id, null, null, null, null, null, null, null, List.of(new KeyedElement(transformerId, 1)), null, null, null,
                List.of(), ranges);
    }

    private String refused(RangeActionSpec spec, List<UsageRuleSpec> usageRules) {
        return assertThrows(IllegalArgumentException.class, () -> RangeActionEdits.add(crac, network, spec, usageRules)).getMessage();
    }

    @Test
    void readsTheActionsOpenRaoMakes() {
        RangeActionSpec pst = RangeActionSpec.of(crac.getRangeAction(RaoTestCases.PST)).orElseThrow();
        assertEquals(new RangeActionSpec(Type.PST, RaoTestCases.PST, RaoTestCases.PST, null, null, null, null, null, null, List.of(new KeyedElement("PS1", 1)),
                null, new PstTaps(1, PS1_ANGLES), null, List.of(), List.of(new RangeSpec(RangeType.ABSOLUTE, 0., 2.))), pst);

        crac.newCounterTradeRangeAction().withId("ct").withName("Counter-trade").withOperator("RTE").withSpeed(5).withActivationCost(10.)
                .withGroupId("g").withVariationCost(2., VariationDirection.UP).withInitialSetpoint(0.).withAreaId("FR")
                .newRange().withRangeType(RangeType.RELATIVE_TO_INITIAL_NETWORK).withMax(200.).add()
                .newConnectedArea().withAreaId("BE").newBorderRange().withMin(-100.).withMax(100.).add().newBorderRange().withMax(50.).add().add()
                .newConnectedArea().withAreaId("DE").add()
                .add();
        RangeActionSpec counterTrade = RangeActionSpec.of(crac.getRangeAction("ct")).orElseThrow();
        // OpenRAO's adder sets Double.MIN_VALUE as the missing min of a range: told as is
        assertEquals(new RangeActionSpec(Type.COUNTER_TRADE, "ct", "Counter-trade", "RTE", 5, 10., "g", 2., null, List.of(), 0., null, "FR",
                List.of(new ConnectedAreaSpec("BE", List.of(new RangeSpec(RangeType.ABSOLUTE, -100., 100.), new RangeSpec(RangeType.ABSOLUTE, null, 50.))),
                        new ConnectedAreaSpec("DE", List.of())),
                List.of(new RangeSpec(RangeType.RELATIVE_TO_INITIAL_NETWORK, Double.MIN_VALUE, 200.))), counterTrade);

        List<List<String>> areaRows = RangeActionEdits.connectedAreaRows(counterTrade.connectedAreas());
        assertEquals(List.of(List.of("BE", "absolute", "-100", "100"), List.of("BE", "absolute", "", "50"), List.of("DE", "", "", "")), areaRows);
        assertEquals(counterTrade.connectedAreas(), RangeActionEdits.parseConnectedAreas(areaRows));
        List<List<String>> rangeRows = RangeActionEdits.rangeRows(pst.ranges());
        assertEquals(List.of(List.of("absolute", "0", "2")), rangeRows);
        assertEquals(pst.ranges(), RangeActionEdits.parseRanges(rangeRows));
        assertEquals(List.of(new RangeSpec(RangeType.RELATIVE_TO_PREVIOUS_INSTANT, null, 3.)),
                RangeActionEdits.parseRanges(List.of(List.of(RangeActionEdits.label(RangeType.RELATIVE_TO_PREVIOUS_INSTANT), "", "3"))));
        assertThrows(IllegalArgumentException.class, () -> RangeActionEdits.parseRanges(List.of(List.of("sideways", "0", "1"))));
        assertThrows(IllegalArgumentException.class, () -> RangeActionEdits.parseKeys(List.of(List.of("G1", ""))));
    }

    @Test
    void makesThemFromTheNetwork() {
        assertEquals(new RangeActionSpec(Type.PST, "p", null, null, null, null, null, null, null, List.of(new KeyedElement("PS1", 1)), null,
                new PstTaps(1, PS1_ANGLES), null, List.of(), List.of(new RangeSpec(RangeType.ABSOLUTE, 0., 2.))), RangeActionEdits.pst(network, "p", "PS1"));
        assertThrows(IllegalArgumentException.class, () -> RangeActionEdits.pst(network, "p", "L1"));

        // from side 2 to side 1: negative
        Network hvdcNetwork = HvdcTestNetwork.createVsc();
        assertEquals(new RangeActionSpec(Type.HVDC, "h", null, null, null, null, null, null, null, List.of(new KeyedElement("L", 1)), -280., null, null,
                List.of(), List.of(new RangeSpec(RangeType.ABSOLUTE, -300., 300.))), RangeActionEdits.hvdc(hvdcNetwork, "h", "L"));

        // a single generator within its limits; a load counted negatively
        assertEquals(injection("i", List.of(new KeyedElement("G1", 1)), 100., List.of(new RangeSpec(RangeType.ABSOLUTE, 50., 150.))),
                RangeActionEdits.injection(network, "i", List.of("G1", "VL1")));
        assertEquals(injection("i", List.of(new KeyedElement("LD2", 1)), -100.,
                List.of(new RangeSpec(RangeType.RELATIVE_TO_INITIAL_NETWORK, -100., 100.))), RangeActionEdits.injection(network, "i", List.of("LD2")));
        network.getLoad("LD2").setP0(300.);
        assertEquals(injection("i", List.of(new KeyedElement("G1", -0.5), new KeyedElement("LD2", 1.5)), -200.,
                List.of(new RangeSpec(RangeType.RELATIVE_TO_INITIAL_NETWORK, -100., 100.))),
                RangeActionEdits.injection(network, "i", List.of("G1", "LD2")));
        assertThrows(IllegalArgumentException.class, () -> RangeActionEdits.injection(network, "i", List.of("L1")));

        Network twoCountries = EurostagTutorialExample1Factory.create();
        twoCountries.getSubstation("P2").setCountry(Country.BE);
        assertEquals(List.of(Country.BE, Country.FR), RangeActionEdits.countries(twoCountries));
        assertEquals(new RangeActionSpec(Type.COUNTER_TRADE, "c", null, null, null, null, null, null, null, List.of(), 0., null, "FR",
                List.of(new ConnectedAreaSpec("BE", List.of())), List.of(new RangeSpec(RangeType.ABSOLUTE, -100., 100.))),
                RangeActionEdits.counterTrade(twoCountries, "c", Country.FR));
        // a single country: no neighbour
        RangeAction<?> alone = RangeActionEdits.add(crac, network, RangeActionEdits.counterTrade(network, "c", Country.FR), List.of());
        assertEquals(List.of(), RangeActionSpec.of(alone).orElseThrow().connectedAreas());
        // listed with the other remedial actions, but neither changed nor removed
        assertTrue(CracElements.remedialActions(crac).anyMatch(action -> action == alone));
        RangeActionSpec aloneSpec = RangeActionSpec.of(alone).orElseThrow();
        assertEquals(Messages.get("rangeActionEdits.counterTradeFixed"), assertThrows(IllegalArgumentException.class, () -> RangeActionEdits.change(crac, network,
                alone, new RangeActionSpec(Type.COUNTER_TRADE, "d", null, null, null, null, null, null, null, List.of(), 0., null, "FR", List.of(),
                aloneSpec.ranges()))).getMessage());
        assertThrows(IllegalArgumentException.class, () -> RangeActionEdits.remove(crac, "c"));
        RangeActionEdits.remove(crac, RaoTestCases.PST);
        assertNull(crac.getRangeAction(RaoTestCases.PST));
    }

    @Test
    void addsCheckingThem() {
        RangeAction<?> added = RangeActionEdits.add(crac, network, pst("p", "PS1", List.of(new RangeSpec(RangeType.RELATIVE_TO_INITIAL_NETWORK, -1., null))),
                List.of());
        assertSame(added, crac.getRangeAction("p"));
        // the taps from the network, the missing bound none
        assertEquals(1, ((PstRangeAction) added).getInitialTap());
        assertEquals(List.of(new RangeSpec(RangeType.RELATIVE_TO_INITIAL_NETWORK, -1., null)), RangeActionSpec.of(added).orElseThrow().ranges());
        // the missing min of a standard range: unbounded
        RangeAction<?> injection = RangeActionEdits.add(crac, network, injection("i", List.of(new KeyedElement("G1", 1)),
                List.of(new RangeSpec(RangeType.RELATIVE_TO_INITIAL_NETWORK, null, 10.))), List.of());
        assertEquals(List.of(new RangeSpec(RangeType.RELATIVE_TO_INITIAL_NETWORK, null, 10.)), RangeActionSpec.of(injection).orElseThrow().ranges());
        // an HVDC line missing from the network is kept
        RangeActionEdits.add(crac, network, new RangeActionSpec(Type.HVDC, "h", null, null, null, null, null, null, null, List.of(new KeyedElement("HVDC", 1)),
                null, null, null, List.of(), List.of(new RangeSpec(RangeType.ABSOLUTE, -1., 1.))), List.of());

        List<RangeSpec> taps = List.of(new RangeSpec(RangeType.ABSOLUTE, 0., 2.));
        assertEquals(Messages.get("networkActionEdits.exists", "p"), refused(pst("p", "PS1", taps), List.of()));
        assertEquals(Messages.get("rangeActionEdits.wrongElement", "L1", Type.PST.label()), refused(pst("q", "L1", taps), List.of()));
        assertEquals(Messages.get("rangeActionEdits.notInNetwork", "PS9", Type.PST.label()), refused(pst("q", "PS9", taps), List.of()));
        assertEquals(Messages.get("rangeActionEdits.tapNotInteger", "0.5"), refused(pst("q", "PS1", List.of(new RangeSpec(RangeType.ABSOLUTE, 0.5, 2.))), List.of()));
        assertEquals(Messages.get("rangeActionEdits.minAboveMax", "2", "0"), refused(pst("q", "PS1", List.of(new RangeSpec(RangeType.ABSOLUTE, 2., 0.))), List.of()));
        assertEquals(Messages.get("rangeActionEdits.previousInstantPreventive"),
                refused(pst("q", "PS1", List.of(new RangeSpec(RangeType.RELATIVE_TO_PREVIOUS_INSTANT, -1., 1.))), List.of()));
        assertEquals(Messages.get("rangeActionEdits.rangeRequired"), refused(injection("j", List.of(new KeyedElement("G1", 1)), List.of()), List.of()));
        List<RangeSpec> range = List.of(new RangeSpec(RangeType.ABSOLUTE, 0., 10.));
        assertEquals(Messages.get("rangeActionEdits.absoluteBounds"),
                refused(injection("j", List.of(new KeyedElement("G1", 1)), List.of(new RangeSpec(RangeType.ABSOLUTE, 0., null))), List.of()));
        assertEquals(Messages.get("rangeActionEdits.zeroKey", "G1"), refused(injection("j", List.of(new KeyedElement("G1", 0)), range), List.of()));
        assertEquals(Messages.get("rangeActionEdits.wrongElement", "L1", Type.INJECTION.label()),
                refused(injection("j", List.of(new KeyedElement("L1", 1)), range), List.of()));
        assertEquals(Messages.get("rangeActionEdits.injectionRequired"), refused(injection("j", List.of(), range), List.of()));
        assertEquals(Messages.get("rangeActionEdits.ownArea", "FR"), refused(new RangeActionSpec(Type.COUNTER_TRADE, "c", null, null, null, null, null, null,
                null, List.of(), 0., null, "FR", List.of(new ConnectedAreaSpec("FR", List.of())), range), List.of()));
    }

    @Test
    void changesThemKeepingTheirUsageRules() {
        RangeAction<?> pst = crac.getRangeAction(RaoTestCases.PST);
        RangeActionSpec spec = RangeActionSpec.of(pst).orElseThrow();
        assertSame(pst, RangeActionEdits.change(crac, network, pst, spec));

        RangeActionSpec renamed = new RangeActionSpec(Type.PST, "taps", "Taps", "RTE", null, null, null, 1., 1., spec.elements(), null, spec.pstTaps(), null,
                List.of(), List.of(new RangeSpec(RangeType.ABSOLUTE, 0., 1.)));
        RangeAction<?> changed = RangeActionEdits.change(crac, network, pst, renamed);
        assertNotSame(pst, changed);
        assertNull(crac.getRemedialAction(RaoTestCases.PST));
        assertEquals(renamed, RangeActionSpec.of(changed).orElseThrow());
        assertEquals(Set.copyOf(pst.getUsageRules()).size(), changed.getUsageRules().size());
        assertEquals("preventive", changed.getUsageRules().iterator().next().getInstant().getId());

        // refused: put back as it was
        RangeActionSpec refused = new RangeActionSpec(Type.PST, "taps", null, null, null, null, null, null, null, List.of(new KeyedElement("L1", 1)), null,
                null, null, List.of(), renamed.ranges());
        assertThrows(IllegalArgumentException.class, () -> RangeActionEdits.change(crac, network, changed, refused));
        RangeAction<?> putBack = crac.getRangeAction("taps");
        assertNotSame(changed, putBack);
        assertEquals(renamed, RangeActionSpec.of(putBack).orElseThrow());
        assertEquals(1, putBack.getUsageRules().size());
    }
}
