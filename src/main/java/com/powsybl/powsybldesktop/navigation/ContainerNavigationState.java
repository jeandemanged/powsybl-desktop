/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.navigation;

import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class ContainerNavigationState extends NetworkNavigationState {

    /**
     * Which of {@link com.powsybl.powsybldesktop.network.SubstationsController}'s tabs was showing: the
     * two diagram tabs, plus one per embedded equipment table (shown only when the selected container has
     * at least one row for it).
     */
    public enum ContainerTab {
        SINGLE_LINE, AREA, SWITCHES,
        GENERATORS, SHUNT_COMPENSATORS, STATIC_VAR_COMPENSATORS, LOADS,
        LINES, TRANSFORMERS, TIE_LINES, BOUNDARY_LINES,
        BUSBAR_SECTIONS, BUSES_BUS_VIEW, BUSES_BUS_BREAKER_VIEW
    }

    private final Container<?> container;
    private final ContainerTab tab;
    // the row to select in an equipment tab, or null
    private final Identifiable<?> equipment;

    protected ContainerNavigationState(Network selectedNetwork, Container<?> container, ContainerTab tab, Identifiable<?> equipment) {
        super(selectedNetwork);
        this.container = container;
        this.tab = tab;
        this.equipment = equipment;
    }

    public static ContainerNavigationState create(Container<?> container) {
        return create(container, ContainerTab.SINGLE_LINE);
    }

    public static ContainerNavigationState create(Container<?> container, ContainerTab tab) {
        return create(container, tab, null);
    }

    public static ContainerNavigationState create(Container<?> container, ContainerTab tab, Identifiable<?> equipment) {
        return new ContainerNavigationState(container.getParentNetwork(), container, tab, equipment);
    }

    public static ContainerNavigationState createNoContainer(Network network) {
        return new ContainerNavigationState(network, null, ContainerTab.SINGLE_LINE, null);
    }

    public Container<?> getContainer() {
        return container;
    }

    public ContainerTab getTab() {
        return tab;
    }

    public Identifiable<?> getEquipment() {
        return equipment;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && container == ((ContainerNavigationState) o).container
                && tab == ((ContainerNavigationState) o).tab && equipment == ((ContainerNavigationState) o).equipment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), container, tab, equipment);
    }
}
