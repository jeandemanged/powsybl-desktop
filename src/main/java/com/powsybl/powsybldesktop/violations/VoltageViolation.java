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
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;

import java.util.Comparator;
import java.util.Objects;

/**
 * A high or low voltage limit violation of one bus of the bus view. The {@link LimitViolation}'s subject is the
 * voltage level, as built by PowSyBl's own detection.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record VoltageViolation(LimitViolation violation, String busId, double nominalV) implements Comparable<VoltageViolation> {

    /**
     * Most severe first: the farthest beyond its limit, relative to the nominal voltage.
     */
    public static final Comparator<VoltageViolation> SEVERITY = Comparator
            .comparingDouble(VoltageViolation::percentBeyondLimit).reversed();

    public VoltageViolation {
        Objects.requireNonNull(violation);
        Objects.requireNonNull(busId);
    }

    /**
     * By {@link #SEVERITY}: the most severe first.
     */
    @Override
    public int compareTo(VoltageViolation other) {
        return SEVERITY.compare(this, other);
    }

    /**
     * A voltage violation reported by a computation, e.g. a security analysis, located on the bus of the bus view its
     * violation location resolves to in the network (the voltage level if it has no location). Null if the voltage
     * level isn't in the network.
     */
    public static VoltageViolation of(LimitViolation violation, Network network) {
        VoltageLevel voltageLevel = network.getVoltageLevel(violation.getSubjectId());
        if (voltageLevel == null) {
            return null;
        }
        String busId = violation.getViolationLocation()
                .flatMap(location -> location.getBusView(network).getBusStream().findFirst())
                .map(Bus::getId)
                .orElse(voltageLevel.getId());
        return new VoltageViolation(violation, busId, voltageLevel.getNominalV());
    }

    public boolean isHigh() {
        return violation.getLimitType() == LimitViolationType.HIGH_VOLTAGE;
    }

    public double percentOfNominal() {
        return violation.getValue() / nominalV * 100;
    }

    public double percentBeyondLimit() {
        return Math.abs(violation.getValue() - violation.getLimit()) / nominalV * 100;
    }
}
