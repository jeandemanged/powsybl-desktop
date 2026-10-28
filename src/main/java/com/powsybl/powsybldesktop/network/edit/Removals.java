/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.modification.topology.RemoveFeederBayBuilder;
import com.powsybl.iidm.modification.topology.RemoveSubstationBuilder;
import com.powsybl.iidm.modification.topology.RemoveVoltageLevelBuilder;
import com.powsybl.iidm.network.Area;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OverloadManagementSystem;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.notification.Notification;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Removes a network object, after a confirmation listing what goes with it: a substation's voltage levels, a voltage
 * level's equipment and the branches connected to it (removed with their bays at their other ends), the HVDC lines of
 * converter stations, the tie lines of boundary lines (their other boundary line staying, unpaired).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class Removals {

    private static final Logger LOGGER = LoggerFactory.getLogger(Removals.class);
    private static final int MAX_LISTED = 15;

    private Removals() {
    }

    public static boolean supports(Identifiable<?> identifiable) {
        return identifiable instanceof Substation || identifiable instanceof VoltageLevel || identifiable instanceof Connectable<?>
                || identifiable instanceof HvdcLine || identifiable instanceof TieLine || identifiable instanceof Switch
                || identifiable instanceof Bus bus && isConfiguredBus(bus)
                || identifiable instanceof Area || identifiable instanceof OverloadManagementSystem;
    }

    // a bus-breaker voltage level's own buses, unlike the calculated ones
    private static boolean isConfiguredBus(Bus bus) {
        VoltageLevel voltageLevel = bus.getVoltageLevel();
        return voltageLevel.getTopologyKind() == TopologyKind.BUS_BREAKER && voltageLevel.getBusBreakerView().getBus(bus.getId()) == bus;
    }

    /**
     * Asks for confirmation, then removes {@code identifiable}; a failure is reported as an error notification.
     */
    public static void remove(MainModel mainModel, Window owner, Identifiable<?> identifiable) {
        String description = Kinds.describe(identifiable);
        if (!confirm(owner, description, dependents(identifiable))) {
            return;
        }
        try {
            NetworkEdits.apply(mainModel, identifiable.getParentNetwork(), network -> remove(network, identifiable));
        } catch (PowsyblException | IllegalArgumentException | IllegalStateException e) {
            LOGGER.error("Failed to remove {}", identifiable.getId(), e);
            mainModel.getNotificationsModel().add(Notification.createError(Instant.now(), "edit.remove.failed")
                    .withMessageArgs(description, e.getMessage()));
        }
    }

    private static boolean confirm(Window owner, String description, List<String> dependents) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle(Messages.get("desktop.common.action.remove"));
        alert.setHeaderText(Messages.get("edit.remove.confirm", description));
        if (!dependents.isEmpty()) {
            List<String> listed = dependents.stream().limit(MAX_LISTED).map(dependent -> "- " + dependent).collect(Collectors.toCollection(ArrayList::new));
            if (dependents.size() > MAX_LISTED) {
                listed.add(Messages.get("edit.remove.more", dependents.size() - MAX_LISTED));
            }
            alert.setContentText(Messages.get("edit.remove.alsoRemoved") + "\n" + String.join("\n", listed));
        }
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    /**
     * What removing {@code identifiable} also removes or changes, described for the confirmation.
     */
    static List<String> dependents(Identifiable<?> identifiable) {
        Set<String> dependents = new LinkedHashSet<>();
        switch (identifiable) {
            case Substation substation -> substation.getVoltageLevelStream().forEach(voltageLevel -> {
                dependents.add(Kinds.describe(voltageLevel));
                addContents(voltageLevel, dependents);
            });
            case VoltageLevel voltageLevel -> addContents(voltageLevel, dependents);
            case HvdcConverterStation<?> station when station.getHvdcLine() != null -> dependents.add(Kinds.describe(station.getHvdcLine()));
            case BoundaryLine boundaryLine -> boundaryLine.getTieLine().ifPresent(tieLine -> addTieLine(tieLine, boundaryLine, dependents));
            case TieLine tieLine -> {
                dependents.add(Messages.get("edit.remove.unpaired", Kinds.describe(tieLine.getBoundaryLine1())));
                dependents.add(Messages.get("edit.remove.unpaired", Kinds.describe(tieLine.getBoundaryLine2())));
            }
            case BusbarSection busbarSection -> switchesAt(busbarSection).forEach(aSwitch -> dependents.add(Kinds.describe(aSwitch)));
            default -> {
                // removes only itself
            }
        }
        return new ArrayList<>(dependents);
    }

    private static void addContents(VoltageLevel voltageLevel, Set<String> dependents) {
        voltageLevel.getConnectableStream().forEach(connectable -> {
            dependents.add(Kinds.describe(connectable));
            if (connectable instanceof HvdcConverterStation<?> station && station.getHvdcLine() != null) {
                dependents.add(Kinds.describe(station.getHvdcLine()));
            }
            if (connectable instanceof BoundaryLine boundaryLine) {
                boundaryLine.getTieLine().ifPresent(tieLine -> addTieLine(tieLine, boundaryLine, dependents));
            }
        });
    }

    private static void addTieLine(TieLine tieLine, BoundaryLine removed, Set<String> dependents) {
        dependents.add(Kinds.describe(tieLine));
        BoundaryLine other = tieLine.getBoundaryLine1() == removed ? tieLine.getBoundaryLine2() : tieLine.getBoundaryLine1();
        dependents.add(Messages.get("edit.remove.unpaired", Kinds.describe(other)));
    }

    static void remove(Network network, Identifiable<?> identifiable) {
        switch (identifiable) {
            case Substation substation -> NetworkEdits.apply(network, new RemoveSubstationBuilder().withSubstationId(substation.getId()).build());
            case VoltageLevel voltageLevel -> NetworkEdits.apply(network, new RemoveVoltageLevelBuilder().withVoltageLevelId(voltageLevel.getId()).build());
            case HvdcLine hvdcLine -> hvdcLine.remove();
            case TieLine tieLine -> tieLine.remove();
            case BusbarSection busbarSection -> removeBusbarSection(busbarSection);
            case HvdcConverterStation<?> station -> {
                if (station.getHvdcLine() != null) {
                    station.getHvdcLine().remove();
                }
                removeFeederBay(network, station);
            }
            case BoundaryLine boundaryLine -> {
                boundaryLine.getTieLine().ifPresent(TieLine::remove);
                removeFeederBay(network, boundaryLine);
            }
            case Connectable<?> connectable -> removeFeederBay(network, connectable);
            case Switch aSwitch -> {
                VoltageLevel voltageLevel = aSwitch.getVoltageLevel();
                if (voltageLevel.getTopologyKind() == TopologyKind.NODE_BREAKER) {
                    voltageLevel.getNodeBreakerView().removeSwitch(aSwitch.getId());
                } else {
                    voltageLevel.getBusBreakerView().removeSwitch(aSwitch.getId());
                }
            }
            case Bus bus -> bus.getVoltageLevel().getBusBreakerView().removeBus(bus.getId());
            case Area area -> area.remove();
            case OverloadManagementSystem system -> system.remove();
            default -> throw new IllegalArgumentException("Unsupported removal: " + identifiable.getClass());
        }
    }

    private static void removeFeederBay(Network network, Connectable<?> connectable) {
        NetworkEdits.apply(network, new RemoveFeederBayBuilder().withConnectableId(connectable.getId()).build());
    }

    // with the switches at its node: the bays' disconnectors to it, its section switches
    private static void removeBusbarSection(BusbarSection busbarSection) {
        VoltageLevel.NodeBreakerView topology = busbarSection.getTerminal().getVoltageLevel().getNodeBreakerView();
        int node = busbarSection.getTerminal().getNodeBreakerView().getNode();
        List<Switch> switches = switchesAt(busbarSection);
        List<int[]> internalConnections = topology.getInternalConnectionStream()
                .filter(connection -> connection.getNode1() == node || connection.getNode2() == node)
                .map(connection -> new int[] {connection.getNode1(), connection.getNode2()})
                .toList();
        busbarSection.remove();
        switches.forEach(aSwitch -> topology.removeSwitch(aSwitch.getId()));
        internalConnections.forEach(nodes -> topology.removeInternalConnections(nodes[0], nodes[1]));
    }

    private static List<Switch> switchesAt(BusbarSection busbarSection) {
        VoltageLevel.NodeBreakerView topology = busbarSection.getTerminal().getVoltageLevel().getNodeBreakerView();
        int node = busbarSection.getTerminal().getNodeBreakerView().getNode();
        return topology.getSwitchStream()
                .filter(aSwitch -> topology.getNode1(aSwitch.getId()) == node || topology.getNode2(aSwitch.getId()) == node)
                .toList();
    }
}
