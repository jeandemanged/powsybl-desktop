/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Container;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.BusbarSectionPosition;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The creation and removal menu items of a network object, the same wherever they are offered (substations tree,
 * diagrams, tables, map), built when the menu shows: disabled while the network is busy.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class EditMenus {

    private EditMenus() {
    }

    /**
     * The items for {@code context}, an object of {@code network} or null for the network itself: what can be
     * created in it, then its removal.
     */
    public static List<MenuItem> items(SceneModel sceneModel, Supplier<Window> owner, Network network, Object context) {
        List<MenuItem> items = new ArrayList<>();
        if (network == null) {
            return items;
        }
        Menu create = new Menu(Messages.get("edit.menu.new"), new FontIcon("mdi2p-plus"));
        if (context instanceof Substation substation) {
            addSubstationCreations(sceneModel, owner, substation, create);
        } else if (context instanceof VoltageLevel voltageLevel) {
            addVoltageLevelCreations(sceneModel, owner, voltageLevel, null, create);
        } else if (context instanceof BusbarSection busbarSection) {
            addVoltageLevelCreations(sceneModel, owner, busbarSection.getTerminal().getVoltageLevel(), busbarSection, create);
        } else if (context instanceof Bus bus && bus.getVoltageLevel().getTopologyKind() == TopologyKind.BUS_BREAKER) {
            addVoltageLevelCreations(sceneModel, owner, bus.getVoltageLevel(), bus, create);
        } else if (context instanceof BoundaryLine boundaryLine && !boundaryLine.isPaired()) {
            create.getItems().add(tieLineItem(sceneModel, owner, network, boundaryLine));
        } else if (context instanceof HvdcConverterStation<?> station && station.getHvdcLine() == null) {
            create.getItems().add(hvdcLineItem(sceneModel, owner, network, station));
        } else if (context == null || context instanceof Network) {
            addNetworkCreations(sceneModel, owner, network, create);
        }
        if (!create.getItems().isEmpty()) {
            items.add(create);
        }
        Menu topology = new Menu(Messages.get("edit.menu.topology"), new FontIcon("mdi2s-sitemap"));
        topology.getItems().setAll(topologyItems(sceneModel, owner, network, context));
        if (!topology.getItems().isEmpty()) {
            items.add(topology);
        }
        if (context instanceof Identifiable<?> identifiable && !(context instanceof Network) && Removals.supports(identifiable)) {
            if (!items.isEmpty()) {
                items.add(new SeparatorMenuItem());
            }
            items.add(item(Messages.get("edit.menu.remove", Kinds.describe(identifiable)), "mdi2d-delete",
                    () -> Removals.remove(sceneModel.getMainModel(), owner.get(), identifiable)));
        }
        boolean busy = sceneModel.getMainModel().isBusy(network);
        if (busy) {
            items.forEach(item -> item.setDisable(true));
        }
        return items;
    }

    private static void addNetworkCreations(SceneModel sceneModel, Supplier<Window> owner, Network network, Menu create) {
        create.getItems().add(substationItem(sceneModel, owner, network));
        create.getItems().add(voltageLevelItem(sceneModel, owner, network, null));
        create.getItems().add(new SeparatorMenuItem());
        create.getItems().add(lineItem(sceneModel, owner, network, null, null));
        create.getItems().add(tieLineItem(sceneModel, owner, network, null));
        create.getItems().add(hvdcLineItem(sceneModel, owner, network, null));
        create.getItems().add(new SeparatorMenuItem());
        create.getItems().add(areaItem(sceneModel, owner, network));
        create.getItems().add(voltageAngleLimitItem(sceneModel, owner, network));
    }

    private static void addSubstationCreations(SceneModel sceneModel, Supplier<Window> owner, Substation substation, Menu create) {
        create.getItems().add(voltageLevelItem(sceneModel, owner, substation.getParentNetwork(), substation));
        create.getItems().add(new SeparatorMenuItem());
        // from its highest voltage level, not the network's for a substation without any
        VoltageLevel highest = voltageLevelOf(substation.getParentNetwork(), substation);
        MenuItem line = lineItem(sceneModel, owner, substation.getParentNetwork(), highest, null);
        line.setDisable(highest == null);
        create.getItems().add(line);
        create.getItems().addAll(transformerItems(sceneModel, owner, substation, null, null));
        create.getItems().add(new SeparatorMenuItem());
        create.getItems().add(overloadManagementSystemItem(sceneModel, owner, substation.getParentNetwork(), substation));
    }

    /**
     * Gives {@code control} a context menu of {@code items}, built for each right-click: a context menu doesn't
     * show while empty, so building them only once it shows would leave it never shown.
     */
    public static void installContextMenu(Control control, Supplier<List<MenuItem>> items) {
        ContextMenu contextMenu = new ContextMenu();
        // a filter, to run before the control's own handler shows its context menu
        control.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> contextMenu.getItems().setAll(items.get()));
        control.setContextMenu(contextMenu);
    }

    public static MenuItem areaItem(SceneModel sceneModel, Supplier<Window> owner, Network network) {
        return disabledIf(network == null, kindItem("desktop.common.equipment.area", () -> NetworkObjectEdits.newArea(sceneModel, owner.get(), network)));
    }

    public static MenuItem voltageAngleLimitItem(SceneModel sceneModel, Supplier<Window> owner, Network network) {
        return disabledIf(network == null, kindItem("desktop.common.equipment.voltageAngleLimit",
                () -> NetworkObjectEdits.newVoltageAngleLimit(sceneModel, owner.get(), network)));
    }

    /**
     * @param substation the new system's substation, the network's first if null
     */
    public static MenuItem overloadManagementSystemItem(SceneModel sceneModel, Supplier<Window> owner, Network network, Substation substation) {
        Substation chosen = substation != null || network == null ? substation : network.getSubstationStream().findFirst().orElse(null);
        return disabledIf(chosen == null, kindItem("desktop.common.equipment.overloadManagementSystem",
                () -> NetworkObjectEdits.newOverloadManagementSystem(sceneModel, owner.get(), chosen)));
    }

    // the following items are disabled without network (or what else they need)

    public static MenuItem substationItem(SceneModel sceneModel, Supplier<Window> owner, Network network) {
        return disabledIf(network == null,
                kindItem("desktop.common.equipment.substation", () -> ContainerCreations.newSubstation(sceneModel, owner.get(), network, null, null)));
    }

    /**
     * @param substation the new voltage level's substation, none if null
     */
    public static MenuItem voltageLevelItem(SceneModel sceneModel, Supplier<Window> owner, Network network, Substation substation) {
        return disabledIf(network == null,
                kindItem("desktop.common.equipment.voltageLevel", () -> ContainerCreations.newVoltageLevel(sceneModel, owner.get(), network, substation)));
    }

    /**
     * A busbar section of {@code voltageLevel}, which must be node breaker.
     */
    public static MenuItem busbarSectionItem(SceneModel sceneModel, Supplier<Window> owner, VoltageLevel voltageLevel) {
        return disabledIf(voltageLevel == null || voltageLevel.getTopologyKind() != TopologyKind.NODE_BREAKER,
                kindItem("desktop.common.equipment.busbarSection", () -> TopologyEdits.newBusbarSection(sceneModel, owner.get(), voltageLevel)));
    }

    /**
     * A switch of {@code voltageLevel}, which must be node breaker.
     */
    public static MenuItem switchItem(SceneModel sceneModel, Supplier<Window> owner, VoltageLevel voltageLevel) {
        return disabledIf(voltageLevel == null || voltageLevel.getTopologyKind() != TopologyKind.NODE_BREAKER,
                kindItem("desktop.common.equipment.switch", () -> TopologyEdits.newSwitch(sceneModel, owner.get(), voltageLevel, null)));
    }

    /**
     * A bus of {@code voltageLevel}, which must be bus breaker.
     */
    public static MenuItem busItem(SceneModel sceneModel, Supplier<Window> owner, VoltageLevel voltageLevel) {
        return disabledIf(voltageLevel == null || voltageLevel.getTopologyKind() != TopologyKind.BUS_BREAKER,
                kindItem("desktop.common.equipment.bus", () -> TopologyEdits.newBus(sceneModel, owner.get(), voltageLevel)));
    }

    private static MenuItem disabledIf(boolean disabled, MenuItem item) {
        item.setDisable(disabled);
        return item;
    }

    // on a busbar section or bus: what can be connected to it
    private static void addVoltageLevelCreations(SceneModel sceneModel, Supplier<Window> owner, VoltageLevel voltageLevel,
                                                 Identifiable<?> busOrBusbarSection, Menu create) {
        for (InjectionCreations.Kind kind : InjectionCreations.Kind.values()) {
            create.getItems().add(kindItem(kind.key(),
                    () -> InjectionCreations.create(sceneModel, owner.get(), kind, voltageLevel, busOrBusbarSection)));
        }
        create.getItems().add(new SeparatorMenuItem());
        create.getItems().add(lineItem(sceneModel, owner, voltageLevel.getParentNetwork(), voltageLevel, busOrBusbarSection));
        voltageLevel.getSubstation().ifPresent(substation ->
                create.getItems().addAll(transformerItems(sceneModel, owner, substation, voltageLevel, busOrBusbarSection)));
    }

    private static List<MenuItem> topologyItems(SceneModel sceneModel, Supplier<Window> owner, Network network, Object context) {
        List<MenuItem> items = new ArrayList<>();
        switch (context) {
            case VoltageLevel voltageLevel when voltageLevel.getTopologyKind() == TopologyKind.NODE_BREAKER -> {
                items.add(item("edit.topology.newBusbarSection", () -> TopologyEdits.newBusbarSection(sceneModel, owner.get(), voltageLevel)));
                items.add(item("edit.topology.newCouplingDevice", () -> TopologyEdits.newCouplingDevice(sceneModel, owner.get(), voltageLevel, null)));
                items.add(item("edit.topology.newSwitch", () -> TopologyEdits.newSwitch(sceneModel, owner.get(), voltageLevel, null)));
                items.add(item("edit.topology.newInternalConnection", () -> TopologyEdits.newInternalConnection(sceneModel, owner.get(), voltageLevel, null)));
                items.add(item("edit.topology.connectFeeders", () -> TopologyEdits.connectFeeders(sceneModel, owner.get(), voltageLevel)));
            }
            case VoltageLevel voltageLevel -> {
                items.add(item("edit.topology.newBus", () -> TopologyEdits.newBus(sceneModel, owner.get(), voltageLevel)));
                items.add(item("edit.topology.newCouplingDevice", () -> TopologyEdits.newCouplingDevice(sceneModel, owner.get(), voltageLevel, null)));
            }
            case BusbarSection busbarSection -> {
                VoltageLevel voltageLevel = busbarSection.getTerminal().getVoltageLevel();
                int node = busbarSection.getTerminal().getNodeBreakerView().getNode();
                if (busbarSection.getExtension(BusbarSectionPosition.class) != null) {
                    items.add(item("edit.topology.newBusbarSections", () -> TopologyEdits.newBusbarSections(sceneModel, owner.get(), busbarSection)));
                }
                items.add(item("edit.topology.newCouplingDevice", () -> TopologyEdits.newCouplingDevice(sceneModel, owner.get(), voltageLevel, busbarSection)));
                items.add(item("edit.topology.newSwitch", () -> TopologyEdits.newSwitch(sceneModel, owner.get(), voltageLevel, node)));
                items.add(item("edit.topology.newInternalConnection", () -> TopologyEdits.newInternalConnection(sceneModel, owner.get(), voltageLevel, node)));
            }
            case Bus bus when bus.getVoltageLevel().getTopologyKind() == TopologyKind.BUS_BREAKER -> {
                VoltageLevel voltageLevel = bus.getVoltageLevel();
                items.add(item("edit.topology.newCouplingDevice", () -> TopologyEdits.newCouplingDevice(sceneModel, owner.get(), voltageLevel, bus)));
            }
            case Connectable<?> connectable -> {
                items.add(item("edit.topology.move", () -> TopologyEdits.moveFeeder(sceneModel, owner.get(), connectable)));
                if (connectable instanceof Line line) {
                    items.add(new SeparatorMenuItem());
                    items.addAll(lineItems(sceneModel, owner, network, line));
                }
            }
            case Network n -> items.addAll(lineItems(sceneModel, owner, network, null));
            case null -> items.addAll(lineItems(sceneModel, owner, network, null));
            default -> {
                // nothing to edit in its topology
            }
        }
        return items;
    }

    // line cuttings of line, and their reverts, of the lines around it if any
    private static List<MenuItem> lineItems(SceneModel sceneModel, Supplier<Window> owner, Network network, Line line) {
        List<MenuItem> items = new ArrayList<>();
        if (line != null) {
            items.add(item("edit.topology.tapLine", () -> LineEdits.tapLine(sceneModel, owner.get(), line)));
            items.add(item("edit.topology.connectVoltageLevelOnLine", () -> LineEdits.connectVoltageLevelOnLine(sceneModel, owner.get(), line)));
        }
        boolean noLines = network.getLineCount() < 2;
        MenuItem revertTap = item("edit.topology.revertTapLine", () -> LineEdits.revertTapLine(sceneModel, owner.get(), network, line));
        MenuItem revertConnect = item("edit.topology.revertConnectVoltageLevelOnLine",
                () -> LineEdits.revertConnectVoltageLevelOnLine(sceneModel, owner.get(), network, line));
        MenuItem replaceTeePoint = item("edit.topology.replaceTeePoint", () -> LineEdits.replaceTeePoint(sceneModel, owner.get(), network, line));
        List.of(revertTap, revertConnect, replaceTeePoint).forEach(menuItem -> menuItem.setDisable(noLines));
        items.addAll(List.of(revertTap, revertConnect, replaceTeePoint));
        return items;
    }

    private static MenuItem item(String key, Runnable action) {
        return item(Messages.get(key) + "…", null, action);
    }

    /**
     * @param voltageLevel the voltage level of its first side, the network's highest if null
     * @param busOrBusbarSection where its first side is connected, the voltage level's first if null
     */
    public static MenuItem lineItem(SceneModel sceneModel, Supplier<Window> owner, Network network, VoltageLevel voltageLevel,
                                    Identifiable<?> busOrBusbarSection) {
        VoltageLevel first = voltageLevel != null || network == null ? voltageLevel : voltageLevelOf(network, null);
        MenuItem item = kindItem("desktop.common.equipment.line",
                () -> BranchCreations.newLine(sceneModel, owner.get(), network, first, busOrBusbarSection));
        item.setDisable(first == null);
        return item;
    }

    /**
     * The two- and three-winding transformer items of {@code substation}, their first side as {@link #lineItem}'s.
     */
    public static List<MenuItem> transformerItems(SceneModel sceneModel, Supplier<Window> owner, Substation substation,
                                                  VoltageLevel voltageLevel, Identifiable<?> busOrBusbarSection) {
        MenuItem twoWindings = kindItem("desktop.common.equipment.twoWindingsTransformer",
                () -> BranchCreations.newTwoWindingsTransformer(sceneModel, owner.get(), substation, voltageLevel, busOrBusbarSection));
        MenuItem threeWindings = kindItem("desktop.common.equipment.threeWindingsTransformer",
                () -> BranchCreations.newThreeWindingsTransformer(sceneModel, owner.get(), substation, voltageLevel, busOrBusbarSection));
        boolean noVoltageLevel = substation == null || substation.getVoltageLevelStream().findAny().isEmpty();
        twoWindings.setDisable(noVoltageLevel);
        threeWindings.setDisable(noVoltageLevel);
        return List.of(twoWindings, threeWindings);
    }

    /**
     * @param boundaryLine the first boundary line to pair, the first unpaired one if null
     */
    public static MenuItem tieLineItem(SceneModel sceneModel, Supplier<Window> owner, Network network, BoundaryLine boundaryLine) {
        MenuItem item = kindItem("desktop.common.equipment.tieLine", () -> BranchCreations.newTieLine(sceneModel, owner.get(), network, boundaryLine));
        item.setDisable(network == null || network.getBoundaryLineStream().filter(line -> !line.isPaired()).count() < 2);
        return item;
    }

    /**
     * @param station its first converter station, the first one without HVDC line if null
     */
    public static MenuItem hvdcLineItem(SceneModel sceneModel, Supplier<Window> owner, Network network, HvdcConverterStation<?> station) {
        MenuItem item = kindItem("desktop.common.equipment.hvdcLine", () -> BranchCreations.newHvdcLine(sceneModel, owner.get(), network, station));
        item.setDisable(network == null || network.getHvdcConverterStationStream().filter(s -> s.getHvdcLine() == null).count() < 2);
        return item;
    }

    /**
     * The creation items of injections of {@code kinds}, in {@code context}'s voltage level: the container itself, the
     * first of a substation, of the network if null - disabled where there is none yet.
     */
    public static List<MenuItem> injectionItems(SceneModel sceneModel, Supplier<Window> owner, Network network, Container<?> context,
                                                InjectionCreations.Kind... kinds) {
        VoltageLevel voltageLevel = network == null ? null : voltageLevelOf(network, context);
        List<MenuItem> items = new ArrayList<>();
        for (InjectionCreations.Kind kind : kinds) {
            MenuItem item = kindItem(kind.key(), () -> InjectionCreations.create(sceneModel, owner.get(), kind, voltageLevel, null));
            item.setDisable(voltageLevel == null);
            items.add(item);
        }
        return items;
    }

    static VoltageLevel voltageLevelOf(Network network, Container<?> context) {
        Stream<VoltageLevel> voltageLevels = switch (context) {
            case VoltageLevel voltageLevel -> Stream.of(voltageLevel);
            case Substation substation -> substation.getVoltageLevelStream();
            case null, default -> network.getVoltageLevelStream();
        };
        return voltageLevels.max(Comparator.comparingDouble(VoltageLevel::getNominalV)).orElse(null);
    }

    static MenuItem kindItem(String kindKey, Runnable action) {
        return item(Messages.get(kindKey) + "…", null, action);
    }

    static MenuItem item(String text, String icon, Runnable action) {
        MenuItem item = icon == null ? new MenuItem(text) : new MenuItem(text, new FontIcon(icon));
        item.setOnAction(event -> action.run());
        return item;
    }
}
