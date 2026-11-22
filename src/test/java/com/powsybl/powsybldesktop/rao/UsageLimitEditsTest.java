/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.RaUsageLimits;
import com.powsybl.powsybldesktop.rao.UsageLimitEdits.TsoLimits;
import com.powsybl.powsybldesktop.rao.UsageLimitEdits.UsageLimitsSpec;
import com.powsybl.powsybldesktop.utils.Messages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class UsageLimitEditsTest {

    private final Crac crac = RaoTestCases.crac(RaoTestCases.network());
    private final Instant curative = crac.getInstant("curative");

    private String refused(Instant instant, UsageLimitsSpec spec) {
        return assertThrows(IllegalArgumentException.class, () -> UsageLimitEdits.set(crac, instant, spec)).getMessage();
    }

    @Test
    void setsAndReadsThem() {
        assertTrue(UsageLimitEdits.of(crac, curative).isEmpty());

        UsageLimitsSpec spec = new UsageLimitsSpec(3, List.of(new TsoLimits("RTE", 1, null, 2, null), new TsoLimits("ELIA", null, 1, null, 4)));
        UsageLimitEdits.set(crac, curative, spec);
        RaUsageLimits limits = crac.getRaUsageLimits(curative);
        assertEquals(3, limits.getMaxRa());
        assertEquals(Map.of("RTE", 1), limits.getMaxTopoPerTso());
        assertEquals(Map.of("ELIA", 1), limits.getMaxPstPerTso());
        assertEquals(Map.of("RTE", 2), limits.getMaxRaPerTso());
        assertEquals(Map.of("ELIA", 4), limits.getMaxElementaryActionsPerTso());
        // the TSOs sorted
        UsageLimitsSpec read = UsageLimitEdits.of(crac, curative);
        assertEquals(new UsageLimitsSpec(3, List.of(spec.perTso().get(1), spec.perTso().get(0))), read);
        assertEquals(List.of(List.of("ELIA", "", "1", "", "4"), List.of("RTE", "1", "", "2", "")), UsageLimitEdits.tsoRows(read.perTso()));
        assertEquals(read.perTso(), UsageLimitEdits.parseTsoRows(UsageLimitEdits.tsoRows(read.perTso())));

        // none: no usage limits for the instant
        UsageLimitEdits.set(crac, curative, new UsageLimitsSpec(null, List.of()));
        assertFalse(crac.getRaUsageLimitsPerInstant().containsKey(curative));
    }

    @Test
    void refusesInconsistentLimits() {
        UsageLimitEdits.set(crac, curative, new UsageLimitsSpec(2, List.of()));

        assertEquals(Messages.get("usageLimitEdits.outage", "outage"), refused(crac.getInstant("outage"), new UsageLimitsSpec(1, List.of())));
        assertEquals(Messages.get("usageLimitEdits.negative", -1), refused(curative, new UsageLimitsSpec(-1, List.of())));
        assertEquals(Messages.get("usageLimitEdits.negative", -2), refused(curative, new UsageLimitsSpec(null, List.of(new TsoLimits("RTE", null, -2, null, null)))));
        assertEquals(Messages.get("usageLimitEdits.tsoRequired"), refused(curative, new UsageLimitsSpec(null, List.of(new TsoLimits(" ", 1, null, null, null)))));
        assertEquals(Messages.get("usageLimitEdits.tsoTwice", "RTE"), refused(curative, new UsageLimitsSpec(null,
                List.of(new TsoLimits("RTE", 1, null, null, null), new TsoLimits("RTE", null, 1, null, null)))));
        // OpenRAO's: fewer remedial actions than topological actions
        refused(curative, new UsageLimitsSpec(null, List.of(new TsoLimits("RTE", 2, null, 1, null))));
        // kept as they were
        assertEquals(new UsageLimitsSpec(2, List.of()), UsageLimitEdits.of(crac, curative));

        assertThrows(IllegalArgumentException.class, () -> UsageLimitEdits.parseTsoRows(List.of(List.of("RTE", "x"))));
        assertThrows(IllegalArgumentException.class, () -> UsageLimitEdits.parseTsoRows(List.of(List.of("", "1"))));
    }
}
