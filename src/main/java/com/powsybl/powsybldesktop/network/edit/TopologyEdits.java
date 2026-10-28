/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.ConnectFeedersToBusbarSectionsBuilder;
import com.powsybl.iidm.modification.topology.CreateCouplingDeviceBuilder;
import com.powsybl.iidm.modification.topology.CreateVoltageLevelSectionsBuilder;
import com.powsybl.iidm.modification.topology.MoveFeederBayBuilder;
import com.powsybl.iidm.network.Bus;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.Connectable;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Switch;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.BusbarSectionPosition;
import com.powsybl.iidm.network.extensions.BusbarSectionPositionAdder;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.detail.DetailSections;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.controlsfx.control.CheckListView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Dialogs editing a voltage level's topology: busbar sections, couplings, switches and internal connections between
 * nodes (node breaker), buses (bus breaker), and moving a feeder to another busbar section or bus.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class TopologyEdits {

    private TopologyEdits() {
    }

    private static String title(String key) {
        return Messages.get(key);
    }

    /**
     * Adds sections next to {@code reference}, which must have a {@link BusbarSectionPosition}: on its busbar or all
     * the parallel ones, with the switches joining them.
     */
    public static Optional<Boolean> newBusbarSections(SceneModel sceneModel, Window owner, BusbarSection reference) {
        CreationForm form = new CreationForm();
        CreationForm.Field<Boolean> after = form.check("edit.topology.sections.after", true);
        CreationForm.Field<Boolean> allBusbars = form.check("edit.topology.sections.allBusbars", true);
        List<SwitchKind> kinds = List.of(SwitchKind.BREAKER, SwitchKind.DISCONNECTOR);
        CreationForm.Field<SwitchKind> leftKind = form.choice("edit.topology.sections.leftSwitchKind", kinds, DetailSections.enumFormat(), SwitchKind.DISCONNECTOR, true);
        CreationForm.Field<Boolean> leftOpen = form.check("edit.topology.sections.leftSwitchOpen", false);
        CreationForm.Field<SwitchKind> rightKind = form.choice("edit.topology.sections.rightSwitchKind", kinds, DetailSections.enumFormat(), SwitchKind.DISCONNECTOR, true);
        CreationForm.Field<Boolean> rightOpen = form.check("edit.topology.sections.rightSwitchOpen", false);
        String voltageLevelId = reference.getTerminal().getVoltageLevel().getId();
        CreationForm.Field<String> busbarSectionPrefix = form.text("edit.topology.busbarSectionPrefix", voltageLevelId, true);
        CreationForm.Field<String> switchPrefix = form.text("edit.topology.switchPrefix", voltageLevelId, true);
        return CreateDialog.show(owner, title("edit.topology.newBusbarSections"), form, () -> NetworkEdits.apply(sceneModel.getMainModel(),
                reference.getParentNetwork(), network -> NetworkEdits.apply(network, new CreateVoltageLevelSectionsBuilder()
                        .withReferenceBusbarSectionId(reference.getId())
                        .withCreateTheBusbarSectionsAfterTheReferenceBusbarSection(after.get())
                        .withAllBusbars(allBusbars.get())
                        .withLeftSwitchKind(leftKind.get())
                        .withLeftSwitchOpen(leftOpen.get())
                        .withRightSwitchKind(rightKind.get())
                        .withRightSwitchOpen(rightOpen.get())
                        .withBusbarSectionPrefixId(busbarSectionPrefix.get())
                        .withSwitchPrefixId(switchPrefix.get())
                        .build())));
    }

    /**
     * Adds a busbar section at a node of {@code voltageLevel}, optionally positioned in its grid of busbars and sections.
     */
    public static Optional<BusbarSection> newBusbarSection(SceneModel sceneModel, Window owner, VoltageLevel voltageLevel) {
        Network network = voltageLevel.getParentNetwork();
        VoltageLevel.NodeBreakerView topology = voltageLevel.getNodeBreakerView();
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, voltageLevel.getId() + "_BBS"));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        CreationForm.Field<Integer> node = form.integer("edit.topology.node", topology.getMaximumNodeIndex() + 1);
        CreationForm.Field<Integer> busbarIndex = form.integer("edit.topology.busbarIndex", null, false);
        CreationForm.Field<Integer> sectionIndex = form.integer("edit.topology.sectionIndex", null, false);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.busbarSection")),
                form, () -> {
                    String busbarSectionId = id.get();
                    Integer busbar = busbarIndex.get();
                    Integer section = sectionIndex.get();
                    if (busbar == null != (section == null)) {
                        throw new CreationForm.FieldException((busbar == null ? busbarIndex : sectionIndex).getEditor(),
                                Messages.get("edit.topology.positionIncomplete"));
                    }
                    NetworkEdits.apply(sceneModel.getMainModel(), network, n -> {
                        BusbarSection busbarSection = topology.newBusbarSection().setId(busbarSectionId).setName(name.get()).setNode(node.get()).add();
                        if (busbar != null) {
                            busbarSection.newExtension(BusbarSectionPositionAdder.class).withBusbarIndex(busbar).withSectionIndex(section).add();
                        }
                    });
                    return network.getBusbarSection(busbarSectionId);
                });
    }

    /**
     * Adds a coupling device between two busbar sections or buses of {@code voltageLevel}: a breaker with its
     * disconnectors (node breaker), a switch (bus breaker).
     */
    public static Optional<Boolean> newCouplingDevice(SceneModel sceneModel, Window owner, VoltageLevel voltageLevel, Identifiable<?> first) {
        List<Identifiable<?>> buses = busesOrBusbarSections(voltageLevel);
        CreationForm form = new CreationForm();
        Identifiable<?> initial1 = first != null ? first : buses.stream().findFirst().orElse(null);
        CreationForm.Field<Identifiable<?>> bus1 = form.choice("edit.topology.coupling.first", buses, CreationForm.identifiableFormat(), initial1, true);
        CreationForm.Field<Identifiable<?>> bus2 = form.choice("edit.topology.coupling.second", buses, CreationForm.identifiableFormat(),
                buses.stream().filter(bus -> bus != initial1).findFirst().orElse(null), true);
        CreationForm.Field<String> prefix = form.text("edit.topology.switchPrefix", null, false);
        return CreateDialog.show(owner, title("edit.topology.newCouplingDevice"), form, () -> {
            if (bus1.get() == bus2.get()) {
                throw new CreationForm.FieldException(bus2.getEditor(), Messages.get("edit.topology.coupling.same"));
            }
            CreateCouplingDeviceBuilder builder = new CreateCouplingDeviceBuilder()
                    .withBusOrBusbarSectionId1(bus1.get().getId())
                    .withBusOrBusbarSectionId2(bus2.get().getId());
            if (prefix.get() != null) {
                builder.withSwitchPrefixId(prefix.get());
            }
            return NetworkEdits.apply(sceneModel.getMainModel(), voltageLevel.getParentNetwork(), network -> NetworkEdits.apply(network, builder.build()));
        });
    }

    static List<Identifiable<?>> busesOrBusbarSections(VoltageLevel voltageLevel) {
        List<Identifiable<?>> buses = new ArrayList<>();
        if (voltageLevel.getTopologyKind() == TopologyKind.NODE_BREAKER) {
            voltageLevel.getNodeBreakerView().getBusbarSectionStream().forEach(buses::add);
        } else {
            voltageLevel.getBusBreakerView().getBusStream().forEach(buses::add);
        }
        buses.sort(Comparator.comparing(Identifiable::getId));
        return buses;
    }

    /**
     * Adds a switch between two nodes of {@code voltageLevel}, the first one being {@code node1} if not null.
     */
    public static Optional<Switch> newSwitch(SceneModel sceneModel, Window owner, VoltageLevel voltageLevel, Integer node1) {
        Network network = voltageLevel.getParentNetwork();
        VoltageLevel.NodeBreakerView topology = voltageLevel.getNodeBreakerView();
        int nextNode = topology.getMaximumNodeIndex() + 1;
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, voltageLevel.getId() + "_SW"));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        CreationForm.Field<SwitchKind> kind = form.choice("switches.column.kind", DetailSections.values(SwitchKind.class),
                DetailSections.enumFormat(), SwitchKind.BREAKER, true);
        CreationForm.Field<Integer> from = form.integer("edit.topology.node1", node1 != null ? node1 : nextNode);
        CreationForm.Field<Integer> to = form.integer("edit.topology.node2", node1 != null ? nextNode : nextNode + 1);
        CreationForm.Field<Boolean> open = form.check("switches.column.open", false);
        CreationForm.Field<Boolean> retained = form.check("switches.column.retained", false);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.switch")),
                form, () -> {
                    String switchId = id.get();
                    NetworkEdits.apply(sceneModel.getMainModel(), network, n -> topology.newSwitch().setId(switchId).setName(name.get())
                            .setKind(kind.get()).setNode1(from.get()).setNode2(to.get()).setOpen(open.get()).setRetained(retained.get()).add());
                    return network.getSwitch(switchId);
                });
    }

    /**
     * Adds an internal connection (a zero impedance link, without switch) between two nodes of {@code voltageLevel}.
     */
    public static Optional<Boolean> newInternalConnection(SceneModel sceneModel, Window owner, VoltageLevel voltageLevel, Integer node1) {
        VoltageLevel.NodeBreakerView topology = voltageLevel.getNodeBreakerView();
        int nextNode = topology.getMaximumNodeIndex() + 1;
        CreationForm form = new CreationForm();
        CreationForm.Field<Integer> from = form.integer("edit.topology.node1", node1 != null ? node1 : nextNode);
        CreationForm.Field<Integer> to = form.integer("edit.topology.node2", node1 != null ? nextNode : nextNode + 1);
        return CreateDialog.show(owner, title("edit.topology.newInternalConnection"), form, () -> NetworkEdits.apply(sceneModel.getMainModel(),
                voltageLevel.getParentNetwork(), network -> topology.newInternalConnection().setNode1(from.get()).setNode2(to.get()).add()));
    }

    /**
     * Adds a bus to a bus breaker {@code voltageLevel}.
     */
    public static Optional<Bus> newBus(SceneModel sceneModel, Window owner, VoltageLevel voltageLevel) {
        Network network = voltageLevel.getParentNetwork();
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, voltageLevel.getId() + "_BUS"));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        return CreateDialog.show(owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.bus")), form, () -> {
            String busId = id.get();
            NetworkEdits.apply(sceneModel.getMainModel(), network, n -> voltageLevel.getBusBreakerView().newBus().setId(busId).setName(name.get()).add());
            return voltageLevel.getBusBreakerView().getBus(busId);
        });
    }

    /**
     * Connects feeders of a node breaker {@code voltageLevel} to busbar sections, with disconnectors from their bays,
     * which their busbar sections need a {@link BusbarSectionPosition} for.
     */
    public static Optional<Boolean> connectFeeders(SceneModel sceneModel, Window owner, VoltageLevel voltageLevel) {
        CreationForm form = new CreationForm();
        CheckListView<BusbarSection> busbarSections = checkList(voltageLevel.getNodeBreakerView().getBusbarSectionStream()
                .filter(busbarSection -> busbarSection.getExtension(BusbarSectionPosition.class) != null)
                .sorted(Comparator.comparing(BusbarSection::getId)).toList());
        CheckListView<Connectable<?>> feeders = checkList(voltageLevel.getConnectableStream()
                .filter(connectable -> !(connectable instanceof BusbarSection))
                .<Connectable<?>>map(connectable -> connectable)
                .sorted(Comparator.comparing(Connectable::getId)).toList());
        form.custom("desktop.common.equipment.busbarSection.plural", busbarSections, null, () -> null);
        form.custom("edit.topology.feeders", feeders, null, () -> null);
        CreationForm.Field<Boolean> couplings = form.check("edit.topology.connectCouplingDevices", false);
        return CreateDialog.show(owner, title("edit.topology.connectFeeders"), form, () -> {
            List<BusbarSection> chosenBusbarSections = List.copyOf(busbarSections.getCheckModel().getCheckedItems());
            List<Connectable> chosenFeeders = List.copyOf(feeders.getCheckModel().getCheckedItems());
            if (chosenBusbarSections.isEmpty()) {
                throw new CreationForm.FieldException(busbarSections, Messages.get("edit.topology.noneChecked"));
            }
            return NetworkEdits.apply(sceneModel.getMainModel(), voltageLevel.getParentNetwork(), network -> NetworkEdits.apply(network,
                    new ConnectFeedersToBusbarSectionsBuilder()
                            .withBusbarSectionsToConnect(chosenBusbarSections)
                            .withConnectablesToConnect(chosenFeeders)
                            .withConnectCouplingDevices(couplings.get())
                            .build()));
        });
    }

    static <T extends Identifiable<?>> CheckListView<T> checkList(List<T> items) {
        CheckListView<T> list = new CheckListView<>();
        list.getItems().setAll(items);
        list.setCellFactory(view -> new CheckBoxListCell<>(list::getItemBooleanProperty, new StringConverter<>() {
            @Override
            public String toString(T identifiable) {
                return identifiable == null ? "" : identifiable.getNameOrId();
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        }));
        list.setPrefHeight(150);
        return list;
    }

    /**
     * Moves one of {@code connectable}'s terminals to another busbar section or bus, possibly of another voltage level:
     * its bay moves with it in node breaker voltage levels.
     */
    public static Optional<Boolean> moveFeeder(SceneModel sceneModel, Window owner, Connectable<?> connectable) {
        Network network = connectable.getParentNetwork();
        List<Terminal> terminals = new ArrayList<>(connectable.getTerminals());
        CreationForm form = new CreationForm();
        CreationForm.Field<Terminal> terminal = form.choice("desktop.common.column.side", terminals, terminalFormat(), terminals.getFirst(), true);
        VoltageLevel voltageLevel = terminals.getFirst().getVoltageLevel();
        ConnectionSection target = new ConnectionSection(form, Messages.get("edit.topology.target"), network.getVoltageLevelStream().toList(),
                voltageLevel, null, ConnectablePosition.Direction.BOTTOM);
        return CreateDialog.show(owner, Messages.get("edit.topology.moveTitle", Kinds.describe(connectable)), form, () -> NetworkEdits.apply(
                sceneModel.getMainModel(), network, n -> NetworkEdits.apply(n, new MoveFeederBayBuilder()
                        .withConnectableId(connectable.getId())
                        .withTerminal(terminal.get())
                        .withTargetVoltageLevelId(target.voltageLevel().getId())
                        .withTargetBusOrBusBarSectionId(target.busOrBusbarSectionId())
                        .build())));
    }

    private static StringConverter<Terminal> terminalFormat() {
        return new StringConverter<>() {
            @Override
            public String toString(Terminal terminal) {
                if (terminal == null) {
                    return "";
                }
                String voltageLevel = terminal.getVoltageLevel().getNameOrId();
                return terminal.getSide() == null ? voltageLevel : Messages.get("limits.side", terminal.getSide().getNum()) + " - " + voltageLevel;
            }

            @Override
            public Terminal fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
