/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.StaticVarCompensator;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class StaticVarCompensatorNavigationState extends NetworkNavigationState {

    private final StaticVarCompensator staticVarCompensator;

    protected StaticVarCompensatorNavigationState(Network selectedNetwork, StaticVarCompensator staticVarCompensator) {
        super(selectedNetwork);
        this.staticVarCompensator = staticVarCompensator;
    }

    public static StaticVarCompensatorNavigationState create(StaticVarCompensator staticVarCompensator) {
        return new StaticVarCompensatorNavigationState(staticVarCompensator.getNetwork(), staticVarCompensator);
    }

    public static StaticVarCompensatorNavigationState createNoStaticVarCompensator(Network network) {
        return new StaticVarCompensatorNavigationState(network, null);
    }

    public StaticVarCompensator getStaticVarCompensator() {
        return staticVarCompensator;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && staticVarCompensator == ((StaticVarCompensatorNavigationState) o).staticVarCompensator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), staticVarCompensator);
    }
}
