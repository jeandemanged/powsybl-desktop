/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.limitscaling;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.security.json.limitscaling.LimitScalingListSerDeUtil;
import com.powsybl.security.limitscaling.DefaultLimitScalingsApplier;
import com.powsybl.security.limitscaling.LimitScaling;
import com.powsybl.security.limitscaling.LimitScalingList;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Limit scalings ({@code com.powsybl.security.limitscaling}) as the app uses them: the limits violations are detected
 * against, JSON files and content comparison.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class LimitScalings {

    public static final List<LimitType> LIMIT_TYPES = List.of(LimitType.CURRENT, LimitType.ACTIVE_POWER, LimitType.APPARENT_POWER);

    private LimitScalings() {
    }

    /**
     * The limits of the base case (no contingency) scaled by {@code scalings}, in order: of several scalings matching
     * the same limit, the last one wins.
     */
    public static LimitsComputer<Identifiable<?>, LoadingLimits> limitsComputer(List<LimitScaling> scalings) {
        return limitsComputer(scalings, null);
    }

    /**
     * The limits after {@code contingencyId}, or of the base case when null, scaled by {@code scalings}.
     * <p>
     * Both monitoring only scalings and the others scale the reported violations (monitoring only ones just don't
     * scale the conditions of remedial actions), but {@code AbstractLimitScalingsApplier} only applies the scalings
     * whose monitoring only flag equals the one it is asked for: the flag is cleared on copies so that every scaling
     * applies to {@code computeLimits(..., false)}, the order being kept.
     */
    public static LimitsComputer<Identifiable<?>, LoadingLimits> limitsComputer(List<LimitScaling> scalings, String contingencyId) {
        if (scalings.isEmpty()) {
            return LimitsComputer.NO_MODIFICATIONS;
        }
        DefaultLimitScalingsApplier applier = new DefaultLimitScalingsApplier(scalings.stream()
                .map(scaling -> scaling.isMonitoringOnly() ? toBuilder(scaling).withMonitoringOnly(false).build() : scaling)
                .toList());
        applier.setWorkingContingency(contingencyId);
        return applier;
    }

    /**
     * A builder starting from every field of {@code scaling} but the limit type and value, given.
     */
    public static LimitScaling.Builder toBuilder(LimitScaling scaling, LimitType limitType, double value) {
        return LimitScaling.builder(limitType, value)
                .withMonitoringOnly(scaling.isMonitoringOnly())
                .withContingencyContext(scaling.getContingencyContext())
                .withNetworkElementCriteria(scaling.getNetworkElementCriteria())
                .withLimitDurationCriteria(scaling.getDurationCriteria())
                .withOperationalLimitsGroupIdSelection(scaling.getOperationalLimitsGroupIdsSelection());
    }

    public static LimitScaling.Builder toBuilder(LimitScaling scaling) {
        return toBuilder(scaling, scaling.getLimitType(), scaling.getValue());
    }

    // LimitScaling has no equals: two lists are compared through their JSON form
    public static boolean sameContent(List<LimitScaling> scalings1, List<LimitScaling> scalings2) {
        return Arrays.equals(toJson(scalings1), toJson(scalings2));
    }

    private static byte[] toJson(List<LimitScaling> scalings) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LimitScalingListSerDeUtil.write(new LimitScalingList(scalings), out);
        return out.toByteArray();
    }

    // through streams rather than path.toFile(): the path can be inside a study's zip file system
    public static List<LimitScaling> read(Path path) {
        return LimitScalingListSerDeUtil.read(path).getLimitScalings();
    }

    public static void write(List<LimitScaling> scalings, Path path) {
        LimitScalingListSerDeUtil.write(new LimitScalingList(scalings), path);
    }
}
