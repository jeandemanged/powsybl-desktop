/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ShuntCompensatorNavigationState extends NetworkNavigationState {

    private final ShuntCompensator shuntCompensator;

    protected ShuntCompensatorNavigationState(Network selectedNetwork, ShuntCompensator shuntCompensator) {
        super(selectedNetwork);
        this.shuntCompensator = shuntCompensator;
    }

    public static ShuntCompensatorNavigationState create(ShuntCompensator shuntCompensator) {
        return new ShuntCompensatorNavigationState(shuntCompensator.getNetwork(), shuntCompensator);
    }

    public static ShuntCompensatorNavigationState createNoShuntCompensator(Network network) {
        return new ShuntCompensatorNavigationState(network, null);
    }

    public ShuntCompensator getShuntCompensator() {
        return shuntCompensator;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && shuntCompensator == ((ShuntCompensatorNavigationState) o).shuntCompensator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), shuntCompensator);
    }
}
