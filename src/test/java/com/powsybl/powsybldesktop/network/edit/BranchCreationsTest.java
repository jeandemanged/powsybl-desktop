/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.HvdcLine;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class BranchCreationsTest extends AbstractCreationDialogTest {

    @Test
    void lineBetweenTwoNodeBreakerVoltageLevels() {
        Network network = network();
        VoltageLevel voltageLevel = network.getVoltageLevel("VL400");

        Line line = openAndConfirm(sceneModel -> BranchCreations.newLine(sceneModel, stage, network, voltageLevel, null), network);

        assertNotNull(line);
        assertEquals("VL400", line.getTerminal1().getVoltageLevel().getId());
        assertEquals("VL225", line.getTerminal2().getVoltageLevel().getId());
        assertTrue(line.getTerminal1().isConnected() && line.getTerminal2().isConnected());
        ConnectablePosition<Line> position = line.getExtension(ConnectablePosition.class);
        assertEquals(ConnectablePosition.Direction.TOP, position.getFeeder1().getDirection());
    }

    @Test
    void twoWindingsTransformerRatedVoltagesDefaultToTheNominalOnes() {
        Network network = network();

        TwoWindingsTransformer transformer = openAndConfirm(sceneModel -> BranchCreations.newTwoWindingsTransformer(sceneModel, stage,
                network.getSubstation("S"), null, null), network);

        assertNotNull(transformer);
        assertEquals(400, transformer.getRatedU1());
        assertEquals(225, transformer.getRatedU2());
    }

    @Test
    void threeWindingsTransformerMixingTopologyKinds() {
        Network network = network();

        ThreeWindingsTransformer transformer = openAndConfirm(sceneModel -> BranchCreations.newThreeWindingsTransformer(sceneModel, stage,
                network.getSubstation("S"), null, null), network);

        assertNotNull(transformer);
        assertEquals("VL400", transformer.getLeg1().getTerminal().getVoltageLevel().getId());
        assertEquals("VL225", transformer.getLeg2().getTerminal().getVoltageLevel().getId());
        assertEquals("VL63", transformer.getLeg3().getTerminal().getVoltageLevel().getId());
        assertEquals(400, transformer.getRatedU0());
        for (ThreeSides side : ThreeSides.values()) {
            assertTrue(transformer.getTerminal(side).isConnected(), side + " connected");
        }
        ConnectablePosition<ThreeWindingsTransformer> position = transformer.getExtension(ConnectablePosition.class);
        assertNotNull(position.getFeeder1());
        assertNotNull(position.getFeeder2());
        assertEquals(null, position.getFeeder3(), "a bus breaker leg has no position");
        assertNotEquals(0, network.getVoltageLevel("VL225").getNodeBreakerView().getSwitchCount());
    }

    @Test
    void tieLinePairsTheBoundaryLinesOfARemovedOne() {
        Network network = EurostagTutorialExample1Factory.createWithTieLine();
        TieLine removed = network.getTieLine("NHV1_NHV2_1");
        String boundaryLine1 = removed.getBoundaryLine1().getId();
        String boundaryLine2 = removed.getBoundaryLine2().getId();
        removed.remove();

        TieLine tieLine = openAndConfirm(sceneModel -> BranchCreations.newTieLine(sceneModel, stage, network, network.getBoundaryLine(boundaryLine1)), network);

        assertNotNull(tieLine);
        assertEquals(boundaryLine1, tieLine.getBoundaryLine1().getId());
        assertEquals(boundaryLine2, tieLine.getBoundaryLine2().getId());
    }

    @Test
    void hvdcLineLinksTwoFreeConverterStations() {
        Network network = HvdcTestNetwork.createVsc();
        network.getHvdcLine("L").remove();

        HvdcLine hvdcLine = openAndConfirm(sceneModel -> BranchCreations.newHvdcLine(sceneModel, stage, network, null), network);

        assertNotNull(hvdcLine);
        assertEquals("C1", hvdcLine.getConverterStation1().getId());
        assertEquals("C2", hvdcLine.getConverterStation2().getId());
    }
}
