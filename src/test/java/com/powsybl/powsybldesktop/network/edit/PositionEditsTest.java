/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.LinePosition;
import com.powsybl.iidm.network.extensions.SubstationPosition;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class PositionEditsTest {

    private static final Coordinate A = new Coordinate(48, 2);
    private static final Coordinate B = new Coordinate(49, 4);

    private Network network;

    // S_A at A, S_B at B, S_NONE without position, VL_ALONE without substation, S_EMPTY without voltage level; L_AB, L_A_NONE and
    // L_A_ALONE from S_A; tie line TL from S_A to S_B
    @BeforeEach
    void setUp() {
        network = Network.create("test", "code");
        addSubstation("S_A", A);
        addSubstation("S_B", B);
        addSubstation("S_NONE", null);
        network.newSubstation().setId("S_EMPTY").add();
        bus(network.newVoltageLevel().setId("VL_ALONE").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add());
        addLine("L_AB", "S_A", "S_B_VL");
        addLine("L_A_NONE", "S_A", "S_NONE_VL");
        addLine("L_A_ALONE", "S_A", "VL_ALONE");
        BoundaryLine half1 = boundaryLine("S_A_VL", "BL1");
        BoundaryLine half2 = boundaryLine("S_B_VL", "BL2");
        network.newTieLine().setId("TL").setBoundaryLine1(half1.getId()).setBoundaryLine2(half2.getId()).add();
        boundaryLine("S_A_VL", "BL_UNPAIRED");
    }

    private void addSubstation(String id, Coordinate coordinate) {
        Substation substation = network.newSubstation().setId(id).add();
        if (coordinate != null) {
            substation.newExtension(SubstationPositionAdder.class).withCoordinate(coordinate).add();
        }
        bus(substation.newVoltageLevel().setId(id + "_VL").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add());
    }

    private static void bus(VoltageLevel voltageLevel) {
        voltageLevel.getBusBreakerView().newBus().setId(voltageLevel.getId() + "_B").add();
    }

    private void addLine(String id, String substation1, String voltageLevel2) {
        network.newLine().setId(id)
                .setVoltageLevel1(substation1 + "_VL").setBus1(substation1 + "_VL_B")
                .setVoltageLevel2(voltageLevel2).setBus2(voltageLevel2 + "_B")
                .setR(1).setX(10)
                .add();
    }

    private BoundaryLine boundaryLine(String voltageLevel, String id) {
        return network.getVoltageLevel(voltageLevel).newBoundaryLine().setId(id).setBus(voltageLevel + "_B")
                .setR(1).setX(1).setG(0).setB(0).setP0(0).setQ0(0).setPairingKey(id.startsWith("BL_") ? id : "TL")
                .add();
    }

    @Test
    void straightRouteGoesFromSubstationToSubstation() {
        assertEquals(Optional.of(List.of(A, B)), PositionEdits.straightRoute(network.getLine("L_AB")));
    }

    // an end without position, without substation at all, or an unpaired boundary line
    @Test
    void noStraightRouteWithoutBothEndsPlaced() {
        assertTrue(PositionEdits.straightRoute(network.getLine("L_A_NONE")).isEmpty());
        assertTrue(PositionEdits.straightRoute(network.getLine("L_A_ALONE")).isEmpty());
        assertTrue(PositionEdits.straightRoute(network.getBoundaryLine("BL_UNPAIRED")).isEmpty());
        assertTrue(PositionEdits.substationCoordinate(network.getVoltageLevel("VL_ALONE")).isEmpty());
    }

    @Test
    void tieLineHalvesMeetHalfway() {
        Coordinate middle = new Coordinate(48.5, 3);

        assertEquals(Optional.of(List.of(A, middle)), PositionEdits.straightRoute(network.getBoundaryLine("BL1")));
        assertEquals(Optional.of(List.of(B, middle)), PositionEdits.straightRoute(network.getBoundaryLine("BL2")));
    }

    @Test
    void createsStraightRoutesWhereBothEndsArePlaced() {
        // L_AB, and TL once for its two halves
        assertEquals(2, PositionEdits.unroutedCount(network));

        assertEquals(2, PositionEdits.createStraightRoutes(network));

        assertEquals(List.of(A, B), PositionEdits.route(network.getLine("L_AB")));
        assertEquals(2, PositionEdits.route(network.getBoundaryLine("BL2")).size());
        assertTrue(PositionEdits.route(network.getLine("L_A_NONE")).isEmpty());
        assertTrue(PositionEdits.route(network.getLine("L_A_ALONE")).isEmpty());
        assertEquals(0, PositionEdits.unroutedCount(network));
    }

    @Test
    void movingASubstationMovesTheRouteEndsAtIt() {
        Coordinate bend = new Coordinate(48.2, 3);
        Coordinate moved = new Coordinate(47, 1);
        PositionEdits.setRoute(network.getLine("L_AB"), List.of(A, bend, B));
        // ends elsewhere: left alone
        PositionEdits.setRoute(network.getLine("L_A_NONE"), List.of(bend, B));
        PositionEdits.createStraightRoutes(network);

        PositionEdits.setSubstationPosition(network.getSubstation("S_A"), moved);

        assertEquals(moved, network.getSubstation("S_A").getExtension(SubstationPosition.class).getCoordinate());
        assertEquals(List.of(moved, bend, B), PositionEdits.route(network.getLine("L_AB")));
        assertEquals(List.of(bend, B), PositionEdits.route(network.getLine("L_A_NONE")));
        assertEquals(moved, PositionEdits.route(network.getBoundaryLine("BL1")).getFirst());
    }

    @Test
    void placesASubstationWithoutVoltageLevelOrPosition() {
        PositionEdits.setSubstationPosition(network.getSubstation("S_EMPTY"), A);
        PositionEdits.setSubstationPosition(network.getSubstation("S_NONE"), B);

        assertEquals(Optional.of(A), PositionEdits.substationCoordinate(network.getSubstation("S_EMPTY")));
        assertEquals(Optional.of(B), PositionEdits.substationCoordinate(network.getVoltageLevel("S_NONE_VL")));
    }

    @Test
    void emptyRouteRemovesIt() {
        Line line = network.getLine("L_AB");
        PositionEdits.setRoute(line, List.of(A, B));

        PositionEdits.setRoute(line, List.of());

        assertNull(line.getExtension(LinePosition.class));
        PositionEdits.removeSubstationPosition(network.getSubstation("S_A"));
        assertTrue(PositionEdits.substationCoordinate(network.getSubstation("S_A")).isEmpty());
    }

    @Test
    void tieLineHasNoRouteOfItsOwn() {
        TieLine tieLine = network.getTieLine("TL");

        assertTrue(PositionEdits.straightRoute(tieLine).isEmpty());
    }
}
