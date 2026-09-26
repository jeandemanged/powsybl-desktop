/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SubstationNavigationState extends NetworkNavigationState {

    private final Substation substation;

    protected SubstationNavigationState(Network selectedNetwork, Substation substation) {
        super(selectedNetwork);
        this.substation = substation;
    }

    public static SubstationNavigationState create(Substation substation) {
        return new SubstationNavigationState(substation.getNetwork(), substation);
    }

    public static SubstationNavigationState createNoSubstation(Network network) {
        return new SubstationNavigationState(network, null);
    }

    public Substation getSubstation() {
        return substation;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && substation == ((SubstationNavigationState) o).substation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), substation);
    }
}
