/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.powsybldesktop.utils.Messages;

/**
 * A kind of limit violation the map can mark with warning bubbles, each shown or not from its own checkbox, a GUI
 * parameter.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public enum MapViolationLayer {
    BASE_CASE_FLOW("baseCaseFlow", "map.violations.baseCaseFlow"),
    BASE_CASE_VOLTAGE("baseCaseVoltage", "map.violations.baseCaseVoltage");

    private final String jsonName;
    private final String labelKey;

    MapViolationLayer(String jsonName, String labelKey) {
        this.jsonName = jsonName;
        this.labelKey = labelKey;
    }

    // its field in the parameters file
    public String getJsonName() {
        return jsonName;
    }

    public String getLabel() {
        return Messages.get(labelKey);
    }
}
