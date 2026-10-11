/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.violations;

import com.powsybl.contingency.violations.LimitViolation;

import java.util.Comparator;
import java.util.Objects;

/**
 * A current, active power or apparent power limit violation on one side of an equipment, with the permanent limit of
 * the same side, limit type and operational limits group: the loading percentage is always relative to it, whichever
 * limit (permanent or temporary) is crossed. The permanent limit is NaN for a {@code DetectionKind.LOW} limit set,
 * which has none: the percentage is then relative to the crossed limit itself.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record FlowViolation(LimitViolation violation, double permanentLimit) implements Comparable<FlowViolation> {

    /**
     * Most severe first: the shortest acceptable duration (a permanent limit violation's being
     * {@link Integer#MAX_VALUE}), then the highest percentage, a NaN one last.
     */
    public static final Comparator<FlowViolation> SEVERITY = Comparator
            .comparingInt((FlowViolation v) -> v.violation().getAcceptableDuration())
            .thenComparing(FlowViolation::sortablePercent, Comparator.reverseOrder());

    public FlowViolation {
        Objects.requireNonNull(violation);
    }

    public double percent() {
        double referenceLimit = Double.isNaN(permanentLimit) ? violation.getLimit() : permanentLimit;
        return Math.abs(violation.getValue()) / referenceLimit * 100;
    }

    /**
     * By {@link #SEVERITY}: the most severe first.
     */
    @Override
    public int compareTo(FlowViolation other) {
        return SEVERITY.compare(this, other);
    }

    private double sortablePercent() {
        double percent = percent();
        return Double.isNaN(percent) ? Double.NEGATIVE_INFINITY : percent;
    }
}
