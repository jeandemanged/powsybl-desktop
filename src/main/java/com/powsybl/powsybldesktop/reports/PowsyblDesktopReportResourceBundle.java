/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.reports;

import com.google.auto.service.AutoService;
import com.powsybl.commons.report.ReportResourceBundle;

@AutoService(ReportResourceBundle.class)
/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class PowsyblDesktopReportResourceBundle implements ReportResourceBundle {

    public static final String BASE_NAME = "com.powsybl.powsybldesktop.reports";

    public String getBaseName() {
        return BASE_NAME;
    }
}
