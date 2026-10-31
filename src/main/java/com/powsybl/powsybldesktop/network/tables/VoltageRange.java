/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.violations.VoltageViolation;

import java.util.Comparator;

/**
 * A bus voltage within its voltage level's limits (NaN when not set), for the "Voltage Range" columns
 * ({@link TableColumnSupport#configureVoltageRangeColumn}), with the bus' cached voltage violation, if any.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
record VoltageRange(double v, double nominalV, double lowLimit, double highLimit, VoltageViolation violation)
        implements Comparable<VoltageRange> {

    /**
     * Most severe first: violations by {@link VoltageViolation#SEVERITY}, then the farthest from the nominal voltage.
     */
    private static final Comparator<VoltageRange> SEVERITY = Comparator
            .comparing(VoltageRange::violation, Comparator.nullsLast(VoltageViolation.SEVERITY))
            .thenComparing(range -> Math.abs(range.percentOfNominal() - 100), Comparator.reverseOrder());

    /**
     * Null if {@code v} is unknown, e.g. before any load flow run.
     */
    static VoltageRange of(double v, VoltageLevel voltageLevel, VoltageViolation violation) {
        return Double.isNaN(v) ? null
                : new VoltageRange(v, voltageLevel.getNominalV(), voltageLevel.getLowVoltageLimit(), voltageLevel.getHighVoltageLimit(), violation);
    }

    double percentOfNominal() {
        return v / nominalV * 100;
    }

    @Override
    public int compareTo(VoltageRange other) {
        return SEVERITY.compare(this, other);
    }
}
