/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.LinePositionAdder;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;
import com.powsybl.iidm.network.limitmodification.LimitsComputer;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.powsybldesktop.parameters.MapViolationLayer;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import com.powsybl.security.LimitViolationsResult;
import com.powsybl.security.PostContingencyComputationStatus;
import com.powsybl.security.SecurityAnalysisResult;
import com.powsybl.security.results.ConnectivityResult;
import com.powsybl.security.results.NetworkResult;
import com.powsybl.security.results.PostContingencyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
    private static final Set<MapViolationLayer> ALL_VIOLATIONS = Set.of(MapViolationLayer.values());

    private Network network;
    private MapNetworkData data;

    // S0 at (48, 2), S1 to S6 east of it 0.1 degree apart, S7 at S0's position, S8 without a position, VL_ALONE
    // without substation
    @BeforeEach
    void setUp() {
        network = Network.create("test", "code");
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
        data = MapNetworkData.build(network, NetworkViolations.EMPTY, null, _ -> null, (_, defaultColor) -> defaultColor);
    }

    // L overloaded on side 1, a transformer of S4 overloaded, S3's bus above its voltage level's high limit
    private MapNetworkData buildWithViolations() {
        network.getBusBreakerView().getBus("S0_B").setV(400);
        Line line = network.getLine("L");
        line.getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits().setPermanentLimit(100).add();
        // about 144 A at 400 kV
        line.getTerminal1().setP(100).setQ(0);
        Substation s4 = network.getSubstation("S4");
        s4.newVoltageLevel().setId("S4_VL2").setNominalV(225).setTopologyKind(TopologyKind.BUS_BREAKER).add()
                .getBusBreakerView().newBus().setId("S4_B2").add();
        TwoWindingsTransformer transformer = s4.newTwoWindingsTransformer()
                .setId("T4")
                .setVoltageLevel1("S4_VL").setBus1("S4_B")
                .setVoltageLevel2("S4_VL2").setBus2("S4_B2")
                .setR(1).setX(10).setG(0).setB(0).setRatedU1(400).setRatedU2(225)
                .add();
        transformer.getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits().setPermanentLimit(100).add();
        network.getBusBreakerView().getBus("S4_B").setV(400);
        transformer.getTerminal1().setP(100).setQ(0);
        // a bus without equipment isn't one of the bus view
        network.getVoltageLevel("S3_VL").newLoad().setId("S3_LOAD").setBus("S3_B").setP0(0).setQ0(0).add();
        network.getVoltageLevel("S3_VL").setHighVoltageLimit(410);
        network.getBusBreakerView().getBus("S3_B").setV(420);
        return MapNetworkData.build(network, NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS), null,
                _ -> null, (_, defaultColor) -> defaultColor);
    }

    // After C1, L overloaded and S5's voltage level too high; C2 failed, its violation of L_NO_POSITION left out
    private SecurityAnalysisResult securityAnalysisResult() {
        LimitViolation overload = new LimitViolation("L", null, null, LimitViolationType.CURRENT, "permanent", Integer.MAX_VALUE,
                100, 1, 150, ThreeSides.ONE, null);
        LimitViolation highVoltage = LimitViolation.builder().subject("S5_VL").type(LimitViolationType.HIGH_VOLTAGE)
                .limit(420).value(430).build();
        LimitViolation failedOverload = new LimitViolation("L_NO_POSITION", null, null, LimitViolationType.CURRENT, "permanent",
                Integer.MAX_VALUE, 100, 1, 150, ThreeSides.ONE, null);
        return new SecurityAnalysisResult(LimitViolationsResult.empty(), LoadFlowResult.ComponentResult.Status.CONVERGED, List.of(
                postContingencyResult("C1", PostContingencyComputationStatus.CONVERGED, overload, highVoltage),
                postContingencyResult("C2", PostContingencyComputationStatus.FAILED, failedOverload)));
    }

    private static PostContingencyResult postContingencyResult(String id, PostContingencyComputationStatus status, LimitViolation... violations) {
        return new PostContingencyResult(new Contingency(id), status, new LimitViolationsResult(List.of(violations)), NetworkResult.empty(),
                ConnectivityResult.empty(), 0, List.of());
    }

    // the point BUBBLE_OFFSET pixels above a position at whole zoom level zoom: the center of its bubble
    private static double[] bubbleCenter(double latitude, double longitude, int zoom) {
        double y = MapNetworkData.projectY(latitude) - MapNetworkData.BUBBLE_OFFSET / Math.pow(2, zoom);
        return new double[] {MapNetworkData.unprojectLatitude(y), longitude};
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
        MapNetworkData.Hit hit = data.hitTest(48, 2.05, 12, Set.of(), ALL_VIOLATIONS);

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
        MapNetworkData.Hit hit = data.hitTest(48, 2.3, 10, Set.of(), ALL_VIOLATIONS);
        assertEquals("S3", hit.id());

        double scale = Math.pow(2, 10);
        int tileX = (int) (MapNetworkData.projectX(2.3) * scale / MapTileRenderer.TILE_SIZE);
        int tileY = (int) (MapNetworkData.projectY(48) * scale / MapTileRenderer.TILE_SIZE);
        assertNotNull(MapTileRenderer.render(data, false, Set.of(), ALL_VIOLATIONS, 10, tileX, tileY, 1));
    }

    @Test
    void violationsAreMarkedOnTheirLineOrSubstation() {
        MapNetworkData withViolations = buildWithViolations();

        MapNetworkData.ViolationMarker line = withViolations.violationMarker("L");
        assertFalse(line.substation());
        assertEquals(1, line.flowViolations().size());
        assertNull(line.flowViolations().getFirst().equipmentText());
        // at the line's middle
        assertEquals(MapNetworkData.projectY(48.5), line.y(), DELTA);

        MapNetworkData.ViolationMarker transformer = withViolations.violationMarker("S4");
        assertTrue(transformer.substation());
        assertEquals("T4", transformer.flowViolations().getFirst().equipmentText());
        assertTrue(transformer.voltageViolations().isEmpty());

        MapNetworkData.ViolationMarker voltage = withViolations.violationMarker("S3");
        assertEquals("S3_VL", voltage.voltageViolations().getFirst().voltageLevelText());
        assertEquals(MapViolationLayer.BASE_CASE_VOLTAGE, voltage.voltageViolations().getFirst().layer());
        assertTrue(voltage.isShown(Set.of(MapViolationLayer.BASE_CASE_VOLTAGE)));
        assertFalse(voltage.isShown(Set.of(MapViolationLayer.BASE_CASE_FLOW)));

        assertNull(withViolations.violationMarker("S0"));
    }

    // above L's apex, too far from the line itself to hit it
    @Test
    void bubbleHitsWhatItMarksWhenShown() {
        MapNetworkData withViolations = buildWithViolations();
        double[] bubble = bubbleCenter(48.5, 2.05, 12);

        assertEquals("L", withViolations.hitTest(bubble[0], bubble[1], 12, Set.of(), ALL_VIOLATIONS).id());
        assertNull(withViolations.hitTest(bubble[0], bubble[1], 12, Set.of(), Set.of(MapViolationLayer.BASE_CASE_VOLTAGE)));
        assertNull(data.hitTest(bubble[0], bubble[1], 12, Set.of(), ALL_VIOLATIONS));
    }

    // S3 alone in its tile at zoom 14, the bubble above it
    @Test
    void bubbleIsDrawnWhenShown() throws IOException {
        MapNetworkData withViolations = buildWithViolations();
        int zoom = 14;
        double scale = Math.pow(2, zoom);
        double[] bubble = bubbleCenter(48, 2.3, zoom);
        int tileX = (int) (MapNetworkData.projectX(bubble[1]) * scale / MapTileRenderer.TILE_SIZE);
        int tileY = (int) (MapNetworkData.projectY(bubble[0]) * scale / MapTileRenderer.TILE_SIZE);
        int pixelX = (int) (MapNetworkData.projectX(bubble[1]) * scale) - tileX * MapTileRenderer.TILE_SIZE;
        int pixelY = (int) (MapNetworkData.projectY(bubble[0]) * scale) - tileY * MapTileRenderer.TILE_SIZE;

        BufferedImage shown = ImageIO.read(new ByteArrayInputStream(
                MapTileRenderer.render(withViolations, false, Set.of(), ALL_VIOLATIONS, zoom, tileX, tileY, 1)));
        // the bubble's color, beside its exclamation mark
        assertEquals(0xD32F2F, shown.getRGB(pixelX - 4, pixelY) & 0xFFFFFF);
        // S3 itself is still drawn in the tile
        BufferedImage hidden = ImageIO.read(new ByteArrayInputStream(
                MapTileRenderer.render(withViolations, false, Set.of(), Set.of(), zoom, tileX, tileY, 1)));
        assertEquals(0, hidden.getRGB(pixelX - 4, pixelY) >>> 24);
    }

    @Test
    void contingencyViolationsAreMarkedAfterBaseCaseOnes() {
        buildWithViolations();
        MapNetworkData withViolations = MapNetworkData.build(network, NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS),
                securityAnalysisResult(), _ -> null, (_, defaultColor) -> defaultColor);

        List<MapNetworkData.MarkedFlowViolation> line = withViolations.violationMarker("L").flowViolations();
        assertEquals(2, line.size());
        assertNull(line.getFirst().contingency());
        assertEquals(MapViolationLayer.CONTINGENCY_FLOW, line.getLast().layer());
        assertEquals("C1", line.getLast().contingency());

        MapNetworkData.ViolationMarker voltage = withViolations.violationMarker("S5");
        assertEquals(MapViolationLayer.CONTINGENCY_VOLTAGE, voltage.voltageViolations().getFirst().layer());
        assertFalse(voltage.hasShownBaseCase(ALL_VIOLATIONS));
        assertTrue(withViolations.violationMarker("L").hasShownBaseCase(ALL_VIOLATIONS));
        assertFalse(withViolations.violationMarker("L").hasShownBaseCase(Set.of(MapViolationLayer.CONTINGENCY_FLOW)));

        // a failed contingency's
        assertNull(withViolations.violationMarker("L_NO_POSITION"));
    }

    @Test
    void failedPreContingencyLoadFlowMarksNoContingencyViolations() {
        SecurityAnalysisResult result = securityAnalysisResult();
        SecurityAnalysisResult failed = new SecurityAnalysisResult(LimitViolationsResult.empty(), LoadFlowResult.ComponentResult.Status.FAILED,
                result.getPostContingencyResults());

        MapNetworkData withViolations = MapNetworkData.build(network, NetworkViolations.EMPTY, failed, _ -> null, (_, defaultColor) -> defaultColor);

        assertNull(withViolations.violationMarker("L"));
        assertNull(withViolations.violationMarker("S5"));
    }

    // orange: S5 has contingency violations only
    @Test
    void contingencyOnlyBubbleIsOrange() throws IOException {
        MapNetworkData withViolations = MapNetworkData.build(network, NetworkViolations.EMPTY, securityAnalysisResult(), _ -> null,
                (_, defaultColor) -> defaultColor);
        int zoom = 14;
        double scale = Math.pow(2, zoom);
        double[] bubble = bubbleCenter(48, 2.5, zoom);
        int tileX = (int) (MapNetworkData.projectX(bubble[1]) * scale / MapTileRenderer.TILE_SIZE);
        int tileY = (int) (MapNetworkData.projectY(bubble[0]) * scale / MapTileRenderer.TILE_SIZE);
        int pixelX = (int) (MapNetworkData.projectX(bubble[1]) * scale) - tileX * MapTileRenderer.TILE_SIZE;
        int pixelY = (int) (MapNetworkData.projectY(bubble[0]) * scale) - tileY * MapTileRenderer.TILE_SIZE;

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                MapTileRenderer.render(withViolations, false, Set.of(), ALL_VIOLATIONS, zoom, tileX, tileY, 1)));
        assertEquals(0xF57C00, image.getRGB(pixelX - 4, pixelY) & 0xFFFFFF);
    }

    // Lesotho is a hole in South Africa; the Atlantic and Somaliland, unrecognized, have no country
    @Test
    void countryAtFollowsTheOfflineBasemap() {
        assertEquals(Country.FR, MapTileRenderer.countryAt(48.85, 2.35));
        assertEquals(Country.LS, MapTileRenderer.countryAt(-29.5, 28.2));
        assertEquals(Country.ZA, MapTileRenderer.countryAt(-30, 25));
        assertNull(MapTileRenderer.countryAt(45, -30));
        assertNull(MapTileRenderer.countryAt(9.56, 44.06));
    }
}
