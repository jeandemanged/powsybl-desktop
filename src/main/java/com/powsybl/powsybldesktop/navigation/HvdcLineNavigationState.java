/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;

import java.util.Objects;
import java.util.Set;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class HvdcLineNavigationState extends NetworkNavigationState {

    private final HvdcLine hvdcLine;

    protected HvdcLineNavigationState(Network selectedNetwork, HvdcLine hvdcLine) {
        super(selectedNetwork);
        this.hvdcLine = hvdcLine;
    }

    public static HvdcLineNavigationState create(HvdcLine hvdcLine) {
        return new HvdcLineNavigationState(hvdcLine.getParentNetwork(), hvdcLine);
    }

    public static HvdcLineNavigationState createNoHvdcLine(Network network) {
        return new HvdcLineNavigationState(network, null);
    }

    public HvdcLine getHvdcLine() {
        return hvdcLine;
    }

    @Override
    public boolean refersTo(Set<Identifiable<?>> removed) {
        return removed.contains(hvdcLine);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && hvdcLine == ((HvdcLineNavigationState) o).hvdcLine;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), hvdcLine);
    }
}
