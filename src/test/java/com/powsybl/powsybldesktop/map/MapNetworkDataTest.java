/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.LinePositionAdder;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class MapNetworkDataTest {

    private static final double DELTA = 1e-9;

    private MapNetworkData data;

    // S0 at (48, 2), S1 to S6 east of it 0.1 degree apart, S7 at S0's position, S8 without a position, VL_ALONE
    // without substation
    @BeforeEach
    void setUp() {
        Network network = Network.create("test", "code");
        for (int i = 0; i <= 6; i++) {
            addSubstation(network, "S" + i, new Coordinate(48, 2 + 0.1 * i));
        }
        addSubstation(network, "S7", new Coordinate(48, 2));
        addSubstation(network, "S8", null);
        Line line = network.newLine()
                .setId("L")
                .setVoltageLevel1("S0_VL").setBus1("S0_B")
                .setVoltageLevel2("S1_VL").setBus2("S1_B")
                .setR(1).setX(10)
                .add();
        line.newExtension(LinePositionAdder.class)
                .withCoordinates(List.of(new Coordinate(48, 2), new Coordinate(48.5, 2.05), new Coordinate(48, 2.1)))
                .add();
        network.newLine()
                .setId("L_NO_POSITION")
                .setVoltageLevel1("S0_VL").setBus1("S0_B")
                .setVoltageLevel2("S2_VL").setBus2("S2_B")
                .setR(1).setX(10)
                .add();
        network.newLine()
                .setId("L_TO_UNPLACED")
                .setVoltageLevel1("S0_VL").setBus1("S0_B")
                .setVoltageLevel2("S8_VL").setBus2("S8_B")
                .setR(1).setX(10)
                .add();
        network.newVoltageLevel().setId("VL_ALONE").setNominalV(400).setTopologyKind(TopologyKind.BUS_BREAKER).add()
                .getBusBreakerView().newBus().setId("VL_ALONE_B").add();
        network.newLine()
                .setId("L_TO_NO_SUBSTATION")
                .setVoltageLevel1("S0_VL").setBus1("S0_B")
                .setVoltageLevel2("VL_ALONE").setBus2("VL_ALONE_B")
                .setR(1).setX(10)
                .add();
        data = MapNetworkData.build(network, nominalV -> null, (baseVoltage, defaultColor) -> defaultColor);
    }

    private static void addSubstation(Network network, String id, Coordinate coordinate) {
        Substation substation = network.newSubstation().setId(id).add();
        if (coordinate != null) {
            substation.newExtension(SubstationPositionAdder.class).withCoordinate(coordinate).add();
        }
        substation.newVoltageLevel()
                .setId(id + "_VL")
                .setNominalV(400)
                .setTopologyKind(TopologyKind.BUS_BREAKER)
                .add()
                .getBusBreakerView().newBus().setId(id + "_B").add();
    }

    @Test
    void substationNeighbourhoodHoldsItsNearestNeighboursAroundIt() {
        double[] bounds = data.substationNeighbourhoodLatLngBounds("S0");

        // S5, the 5th nearest (S7, sharing its position, doesn't count), with a 1.2 margin: 0.5 * 1.2 degrees
        assertEquals(1.4, bounds[1], DELTA);
        assertEquals(2.6, bounds[3], DELTA);
        assertTrue(bounds[0] < 48 && bounds[2] > 48);
    }

    @Test
    void substationWithoutPositionHasNoNeighbourhood() {
        assertNull(data.substationNeighbourhoodLatLngBounds("S8"));
    }

    @Test
    void lineBoundsHoldAllItsPoints() {
        double[] bounds = data.lineLatLngBounds("L");

        assertEquals(48, bounds[0], DELTA);
        assertEquals(2, bounds[1], DELTA);
        assertEquals(48.5, bounds[2], DELTA);
        assertEquals(2.1, bounds[3], DELTA);
    }

    @Test
    void lineWithoutPositionIsDrawnStraightBetweenItsSubstations() {
        double[] bounds = data.lineLatLngBounds("L_NO_POSITION");

        assertEquals(48, bounds[0], DELTA);
        assertEquals(2, bounds[1], DELTA);
        assertEquals(48, bounds[2], DELTA);
        assertEquals(2.2, bounds[3], DELTA);
        int index = List.of(data.lineIds()).indexOf("L_NO_POSITION");
        assertTrue(data.lineImplied()[index]);
        assertFalse(data.lineImplied()[List.of(data.lineIds()).indexOf("L")]);
    }

    // one end without position, or without substation at all
    @Test
    void lineWithAnUnplacedEndIsNotDrawn() {
        assertNull(data.lineLatLngBounds("L_TO_UNPLACED"));
        assertNull(data.lineLatLngBounds("L_TO_NO_SUBSTATION"));
    }

    @Test
    void listsUnplacedSubstationsAndUnroutedLines() {
        assertEquals(List.of(new MapNetworkData.UnplacedSubstation("S8", "S8")), data.unplacedSubstations());
        assertEquals(1, data.unroutedCount());
    }

    @Test
    void impliedLineIsHitAsImplied() {
        // a quarter of the way from S0 to S2, clear of S1 halfway
        MapNetworkData.Hit hit = data.hitTest(48, 2.05, 12, Set.of());

        assertEquals("L_NO_POSITION", hit.id());
        assertTrue(hit.implied());
    }

    // its two segments are mirror images, so halfway along is their shared point
    @Test
    void lineMiddleIsHalfwayAlongIt() {
        double[] middle = data.lineMiddleLatLng("L");

        assertEquals(48.5, middle[0], DELTA);
        assertEquals(2.05, middle[1], DELTA);
        assertNull(data.lineMiddleLatLng("L_TO_UNPLACED"));
    }

    @Test
    void substationLatLngIsItsPosition() {
        double[] position = data.substationLatLng("S3");

        assertEquals(48, position[0], DELTA);
        assertEquals(2.3, position[1], DELTA);
        assertNull(data.substationLatLng("S8"));
    }

    @Test
    void substationsBoundsSkipSubstationsWithoutPosition() {
        double[] bounds = data.substationsLatLngBounds(Set.of("S0", "S3", "S8"));

        assertEquals(48, bounds[0], DELTA);
        assertEquals(2, bounds[1], DELTA);
        assertEquals(48, bounds[2], DELTA);
        assertEquals(2.3, bounds[3], DELTA);
        assertNull(data.substationsLatLngBounds(Set.of("S8")));
    }

    // substations without voltage levels, or nominal voltages outside the base voltages, have none: an
    // immutable set of hidden ones throws on contains(null)
    @Test
    void elementsWithoutBaseVoltageAreHitAndDrawn() throws IOException {
        MapNetworkData.Hit hit = data.hitTest(48, 2.3, 10, Set.of());
        assertEquals("S3", hit.id());

        double scale = Math.pow(2, 10);
        int tileX = (int) (MapNetworkData.projectX(2.3) * scale / MapTileRenderer.TILE_SIZE);
        int tileY = (int) (MapNetworkData.projectY(48) * scale / MapTileRenderer.TILE_SIZE);
        assertNotNull(MapTileRenderer.render(data, false, Set.of(), 10, tileX, tileY, 1));
    }
}
