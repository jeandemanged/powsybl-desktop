/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.rao;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.raoresult.api.RaoResult;

/**
 * A RAO result with the CRAC it refers to, the run's copy (see {@link RaoRun}), and its report.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record RaoResultAndReport(
        RaoResult raoResult,
        Crac crac,
        ReportNode reportNode
) {
}
