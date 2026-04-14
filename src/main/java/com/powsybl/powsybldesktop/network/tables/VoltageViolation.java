/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.utils.Messages;

import java.util.Locale;

/**
 * Whether a measured voltage magnitude breaches its voltage level's configured
 * {@link VoltageLevel#getHighVoltageLimit()}/{@link VoltageLevel#getLowVoltageLimit()}. {@link #NONE} both when the
 * voltage is unknown (NaN, e.g. no load flow run yet) and when no limit is configured on that side. Sorts by how far
 * the voltage strays from its nominal value (percent-of-nominal distance from 100%), not by raw percentage - e.g. a
 * 95% (5 points away) bus outranks a 102% (2 points away) one. Shared by every equipment table whose "Solved Values"
 * toolbar group flags an out-of-limits voltage, via {@link TableColumnSupport#configureVoltageViolationColumn}
 * ({@link AbstractBusesController}, {@link BusbarSectionsController}).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class VoltageViolation implements Comparable<VoltageViolation> {
    private static final VoltageViolation NONE = new VoltageViolation(null, 0, Double.NaN);

    private final String messageKey;
    private final long percentOfNominal;
    private final double limit;

    private VoltageViolation(String messageKey, long percentOfNominal, double limit) {
        this.messageKey = messageKey;
        this.percentOfNominal = percentOfNominal;
        this.limit = limit;
    }

    static VoltageViolation of(double v, VoltageLevel voltageLevel) {
        if (Double.isNaN(v)) {
            return NONE;
        }
        long percentOfNominal = Math.round(v / voltageLevel.getNominalV() * 100);
        double highVoltageLimit = voltageLevel.getHighVoltageLimit();
        if (!Double.isNaN(highVoltageLimit) && v > highVoltageLimit) {
            return new VoltageViolation("buses.voltageViolation.overvoltage", percentOfNominal, highVoltageLimit);
        }
        double lowVoltageLimit = voltageLevel.getLowVoltageLimit();
        if (!Double.isNaN(lowVoltageLimit) && v < lowVoltageLimit) {
            return new VoltageViolation("buses.voltageViolation.undervoltage", percentOfNominal, lowVoltageLimit);
        }
        return NONE;
    }

    boolean isViolation() {
        return messageKey != null;
    }

    String text() {
        return Messages.get(messageKey, percentOfNominal);
    }

    String limitTooltip() {
        return Messages.get("buses.voltageViolation.limitTooltip", String.format(Locale.ROOT, "%.2f", limit));
    }

    @Override
    public int compareTo(VoltageViolation other) {
        return Long.compare(distanceFromNominal(), other.distanceFromNominal());
    }

    private long distanceFromNominal() {
        return isViolation() ? Math.abs(percentOfNominal - 100) : 0;
    }
}
