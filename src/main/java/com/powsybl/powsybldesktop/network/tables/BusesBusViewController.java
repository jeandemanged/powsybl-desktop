/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.navigation.BusNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import javafx.fxml.FXML;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all buses in the network's bus/branch view ({@link Network#getBusView()}), i.e. one row
 * per merged calculated bus.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class BusesBusViewController extends AbstractBusesController {

    @FXML
    private void initialize() {
        initializeCommonColumns();
    }

    @Override
    Stream<Bus> networkItems(Network network) {
        return network.getBusView().getBusStream();
    }

    @Override
    Optional<Bus> asOwnEntity(Identifiable<?> match) {
        return match instanceof Bus bus ? Optional.ofNullable(busById(bus.getId())) : Optional.empty();
    }

    private Bus busById(String busId) {
        return currentItems.stream().filter(bus -> bus.getId().equals(busId)).findFirst().orElse(null);
    }

    public void goToBus(Bus bus) {
        goToItem(bus);
    }

    @Override
    NavigationEvent ownNavigationEvent(Bus bus) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSES_BUS_VIEW, BusNavigationState.create(bus));
    }
}
