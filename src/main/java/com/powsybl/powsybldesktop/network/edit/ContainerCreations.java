/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.CreateCouplingDeviceBuilder;
import com.powsybl.iidm.modification.topology.CreateVoltageLevelTopologyBuilder;
import com.powsybl.iidm.modification.topology.DefaultNamingStrategy;
import com.powsybl.iidm.modification.topology.NamingStrategy;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.SubstationAdder;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.VoltageLevelAdder;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;
import com.powsybl.powsybldesktop.MainModel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.detail.DetailForm;
import com.powsybl.powsybldesktop.network.detail.DetailSections;
import com.powsybl.powsybldesktop.utils.Labels;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.control.ComboBox;
import javafx.stage.Window;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Creation dialogs of networks, substations and voltage levels.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class ContainerCreations {

    // the source format of networks created from scratch
    public static final String SOURCE_FORMAT = "IIDM";

    private ContainerCreations() {
    }

    /**
     * Creates an empty network and loads it.
     */
    public static Optional<Network> newNetwork(Window owner, MainModel mainModel) {
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.text("desktop.common.column.id", uniqueNetworkId(mainModel), true);
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        CreationForm.Field<ZonedDateTime> caseDate = form.dateTime("edit.create.caseDate", ZonedDateTime.now().withSecond(0).withNano(0));
        return CreateDialog.show(owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.network")), form, () -> {
            Network network = Network.create(id.get(), SOURCE_FORMAT);
            network.setName(name.get());
            network.setCaseDate(caseDate.get());
            mainModel.addNetwork(network);
            return network;
        });
    }

    private static String uniqueNetworkId(MainModel mainModel) {
        int n = 1;
        while (hasNetwork(mainModel, "network" + n)) {
            n++;
        }
        return "network" + n;
    }

    private static boolean hasNetwork(MainModel mainModel, String id) {
        return mainModel.getNetworks().stream().anyMatch(network -> network.getId().equals(id));
    }

    /**
     * Creates a substation in {@code network}, at {@code coordinate} if not null.
     */
    public static Optional<Substation> newSubstation(SceneModel sceneModel, Window owner, Network network, Coordinate coordinate) {
        CreationForm form = new CreationForm();
        CreationForm.Field<String> id = form.id(network, NetworkEdits.uniqueId(network, "S"));
        CreationForm.Field<String> name = form.text("desktop.common.column.name", null, false);
        List<Country> countries = new ArrayList<>();
        countries.add(null);
        countries.addAll(DetailSections.values(Country.class));
        CreationForm.Field<Country> country = form.searchableChoice("desktop.common.column.country", countries, Labels.countryFormat(),
                defaultCountry(network), false);
        CreationForm.Field<String> tso = form.text("substations.column.tso", null, false);
        form.section("edit.create.position");
        CreationForm.Field<Double> latitude = form.number("detail.extension.substationPosition.latitude", DetailForm.DEGREE,
                coordinate == null ? Double.NaN : coordinate.getLatitude(), false);
        CreationForm.Field<Double> longitude = form.number("detail.extension.substationPosition.longitude", DetailForm.DEGREE,
                coordinate == null ? Double.NaN : coordinate.getLongitude(), false);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.substation")), form, () -> {
            String substationId = id.get();
            double lat = latitude.get();
            double lon = longitude.get();
            if (Double.isNaN(lat) != Double.isNaN(lon)) {
                throw new CreationForm.FieldException((Double.isNaN(lat) ? latitude : longitude).getEditor(),
                        Messages.get("edit.create.positionIncomplete"));
            }
            NetworkEdits.apply(sceneModel.getMainModel(), network, n -> {
                SubstationAdder adder = n.newSubstation().setId(substationId).setName(name.get()).setTso(tso.get());
                if (country.get() != null) {
                    adder.setCountry(country.get());
                }
                Substation substation = adder.add();
                if (!Double.isNaN(lat)) {
                    substation.newExtension(SubstationPositionAdder.class).withCoordinate(new Coordinate(lat, lon)).add();
                }
            });
            return network.getSubstation(substationId);
        });
    }

    /**
     * Creates a voltage level in {@code substation}, or without substation if null, with its busbar sections (node
     * breaker) or buses (bus breaker): a grid of parallel busbars cut in sections, its section switches and couplings.
     */
    public static Optional<VoltageLevel> newVoltageLevel(SceneModel sceneModel, Window owner, Network network, Substation substation) {
        VoltageLevelForm form = new VoltageLevelForm(network, substation);
        return CreateDialog.showThenDetails(sceneModel, owner, Messages.get("edit.create.title", Messages.get("desktop.common.equipment.voltageLevel")),
                form.form, () -> form.create(sceneModel));
    }

    private static final class VoltageLevelForm {
        private final Network network;
        private final CreationForm form = new CreationForm();
        private final CreationForm.Field<String> id;
        private final CreationForm.Field<String> name;
        private final CreationForm.Field<Substation> substation;
        private final CreationForm.Field<Double> nominalV;
        private final CreationForm.Field<Double> lowVoltageLimit;
        private final CreationForm.Field<Double> highVoltageLimit;
        private final CreationForm.Field<TopologyKind> topologyKind;
        private final CreationForm.Field<Integer> busbarCount;
        private final CreationForm.Field<Integer> sectionCount;
        private final CreationForm.Field<SwitchKind> sectionSwitchKind;
        private final CreationForm.Field<Boolean> couplings;

        private VoltageLevelForm(Network network, Substation substation) {
            this.network = network;
            id = form.id(network, NetworkEdits.uniqueId(network, substation == null ? "VL" : substation.getId() + "_VL"));
            name = form.text("desktop.common.column.name", null, false);
            List<Substation> substations = new ArrayList<>();
            substations.add(null);
            network.getSubstationStream().sorted(Comparator.comparing(Substation::getNameOrId)).forEach(substations::add);
            this.substation = form.choice("desktop.common.equipment.substation", substations, CreationForm.identifiableFormat(), substation, false);
            nominalV = form.number("desktop.common.column.nominalVoltage", DetailForm.KV, 400, true);
            lowVoltageLimit = form.number("voltageLevels.column.lowVoltageLimit", DetailForm.KV, Double.NaN, false);
            highVoltageLimit = form.number("voltageLevels.column.highVoltageLimit", DetailForm.KV, Double.NaN, false);
            form.section("edit.create.vl.topology");
            topologyKind = form.choice("voltageLevels.column.topologyKind", DetailSections.values(TopologyKind.class),
                    DetailSections.enumFormat(), TopologyKind.NODE_BREAKER, true);
            busbarCount = form.integer("edit.create.vl.busbarCount", 1);
            sectionCount = form.integer("edit.create.vl.sectionCount", 1);
            sectionSwitchKind = form.choice("edit.create.vl.sectionSwitchKind", List.of(SwitchKind.BREAKER, SwitchKind.DISCONNECTOR),
                    DetailSections.enumFormat(), SwitchKind.BREAKER, true)
                    .visibleWhen(((ComboBox<?>) topologyKind.getEditor()).valueProperty().isEqualTo(TopologyKind.NODE_BREAKER));
            couplings = form.check("edit.create.vl.couplings", true);
        }

        private VoltageLevel create(SceneModel sceneModel) {
            String voltageLevelId = id.get();
            int bars = busbarCount.get();
            int sections = sectionCount.get();
            if (bars < 0) {
                throw new CreationForm.FieldException(busbarCount.getEditor(), Messages.get("edit.create.notNegative"));
            }
            if (sections < 1) {
                throw new CreationForm.FieldException(sectionCount.getEditor(), Messages.get("edit.create.atLeastOne"));
            }
            Substation chosen = substation.get();
            VoltageLevelAdder adder = (chosen != null ? chosen.newVoltageLevel() : network.newVoltageLevel())
                    .setId(voltageLevelId)
                    .setName(name.get())
                    .setNominalV(nominalV.get())
                    .setLowVoltageLimit(lowVoltageLimit.get())
                    .setHighVoltageLimit(highVoltageLimit.get())
                    .setTopologyKind(topologyKind.get());
            SwitchKind switchKind = sectionSwitchKind.get();
            boolean coupled = couplings.get();
            NetworkEdits.apply(sceneModel.getMainModel(), network, n -> {
                VoltageLevel voltageLevel = adder.add();
                if (bars > 0) {
                    createTopology(n, voltageLevelId, voltageLevel.getTopologyKind(), bars, sections, switchKind, coupled);
                }
            });
            return network.getVoltageLevel(voltageLevelId);
        }
    }

    static void createTopology(Network network, String voltageLevelId, TopologyKind kind, int bars, int sections, SwitchKind sectionSwitchKind,
                               boolean couplings) {
        NetworkEdits.apply(network, new CreateVoltageLevelTopologyBuilder()
                .withVoltageLevelId(voltageLevelId)
                .withAlignedBusesOrBusbarCount(bars)
                .withSectionCount(sections)
                .withSwitchKinds(Collections.nCopies(sections - 1, kind == TopologyKind.NODE_BREAKER ? sectionSwitchKind : SwitchKind.BREAKER))
                .build());
        if (couplings) {
            // the ids CreateVoltageLevelTopology gives, with the voltage level's id as prefix
            NamingStrategy naming = new DefaultNamingStrategy();
            for (int section = 1; section <= sections; section++) {
                for (int bar = 1; bar < bars; bar++) {
                    NetworkEdits.apply(network, new CreateCouplingDeviceBuilder()
                            .withBusOrBusbarSectionId1(naming.getBusbarId(voltageLevelId, bar, section))
                            .withBusOrBusbarSectionId2(naming.getBusbarId(voltageLevelId, bar + 1, section))
                            .build());
                }
            }
        }
    }

    // the network's only country, if it has one: most networks are national
    private static Country defaultCountry(Network network) {
        return network.getCountries().size() == 1 ? network.getCountries().iterator().next() : null;
    }
}
