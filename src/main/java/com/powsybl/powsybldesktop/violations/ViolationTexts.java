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
import com.powsybl.powsybldesktop.utils.Messages;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * How limit violations read in the views.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class ViolationTexts {

    private ViolationTexts() {
    }

    /**
     * {@code seconds} in minutes, e.g. "10'" or "1.5'", as the acceptable duration of a temporary limit violation.
     */
    public static String minutesText(int seconds) {
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "'";
    }

    public static String flowViolationText(FlowViolation violation) {
        LimitViolation limitViolation = violation.violation();
        return Messages.get("violations.flowTooltip", limitViolationTypeText(limitViolation.getLimitType()),
                Objects.requireNonNullElse(limitViolation.getLimitName(), "-"),
                limitViolation.getSide() == null ? "-" : limitViolation.getSide().getNum(), acceptableDurationText(limitViolation.getAcceptableDuration()));
    }

    // as a string: MessageFormat would group the digits, e.g. "2,400 s"
    public static String acceptableDurationText(int acceptableDuration) {
        return acceptableDuration == Integer.MAX_VALUE ? Messages.get("limits.permanent") : Messages.get("limits.duration", String.valueOf(acceptableDuration));
    }

    public static String limitViolationTypeText(LimitViolationType type) {
        return Messages.get("desktop.common.limitViolationType." + type.name());
    }
}
