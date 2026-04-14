/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class NetworkNavigationState implements NavigationState {

    private final Network selectedNetwork;

    protected NetworkNavigationState(Network selectedNetwork) {
        this.selectedNetwork = selectedNetwork;
    }

    public static NetworkNavigationState create(Network selectedNetwork) {
        return new NetworkNavigationState(selectedNetwork);
    }

    @Override
    public Network getSelectedNetwork() {
        return selectedNetwork;
    }

    // identity-based, like the underlying PowSyBl objects: used to collapse duplicate consecutive
    // navigation history entries (see MainModel.addNavigationEvent)
    @Override
    public boolean equals(Object o) {
        return o != null && getClass() == o.getClass()
                && selectedNetwork == ((NetworkNavigationState) o).selectedNetwork;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(selectedNetwork);
    }
}
