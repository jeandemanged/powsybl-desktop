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
 * The map's background layer, a GUI parameter.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public enum Basemap {
    OFFLINE("offline", "map.basemap.offline"),
    OPEN_STREET_MAP("osm", "map.basemap.openStreetMap");

    private final String jsName;
    private final String labelKey;

    Basemap(String jsName, String labelKey) {
        this.jsName = jsName;
        this.labelKey = labelKey;
    }

    // name of the layer in map.js
    public String getJsName() {
        return jsName;
    }

    public String getLabel() {
        return Messages.get(labelKey);
    }
}
