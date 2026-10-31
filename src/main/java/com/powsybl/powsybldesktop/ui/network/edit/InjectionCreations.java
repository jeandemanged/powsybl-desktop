/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.edit;

import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LoadType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensatorModelType;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.powsybldesktop.network.VoltageRegulations;
import com.powsybl.powsybldesktop.network.edit.Connection;
import com.powsybl.powsybldesktop.network.edit.Injections;
import com.powsybl.powsybldesktop.network.edit.NetworkEdits;
import com.powsybl.powsybldesktop.ui.form.CreationForm;
import com.powsybl.powsybldesktop.ui.form.DetailForm;
import com.powsybl.powsybldesktop.ui.form.DetailSections;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
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

    // the values of an injection's kind-specific fields
    @FunctionalInterface
    private interface SpecFields {
        Injections.Spec read();
    }

    private InjectionCreations() {
    }

    /**
     * Creates an injection of {@code kind} in {@code voltageLevel}, on {@code busOrBusbarSection} if not null.
     */
    public static Optional<Identifiable<?>> create(SceneModel sceneModel, Window owner, Injections.Kind kind, VoltageLevel voltageLevel,
                                                  Identifiable<?> busOrBusbarSection) {
        Network network = voltageLevel.getParentNetwork();
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, voltageLevel.getId() + kind.idSuffix()));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        SpecFields specFields = switch (kind) {
            case GENERATOR -> generatorFields(form);
            case BATTERY -> batteryFields(form);
            case LOAD -> loadFields(form);
            case SHUNT_COMPENSATOR -> shuntFields(form);
            case STATIC_VAR_COMPENSATOR -> svcFields(form);
            case VSC_CONVERTER_STATION -> vscFields(form);
            case LCC_CONVERTER_STATION -> lccFields(form);
            case BOUNDARY_LINE -> boundaryLineFields(form);
            case GROUND -> Injections.Ground::new;
        };
        List<VoltageLevel> voltageLevels = network.getVoltageLevelStream().toList();
        ConnectionSection connection = new ConnectionSection(form, Messages.get("detail.section.connection"), voltageLevels, voltageLevel,
                busOrBusbarSection, ConnectablePosition.Direction.BOTTOM);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get(kind.key())), form, () -> {
            String injectionId = id.get();
            String injectionName = name.get();
            Injections.Spec spec = specFields.read();
            Connection at = connection.connection();
            NetworkEdits.apply(sceneModel.getMainModel(), network, n -> Injections.add(n, injectionId, injectionName, spec, at));
            return network.getIdentifiable(injectionId);
        });
    }

    private static SpecFields loadFields(CreationForm form) {
        CreationForm.Field<LoadType> type = form.choice("desktop.common.column.type", DetailSections.values(LoadType.class),
                DetailSections.enumFormat(), LoadType.UNDEFINED, true);
        CreationForm.Field<Double> p0 = form.number("desktop.common.column.p0", DetailForm.MW, 0, true);
        CreationForm.Field<Double> q0 = form.number("desktop.common.column.q0", DetailForm.MVAR, 0, true);
        return () -> new Injections.Load(type.get(), p0.get(), q0.get());
    }

    private static SpecFields generatorFields(CreationForm form) {
        CreationForm.Field<EnergySource> energySource = form.choice("detail.generator.energySource", DetailSections.values(EnergySource.class),
                DetailSections.enumFormat(), EnergySource.OTHER, true);
        CreationForm.Field<Double> minP = form.number("desktop.common.column.minP", DetailForm.MW, 0, true);
        CreationForm.Field<Double> maxP = form.number("desktop.common.column.maxP", DetailForm.MW, 100, true);
        CreationForm.Field<Double> targetP = form.number("generators.column.targetP", DetailForm.MW, 0, true);
        Regulation regulation = new Regulation(form, RegulationMode.VOLTAGE);
        return () -> new Injections.Generator(energySource.get(), minP.get(), maxP.get(), targetP.get(), regulation.read());
    }

    private static SpecFields batteryFields(CreationForm form) {
        CreationForm.Field<Double> minP = form.number("desktop.common.column.minP", DetailForm.MW, 0, true);
        CreationForm.Field<Double> maxP = form.number("desktop.common.column.maxP", DetailForm.MW, 100, true);
        CreationForm.Field<Double> targetP = form.number("generators.column.targetP", DetailForm.MW, 0, true);
        Regulation regulation = new Regulation(form, null);
        return () -> new Injections.Battery(minP.get(), maxP.get(), targetP.get(), regulation.read());
    }

    private static SpecFields shuntFields(CreationForm form) {
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
        return () -> {
            boolean linear = model.get() == ShuntCompensatorModelType.LINEAR;
            return new Injections.ShuntCompensator(model.get(), linear ? bPerSection.get() : Double.NaN, linear ? gPerSection.get() : Double.NaN,
                    linear ? maximumSectionCount.get() : 0, linear ? List.of() : parseSections(sections), sectionCount.get(), regulation.read());
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
            } catch (NumberFormatException _) {
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

    private static SpecFields svcFields(CreationForm form) {
        CreationForm.Field<Double> bMin = form.number("detail.svc.bMin", DetailForm.SIEMENS, -0.01, true);
        CreationForm.Field<Double> bMax = form.number("detail.svc.bMax", DetailForm.SIEMENS, 0.01, true);
        Regulation regulation = new Regulation(form, RegulationMode.VOLTAGE);
        return () -> new Injections.StaticVarCompensator(bMin.get(), bMax.get(), regulation.read());
    }

    private static SpecFields vscFields(CreationForm form) {
        CreationForm.Field<Double> lossFactor = form.number("converterStations.column.lossFactor", null, 0, true);
        Regulation regulation = new Regulation(form, RegulationMode.VOLTAGE);
        return () -> new Injections.VscConverterStation(lossFactor.get(), regulation.read());
    }

    private static SpecFields lccFields(CreationForm form) {
        CreationForm.Field<Double> lossFactor = form.number("converterStations.column.lossFactor", null, 0, true);
        CreationForm.Field<Double> powerFactor = form.number("lccConverterStations.column.powerFactor", null, 0.8, true);
        return () -> new Injections.LccConverterStation(lossFactor.get(), powerFactor.get());
    }

    private static SpecFields boundaryLineFields(CreationForm form) {
        CreationForm.Field<Double> r = form.number("desktop.common.column.r", DetailForm.OHM, 0, true);
        CreationForm.Field<Double> x = form.number("desktop.common.column.x", DetailForm.OHM, 1, true);
        CreationForm.Field<Double> g = form.number("desktop.common.column.g", DetailForm.SIEMENS, 0, true);
        CreationForm.Field<Double> b = form.number("desktop.common.column.b", DetailForm.SIEMENS, 0, true);
        CreationForm.Field<Double> p0 = form.number("desktop.common.column.p0", DetailForm.MW, 0, true);
        CreationForm.Field<Double> q0 = form.number("desktop.common.column.q0", DetailForm.MVAR, 0, true);
        CreationForm.Field<String> pairingKey = form.text("desktop.common.column.pairingKey", null, false);
        return () -> new Injections.BoundaryLine(r.get(), x.get(), g.get(), b.get(), p0.get(), q0.get(), pairingKey.get());
    }

    /**
     * The fields of an {@link Injections.Regulation}, the target V defaulting to the voltage level's nominal voltage.
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

        private Injections.Regulation read() {
            return new Injections.Regulation(mode.get(), regulating.get(), targetV.get(), targetQ.get());
        }
    }
}
