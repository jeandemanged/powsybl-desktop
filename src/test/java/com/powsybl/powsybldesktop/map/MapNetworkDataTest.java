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
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.OperationalLimitsGroup;
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
import java.util.Arrays;
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
    private static final int FLOW_ZOOM = 11;
    // pixels from a line's end around all its chevrons there
    private static final double FLOW_CHEVRON_SIZE = MapTileRenderer.chevronSize(Math.pow(2, FLOW_ZOOM));
    private static final double CHEVRONS_FROM = MapTileRenderer.chevronDistance(0, FLOW_CHEVRON_SIZE) - 6;
    private static final double CHEVRONS_TO = MapTileRenderer.chevronDistance(MapNetworkData.MAX_CHEVRONS - 1, FLOW_CHEVRON_SIZE) + 6;

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
        MapNetworkData.Hit hit = data.hitTest(48, 2.05, 12, Set.of(), ALL_VIOLATIONS, false);

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
        MapNetworkData.Hit hit = data.hitTest(48, 2.3, 10, Set.of(), ALL_VIOLATIONS, false);
        assertEquals("S3", hit.id());

        double scale = Math.pow(2, 10);
        int tileX = (int) (MapNetworkData.projectX(2.3) * scale / MapTileRenderer.TILE_SIZE);
        int tileY = (int) (MapNetworkData.projectY(48) * scale / MapTileRenderer.TILE_SIZE);
        assertNotNull(MapTileRenderer.render(data, false, Set.of(), ALL_VIOLATIONS, false, 10, tileX, tileY, 1));
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

        assertEquals("L", withViolations.hitTest(bubble[0], bubble[1], 12, Set.of(), ALL_VIOLATIONS, false).id());
        assertNull(withViolations.hitTest(bubble[0], bubble[1], 12, Set.of(), Set.of(MapViolationLayer.BASE_CASE_VOLTAGE), false));
        assertNull(data.hitTest(bubble[0], bubble[1], 12, Set.of(), ALL_VIOLATIONS, false));
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
                MapTileRenderer.render(withViolations, false, Set.of(), ALL_VIOLATIONS, false, zoom, tileX, tileY, 1)));
        // the bubble's color, beside its exclamation mark
        assertEquals(0xD32F2F, shown.getRGB(pixelX - 4, pixelY) & 0xFFFFFF);
        // S3 itself is still drawn in the tile
        BufferedImage hidden = ImageIO.read(new ByteArrayInputStream(
                MapTileRenderer.render(withViolations, false, Set.of(), Set.of(), false, zoom, tileX, tileY, 1)));
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
                MapTileRenderer.render(withViolations, false, Set.of(), ALL_VIOLATIONS, false, zoom, tileX, tileY, 1)));
        assertEquals(0xF57C00, image.getRGB(pixelX - 4, pixelY) & 0xFFFFFF);
    }

    // LF straight east from S3 to S4, or its route the other way round, both ends' buses at 400 kV
    private Line addFlowLine(boolean reversedRoute) {
        Line line = network.newLine()
                .setId("LF")
                .setVoltageLevel1("S3_VL").setBus1("S3_B")
                .setVoltageLevel2("S4_VL").setBus2("S4_B")
                .setR(1).setX(10)
                .add();
        List<Coordinate> route = List.of(new Coordinate(48, 2.3), new Coordinate(48, 2.4));
        line.newExtension(LinePositionAdder.class).withCoordinates(reversedRoute ? route.reversed() : route).add();
        network.getBusBreakerView().getBus("S3_B").setV(400);
        network.getBusBreakerView().getBus("S4_B").setV(400);
        return line;
    }

    // 100 MW at 400 kV is about 144 A, 40 MW about 58 A; the same permanent limit on both sides, none for NaN
    private MapNetworkData buildWithFlow(Line line, double p1, double p2, double permanentLimit) {
        if (Double.isNaN(permanentLimit)) {
            line.getOperationalLimitsGroups1().forEach(OperationalLimitsGroup::removeCurrentLimits);
            line.getOperationalLimitsGroups2().forEach(OperationalLimitsGroup::removeCurrentLimits);
        } else {
            line.getOrCreateSelectedOperationalLimitsGroup1().newCurrentLimits().setPermanentLimit(permanentLimit).add();
            line.getOrCreateSelectedOperationalLimitsGroup2().newCurrentLimits().setPermanentLimit(permanentLimit).add();
        }
        line.getTerminal1().setP(p1).setQ(0);
        line.getTerminal2().setP(p2).setQ(0);
        return MapNetworkData.build(network, NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS), null,
                _ -> null, (_, defaultColor) -> defaultColor);
    }

    private static int lineIndex(MapNetworkData data, String id) {
        return Arrays.asList(data.lineIds()).indexOf(id);
    }

    // the chevrons at the first and last points of a drawn line
    private static List<Integer> chevrons(MapNetworkData data, String id) {
        int line = lineIndex(data, id);
        return List.of((int) data.lineStartChevrons()[line], (int) data.lineEndChevrons()[line]);
    }

    /**
     * The zoom {@value #FLOW_ZOOM} tile holding LF whole, with and without flows, and where LF is in it: its ends' x,
     * its y.
     */
    private record FlowTile(BufferedImage shown, BufferedImage hidden, double startX, double endX, double y) {

        // pixels painted by the flows only, in columns x0 to x1 and rows y0 to y1, rounded down
        int painted(double x0, double x1, double y0, double y1) {
            int count = 0;
            for (int row = (int) y0; row <= (int) y1; row++) {
                for (int column = (int) x0; column <= (int) x1; column++) {
                    if (shown.getRGB(column, row) != hidden.getRGB(column, row)) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    private static FlowTile flowTile(MapNetworkData data) throws IOException {
        double scale = Math.pow(2, FLOW_ZOOM);
        double startX = MapNetworkData.projectX(2.3) * scale;
        double y = MapNetworkData.projectY(48) * scale;
        int tileX = (int) (startX / MapTileRenderer.TILE_SIZE);
        int tileY = (int) (y / MapTileRenderer.TILE_SIZE);
        double originX = tileX * (double) MapTileRenderer.TILE_SIZE;
        return new FlowTile(tileImage(data, true, tileX, tileY), tileImage(data, false, tileX, tileY), startX - originX,
                MapNetworkData.projectX(2.4) * scale - originX, y - tileY * MapTileRenderer.TILE_SIZE);
    }

    private static BufferedImage tileImage(MapNetworkData data, boolean showFlows, int tileX, int tileY) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(
                MapTileRenderer.render(data, false, Set.of(), Set.of(), showFlows, FLOW_ZOOM, tileX, tileY, 1)));
    }

    // power enters LF at S3 and leaves it at S4: all chevrons point east, along the route, then all west
    @Test
    void chevronsAtEachEndFollowItsActivePower() {
        Line line = addFlowLine(false);

        assertEquals(List.of(1, 1), chevrons(buildWithFlow(line, 100, -99, 1000), "LF"));
        assertEquals(List.of(-1, -1), chevrons(buildWithFlow(line, -100, 99, 1000), "LF"));
        // no load flow result
        assertEquals(List.of(0, 0), chevrons(buildWithFlow(line, 100, -99, 1000), "L_NO_POSITION"));
    }

    @Test
    void chevronsFollowARouteStartingAtSide2() {
        MapNetworkData withFlow = buildWithFlow(addFlowLine(true), 100, -99, 1000);

        assertEquals(List.of(-1, -1), chevrons(withFlow, "LF"));
    }

    // side 1 about 90 % loaded, side 2 about 36 %; then side 1 overloaded, side 2 about 58 %
    @Test
    void chevronsCountFollowsTheirSideLoading() {
        Line line = addFlowLine(false);

        MapNetworkData underLimit = buildWithFlow(line, 100, -40, 160.3);
        assertEquals(List.of(4, 2), chevrons(underLimit, "LF"));
        assertFalse(underLimit.lineOverloaded()[lineIndex(underLimit, "LF")]);
        MapNetworkData overLimit = buildWithFlow(line, 100, -40, 100);
        assertEquals(List.of(4, 3), chevrons(overLimit, "LF"));
        assertTrue(overLimit.lineOverloaded()[lineIndex(overLimit, "LF")]);
    }

    @Test
    void lineWithoutLimitsGetsASingleChevronAtEachEnd() {
        Line line = addFlowLine(false);

        assertEquals(List.of(1, 1), chevrons(buildWithFlow(line, 100, -99, Double.NaN), "LF"));
        MapNetworkData uncached = MapNetworkData.build(network, NetworkViolations.EMPTY, null, _ -> null, (_, defaultColor) -> defaultColor);
        assertEquals(List.of(1, 1), chevrons(uncached, "LF"));
        assertFalse(uncached.lineOverloaded()[lineIndex(uncached, "LF")]);
    }

    // nominal size up to zoom 10
    @Test
    void chevronsGrowWhenZoomedFarIn() {
        assertEquals(1, MapTileRenderer.chevronSize(Math.pow(2, 10)), DELTA);
        assertEquals(1.25, MapTileRenderer.chevronSize(Math.pow(2, 11)), DELTA);
        assertEquals(2.5, MapTileRenderer.chevronSize(Math.pow(2, 18)), DELTA);
        assertEquals(1, MapTileRenderer.chevronSize(Math.pow(2, 5)), DELTA);
    }

    // BL routed east from S5 to its boundary point; 100 MW enter it at S5, 99 MW leave it at the boundary point (its
    // P0); about 90 % loaded at S5, the only side with limits
    @Test
    void boundaryLineChevronsFollowTheFlowAtEachEnd() {
        BoundaryLine boundaryLine = network.getVoltageLevel("S5_VL").newBoundaryLine().setId("BL").setBus("S5_B")
                .setR(1).setX(1).setG(0).setB(0).setP0(99).setQ0(0)
                .add();
        boundaryLine.newExtension(LinePositionAdder.class)
                .withCoordinates(List.of(new Coordinate(48, 2.5), new Coordinate(48, 2.6)))
                .add();
        network.getBusBreakerView().getBus("S5_B").setV(400);
        boundaryLine.getTerminal().setP(100).setQ(0);

        MapNetworkData withoutLimits = MapNetworkData.build(network, NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS),
                null, _ -> null, (_, defaultColor) -> defaultColor);
        assertEquals(List.of(1, 1), chevrons(withoutLimits, "BL"));
        boundaryLine.getOrCreateSelectedOperationalLimitsGroup().newCurrentLimits().setPermanentLimit(160.3).add();
        MapNetworkData withLimits = MapNetworkData.build(network, NetworkViolations.compute(network, LimitsComputer.NO_MODIFICATIONS),
                null, _ -> null, (_, defaultColor) -> defaultColor);
        assertEquals(List.of(4, 4), chevrons(withLimits, "BL"));
    }

    // routed lines to S8, without position, to VL_ALONE, without substation, and from S0 to S7, at the same position
    @Test
    void noChevronsWhenSubstationPositionsDontTellWhichEndIsWhich() {
        List<Coordinate> route = List.of(new Coordinate(48, 2), new Coordinate(48.1, 2.05));
        for (String id : List.of("L_TO_UNPLACED", "L_TO_NO_SUBSTATION")) {
            Line line = network.getLine(id);
            line.newExtension(LinePositionAdder.class).withCoordinates(route).add();
            line.getTerminal1().setP(100);
            line.getTerminal2().setP(-99);
        }
        Line sameSite = network.newLine()
                .setId("L_SAME_SITE")
                .setVoltageLevel1("S0_VL").setBus1("S0_B")
                .setVoltageLevel2("S7_VL").setBus2("S7_B")
                .setR(1).setX(10)
                .add();
        sameSite.newExtension(LinePositionAdder.class).withCoordinates(route).add();
        sameSite.getTerminal1().setP(100);
        sameSite.getTerminal2().setP(-99);

        MapNetworkData withFlows = MapNetworkData.build(network, NetworkViolations.EMPTY, null, _ -> null, (_, defaultColor) -> defaultColor);
        for (String id : List.of("L_TO_UNPLACED", "L_TO_NO_SUBSTATION", "L_SAME_SITE")) {
            assertEquals(List.of(0, 0), chevrons(withFlows, id), id);
        }
    }

    // 1 to 2 pixels from the line, halfway along it: inside the highlight but outside the line itself
    @Test
    void overloadedLineIsHighlightedWhenFlowsShown() throws IOException {
        FlowTile tile = flowTile(buildWithFlow(addFlowLine(false), 100, -99, 100));

        int x = (int) ((tile.startX() + tile.endX()) / 2);
        int row = (int) (tile.y() - 2);
        assertEquals(0xD32F2F, tile.shown().getRGB(x, row) & 0xFFFFFF);
        assertEquals(0xFF, tile.shown().getRGB(x, row) >>> 24);
        assertEquals(0, tile.hidden().getRGB(x, row) >>> 24);
    }

    // 6 pixels below the line, away from its bubble: beyond a line's hit distance, within a highlighted one's
    @Test
    void overloadedLineIsHitAsWideAsHighlighted() {
        MapNetworkData overloaded = buildWithFlow(addFlowLine(false), 100, -99, 100);
        double latitude = MapNetworkData.unprojectLatitude(MapNetworkData.projectY(48) + 6 / Math.pow(2, FLOW_ZOOM));

        assertEquals("LF", overloaded.hitTest(latitude, 2.35, FLOW_ZOOM, Set.of(), ALL_VIOLATIONS, true).id());
        assertNull(overloaded.hitTest(latitude, 2.35, FLOW_ZOOM, Set.of(), ALL_VIOLATIONS, false));
    }

    // A chevron pointing east has its arms' ends west of its middle: above the line, only west of it is painted
    @Test
    void chevronsPointTheWayTheFlowGoesAtEachEnd() throws IOException {
        Line line = addFlowLine(false);

        FlowTile eastwards = flowTile(buildWithFlow(line, 100, -99, 1000));
        double y = eastwards.y();
        for (double x : new double[] {eastwards.startX() + MapTileRenderer.chevronDistance(0, FLOW_CHEVRON_SIZE),
            eastwards.endX() - MapTileRenderer.chevronDistance(0, FLOW_CHEVRON_SIZE)}) {
            assertTrue(eastwards.painted(x - 9, x - 2, y - 8, y - 6) > 0);
            assertEquals(0, eastwards.painted(x + 2, x + 9, y - 8, y - 6));
        }
        FlowTile westwards = flowTile(buildWithFlow(line, -100, 99, 1000));
        for (double x : new double[] {westwards.startX() + MapTileRenderer.chevronDistance(0, FLOW_CHEVRON_SIZE),
            westwards.endX() - MapTileRenderer.chevronDistance(0, FLOW_CHEVRON_SIZE)}) {
            assertEquals(0, westwards.painted(x - 9, x - 2, y - 8, y - 6));
            assertTrue(westwards.painted(x + 2, x + 9, y - 8, y - 6) > 0);
        }
    }

    // about 10, 30, 60 and 90 % loaded, then overloaded: 1 to 4 chevrons at the start, still 4 when overloaded
    @Test
    void moreLoadedSidesGetMoreChevrons() throws IOException {
        Line line = addFlowLine(false);
        int previous = 0;
        for (double limit : new double[] {1443, 481, 240.5, 160.3}) {
            FlowTile tile = flowTile(buildWithFlow(line, 100, -99, limit));
            int painted = tile.painted(tile.startX() + CHEVRONS_FROM, tile.startX() + CHEVRONS_TO, tile.y() - 6, tile.y() - 4);
            assertTrue(painted > previous, "limit " + limit);
            previous = painted;
        }
        // beyond the highlight, 3 pixels wide on each side
        FlowTile overloaded = flowTile(buildWithFlow(line, 100, -99, 100));
        assertTrue(overloaded.painted(overloaded.startX() + CHEVRONS_FROM, overloaded.startX() + CHEVRONS_TO, overloaded.y() - 7,
                overloaded.y() - 4.5) > 0);
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
