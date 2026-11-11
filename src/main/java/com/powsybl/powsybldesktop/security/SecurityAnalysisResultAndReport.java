/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.security;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.security.SecurityAnalysisResult;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record SecurityAnalysisResultAndReport(
        SecurityAnalysisResult securityAnalysisResult,
        ReportNode reportNode
) {
}
