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
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.navigation.BusNavigationState;
import com.powsybl.powsybldesktop.navigation.ContainerNavigationState;
import com.powsybl.powsybldesktop.navigation.NavigationEvent;
import com.powsybl.powsybldesktop.navigation.NavigationType;
import com.powsybl.powsybldesktop.network.search.NetworkSearch;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Flat table of all buses in the network's bus/breaker view ({@link Network#getBusBreakerView()}), i.e.
 * one row per configured bus. The last column links to the corresponding bus in the bus/branch view
 * ({@link #busViewBusOf(Bus)}), when the configured bus belongs to one (e.g. it may not if disconnected).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class BusesBusBreakerViewController extends AbstractBusesController {

    @FXML
    TableColumn<Bus, Bus> busInBusViewColumn;

    @FXML
    private void initialize() {
        initializeCommonColumns();
        configureBusInBusViewColumn();
    }

    private void configureBusInBusViewColumn() {
        busInBusViewColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        busInBusViewColumn.setCellFactory(col -> new TableCell<Bus, Bus>() {
            @Override
            protected void updateItem(Bus bus, boolean empty) {
                super.updateItem(bus, empty);
                setGraphic(empty || bus == null ? null : busInBusViewNode(bus));
            }
        });
    }

    private Node busInBusViewNode(Bus bus) {
        return busViewBusOf(bus).<Node>map(busViewBus -> {
            Hyperlink link = new Hyperlink(busViewBus.getNameOrId());
            link.getStyleClass().add("container-link");
            link.setOnAction(event -> {
                if (isEmbedded()) {
                    mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.SUBSTATIONS,
                            ContainerNavigationState.create(busViewBus.getVoltageLevel(), ContainerNavigationState.ContainerTab.BUSES_BUS_VIEW)));
                } else {
                    mainModel.addNavigationEvent(ownNavigationEvent(bus), false);
                    mainModel.addNavigationEvent(NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSES_BUS_VIEW, BusNavigationState.create(busViewBus)));
                }
            });
            return link;
        }).orElse(null);
    }

    /**
     * @param bus a bus in the bus/breaker view
     * @return the bus in the bus/branch view containing {@code bus}, if there is one
     */
    private static Optional<Bus> busViewBusOf(Bus bus) {
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        if (voltageLevel.getTopologyKind() == TopologyKind.BUS_BREAKER) {
            // Bus/Breaker. There is an easy method directly available.
            return Optional.ofNullable(voltageLevel.getBusView().getMergedBus(bus.getId()));
        } else {
            // Node/Breaker.
            // First we try the fast and easy way using connected terminals. Works for the vast majority of buses.
            Optional<Bus> busInBusView = bus.getConnectedTerminalStream().map(t -> t.getBusView().getBus())
                    .filter(Objects::nonNull)
                    .findFirst();
            if (busInBusView.isPresent()) {
                return busInBusView;
            }
            // Didn't find using connected terminals. There is the possibility that the bus has zero connected terminal
            // on its own but is still part of a Merged Bus via a closed retained switch. We examine this case below.
            // We should probably build something more efficient on powsybl-core side to avoid having
            // to loop over all buses in the voltage level.
            return voltageLevel.getBusView().getBusStream()
                    .filter(busViewBus -> voltageLevel.getBusBreakerView().getBusStreamFromBusViewBusId(busViewBus.getId())
                            .anyMatch(b2 -> bus.getId().equals(b2.getId())))
                    .findFirst();
        }
    }

    @Override
    Stream<Bus> networkItems(Network network) {
        return network.getBusBreakerView().getBusStream();
    }

    /**
     * A search match is either one of this table's own configured buses directly (a
     * {@link NetworkSearch.Kind#CONFIGURED_BUS} match), or a bus/branch view bus ({@link NetworkSearch.Kind#BUS})
     * that needs resolving down to one of them.
     */
    @Override
    Optional<Bus> asOwnEntity(Identifiable<?> match) {
        if (!(match instanceof Bus bus)) {
            return Optional.empty();
        }
        return isConfiguredBus(bus) ? Optional.of(bus) : Optional.ofNullable(firstConfiguredBusIn(bus));
    }

    // a configured bus and a bus-view bus never share an id (even a BUS_BREAKER voltage level with a single,
    // unmerged configured bus still computes a differently-named bus-view bus for it), so id membership in the
    // bus-breaker view reliably tells the two apart
    private static boolean isConfiguredBus(Bus bus) {
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        return voltageLevel.getTopologyKind() == TopologyKind.BUS_BREAKER
                && voltageLevel.getBusBreakerView().getBusStream().anyMatch(configuredBus -> configuredBus.getId().equals(bus.getId()));
    }

    private static Bus firstConfiguredBusIn(Bus busViewBus) {
        return busViewBus.getVoltageLevel().getBusBreakerView()
                .getBusStreamFromBusViewBusId(busViewBus.getId())
                .findFirst()
                .orElse(null);
    }

    public void goToBus(Bus bus) {
        goToItem(bus);
    }

    @Override
    NavigationEvent ownNavigationEvent(Bus bus) {
        return NavigationEvent.create(NavigationType.NETWORK_TABLE_BUSES_BUS_BREAKER_VIEW, BusNavigationState.create(bus));
    }
}
