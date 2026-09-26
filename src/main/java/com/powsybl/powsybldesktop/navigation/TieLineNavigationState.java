/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TieLine;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class TieLineNavigationState extends NetworkNavigationState {

    private final TieLine tieLine;

    protected TieLineNavigationState(Network selectedNetwork, TieLine tieLine) {
        super(selectedNetwork);
        this.tieLine = tieLine;
    }

    public static TieLineNavigationState create(TieLine tieLine) {
        return new TieLineNavigationState(tieLine.getNetwork(), tieLine);
    }

    public static TieLineNavigationState createNoTieLine(Network network) {
        return new TieLineNavigationState(network, null);
    }

    public TieLine getTieLine() {
        return tieLine;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && tieLine == ((TieLineNavigationState) o).tieLine;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tieLine);
    }
}
