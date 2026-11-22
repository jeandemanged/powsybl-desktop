/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.commons.report.ReportNode;

import java.util.Objects;

/**
 * A RAO that completed with a failed result: OpenRAO reports most failures that way rather than by throwing, its failed
 * result answering nothing but its status and execution details (the message). The report tells what happened.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class RaoFailedException extends Exception {

    private final transient ReportNode reportNode;

    public RaoFailedException(String executionDetails, ReportNode reportNode) {
        super(executionDetails);
        this.reportNode = Objects.requireNonNull(reportNode);
    }

    public ReportNode getReportNode() {
        return reportNode;
    }
}
