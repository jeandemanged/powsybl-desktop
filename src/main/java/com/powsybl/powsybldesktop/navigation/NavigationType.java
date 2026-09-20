/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public enum NavigationType {
    NETWORKS,
    SUBSTATIONS,
    LOGS,
    PARAMETERS,
    NETWORK_TABLE_SUBSTATIONS,
    NETWORK_TABLE_VOLTAGE_LEVELS,
    NETWORK_TABLE_BUSBAR_SECTIONS,
    NETWORK_TABLE_BUSES_BUS_VIEW,
    NETWORK_TABLE_BUSES_BUS_BREAKER_VIEW,
    NETWORK_TABLE_GENERATORS,
    NETWORK_TABLE_SHUNT_COMPENSATORS,
    NETWORK_TABLE_STATIC_VAR_COMPENSATORS,
    NETWORK_TABLE_LOADS,
    NETWORK_TABLE_LINES,
    NETWORK_TABLE_TRANSFORMERS,
    NETWORK_TABLE_TIE_LINES,
    NETWORK_TABLE_BOUNDARY_LINES,
    NETWORK_TABLE_COMPONENTS,
    REPORTS
}
