/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.TopologyModificationUtils;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;

/**
 * Where a new connectable (or one side of it) is connected, as {@code CreateFeederBay} and
 * {@code CreateBranchFeederBays} take it: a busbar section with the feeder's position order and direction in a node
 * breaker voltage level (its bay of switches is created), or a bus in a bus breaker one.
 *
 * @param positionOrder the requested order, fitted by {@link FeederOrders#reserve}; node breaker only
 * @param direction     node breaker only
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public record Connection(VoltageLevel voltageLevel, Identifiable<?> busOrBusbarSection, int positionOrder,
                         ConnectablePosition.Direction direction) {

    public boolean isNodeBreaker() {
        return busOrBusbarSection instanceof BusbarSection;
    }

    public String busOrBusbarSectionId() {
        return busOrBusbarSection.getId();
    }

    /**
     * Reserves the position orders of {@code count} feeders; node breaker only.
     */
    public FeederOrders.Slot reserve(FeederOrders orders, int count) {
        return orders.reserve((BusbarSection) busOrBusbarSection, positionOrder, count);
    }

    /**
     * Whether a feeder position is given to {@code connectable} in {@code voltageLevel}, as {@code CreateFeederBay}
     * decides: not where the other connectables have none (e.g. imported without).
     */
    public static boolean usesPositions(VoltageLevel voltageLevel, Connectable<?> connectable) {
        return !TopologyModificationUtils.getFeederPositions(voltageLevel).isEmpty()
                || voltageLevel.getConnectableStream().allMatch(other -> other instanceof BusbarSection || other == connectable);
    }
}
