/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.limitscaling;

import com.powsybl.contingency.ContingencyContext;
import com.powsybl.iidm.network.DetectionKind;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.iidm.network.limitmodification.result.LimitsContainer;
import com.powsybl.security.limitscaling.LimitScaling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The limits a limit scaling changes in a network, whatever its contingency context, for the editor's preview.
 * Unpaired boundary lines are left out: like {@code NetworkViolations}, PowSyBl's limits computers don't handle them.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class AffectedLimits {

    /**
     * One operational limits group's limits of the scaling's type on one side of an equipment. A permanent limit is NaN
     * for a {@code DetectionKind.LOW} limit set, which has none.
     */
    public record AffectedLimit(Identifiable<?> equipment, ThreeSides side, String groupId, double permanentLimit,
                                double scaledPermanentLimit, int scaledTemporaryLimitCount, int temporaryLimitCount) {
    }

    private static final double PROBE_FACTOR = 0.5;

    private AffectedLimits() {
    }

    public static List<AffectedLimit> compute(Network network, LimitScaling scaling) {
        // matched limits are found with a probe factor, so that a factor of 1 still shows what the criteria match
        LimitsComputer<Identifiable<?>, LoadingLimits> computer = LimitScalings.limitsComputer(List.of(
                LimitScalings.toBuilder(scaling, scaling.getLimitType(), PROBE_FACTOR).withContingencyContext(ContingencyContext.all()).build()));
        List<AffectedLimit> affected = new ArrayList<>();
        network.getBranchStream().forEach(branch -> {
            for (TwoSides side : TwoSides.values()) {
                collect(branch, side.toThreeSides(), scaling, computer, affected::add);
            }
        });
        network.getThreeWindingsTransformerStream().forEach(transformer -> {
            for (ThreeSides side : ThreeSides.values()) {
                collect(transformer, side, scaling, computer, affected::add);
            }
        });
        affected.sort(Comparator.comparing((AffectedLimit a) -> a.equipment().getId()).thenComparing(AffectedLimit::side));
        return affected;
    }

    private static void collect(Identifiable<?> equipment, ThreeSides side, LimitScaling scaling,
                                LimitsComputer<Identifiable<?>, LoadingLimits> computer, Consumer<AffectedLimit> consumer) {
        for (LimitsContainer<LoadingLimits> container : computer.computeLimits(equipment, scaling.getLimitType(), side, false)) {
            if (!container.isDistinct()) {
                continue;
            }
            LoadingLimits original = container.getOriginalLimits();
            LoadingLimits probed = container.getLimits();
            int scaledTemporaryCount = (int) original.getTemporaryLimits().stream()
                    .filter(t -> probed.getTemporaryLimit(t.getAcceptableDuration()) != null
                            && probed.getTemporaryLimit(t.getAcceptableDuration()).getValue() != t.getValue())
                    .count();
            double permanent = original.getDetectionKind() == DetectionKind.HIGH ? original.getPermanentLimit() : Double.NaN;
            boolean permanentScaled = !Double.isNaN(permanent) && probed.getPermanentLimit() != permanent;
            if (scaledTemporaryCount == 0 && !permanentScaled) {
                continue;
            }
            consumer.accept(new AffectedLimit(equipment, side, container.getOperationalLimitsGroupId(), permanent,
                    permanentScaled ? permanent * scaling.getValue() : permanent, scaledTemporaryCount, original.getTemporaryLimits().size()));
        }
    }
}
