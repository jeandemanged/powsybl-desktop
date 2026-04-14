/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class BusNavigationState extends NetworkNavigationState {

    private final Bus bus;

    protected BusNavigationState(Network selectedNetwork, Bus bus) {
        super(selectedNetwork);
        this.bus = bus;
    }

    public static BusNavigationState create(Bus bus) {
        return new BusNavigationState(bus.getNetwork(), bus);
    }

    public static BusNavigationState createNoBus(Network network) {
        return new BusNavigationState(network, null);
    }

    public Bus getBus() {
        return bus;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && bus == ((BusNavigationState) o).bus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), bus);
    }
}
