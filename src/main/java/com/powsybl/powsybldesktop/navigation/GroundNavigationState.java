/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Ground;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;

import java.util.Objects;
import java.util.Set;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class GroundNavigationState extends NetworkNavigationState {

    private final Ground ground;

    protected GroundNavigationState(Network selectedNetwork, Ground ground) {
        super(selectedNetwork);
        this.ground = ground;
    }

    public static GroundNavigationState create(Ground ground) {
        return new GroundNavigationState(ground.getParentNetwork(), ground);
    }

    public static GroundNavigationState createNoGround(Network network) {
        return new GroundNavigationState(network, null);
    }

    public Ground getGround() {
        return ground;
    }

    @Override
    public boolean refersTo(Set<Identifiable<?>> removed) {
        return removed.contains(ground);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && ground == ((GroundNavigationState) o).ground;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), ground);
    }
}
