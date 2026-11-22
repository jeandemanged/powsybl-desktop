/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ReportNavigationState implements NavigationState {

    private final ReportNode reportNode;

    protected ReportNavigationState(ReportNode reportNode) {
        this.reportNode = Objects.requireNonNull(reportNode);
    }

    public static ReportNavigationState create(ReportNode reportNode) {
        return new ReportNavigationState(reportNode);
    }

    public ReportNode getReportNode() {
        return reportNode;
    }

    @Override
    public Network getSelectedNetwork() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ReportNavigationState other && reportNode == other.reportNode;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(reportNode);
    }
}
