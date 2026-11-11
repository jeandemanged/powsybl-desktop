/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.sensitivity;

import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sensitivity.SensitivityFunctionType;
import com.powsybl.sensitivity.SensitivityVariableType;

import java.util.Locale;

/**
 * The displayed names of the kinds of sensitivity functions and variables.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class SensitivityTexts {

    private SensitivityTexts() {
    }

    public static String function(SensitivityFunctionType type) {
        return Messages.get("sensitivity.function." + type.name().toLowerCase(Locale.ROOT));
    }

    public static String variable(SensitivityVariableType type) {
        return Messages.get("sensitivity.variable." + type.name().toLowerCase(Locale.ROOT));
    }
}
