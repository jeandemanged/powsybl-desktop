/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.edit;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.powsybldesktop.core.NetworkChanges;
import com.powsybl.powsybldesktop.network.edit.Branches;
import com.powsybl.powsybldesktop.network.edit.Connection;
import com.powsybl.powsybldesktop.network.edit.NetworkEdits;
import com.powsybl.powsybldesktop.ui.form.CreationForm;
import com.powsybl.powsybldesktop.ui.form.DetailForm;
import com.powsybl.powsybldesktop.ui.form.DetailSections;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
     * Creates a line in {@code network}, its first side on {@code voltageLevel} and {@code busOrBusbarSection} if not
     * null, its second side by default on the {@link Branches#otherEnd} of {@code voltageLevel}.
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
        ConnectionSection side2 = new ConnectionSection(form, Messages.get("limits.side", 2), voltageLevels,
                Branches.otherEnd(voltageLevels, voltageLevel), null, ConnectablePosition.Direction.TOP);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.line")), form, () -> {
            Branches.LineSpec spec = new Branches.LineSpec(id.get(), name.get(), r.get(), x.get(), g1.get(), b1.get(), g2.get(), b2.get());
            Connection at1 = side1.connection();
            Connection at2 = side2.connection();
            NetworkChanges.apply(sceneModel.getMainModel(), network, n -> Branches.addLine(n, spec, at1, at2));
            return network.getLine(spec.id());
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
        List<VoltageLevel> voltageLevels = Branches.byDecreasingVoltage(substation);
        VoltageLevel first = voltageLevel != null ? voltageLevel : voltageLevels.stream().findFirst().orElse(null);
        ConnectionSection side1 = new ConnectionSection(form, Messages.get("limits.side", 1), voltageLevels, first, busOrBusbarSection,
                ConnectablePosition.Direction.TOP);
        ConnectionSection side2 = new ConnectionSection(form, Messages.get("limits.side", 2), voltageLevels, Branches.otherThan(voltageLevels, first), null,
                ConnectablePosition.Direction.BOTTOM);
        return CreateDialog.showThenDetails(sceneModel, owner,
                Messages.get("edit.create.title", Messages.get("desktop.common.equipment.twoWindingsTransformer")), form, () -> {
                    Branches.TwoWindingsTransformerSpec spec = new Branches.TwoWindingsTransformerSpec(id.get(), name.get(), r.get(), x.get(),
                            g.get(), b.get(), ratedU1.get(), ratedU2.get(), ratedS.get());
                    Connection at1 = side1.connection();
                    Connection at2 = side2.connection();
                    NetworkChanges.apply(sceneModel.getMainModel(), network, _ -> Branches.addTwoWindingsTransformer(substation, spec, at1, at2));
                    return network.getTwoWindingsTransformer(spec.id());
                });
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
            List<VoltageLevel> voltageLevels = Branches.byDecreasingVoltage(substation);
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
            List<Branches.Leg> legSpecs = legs.stream()
                    .map(leg -> new Branches.Leg(leg.r().get(), leg.x().get(), leg.g().get(), leg.b().get(), leg.ratedU().get(), leg.connection().connection()))
                    .toList();
            Branches.ThreeWindingsTransformerSpec spec = new Branches.ThreeWindingsTransformerSpec(id.get(), name.get(), ratedU0.get(), legSpecs);
            NetworkChanges.apply(sceneModel.getMainModel(), substation.getParentNetwork(), _ -> Branches.addThreeWindingsTransformer(substation, spec));
            return substation.getParentNetwork().getThreeWindingsTransformer(spec.id());
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
                Branches.matchingBoundaryLine(unpaired, first), true);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.tieLine")), form, () -> {
            String tieLineId = id.get();
            BoundaryLine line1 = boundaryLine1.get();
            BoundaryLine line2 = boundaryLine2.get();
            if (line1 == line2) {
                throw new CreationForm.FieldException(boundaryLine2.getEditor(), Messages.get("edit.create.sameBoundaryLine"));
            }
            String tieLineName = name.get();
            NetworkChanges.apply(sceneModel.getMainModel(), network, n -> Branches.addTieLine(n, tieLineId, tieLineName, line1, line2));
            return network.getTieLine(tieLineId);
        });
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
            Branches.HvdcLineSpec spec = new Branches.HvdcLineSpec(hvdcLineId, name.get(), converter1, converter2, r.get(), nominalV.get(),
                    convertersMode.get(), setpoint.get(), maxP.get());
            NetworkChanges.apply(sceneModel.getMainModel(), network, n -> Branches.addHvdcLine(n, spec));
            return network.getHvdcLine(hvdcLineId);
        });
    }
}
