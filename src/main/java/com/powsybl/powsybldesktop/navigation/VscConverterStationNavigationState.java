/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VscConverterStation;

import java.util.Objects;
import java.util.Set;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class VscConverterStationNavigationState extends NetworkNavigationState {

    private final VscConverterStation vscConverterStation;

    protected VscConverterStationNavigationState(Network selectedNetwork, VscConverterStation vscConverterStation) {
        super(selectedNetwork);
        this.vscConverterStation = vscConverterStation;
    }

    public static VscConverterStationNavigationState create(VscConverterStation vscConverterStation) {
        return new VscConverterStationNavigationState(vscConverterStation.getParentNetwork(), vscConverterStation);
    }

    public static VscConverterStationNavigationState createNoVscConverterStation(Network network) {
        return new VscConverterStationNavigationState(network, null);
    }

    public VscConverterStation getVscConverterStation() {
        return vscConverterStation;
    }

    @Override
    public boolean refersTo(Set<Identifiable<?>> removed) {
        return removed.contains(vscConverterStation);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && vscConverterStation == ((VscConverterStationNavigationState) o).vscConverterStation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), vscConverterStation);
    }
}
