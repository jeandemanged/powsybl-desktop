/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LoadNavigationState extends NetworkNavigationState {

    private final Load load;

    protected LoadNavigationState(Network selectedNetwork, Load load) {
        super(selectedNetwork);
        this.load = load;
    }

    public static LoadNavigationState create(Load load) {
        return new LoadNavigationState(load.getNetwork(), load);
    }

    public static LoadNavigationState createNoLoad(Network network) {
        return new LoadNavigationState(network, null);
    }

    public Load getLoad() {
        return load;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && load == ((LoadNavigationState) o).load;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), load);
    }
}
