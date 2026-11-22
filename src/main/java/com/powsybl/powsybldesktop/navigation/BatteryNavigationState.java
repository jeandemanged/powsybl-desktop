/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;

import java.util.Objects;
import java.util.Set;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class BatteryNavigationState extends NetworkNavigationState {

    private final Battery battery;

    protected BatteryNavigationState(Network selectedNetwork, Battery battery) {
        super(selectedNetwork);
        this.battery = battery;
    }

    public static BatteryNavigationState create(Battery battery) {
        return new BatteryNavigationState(battery.getParentNetwork(), battery);
    }

    public static BatteryNavigationState createNoBattery(Network network) {
        return new BatteryNavigationState(network, null);
    }

    public Battery getBattery() {
        return battery;
    }

    @Override
    public boolean refersTo(Set<Identifiable<?>> removed) {
        return removed.contains(battery);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && battery == ((BatteryNavigationState) o).battery;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), battery);
    }
}
