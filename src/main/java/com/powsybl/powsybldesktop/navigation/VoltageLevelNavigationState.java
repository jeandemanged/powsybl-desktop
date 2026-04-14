/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class VoltageLevelNavigationState extends NetworkNavigationState {

    private final VoltageLevel voltageLevel;

    protected VoltageLevelNavigationState(Network selectedNetwork, VoltageLevel voltageLevel) {
        super(selectedNetwork);
        this.voltageLevel = voltageLevel;
    }

    public static VoltageLevelNavigationState create(VoltageLevel voltageLevel) {
        return new VoltageLevelNavigationState(voltageLevel.getNetwork(), voltageLevel);
    }

    public static VoltageLevelNavigationState createNoVoltageLevel(Network network) {
        return new VoltageLevelNavigationState(network, null);
    }

    public VoltageLevel getVoltageLevel() {
        return voltageLevel;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && voltageLevel == ((VoltageLevelNavigationState) o).voltageLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), voltageLevel);
    }
}
