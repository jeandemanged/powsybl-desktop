/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.violations;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.DetectionKind;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OperationalLimitsGroup;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;

import java.util.Comparator;
import java.util.List;
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
     * Most severe first: the shortest acceptable duration, then the highest percentage, a NaN one last. A permanent
     * limit violation's acceptable duration ({@link Integer#MAX_VALUE}) counts as none: it is only reported for a limit
     * set without temporary limits (otherwise a temporary one is), whose permanent limit can't be exceeded at all.
     */
    public static final Comparator<FlowViolation> SEVERITY = Comparator
            .comparingInt(FlowViolation::sortableAcceptableDuration)
            .thenComparing(FlowViolation::sortablePercent, Comparator.reverseOrder());

    public FlowViolation {
        Objects.requireNonNull(violation);
    }

    /**
     * A flow violation reported by a computation, e.g. a security analysis, with the permanent limit of the network's
     * matching selected operational limits group, NaN if none.
     */
    public static FlowViolation of(LimitViolation violation, Network network) {
        LimitType type = LimitType.valueOf(violation.getLimitType().name());
        double permanentLimit = selectedGroups(network.getIdentifiable(violation.getSubjectId()), violation.getSide()).stream()
                .filter(group -> violation.getOperationalLimitsGroupId() == null || group.getId().equals(violation.getOperationalLimitsGroupId()))
                .flatMap(group -> group.getLoadingLimits(type).stream())
                // a LOW detection kind limit set has no permanent limit: getPermanentLimit() throws
                .filter(limits -> limits.getDetectionKind() == DetectionKind.HIGH)
                .mapToDouble(LoadingLimits::getPermanentLimit)
                .findFirst().orElse(Double.NaN);
        return new FlowViolation(violation, permanentLimit);
    }

    private static List<OperationalLimitsGroup> selectedGroups(Identifiable<?> equipment, ThreeSides side) {
        return switch (equipment) {
            case Branch<?> branch when side != null && side != ThreeSides.THREE -> branch.getAllSelectedOperationalLimitsGroups(side.toTwoSides());
            case ThreeWindingsTransformer transformer when side != null -> transformer.getLeg(side).getAllSelectedOperationalLimitsGroups();
            case BoundaryLine boundaryLine -> boundaryLine.getAllSelectedOperationalLimitsGroups();
            case null, default -> List.of();
        };
    }

    public double percent() {
        return percentOf(violation.getValue());
    }

    /**
     * Another value of the same side and limit type, e.g. of another state, relative to the same limit as
     * {@link #percent()}.
     */
    public double percentOf(double value) {
        double referenceLimit = Double.isNaN(permanentLimit) ? violation.getLimit() : permanentLimit;
        return Math.abs(value) / referenceLimit * 100;
    }

    /**
     * By {@link #SEVERITY}: the most severe first.
     */
    @Override
    public int compareTo(FlowViolation other) {
        return SEVERITY.compare(this, other);
    }

    private int sortableAcceptableDuration() {
        int acceptableDuration = violation.getAcceptableDuration();
        return acceptableDuration == Integer.MAX_VALUE ? 0 : acceptableDuration;
    }

    private double sortablePercent() {
        double percent = percent();
        return Double.isNaN(percent) ? Double.NEGATIVE_INFINITY : percent;
    }
}
