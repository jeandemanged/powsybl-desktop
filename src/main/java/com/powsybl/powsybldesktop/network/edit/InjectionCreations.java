/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.CreateFeederBayBuilder;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.InjectionAdder;
import com.powsybl.iidm.network.LoadType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensatorAdder;
import com.powsybl.iidm.network.ShuntCompensatorModelType;
import com.powsybl.iidm.network.ShuntCompensatorNonLinearModelAdder;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.regulation.VoltageRegulationHolderAdder;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.detail.DetailForm;
import com.powsybl.powsybldesktop.network.detail.DetailSections;
import com.powsybl.powsybldesktop.network.tables.VoltageRegulations;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.ComboBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Creation dialogs of the injections, connected with their bay ({@code CreateFeederBay}): the values IIDM requires for
 * the type, the others being set afterwards in the detail view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class InjectionCreations {

    public enum Kind {
        GENERATOR("desktop.common.equipment.generator", "_G"),
        BATTERY("desktop.common.equipment.battery", "_BAT"),
        LOAD("desktop.common.equipment.load", "_LD"),
        SHUNT_COMPENSATOR("desktop.common.equipment.shuntCompensator", "_SH"),
        STATIC_VAR_COMPENSATOR("desktop.common.equipment.staticVarCompensator", "_SVC"),
        VSC_CONVERTER_STATION("desktop.common.equipment.vscConverterStation", "_VSC"),
        LCC_CONVERTER_STATION("desktop.common.equipment.lccConverterStation", "_LCC"),
        BOUNDARY_LINE("desktop.common.equipment.boundaryLine", "_BL"),
        GROUND("desktop.common.equipment.ground", "_GND");

        private final String key;
        private final String idSuffix;

        Kind(String key, String idSuffix) {
            this.key = key;
            this.idSuffix = idSuffix;
        }

        public String key() {
            return key;
        }
    }

    // the adder of an injection, with the values of its kind's fields
    @FunctionalInterface
    private interface AdderFactory {
        InjectionAdder<?, ?> create(VoltageLevel voltageLevel, String id, String name);
    }

    private InjectionCreations() {
    }

    /**
     * Creates an injection of {@code kind} in {@code voltageLevel}, on {@code busOrBusbarSection} if not null.
     */
    public static Optional<Identifiable<?>> create(SceneModel sceneModel, Window owner, Kind kind, VoltageLevel voltageLevel,
                                                  Identifiable<?> busOrBusbarSection) {
        Network network = voltageLevel.getParentNetwork();
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, voltageLevel.getId() + kind.idSuffix));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        AdderFactory adder = switch (kind) {
            case GENERATOR -> generatorFields(form);
            case BATTERY -> batteryFields(form);
            case LOAD -> loadFields(form);
            case SHUNT_COMPENSATOR -> shuntFields(form);
            case STATIC_VAR_COMPENSATOR -> svcFields(form);
            case VSC_CONVERTER_STATION -> vscFields(form);
            case LCC_CONVERTER_STATION -> lccFields(form);
            case BOUNDARY_LINE -> boundaryLineFields(form);
            case GROUND -> (vl, groundId, groundName) -> vl.newGround().setId(groundId).setName(groundName);
        };
        List<VoltageLevel> voltageLevels = network.getVoltageLevelStream().toList();
        ConnectionSection connection = new ConnectionSection(form, Messages.get("detail.section.connection"), voltageLevels, voltageLevel,
                busOrBusbarSection, ConnectablePosition.Direction.BOTTOM);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get(kind.key())), form, () -> {
            String injectionId = id.get();
            VoltageLevel chosen = connection.voltageLevel();
            InjectionAdder<?, ?> injectionAdder = adder.create(chosen, injectionId, name.get());
            CreateFeederBayBuilder builder = new CreateFeederBayBuilder()
                    .withInjectionAdder(injectionAdder)
                    .withBusOrBusbarSectionId(connection.busOrBusbarSectionId())
                    .withLogOrThrowIfIncorrectPositionOrder(true);
            NetworkEdits.apply(sceneModel.getMainModel(), network, n -> FeederOrders.reserving(orders -> {
                if (connection.isNodeBreaker()) {
                    builder.withInjectionPositionOrder(connection.reserve(orders, 1).order()).withInjectionDirection(connection.direction());
                }
                NetworkEdits.apply(n, builder.build());
            }));
            return network.getIdentifiable(injectionId);
        });
    }

    private static AdderFactory loadFields(CreationForm form) {
        CreationForm.Field<LoadType> type = form.choice("desktop.common.column.type", DetailSections.values(LoadType.class),
                DetailSections.enumFormat(), LoadType.UNDEFINED, true);
        CreationForm.Field<Double> p0 = form.number("desktop.common.column.p0", DetailForm.MW, 0, true);
        CreationForm.Field<Double> q0 = form.number("desktop.common.column.q0", DetailForm.MVAR, 0, true);
        return (vl, id, name) -> vl.newLoad().setId(id).setName(name).setLoadType(type.get()).setP0(p0.get()).setQ0(q0.get());
    }

    private static AdderFactory generatorFields(CreationForm form) {
        CreationForm.Field<EnergySource> energySource = form.choice("detail.generator.energySource", DetailSections.values(EnergySource.class),
                DetailSections.enumFormat(), EnergySource.OTHER, true);
        CreationForm.Field<Double> minP = form.number("desktop.common.column.minP", DetailForm.MW, 0, true);
        CreationForm.Field<Double> maxP = form.number("desktop.common.column.maxP", DetailForm.MW, 100, true);
        CreationForm.Field<Double> targetP = form.number("generators.column.targetP", DetailForm.MW, 0, true);
        Regulation regulation = new Regulation(form, RegulationMode.VOLTAGE);
        return (vl, id, name) -> regulation.apply(vl, vl.newGenerator().setId(id).setName(name)
                .setEnergySource(energySource.get())
                .setMinP(minP.get())
                .setMaxP(maxP.get())
                .setTargetP(targetP.get()));
    }

    private static AdderFactory batteryFields(CreationForm form) {
        CreationForm.Field<Double> minP = form.number("desktop.common.column.minP", DetailForm.MW, 0, true);
        CreationForm.Field<Double> maxP = form.number("desktop.common.column.maxP", DetailForm.MW, 100, true);
        CreationForm.Field<Double> targetP = form.number("generators.column.targetP", DetailForm.MW, 0, true);
        Regulation regulation = new Regulation(form, null);
        return (vl, id, name) -> regulation.apply(vl, vl.newBattery().setId(id).setName(name)
                .setMinP(minP.get())
                .setMaxP(maxP.get())
                .setTargetP(targetP.get()));
    }

    private static AdderFactory shuntFields(CreationForm form) {
        CreationForm.Field<ShuntCompensatorModelType> model = form.choice("edit.create.shunt.model", DetailSections.values(ShuntCompensatorModelType.class),
                modelFormat(), ShuntCompensatorModelType.LINEAR, true);
        ComboBox<?> modelBox = (ComboBox<?>) model.getEditor();
        CreationForm.Field<Double> bPerSection = form.number("detail.shunt.bPerSection", DetailForm.SIEMENS, Double.NaN, false)
                .visibleWhen(modelBox.valueProperty().isEqualTo(ShuntCompensatorModelType.LINEAR));
        CreationForm.Field<Double> gPerSection = form.number("detail.shunt.gPerSection", DetailForm.SIEMENS, 0, true)
                .visibleWhen(modelBox.valueProperty().isEqualTo(ShuntCompensatorModelType.LINEAR));
        CreationForm.Field<Integer> maximumSectionCount = form.integer("detail.shunt.maximumSectionCount", 1)
                .visibleWhen(modelBox.valueProperty().isEqualTo(ShuntCompensatorModelType.LINEAR));
        CreationForm.Field<String> sections = form.text("edit.create.shunt.sections", null, false)
                .visibleWhen(modelBox.valueProperty().isEqualTo(ShuntCompensatorModelType.NON_LINEAR));
        CreationForm.Field<Integer> sectionCount = form.integer("detail.shunt.sectionCount", 0);
        Regulation regulation = new Regulation(form, null);
        return (vl, id, name) -> {
            ShuntCompensatorAdder adder = vl.newShuntCompensator().setId(id).setName(name).setSectionCount(sectionCount.get());
            if (model.get() == ShuntCompensatorModelType.LINEAR) {
                adder.newLinearModel().setBPerSection(bPerSection.get()).setGPerSection(gPerSection.get())
                        .setMaximumSectionCount(maximumSectionCount.get()).add();
            } else {
                ShuntCompensatorNonLinearModelAdder nonLinear = adder.newNonLinearModel();
                parseSections(sections).forEach(b -> nonLinear.beginSection().setB(b).setG(0).endSection());
                nonLinear.add();
            }
            return regulation.apply(vl, adder);
        };
    }

    // the sections' susceptances, e.g. "0.001; 0.002"
    private static List<Double> parseSections(CreationForm.Field<String> sections) {
        String text = sections.get();
        if (text == null) {
            throw new CreationForm.FieldException(sections.getEditor(), Messages.get("desktop.common.editError.mandatory"));
        }
        List<Double> values = new ArrayList<>();
        for (String value : text.split(";")) {
            try {
                values.add(Double.parseDouble(value.trim()));
            } catch (NumberFormatException e) {
                throw new CreationForm.FieldException(sections.getEditor(), Messages.get("desktop.common.editError.invalidNumber", value.trim()));
            }
        }
        return values;
    }

    private static StringConverter<ShuntCompensatorModelType> modelFormat() {
        return new StringConverter<>() {
            @Override
            public String toString(ShuntCompensatorModelType type) {
                return type == null ? "" : Messages.get(type == ShuntCompensatorModelType.LINEAR
                        ? "shuntCompensators.modelType.linear" : "shuntCompensators.modelType.nonLinear");
            }

            @Override
            public ShuntCompensatorModelType fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static AdderFactory svcFields(CreationForm form) {
        CreationForm.Field<Double> bMin = form.number("detail.svc.bMin", DetailForm.SIEMENS, -0.01, true);
        CreationForm.Field<Double> bMax = form.number("detail.svc.bMax", DetailForm.SIEMENS, 0.01, true);
        Regulation regulation = new Regulation(form, RegulationMode.VOLTAGE);
        return (vl, id, name) -> regulation.apply(vl, vl.newStaticVarCompensator().setId(id).setName(name).setBmin(bMin.get()).setBmax(bMax.get()));
    }

    private static AdderFactory vscFields(CreationForm form) {
        CreationForm.Field<Double> lossFactor = form.number("converterStations.column.lossFactor", null, 0, true);
        Regulation regulation = new Regulation(form, RegulationMode.VOLTAGE);
        return (vl, id, name) -> regulation.apply(vl, vl.newVscConverterStation().setId(id).setName(name).setLossFactor(lossFactor.get().floatValue()));
    }

    private static AdderFactory lccFields(CreationForm form) {
        CreationForm.Field<Double> lossFactor = form.number("converterStations.column.lossFactor", null, 0, true);
        CreationForm.Field<Double> powerFactor = form.number("lccConverterStations.column.powerFactor", null, 0.8, true);
        return (vl, id, name) -> vl.newLccConverterStation().setId(id).setName(name).setLossFactor(lossFactor.get().floatValue()).setPowerFactor(powerFactor.get().floatValue());
    }

    private static AdderFactory boundaryLineFields(CreationForm form) {
        CreationForm.Field<Double> r = form.number("desktop.common.column.r", DetailForm.OHM, 0, true);
        CreationForm.Field<Double> x = form.number("desktop.common.column.x", DetailForm.OHM, 1, true);
        CreationForm.Field<Double> g = form.number("desktop.common.column.g", DetailForm.SIEMENS, 0, true);
        CreationForm.Field<Double> b = form.number("desktop.common.column.b", DetailForm.SIEMENS, 0, true);
        CreationForm.Field<Double> p0 = form.number("desktop.common.column.p0", DetailForm.MW, 0, true);
        CreationForm.Field<Double> q0 = form.number("desktop.common.column.q0", DetailForm.MVAR, 0, true);
        CreationForm.Field<String> pairingKey = form.text("desktop.common.column.pairingKey", null, false);
        return (vl, id, name) -> vl.newBoundaryLine().setId(id).setName(name).setR(r.get()).setX(x.get()).setG(g.get()).setB(b.get()).setP0(p0.get()).setQ0(q0.get())
                .setPairingKey(pairingKey.get());
    }

    /**
     * Local targets and an optional voltage regulation, as IIDM holds them (see {@link VoltageRegulations}): a local
     * regulation, of the local targets (its own target value being for a remote terminal, set in the detail view). The
     * target V defaults to the voltage level's nominal voltage.
     */
    private static final class Regulation {
        private final CreationForm.Field<RegulationMode> mode;
        private final CreationForm.Field<Boolean> regulating;
        private final CreationForm.Field<Double> targetV;
        private final CreationForm.Field<Double> targetQ;

        private Regulation(CreationForm form, RegulationMode defaultMode) {
            form.section("desktop.common.column.voltageRegulation");
            List<RegulationMode> modes = new ArrayList<>();
            modes.add(null);
            modes.addAll(DetailSections.values(RegulationMode.class));
            mode = form.choice("desktop.common.column.regulationMode", modes, VoltageRegulations.MODE_FORMAT, defaultMode, false);
            regulating = form.check("desktop.common.column.regulating", defaultMode != null);
            targetV = form.number("desktop.common.column.targetV", DetailForm.KV, Double.NaN, false);
            targetQ = form.number("desktop.common.column.targetQ", DetailForm.MVAR, 0, false);
        }

        private <A extends VoltageRegulationHolderAdder<A>> A apply(VoltageLevel voltageLevel, A adder) {
            double v = Double.isNaN(targetV.get()) ? voltageLevel.getNominalV() : targetV.get();
            double q = targetQ.get();
            adder.setLocalTargetV(v).setLocalTargetQ(q);
            RegulationMode regulationMode = mode.get();
            if (regulationMode != null) {
                adder.newVoltageRegulation()
                        .withMode(regulationMode)
                        .withRegulating(regulating.get())
                        .add();
            }
            return adder;
        }
    }
}
