/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.commons.report.ReportNode;
import com.powsybl.iidm.modification.topology.CreateBranchFeederBaysBuilder;
import com.powsybl.iidm.modification.topology.DefaultNamingStrategy;
import com.powsybl.iidm.modification.topology.TopologyModificationUtils;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.BranchAdder;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.ThreeWindingsTransformerAdder;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.iidm.network.extensions.ConnectablePositionAdder;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.detail.DetailForm;
import com.powsybl.powsybldesktop.network.detail.DetailSections;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Creation dialogs of the branches - lines and two-winding transformers connected with their bays
 * ({@code CreateBranchFeederBays}), three-winding transformers likewise leg by leg - and of the equipment linking
 * existing ones: tie lines (two unpaired boundary lines) and HVDC lines (two converter stations).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class BranchCreations {

    private BranchCreations() {
    }

    /**
     * Creates a line in {@code network}, its first side on {@code voltageLevel} and {@code busOrBusbarSection} if not null.
     */
    public static Optional<Line> newLine(SceneModel sceneModel, Window owner, Network network, VoltageLevel voltageLevel,
                                         Identifiable<?> busOrBusbarSection) {
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, "L"));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        form.section("detail.section.characteristics");
        CreationForm.Field<Double> r = form.number("desktop.common.column.r", DetailForm.OHM, 0, true);
        CreationForm.Field<Double> x = form.number("desktop.common.column.x", DetailForm.OHM, 1, true);
        CreationForm.Field<Double> g1 = form.number("desktop.common.column.g1", DetailForm.SIEMENS, 0, true);
        CreationForm.Field<Double> b1 = form.number("desktop.common.column.b1", DetailForm.SIEMENS, 0, true);
        CreationForm.Field<Double> g2 = form.number("desktop.common.column.g2", DetailForm.SIEMENS, 0, true);
        CreationForm.Field<Double> b2 = form.number("desktop.common.column.b2", DetailForm.SIEMENS, 0, true);
        List<VoltageLevel> voltageLevels = network.getVoltageLevelStream().toList();
        ConnectionSection side1 = new ConnectionSection(form, Messages.get("limits.side", 1), voltageLevels, voltageLevel, busOrBusbarSection,
                ConnectablePosition.Direction.TOP);
        ConnectionSection side2 = new ConnectionSection(form, Messages.get("limits.side", 2), voltageLevels, otherThan(voltageLevels, voltageLevel), null,
                ConnectablePosition.Direction.TOP);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.line")), form, () -> {
            String lineId = id.get();
            BranchAdder<?, ?> adder = network.newLine().setId(lineId).setName(name.get())
                    .setR(r.get()).setX(x.get()).setG1(g1.get()).setB1(b1.get()).setG2(g2.get()).setB2(b2.get());
            applyBranch(sceneModel, network, adder, side1, side2);
            return network.getLine(lineId);
        });
    }

    /**
     * Creates a two-winding transformer in {@code substation}, its first side on {@code voltageLevel} and
     * {@code busOrBusbarSection} if not null.
     */
    public static Optional<TwoWindingsTransformer> newTwoWindingsTransformer(SceneModel sceneModel, Window owner, Substation substation,
                                                                             VoltageLevel voltageLevel, Identifiable<?> busOrBusbarSection) {
        Network network = substation.getParentNetwork();
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, substation.getId() + "_T"));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        form.section("detail.section.characteristics");
        CreationForm.Field<Double> r = form.number("desktop.common.column.r", DetailForm.OHM, 0, true);
        CreationForm.Field<Double> x = form.number("desktop.common.column.x", DetailForm.OHM, 1, true);
        CreationForm.Field<Double> g = form.number("desktop.common.column.g", DetailForm.SIEMENS, 0, true);
        CreationForm.Field<Double> b = form.number("desktop.common.column.b", DetailForm.SIEMENS, 0, true);
        CreationForm.Field<Double> ratedU1 = form.number("detail.transformer.ratedU1", DetailForm.KV, Double.NaN, false);
        CreationForm.Field<Double> ratedU2 = form.number("detail.transformer.ratedU2", DetailForm.KV, Double.NaN, false);
        CreationForm.Field<Double> ratedS = form.number("transformers.column.ratedS", DetailForm.MVA, Double.NaN, false);
        List<VoltageLevel> voltageLevels = byDecreasingVoltage(substation);
        VoltageLevel first = voltageLevel != null ? voltageLevel : voltageLevels.stream().findFirst().orElse(null);
        ConnectionSection side1 = new ConnectionSection(form, Messages.get("limits.side", 1), voltageLevels, first, busOrBusbarSection,
                ConnectablePosition.Direction.TOP);
        ConnectionSection side2 = new ConnectionSection(form, Messages.get("limits.side", 2), voltageLevels, otherThan(voltageLevels, first), null,
                ConnectablePosition.Direction.BOTTOM);
        return CreateDialog.showThenDetails(sceneModel, owner,
                Messages.get("edit.create.title", Messages.get("desktop.common.equipment.twoWindingsTransformer")), form, () -> {
                    String transformerId = id.get();
                    BranchAdder<?, ?> adder = substation.newTwoWindingsTransformer().setId(transformerId).setName(name.get())
                            .setR(r.get()).setX(x.get()).setG(g.get()).setB(b.get())
                            .setRatedU1(orNominal(ratedU1.get(), side1)).setRatedU2(orNominal(ratedU2.get(), side2)).setRatedS(ratedS.get());
                    applyBranch(sceneModel, network, adder, side1, side2);
                    return network.getTwoWindingsTransformer(transformerId);
                });
    }

    private static void applyBranch(SceneModel sceneModel, Network network, BranchAdder<?, ?> adder, ConnectionSection side1, ConnectionSection side2) {
        CreateBranchFeederBaysBuilder builder = new CreateBranchFeederBaysBuilder()
                .withBranchAdder(adder)
                .withBusOrBusbarSectionId1(side1.busOrBusbarSectionId())
                .withBusOrBusbarSectionId2(side2.busOrBusbarSectionId())
                .withLogOrThrowIfIncorrectPositionOrder1(true)
                .withLogOrThrowIfIncorrectPositionOrder2(true);
        NetworkEdits.apply(sceneModel.getMainModel(), network, n -> FeederOrders.reserving(orders -> {
            FeederOrders.Slot slot1 = side1.isNodeBreaker() ? side1.reserve(orders, 1) : null;
            FeederOrders.Slot slot2 = side2.isNodeBreaker() ? side2.reserve(orders, 1) : null;
            // read once both are reserved: the second may have moved the first
            if (slot1 != null) {
                builder.withPositionOrder1(slot1.order()).withDirection1(side1.direction());
            }
            if (slot2 != null) {
                builder.withPositionOrder2(slot2.order()).withDirection2(side2.direction());
            }
            NetworkEdits.apply(n, builder.build());
        }));
    }

    private static double orNominal(double ratedU, ConnectionSection side) {
        return Double.isNaN(ratedU) ? side.voltageLevel().getNominalV() : ratedU;
    }

    private static List<VoltageLevel> byDecreasingVoltage(Substation substation) {
        return substation.getVoltageLevelStream().sorted(Comparator.comparingDouble(VoltageLevel::getNominalV).reversed()).toList();
    }

    private static VoltageLevel otherThan(List<VoltageLevel> voltageLevels, VoltageLevel voltageLevel) {
        return voltageLevels.stream().filter(other -> other != voltageLevel).findFirst().orElse(voltageLevel);
    }

    /**
     * Creates a three-winding transformer in {@code substation}, its first leg on {@code voltageLevel} and
     * {@code busOrBusbarSection} if not null.
     */
    public static Optional<ThreeWindingsTransformer> newThreeWindingsTransformer(SceneModel sceneModel, Window owner, Substation substation,
                                                                                 VoltageLevel voltageLevel, Identifiable<?> busOrBusbarSection) {
        ThreeWindingsTransformerForm form = new ThreeWindingsTransformerForm(substation, voltageLevel, busOrBusbarSection);
        return CreateDialog.showThenDetails(sceneModel, owner,
                Messages.get("edit.create.title", Messages.get("desktop.common.equipment.threeWindingsTransformer")), form.form, () -> form.create(sceneModel));
    }

    private static final class ThreeWindingsTransformerForm {
        private final Substation substation;
        private final CreationForm form = new CreationForm();
        private final CreationForm.Field<String> id;
        private final CreationForm.Field<String> name;
        private final CreationForm.Field<Double> ratedU0;
        private final List<Leg> legs = new ArrayList<>();

        private record Leg(CreationForm.Field<Double> r, CreationForm.Field<Double> x, CreationForm.Field<Double> g, CreationForm.Field<Double> b,
                           CreationForm.Field<Double> ratedU, ConnectionSection connection) {
        }

        private ThreeWindingsTransformerForm(Substation substation, VoltageLevel voltageLevel, Identifiable<?> busOrBusbarSection) {
            this.substation = substation;
            Network network = substation.getParentNetwork();
            id = form.id(network, NetworkEdits.uniqueId(network, substation.getId() + "_T"));
            name = form.text("desktop.common.column.name", null, false);
            ratedU0 = form.number("detail.transformer.ratedU0", DetailForm.KV, Double.NaN, false);
            List<VoltageLevel> voltageLevels = byDecreasingVoltage(substation);
            List<VoltageLevel> initial = new ArrayList<>(voltageLevels);
            if (voltageLevel != null) {
                initial.remove(voltageLevel);
                initial.addFirst(voltageLevel);
            }
            for (int leg = 1; leg <= 3; leg++) {
                form.section("detail.section.leg", leg);
                CreationForm.Field<Double> r = form.number("desktop.common.column.r", DetailForm.OHM, 0, true);
                CreationForm.Field<Double> x = form.number("desktop.common.column.x", DetailForm.OHM, 1, true);
                CreationForm.Field<Double> g = form.number("desktop.common.column.g", DetailForm.SIEMENS, 0, true);
                CreationForm.Field<Double> b = form.number("desktop.common.column.b", DetailForm.SIEMENS, 0, true);
                CreationForm.Field<Double> ratedU = form.number("transformers.column.ratedU", DetailForm.KV, Double.NaN, false);
                VoltageLevel legVoltageLevel = initial.isEmpty() ? null : initial.get(Math.min(leg - 1, initial.size() - 1));
                ConnectionSection connection = new ConnectionSection(form, Messages.get("detail.section.connection"), voltageLevels, legVoltageLevel,
                        leg == 1 ? busOrBusbarSection : null, leg == 1 ? ConnectablePosition.Direction.TOP : ConnectablePosition.Direction.BOTTOM);
                legs.add(new Leg(r, x, g, b, ratedU, connection));
            }
        }

        private ThreeWindingsTransformer create(SceneModel sceneModel) {
            String transformerId = id.get();
            ThreeWindingsTransformerAdder adder = substation.newThreeWindingsTransformer().setId(transformerId).setName(name.get());
            Leg leg1 = legs.getFirst();
            adder.setRatedU0(Double.isNaN(ratedU0.get()) ? leg1.connection().voltageLevel().getNominalV() : ratedU0.get());
            // the node of each node breaker leg, the next free ones of its voltage level
            Map<VoltageLevel, Integer> nextNodes = new HashMap<>();
            int[] nodes = new int[3];
            for (int i = 0; i < 3; i++) {
                Leg leg = legs.get(i);
                ConnectionSection connection = leg.connection();
                VoltageLevel voltageLevel = connection.voltageLevel();
                ThreeWindingsTransformerAdder.LegAdder legAdder = (i == 0 ? adder.newLeg1() : i == 1 ? adder.newLeg2() : adder.newLeg3())
                        .setR(leg.r().get()).setX(leg.x().get()).setG(leg.g().get()).setB(leg.b().get())
                        .setRatedU(Double.isNaN(leg.ratedU().get()) ? voltageLevel.getNominalV() : leg.ratedU().get())
                        .setVoltageLevel(voltageLevel.getId());
                if (connection.isNodeBreaker()) {
                    nodes[i] = nextNodes.merge(voltageLevel, voltageLevel.getNodeBreakerView().getMaximumNodeIndex() + 1, (previous, unused) -> previous + 1);
                    legAdder.setNode(nodes[i]);
                } else {
                    legAdder.setBus(connection.busOrBusbarSectionId()).setConnectableBus(connection.busOrBusbarSectionId());
                }
                legAdder.add();
            }
            NetworkEdits.apply(sceneModel.getMainModel(), substation.getParentNetwork(), network -> FeederOrders.reserving(orders -> {
                FeederOrders.Slot[] slots = new FeederOrders.Slot[3];
                for (int i = 0; i < 3; i++) {
                    ConnectionSection connection = legs.get(i).connection();
                    slots[i] = connection.isNodeBreaker() ? connection.reserve(orders, 1) : null;
                }
                ThreeWindingsTransformer transformer = adder.add();
                addBays(network, transformer, nodes, slots);
            }));
            return substation.getParentNetwork().getThreeWindingsTransformer(transformerId);
        }

        // as CreateBranchFeederBays does for each side of a two-winding transformer
        @SuppressWarnings("unchecked")
        private void addBays(Network network, ThreeWindingsTransformer transformer, int[] nodes, FeederOrders.Slot[] slots) {
            ConnectablePositionAdder<ThreeWindingsTransformer> positions = null;
            for (int i = 0; i < 3; i++) {
                ConnectionSection connection = legs.get(i).connection();
                if (!connection.isNodeBreaker()) {
                    continue;
                }
                VoltageLevel voltageLevel = connection.voltageLevel();
                TopologyModificationUtils.createTopologyWithConnectableNode(i + 1, connection.busOrBusbarSectionId(), network, voltageLevel,
                        nodes[i], transformer, new DefaultNamingStrategy(), ReportNode.NO_OP);
                if (ConnectionSection.usesPositions(voltageLevel, transformer)) {
                    if (positions == null) {
                        positions = transformer.newExtension(ConnectablePositionAdder.class);
                    }
                    (i == 0 ? positions.newFeeder1() : i == 1 ? positions.newFeeder2() : positions.newFeeder3())
                            .withName(transformer.getId()).withOrder(slots[i].order()).withDirection(connection.direction()).add();
                }
            }
            if (positions != null) {
                positions.add();
            }
        }
    }

    /**
     * Creates a tie line pairing two unpaired boundary lines, {@code boundaryLine} if not null.
     */
    public static Optional<TieLine> newTieLine(SceneModel sceneModel, Window owner, Network network, BoundaryLine boundaryLine) {
        List<BoundaryLine> unpaired = network.getBoundaryLineStream().filter(line -> !line.isPaired())
                .sorted(Comparator.comparing(BoundaryLine::getNameOrId)).toList();
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, "TL"));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        BoundaryLine first = boundaryLine != null ? boundaryLine : unpaired.stream().findFirst().orElse(null);
        CreationForm.Field<BoundaryLine> boundaryLine1 = form.choice("edit.create.boundaryLine1", unpaired, CreationForm.identifiableFormat(), first, true);
        CreationForm.Field<BoundaryLine> boundaryLine2 = form.choice("edit.create.boundaryLine2", unpaired, CreationForm.identifiableFormat(),
                matching(unpaired, first), true);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.tieLine")), form, () -> {
            String tieLineId = id.get();
            BoundaryLine line1 = boundaryLine1.get();
            BoundaryLine line2 = boundaryLine2.get();
            if (line1 == line2) {
                throw new CreationForm.FieldException(boundaryLine2.getEditor(), Messages.get("edit.create.sameBoundaryLine"));
            }
            NetworkEdits.apply(sceneModel.getMainModel(), network, n -> n.newTieLine().setId(tieLineId).setName(name.get())
                    .setBoundaryLine1(line1.getId()).setBoundaryLine2(line2.getId()).add());
            return network.getTieLine(tieLineId);
        });
    }

    // the other unpaired boundary line with the same pairing key, if any
    private static BoundaryLine matching(List<BoundaryLine> unpaired, BoundaryLine boundaryLine) {
        return unpaired.stream()
                .filter(other -> other != boundaryLine && boundaryLine != null && boundaryLine.getPairingKey() != null
                        && boundaryLine.getPairingKey().equals(other.getPairingKey()))
                .findFirst()
                .orElse(unpaired.stream().filter(other -> other != boundaryLine).findFirst().orElse(null));
    }

    /**
     * Creates an HVDC line between two converter stations of the same type, {@code station} if not null.
     */
    public static Optional<HvdcLine> newHvdcLine(SceneModel sceneModel, Window owner, Network network, HvdcConverterStation<?> station) {
        List<HvdcConverterStation<?>> free = network.getHvdcConverterStationStream().filter(s -> s.getHvdcLine() == null)
                .sorted(Comparator.comparing(HvdcConverterStation::getNameOrId)).toList();
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, "HVDC"));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        HvdcConverterStation<?> first = station != null ? station : free.stream().findFirst().orElse(null);
        CreationForm.Field<HvdcConverterStation<?>> station1 = form.choice("edit.create.converterStation1", free, CreationForm.identifiableFormat(), first, true);
        CreationForm.Field<HvdcConverterStation<?>> station2 = form.choice("edit.create.converterStation2", free, CreationForm.identifiableFormat(),
                free.stream().filter(s -> s != first && (first == null || s.getHvdcType() == first.getHvdcType())).findFirst().orElse(null), true);
        CreationForm.Field<Double> r = form.number("desktop.common.column.r", DetailForm.OHM, 1, true);
        CreationForm.Field<Double> nominalV = form.number("desktop.common.column.nominalVoltage", DetailForm.KV, 400, true);
        CreationForm.Field<HvdcLine.ConvertersMode> convertersMode = form.choice("hvdcLines.column.convertersMode",
                DetailSections.values(HvdcLine.ConvertersMode.class), DetailSections.enumFormat(), HvdcLine.ConvertersMode.SIDE_1_RECTIFIER_SIDE_2_INVERTER, true);
        CreationForm.Field<Double> setpoint = form.number("hvdcLines.column.activePowerSetpoint", DetailForm.MW, 0, true);
        CreationForm.Field<Double> maxP = form.number("desktop.common.column.maxP", DetailForm.MW, 100, true);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.hvdcLine")), form, () -> {
            String hvdcLineId = id.get();
            HvdcConverterStation<?> converter1 = station1.get();
            HvdcConverterStation<?> converter2 = station2.get();
            if (converter1 == converter2) {
                throw new CreationForm.FieldException(station2.getEditor(), Messages.get("edit.create.sameConverterStation"));
            }
            NetworkEdits.apply(sceneModel.getMainModel(), network, n -> n.newHvdcLine().setId(hvdcLineId).setName(name.get())
                    .setConverterStationId1(converter1.getId()).setConverterStationId2(converter2.getId())
                    .setR(r.get()).setNominalV(nominalV.get()).setConvertersMode(convertersMode.get())
                    .setActivePowerSetpoint(setpoint.get()).setMaxP(maxP.get()).add());
            return network.getHvdcLine(hvdcLineId);
        });
    }
}
