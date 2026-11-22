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
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.api.range.RangeType;
import com.powsybl.openrao.data.crac.api.rangeaction.PstRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.RangeAction;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.RangeActionSpec;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.RangeSpec;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits.Kind;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits.UsageRuleSpec;
import com.powsybl.powsybldesktop.utils.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class UsageRuleEditsTest {

    private final Network network = RaoTestCases.network();
    private final Crac crac = RaoTestCases.crac(network);

    private String refused(UsageRuleSpec rule) {
        return assertThrows(IllegalArgumentException.class, () -> UsageRuleEdits.check(crac, rule)).getMessage();
    }

    private String refused(RemedialAction<?> action, List<UsageRuleSpec> rules) {
        return assertThrows(IllegalArgumentException.class, () -> UsageRuleEdits.set(crac, network, action, rules)).getMessage();
    }

    @Test
    void readsEveryKindInOrder() {
        crac.newNetworkAction().withId("rules").newLoadAction().withNetworkElement("LD2").withActivePowerValue(1.).add()
                .newOnFlowConstraintInCountryUsageRule().withCountry(Country.FR).withInstant("curative").add()
                .newOnFlowConstraintInCountryUsageRule().withCountry(Country.FR).withInstant("curative").withContingency(RaoTestCases.CONTINGENCY).add()
                .newOnConstraintUsageRule().withCnec("L1 curative").withInstant("curative").add()
                .newOnContingencyStateUsageRule().withContingency(RaoTestCases.CONTINGENCY).withInstant("curative").add()
                .newOnInstantUsageRule().withInstant("preventive").add()
                .add();
        NetworkAction rules = crac.getNetworkAction("rules");
        List<UsageRuleSpec> specs = List.of(UsageRuleSpec.onInstant("preventive"), UsageRuleSpec.onState(RaoTestCases.CONTINGENCY, "curative"),
                UsageRuleSpec.onConstraint("L1 curative", "curative"), UsageRuleSpec.onCountry(Country.FR, null, "curative"),
                UsageRuleSpec.onCountry(Country.FR, RaoTestCases.CONTINGENCY, "curative"));
        assertEquals(specs, UsageRuleEdits.of(rules));
        // the same: unchanged
        assertSame(rules, UsageRuleEdits.set(crac, network, rules, specs.reversed()));
    }

    @Test
    void offersTheInstantsOfEachKind() {
        assertEquals(List.of("preventive", "curative"), UsageRuleEdits.instants(crac, Kind.ON_INSTANT).stream().map(Instant::getId).toList());
        assertEquals(List.of("curative"), UsageRuleEdits.instants(crac, Kind.ON_CONTINGENCY_STATE).stream().map(Instant::getId).toList());
    }

    @Test
    void checksThemAsOpenRaoDoes() {
        assertEquals(Messages.get("usageRuleEdits.instantRequired"), refused(UsageRuleSpec.onInstant(null)));
        assertEquals(Messages.get("usageRuleEdits.unknownInstant", "auto"), refused(UsageRuleSpec.onInstant("auto")));
        assertEquals(Messages.get("usageRuleEdits.outage", "outage"), refused(UsageRuleSpec.onInstant("outage")));
        assertEquals(Messages.get("usageRuleEdits.onStatePreventive", "preventive"), refused(UsageRuleSpec.onState(RaoTestCases.CONTINGENCY, "preventive")));
        assertEquals(Messages.get("usageRuleEdits.contingencyRequired"), refused(UsageRuleSpec.onState(null, "curative")));
        assertEquals(Messages.get("usageRuleEdits.unknownContingency", "c"), refused(UsageRuleSpec.onState("c", "curative")));
        assertEquals(Messages.get("usageRuleEdits.cnecRequired"), refused(UsageRuleSpec.onConstraint(null, "curative")));
        assertEquals(Messages.get("usageRuleEdits.unknownCnec", "c"), refused(UsageRuleSpec.onConstraint("c", "curative")));
        assertEquals(Messages.get("usageRuleEdits.cnecBefore", "L1 preventive", "preventive", "curative"),
                refused(UsageRuleSpec.onConstraint("L1 preventive", "curative")));
        assertEquals(Messages.get("usageRuleEdits.countryRequired"), refused(UsageRuleSpec.onCountry(null, null, "curative")));
        assertEquals(Messages.get("usageRuleEdits.unknownContingency", "c"), refused(UsageRuleSpec.onCountry(Country.FR, "c", "curative")));
        // a preventive action on a curative CNEC's constraint
        UsageRuleEdits.check(crac, UsageRuleSpec.onConstraint("L1 curative", "preventive"));
    }

    @Test
    void addsAndRemovesThemReplacingTheAction() {
        NetworkAction loadShedding = crac.getNetworkAction(RaoTestCases.LOAD_SHEDDING);
        NetworkAction both = (NetworkAction) UsageRuleEdits.add(crac, network, loadShedding, UsageRuleSpec.onInstant("preventive"));
        assertNotSame(loadShedding, both);
        assertSame(both, crac.getNetworkAction(RaoTestCases.LOAD_SHEDDING));
        assertEquals(List.of(UsageRuleSpec.onInstant("preventive"), UsageRuleSpec.onState(RaoTestCases.CONTINGENCY, "curative")), UsageRuleEdits.of(both));
        // the same action otherwise
        assertEquals(NetworkActionEdits.NetworkActionSpec.of(loadShedding), NetworkActionEdits.NetworkActionSpec.of(both));
        assertEquals(Messages.get("usageRuleEdits.duplicate"), assertThrows(IllegalArgumentException.class,
                () -> UsageRuleEdits.add(crac, network, both, UsageRuleSpec.onInstant("preventive"))).getMessage());

        NetworkAction none = (NetworkAction) UsageRuleEdits.set(crac, network, both, List.of());
        assertTrue(none.getUsageRules().isEmpty());

        RangeAction<?> pst = crac.getRangeAction(RaoTestCases.PST);
        RangeAction<?> curative = (RangeAction<?>) UsageRuleEdits.add(crac, network, pst, UsageRuleSpec.onConstraint("L1 curative", "curative"));
        assertEquals(List.of(UsageRuleSpec.onInstant("preventive"), UsageRuleSpec.onConstraint("L1 curative", "curative")), UsageRuleEdits.of(curative));
        assertEquals(RangeActionSpec.of(pst), RangeActionSpec.of(curative));
        RangeAction<?> curativeOnly = (RangeAction<?>) UsageRuleEdits.remove(crac, network, curative, UsageRuleSpec.onInstant("preventive"));
        assertEquals(List.of(UsageRuleSpec.onConstraint("L1 curative", "curative")), UsageRuleEdits.of(curativeOnly));
        assertTrue(crac.isRangeActionAutoOrCurative(curativeOnly));
    }

    @Test
    void refusesWhatOpenRaoWouldNotKeep() {
        NetworkAction loadShedding = crac.getNetworkAction(RaoTestCases.LOAD_SHEDDING);
        assertEquals(Messages.get("usageRuleEdits.outage", "outage"), refused(loadShedding, List.of(UsageRuleSpec.onInstant("outage"))));
        assertEquals(Messages.get("usageRuleEdits.duplicate"), refused(loadShedding, List.of(UsageRuleSpec.onInstant("curative"), UsageRuleSpec.onInstant("curative"))));
        // untouched
        assertSame(loadShedding, crac.getNetworkAction(RaoTestCases.LOAD_SHEDDING));

        // the range relative to the previous instant of a PST range action left only available at the preventive instant
        RangeAction<?> pst = RangeActionEdits.add(crac, network, new RangeActionSpec(RangeActionEdits.Type.PST, "relative", null, null, null, null, null, null, null,
                List.of(new RangeActionEdits.KeyedElement("PS1", 1)), null, null, null, List.of(), List.of(new RangeSpec(RangeType.RELATIVE_TO_PREVIOUS_INSTANT, -1., 1.))),
                List.of(UsageRuleSpec.onInstant("preventive"), UsageRuleSpec.onState(RaoTestCases.CONTINGENCY, "curative")));
        assertEquals(1, ((PstRangeAction) pst).getRanges().size());
        assertEquals(Messages.get("rangeActionEdits.previousInstantPreventive"), refused(pst, List.of(UsageRuleSpec.onInstant("preventive"))));
        assertSame(pst, crac.getRangeAction("relative"));

        // a counter-trade, which OpenRAO can't remove
        crac.newCounterTradeRangeAction().withId("ct").withAreaId("FR").withInitialSetpoint(0.)
                .newRange().withRangeType(RangeType.ABSOLUTE).withMin(-1.).withMax(1.).add().add();
        assertEquals(Messages.get("rangeActionEdits.counterTradeFixed"), refused(crac.getCounterTradeRangeAction("ct"), List.of(UsageRuleSpec.onInstant("preventive"))));
    }
}
