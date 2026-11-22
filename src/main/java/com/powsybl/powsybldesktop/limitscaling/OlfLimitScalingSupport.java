/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.limitscaling;

import com.powsybl.contingency.ContingencyContextType;
import com.powsybl.iidm.criteria.IdentifiableCriterion;
import com.powsybl.iidm.criteria.NetworkElementCriterion;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.security.limitscaling.LimitScaling;

import java.util.ArrayList;
import java.util.List;

/**
 * How Open Load Flow's security analysis treats a limit scaling. It skips the scalings it doesn't support with a mere
 * log line, and silently ignores part of others, so the app tells the user instead.
 * <p>
 * Mirrors {@code com.powsybl.openloadflow.sa.LimitScalingManager} (Open Load Flow 2.4.0), whose {@code isSupported} is
 * private: {@code OlfLimitScalingSupportTest} runs Open Load Flow on each rule, so that an upgrade changing them fails
 * the build.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class OlfLimitScalingSupport {

    public enum Status {
        APPLIED,
        // applied, part of its criteria being ignored
        PARTIALLY_APPLIED,
        NOT_APPLIED,
        // the security analysis throws
        FAILS
    }

    public record Support(Status status, List<String> reasons) {
    }

    private OlfLimitScalingSupport() {
    }

    public static Support of(LimitScaling scaling) {
        List<String> notApplied = new ArrayList<>();
        if (scaling.getContingencyContext().getContextType() != ContingencyContextType.ALL) {
            notApplied.add(Messages.get("limitScalings.olf.contingencyContext"));
        }
        if (scaling.isMonitoringOnly()) {
            notApplied.add(Messages.get("limitScalings.olf.monitoringOnly"));
        }
        if (scaling.getLimitType() != LimitType.CURRENT) {
            notApplied.add(Messages.get("limitScalings.olf.limitType"));
        }
        List<NetworkElementCriterion> criteria = scaling.getNetworkElementCriteria();
        if (criteria.stream().anyMatch(c -> !(c instanceof IdentifiableCriterion))) {
            notApplied.add(Messages.get("limitScalings.olf.criterionType"));
        }
        if (scaling.getDurationCriteria().size() > 2
                || scaling.getDurationCriteria().size() == 2
                && scaling.getDurationCriteria().getFirst().getType() == scaling.getDurationCriteria().getLast().getType()) {
            notApplied.add(Messages.get("limitScalings.olf.durations"));
        }
        if (!scaling.getOperationalLimitsGroupIdsSelection().isEmpty()) {
            notApplied.add(Messages.get("limitScalings.olf.limitsGroups"));
        }
        if (!notApplied.isEmpty()) {
            return new Support(Status.NOT_APPLIED, notApplied);
        }
        // only the nominal voltage interval of each criterion is read, without checking it is there
        if (criteria.stream().map(IdentifiableCriterion.class::cast).anyMatch(c -> c.getNominalVoltageCriterion() == null)) {
            return new Support(Status.FAILS, List.of(Messages.get("limitScalings.olf.noNominalVoltage")));
        }
        if (criteria.stream().map(IdentifiableCriterion.class::cast).anyMatch(c -> c.getCountryCriterion() != null)) {
            return new Support(Status.PARTIALLY_APPLIED, List.of(Messages.get("limitScalings.olf.countriesIgnored")));
        }
        return new Support(Status.APPLIED, List.of());
    }
}
