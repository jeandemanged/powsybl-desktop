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
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;

/**
 * Creating networks and containers: substations, and voltage levels with their busbar sections or buses.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class Containers {

    public static final String SOURCE_FORMAT = "IIDM";

    private Containers() {
    }

    /**
     * The first of network1, network2... not taken by {@code networks}.
     */
    public static String uniqueNetworkId(Collection<Network> networks) {
        int n = 1;
        while (hasNetwork(networks, "network" + n)) {
            n++;
        }
        return "network" + n;
    }

    private static boolean hasNetwork(Collection<Network> networks, String id) {
        return networks.stream().anyMatch(network -> network.getId().equals(id));
    }

    public static Network newNetwork(String id, String name, ZonedDateTime caseDate) {
        Network network = Network.create(id, SOURCE_FORMAT);
        network.setName(name);
        network.setCaseDate(caseDate);
        return network;
    }

    // the network's only country, if it has one: most networks are national
    public static Country defaultCountry(Network network) {
        return network.getCountries().size() == 1 ? network.getCountries().iterator().next() : null;
    }

    /**
     * @param country  none if null
     * @param position none if null
     */
    public record SubstationSpec(String id, String name, Country country, String tso, Coordinate position) {
    }

    public static Substation addSubstation(Network network, SubstationSpec spec) {
        SubstationAdder adder = network.newSubstation().setId(spec.id()).setName(spec.name()).setTso(spec.tso());
        if (spec.country() != null) {
            adder.setCountry(spec.country());
        }
        Substation substation = adder.add();
        if (spec.position() != null) {
            substation.newExtension(SubstationPositionAdder.class).withCoordinate(spec.position()).add();
        }
        return substation;
    }

    /**
     * A voltage level and its topology: a grid of {@code busbarCount} parallel busbars (none if 0) cut in
     * {@code sectionCount} sections, with section switches of {@code sectionSwitchKind} (node breaker; always breakers
     * between bus breaker buses) and, if {@code couplings}, a coupling device between adjacent busbars of each section.
     *
     * @param substation none if null
     */
    public record VoltageLevelSpec(String id, String name, Substation substation, double nominalV, double lowVoltageLimit,
                                   double highVoltageLimit, TopologyKind topologyKind, int busbarCount, int sectionCount,
                                   SwitchKind sectionSwitchKind, boolean couplings) {
    }

    public static VoltageLevel addVoltageLevel(Network network, VoltageLevelSpec spec) {
        VoltageLevel voltageLevel = (spec.substation() != null ? spec.substation().newVoltageLevel() : network.newVoltageLevel())
                .setId(spec.id())
                .setName(spec.name())
                .setNominalV(spec.nominalV())
                .setLowVoltageLimit(spec.lowVoltageLimit())
                .setHighVoltageLimit(spec.highVoltageLimit())
                .setTopologyKind(spec.topologyKind())
                .add();
        if (spec.busbarCount() > 0) {
            createTopology(network, spec.id(), voltageLevel.getTopologyKind(), spec.busbarCount(), spec.sectionCount(),
                    spec.sectionSwitchKind(), spec.couplings());
        }
        return voltageLevel;
    }

    public static void createTopology(Network network, String voltageLevelId, TopologyKind kind, int bars, int sections, SwitchKind sectionSwitchKind,
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
}
