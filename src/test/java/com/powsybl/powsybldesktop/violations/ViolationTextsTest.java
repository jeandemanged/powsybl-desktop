/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.violations;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.ThreeSides;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class ViolationTextsTest {

    @Test
    void minutesTextShowsUpToTwoDecimals() {
        assertEquals("10'", ViolationTexts.minutesText(600));
        assertEquals("1.5'", ViolationTexts.minutesText(90));
        assertEquals("0.98'", ViolationTexts.minutesText(59));
    }

    @Test
    void acceptableDurationTextDoesntGroupDigits() {
        assertEquals("permanent", ViolationTexts.acceptableDurationText(Integer.MAX_VALUE));
        assertEquals("2400 s", ViolationTexts.acceptableDurationText(2400));
    }

    @Test
    void flowViolationTextDescribesTheCrossedLimit() {
        LimitViolation temporary = LimitViolation.builder().subject("L").type(LimitViolationType.CURRENT).limitName("10'").limit(10).value(11)
                .side(ThreeSides.TWO).duration(600).build();
        assertEquals("Current limit 10' crossed on side 2, acceptable duration: 600 s",
                ViolationTexts.flowViolationText(new FlowViolation(temporary, 8)));

        LimitViolation unnamed = LimitViolation.builder().subject("L").type(LimitViolationType.CURRENT).limit(10).value(11)
                .side(ThreeSides.ONE).duration(Integer.MAX_VALUE).build();
        assertEquals("Current limit - crossed on side 1, acceptable duration: permanent",
                ViolationTexts.flowViolationText(new FlowViolation(unnamed, 10)));
    }
}
