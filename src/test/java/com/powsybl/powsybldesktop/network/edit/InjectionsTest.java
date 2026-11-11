/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.TopologyModificationUtils;
import com.powsybl.iidm.network.Battery;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.BusbarSection;
import com.powsybl.iidm.network.EnergySource;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Ground;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LccConverterStation;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.LoadType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.ShuntCompensatorModelType;
import com.powsybl.iidm.network.StaticVarCompensator;
import com.powsybl.iidm.network.SwitchKind;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.VscConverterStation;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.iidm.network.regulation.RegulationMode;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.FourSubstationsNodeBreakerFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class InjectionsTest {

    private static final Injections.Regulation NO_REGULATION = new Injections.Regulation(null, false, Double.NaN, 0);

    private static Connection at(VoltageLevel voltageLevel, Identifiable<?> busOrBusbarSection) {
        int order = busOrBusbarSection instanceof BusbarSection busbarSection ? FeederOrders.endOf(busbarSection) : 0;
        return new Connection(voltageLevel, busOrBusbarSection, order, ConnectablePosition.Direction.BOTTOM);
    }

    @Test
    void injectionIsCreatedWithItsBayAndPosition() {
        Network network = Network.create("test", Containers.SOURCE_FORMAT);
        VoltageLevel voltageLevel = network.newVoltageLevel().setId("VL").setNominalV(225).setTopologyKind(TopologyKind.NODE_BREAKER).add();
        Containers.createTopology(network, "VL", TopologyKind.NODE_BREAKER, 2, 1, SwitchKind.BREAKER, true);
        int switchCount = voltageLevel.getNodeBreakerView().getSwitchCount();

        Load load = (Load) Injections.add(network, "LD", "Load", new Injections.Load(LoadType.FICTITIOUS, 10, 2),
                at(voltageLevel, network.getBusbarSection("VL_1_1")));

        assertEquals("Load", load.getOptionalName().orElseThrow());
        assertEquals(LoadType.FICTITIOUS, load.getLoadType());
        assertEquals(10, load.getP0());
        assertTrue(load.getTerminal().isConnected());
        // a breaker and a disconnector per parallel busbar section, the one to the other busbar open
        assertEquals(switchCount + 3, voltageLevel.getNodeBreakerView().getSwitchCount());
        ConnectablePosition<Load> position = load.getExtension(ConnectablePosition.class);
        assertNotNull(position);
        assertEquals(ConnectablePosition.Direction.BOTTOM, position.getFeeder().getDirection());
        assertTrue(TopologyModificationUtils.getFeederPositions(voltageLevel).contains(position.getFeeder().getOrder().orElseThrow()));
    }

    @Test
    void connectablesWithoutPositionGetNone() {
        Network network = FourSubstationsNodeBreakerFactory.create();
        VoltageLevel voltageLevel = network.getVoltageLevel("S1VL2");

        Load load = (Load) Injections.add(network, "LD", null, new Injections.Load(LoadType.UNDEFINED, 0, 0),
                at(voltageLevel, network.getBusbarSection("S1VL2_BBS1")));

        assertTrue(load.getTerminal().isConnected());
        assertNull(load.getExtension(ConnectablePosition.class));
    }

    @Test
    void regulationTargetsTheVoltageLevelsNominalVoltageByDefault() {
        Network network = EurostagTutorialExample1Factory.create();
        VoltageLevel voltageLevel = network.getVoltageLevel("VLGEN");

        Generator generator = (Generator) Injections.add(network, "G", null, new Injections.Generator(EnergySource.HYDRO, 0, 100, 50,
                new Injections.Regulation(RegulationMode.VOLTAGE, true, Double.NaN, 0)), at(voltageLevel, network.getBusBreakerView().getBus("NGEN")));

        assertEquals("NGEN", generator.getTerminal().getBusBreakerView().getBus().getId());
        assertEquals(EnergySource.HYDRO, generator.getEnergySource());
        assertTrue(generator.isWithMode(RegulationMode.VOLTAGE));
        assertTrue(generator.getVoltageRegulation().isRegulating());
        assertEquals(voltageLevel.getNominalV(), generator.getRegulatingTargetV());
    }

    @Test
    void regulationKeepsAGivenTargetAndCanBeLeftOut() {
        Network network = EurostagTutorialExample1Factory.create();
        VoltageLevel voltageLevel = network.getVoltageLevel("VLGEN");
        Connection connection = at(voltageLevel, network.getBusBreakerView().getBus("NGEN"));

        StaticVarCompensator svc = (StaticVarCompensator) Injections.add(network, "SVC", null, new Injections.StaticVarCompensator(-0.01, 0.01,
                new Injections.Regulation(RegulationMode.VOLTAGE, false, 25, 0)), connection);
        Battery battery = (Battery) Injections.add(network, "BAT", null, new Injections.Battery(0, 10, 5, NO_REGULATION), connection);

        assertEquals(25, svc.getRegulatingTargetV());
        assertFalse(svc.getVoltageRegulation().isRegulating());
        assertNull(battery.getVoltageRegulation());
    }

    @Test
    void shuntCompensatorGetsItsLinearOrNonLinearModel() {
        Network network = EurostagTutorialExample1Factory.create();
        VoltageLevel voltageLevel = network.getVoltageLevel("VLLOAD");
        Connection connection = at(voltageLevel, network.getBusBreakerView().getBus("NLOAD"));

        ShuntCompensator linear = (ShuntCompensator) Injections.add(network, "SH1", null, new Injections.ShuntCompensator(
                ShuntCompensatorModelType.LINEAR, 0.001, 0, 3, List.of(), 2, NO_REGULATION), connection);
        ShuntCompensator nonLinear = (ShuntCompensator) Injections.add(network, "SH2", null, new Injections.ShuntCompensator(
                ShuntCompensatorModelType.NON_LINEAR, Double.NaN, Double.NaN, 0, List.of(0.001, 0.003), 1, NO_REGULATION), connection);

        assertEquals(3, linear.getMaximumSectionCount());
        assertEquals(0.002, linear.getB(), 1e-12);
        assertEquals(ShuntCompensatorModelType.NON_LINEAR, nonLinear.getModelType());
        assertEquals(2, nonLinear.getMaximumSectionCount());
        assertEquals(0.001, nonLinear.getB(), 1e-12);
    }

    @Test
    void everyKindCreatesItsEquipment() {
        Network network = EurostagTutorialExample1Factory.create();
        VoltageLevel voltageLevel = network.getVoltageLevel("VLLOAD");
        Connection connection = at(voltageLevel, network.getBusBreakerView().getBus("NLOAD"));
        Injections.Regulation regulation = new Injections.Regulation(RegulationMode.VOLTAGE, true, Double.NaN, 0);
        Map<Injections.Spec, Class<?>> specs = Map.of(
                new Injections.VscConverterStation(0.01, regulation), VscConverterStation.class,
                new Injections.LccConverterStation(0.01, 0.8), LccConverterStation.class,
                new Injections.BoundaryLine(0, 1, 0, 0, 0, 0, "XNODE"), BoundaryLine.class,
                new Injections.Ground(), Ground.class);
        int n = 0;
        for (Map.Entry<Injections.Spec, Class<?>> spec : specs.entrySet()) {
            Identifiable<?> created = Injections.add(network, "I" + n++, null, spec.getKey(), connection);
            assertInstanceOf(spec.getValue(), created);
        }
        assertEquals("XNODE", ((BoundaryLine) network.getBoundaryLineStream().findFirst().orElseThrow()).getPairingKey());
    }
}
