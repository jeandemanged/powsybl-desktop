/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.HvdcConverterStation;
import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class BranchesTest {

    @Test
    void lineBetweenTwoNodeBreakerVoltageLevelsGetsItsBaysAndPositions() {
        Network network = EditTestNetworks.network();

        Line line = Branches.addLine(network, new Branches.LineSpec("L", "Line", 0.1, 1, 0, 0, 0, 0),
                EditTestNetworks.at(network, "VL400", ConnectablePosition.Direction.TOP), EditTestNetworks.at(network, "VL225", ConnectablePosition.Direction.BOTTOM));

        assertEquals("VL400", line.getTerminal1().getVoltageLevel().getId());
        assertEquals("VL225", line.getTerminal2().getVoltageLevel().getId());
        assertEquals(0.1, line.getR());
        assertTrue(line.getTerminal1().isConnected() && line.getTerminal2().isConnected());
        ConnectablePosition<Line> position = line.getExtension(ConnectablePosition.class);
        assertEquals(ConnectablePosition.Direction.TOP, position.getFeeder1().getDirection());
        assertEquals(ConnectablePosition.Direction.BOTTOM, position.getFeeder2().getDirection());
    }

    @Test
    void twoWindingsTransformerRatedVoltagesDefaultToTheNominalOnes() {
        Network network = EditTestNetworks.network();
        Branches.TwoWindingsTransformerSpec spec = new Branches.TwoWindingsTransformerSpec("T", null, 0, 1, 0, 0, Double.NaN, Double.NaN, Double.NaN);

        TwoWindingsTransformer transformer = Branches.addTwoWindingsTransformer(network.getSubstation("S"), spec,
                EditTestNetworks.at(network, "VL400", ConnectablePosition.Direction.TOP), EditTestNetworks.at(network, "VL225", ConnectablePosition.Direction.BOTTOM));

        assertEquals(400, transformer.getRatedU1());
        assertEquals(225, transformer.getRatedU2());
    }

    @Test
    void threeWindingsTransformerMixingTopologyKinds() {
        Network network = EditTestNetworks.network();
        List<Branches.Leg> legs = List.of(
                new Branches.Leg(0, 1, 0, 0, Double.NaN, EditTestNetworks.at(network, "VL400", ConnectablePosition.Direction.TOP)),
                new Branches.Leg(0, 1, 0, 0, 230, EditTestNetworks.at(network, "VL225", ConnectablePosition.Direction.BOTTOM)),
                new Branches.Leg(0, 1, 0, 0, Double.NaN, EditTestNetworks.at(network, "VL63", ConnectablePosition.Direction.BOTTOM)));

        ThreeWindingsTransformer transformer = Branches.addThreeWindingsTransformer(network.getSubstation("S"),
                new Branches.ThreeWindingsTransformerSpec("T3", null, Double.NaN, legs));

        assertEquals("VL400", transformer.getLeg1().getTerminal().getVoltageLevel().getId());
        assertEquals("VL225", transformer.getLeg2().getTerminal().getVoltageLevel().getId());
        assertEquals("VL63", transformer.getLeg3().getTerminal().getVoltageLevel().getId());
        assertEquals(400, transformer.getRatedU0());
        assertEquals(230, transformer.getLeg2().getRatedU());
        assertEquals(63, transformer.getLeg3().getRatedU());
        for (ThreeSides side : ThreeSides.values()) {
            assertTrue(transformer.getTerminal(side).isConnected(), side + " connected");
        }
        ConnectablePosition<ThreeWindingsTransformer> position = transformer.getExtension(ConnectablePosition.class);
        assertNotNull(position.getFeeder1());
        assertNotNull(position.getFeeder2());
        assertNull(position.getFeeder3(), "a bus breaker leg has no position");
        assertNotEquals(0, network.getVoltageLevel("VL225").getNodeBreakerView().getSwitchCount());
    }

    @Test
    void tieLinePairsTwoBoundaryLines() {
        Network network = EurostagTutorialExample1Factory.createWithTieLine();
        TieLine removed = network.getTieLine("NHV1_NHV2_1");
        BoundaryLine boundaryLine1 = removed.getBoundaryLine1();
        BoundaryLine boundaryLine2 = removed.getBoundaryLine2();
        removed.remove();

        TieLine tieLine = Branches.addTieLine(network, "TL", null, boundaryLine1, boundaryLine2);

        assertSame(boundaryLine1, tieLine.getBoundaryLine1());
        assertSame(boundaryLine2, tieLine.getBoundaryLine2());
    }

    @Test
    void matchingBoundaryLineHasTheSamePairingKey() {
        Network network = EurostagTutorialExample1Factory.createWithTieLine();
        List<BoundaryLine> boundaryLines = network.getBoundaryLineStream().toList();
        network.getTieLine("NHV1_NHV2_1").remove();
        network.getTieLine("NHV1_NHV2_2").remove();
        BoundaryLine first = boundaryLines.getFirst();

        BoundaryLine matching = Branches.matchingBoundaryLine(boundaryLines, first);

        assertNotEquals(first, matching);
        assertEquals(first.getPairingKey(), matching.getPairingKey());
    }

    @Test
    void hvdcLineLinksTwoConverterStations() {
        Network network = HvdcTestNetwork.createVsc();
        network.getHvdcLine("L").remove();
        HvdcConverterStation<?> station1 = network.getHvdcConverterStation("C1");
        HvdcConverterStation<?> station2 = network.getHvdcConverterStation("C2");

        HvdcLine hvdcLine = Branches.addHvdcLine(network, new Branches.HvdcLineSpec("HVDC", null, station1, station2, 1, 400,
                HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER, 10, 100));

        assertSame(station1, hvdcLine.getConverterStation1());
        assertSame(station2, hvdcLine.getConverterStation2());
        assertEquals(HvdcLine.ConvertersMode.SIDE_1_INVERTER_SIDE_2_RECTIFIER, hvdcLine.getConvertersMode());
    }

    // the closest nominal voltage first, then the nearest substation, never the same substation
    @Test
    void lineOtherEndIsTheNearestSubstationAtTheClosestVoltage() {
        Network network = Network.create("test", Containers.SOURCE_FORMAT);
        VoltageLevel from = voltageLevel(network, "S1", 48.0, 2.0, 400);
        voltageLevel(network, "S1", 48.0, 2.0, 225);
        voltageLevel(network, "FAR", 50.0, 2.0, 400);
        VoltageLevel near = voltageLevel(network, "NEAR", 48.1, 2.0, 400);
        voltageLevel(network, "NEAREST", 48.01, 2.0, 225);

        assertEquals(near, Branches.otherEnd(network.getVoltageLevelStream().toList(), from));
    }

    @Test
    void transformerEndsAreProposedByDecreasingVoltage() {
        Network network = EditTestNetworks.network();
        List<VoltageLevel> voltageLevels = Branches.byDecreasingVoltage(network.getSubstation("S"));

        assertEquals(List.of("VL400", "VL225", "VL63"), voltageLevels.stream().map(VoltageLevel::getId).toList());
        assertEquals("VL225", Branches.otherThan(voltageLevels, network.getVoltageLevel("VL400")).getId());
    }

    private static VoltageLevel voltageLevel(Network network, String substationId, double latitude, double longitude, double nominalV) {
        Substation substation = network.getSubstation(substationId);
        if (substation == null) {
            substation = network.newSubstation().setId(substationId).add();
            substation.newExtension(SubstationPositionAdder.class).withCoordinate(new Coordinate(latitude, longitude)).add();
        }
        return substation.newVoltageLevel().setId(substationId + "_" + (int) nominalV).setNominalV(nominalV)
                .setTopologyKind(TopologyKind.BUS_BREAKER).add();
    }
}
