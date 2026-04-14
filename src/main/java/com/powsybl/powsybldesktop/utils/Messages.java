/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Dedicated i18n bundle for UI strings (menus, labels, tooltips, dialogs). Kept separate from
 * {@code com.powsybl.powsybldesktop.reports} (PowSyBl {@code ReportNode} message templates,
 * registered via {@link com.powsybl.powsybldesktop.reports.PowsyblDesktopReportResourceBundle}).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class Messages {

    private static final String BASE_NAME = "com.powsybl.powsybldesktop.i18n.messages";

    private Messages() {
    }

    public static ResourceBundle bundle() {
        return ResourceBundle.getBundle(BASE_NAME);
    }

    public static String get(String key) {
        return bundle().getString(key);
    }

    public static String get(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }
}
