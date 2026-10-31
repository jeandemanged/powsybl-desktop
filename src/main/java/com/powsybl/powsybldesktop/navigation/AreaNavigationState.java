/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;

import java.util.Objects;
import java.util.Set;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class AreaNavigationState extends NetworkNavigationState {

    private final Area area;

    protected AreaNavigationState(Network selectedNetwork, Area area) {
        super(selectedNetwork);
        this.area = area;
    }

    public static AreaNavigationState create(Area area) {
        return new AreaNavigationState(area.getParentNetwork(), area);
    }

    public static AreaNavigationState createNoArea(Network network) {
        return new AreaNavigationState(network, null);
    }

    public Area getArea() {
        return area;
    }

    @Override
    public boolean refersTo(Set<Identifiable<?>> removed) {
        return removed.contains(area);
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && area == ((AreaNavigationState) o).area;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), area);
    }
}
