/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class LccConverterStationNavigationState extends NetworkNavigationState {

    private final LccConverterStation lccConverterStation;

    protected LccConverterStationNavigationState(Network selectedNetwork, LccConverterStation lccConverterStation) {
        super(selectedNetwork);
        this.lccConverterStation = lccConverterStation;
    }

    public static LccConverterStationNavigationState create(LccConverterStation lccConverterStation) {
        return new LccConverterStationNavigationState(lccConverterStation.getParentNetwork(), lccConverterStation);
    }

    public static LccConverterStationNavigationState createNoLccConverterStation(Network network) {
        return new LccConverterStationNavigationState(network, null);
    }

    public LccConverterStation getLccConverterStation() {
        return lccConverterStation;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && lccConverterStation == ((LccConverterStationNavigationState) o).lccConverterStation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lccConverterStation);
    }
}
