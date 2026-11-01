/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.violations;

import java.util.Comparator;

/**
 * An equipment's (or one of its sides') loading percentage, and the most severe flow violation it comes from, null if
 * not violated.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record Loading(double percent, FlowViolation violation) implements Comparable<Loading> {

    /**
     * Most severe first: violations by {@link FlowViolation#SEVERITY}, then the highest loading.
     */
    private static final Comparator<Loading> SEVERITY = Comparator
            .comparing(Loading::violation, Comparator.nullsLast(FlowViolation.SEVERITY))
            .thenComparing(Loading::percent, Comparator.reverseOrder());

    public static Loading of(FlowViolation violation) {
        return new Loading(violation.percent(), violation);
    }

    public boolean isViolated() {
        return violation != null;
    }

    @Override
    public int compareTo(Loading other) {
        return SEVERITY.compare(this, other);
    }
}
