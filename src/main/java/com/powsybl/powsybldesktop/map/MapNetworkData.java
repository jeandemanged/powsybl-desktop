/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.LinePosition;
import com.powsybl.iidm.network.extensions.SubstationPosition;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.DoubleFunction;

/**
 * An immutable snapshot of what the Map view draws, built off the FX thread from a {@link Network}, and read
 * concurrently by the tile rendering threads ({@link MapTileRenderer}) and the FX thread (hit-testing).
 * <p>
 * Positions are Leaflet's EPSG:3857 pixel coordinates at zoom 0, where the world is {@value #WORLD_SIZE} pixels
 * wide: at zoom z, a pixel position is that times 2^z. Line {@code i}'s points are {@code lineStart[i]} (inclusive)
 * to {@code lineStart[i + 1]} (exclusive), its bounds {@code lineBounds[4 * i]} to {@code [4 * i + 3]} as min x,
 * min y, max x, max y; {@code substationColor}/{@code lineColor} index {@code colors}; base voltages are the
 * {@code BaseVoltagesConfig} names, null outside all ranges.
 *
 * @param grid null when there is nothing to draw
 * @param substationSpacing median distance from a substation to its nearest neighbour, infinite with fewer than
 *                          two distinct positions
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
record MapNetworkData(List<Color> colors,
                      String[] substationIds, String[] substationTexts, String[] substationBaseVoltages,
                      double[] substationX, double[] substationY, int[] substationColor,
                      String[] lineIds, String[] lineTexts, String[] lineBaseVoltages, int[] lineColor,
                      boolean[] lineDisconnected, int[] lineStart, double[] lineBounds,
                      double[] pointX, double[] pointY, int[] pointLine,
                      Grid grid, double substationSpacing) {

    static final String DEFAULT_SUBSTATION_COLOR = "#000000";
    static final String DEFAULT_LINE_COLOR = "#616161";
    static final double WORLD_SIZE = 256;
    static final double LINE_WEIGHT = 2;

    /** Leaflet's {@code L.Projection.SphericalMercator.MAX_LATITUDE}. */
    private static final double MAX_LATITUDE = 85.0511287798;
    /**
     * Substation markers are sized to this times the on-screen {@code substationSpacing}, within
     * {@link #SUBSTATION_MIN_SIZE} and {@link #SUBSTATION_MAX_SIZE} pixels, so a sparse view gets big markers and a
     * dense one small markers that don't pile up.
     */
    private static final double SUBSTATION_SPACING_FACTOR = 0.5;
    private static final int SUBSTATION_MIN_SIZE = 2;
    private static final int SUBSTATION_MAX_SIZE = 12;
    /** Pixels around a substation marker that still hit it. */
    private static final double SUBSTATION_CLICK_TOLERANCE = 5;
    /** Same hit tolerance Leaflet's canvas renderer used for polylines: half the stroke width. */
    private static final double LINE_HIT_DISTANCE = LINE_WEIGHT / 2;
    /** The grid has this many cells along each axis. */
    private static final int GRID_SIZE = 128;
    /** Nearest neighbour distances are measured on at most this many substations, enough for a median. */
    private static final int SPACING_SAMPLE_SIZE = 1000;

    /**
     * Uniform grid over all substations and line points: cell {@code c} lists {@code items[cellStart[c]]} to
     * {@code items[cellStart[c + 1] - 1]}, the substations in it (their index, {@code >= 0}) and the line segments
     * crossing its bounds (their start point index {@code k}, as {@code -(k + 1)}).
     */
    record Grid(double minX, double minY, double maxX, double maxY, double cellWidth, double cellHeight,
                int[] cellStart, int[] items) {

        int cellX(double x) {
            return cell(x, minX, cellWidth);
        }

        int cellY(double y) {
            return cell(y, minY, cellHeight);
        }

        int cellIndex(int cx, int cy) {
            return cy * GRID_SIZE + cx;
        }

        boolean intersects(double x0, double y0, double x1, double y1) {
            return x1 >= minX && x0 <= maxX && y1 >= minY && y0 <= maxY;
        }

        private static int cell(double coordinate, double min, double cellSize) {
            return Math.clamp((long) Math.floor((coordinate - min) / cellSize), 0, GRID_SIZE - 1);
        }
    }

    /**
     * What the mouse is over, and where its tooltip goes: a substation's position, or halfway along a line.
     *
     * @param key identifies the element, to tell whether the hovered element changed
     */
    record Hit(String key, boolean substation, String id, String text, double lat, double lng) {
    }

    private record MarkerData(String id, double x, double y, String text, String baseVoltage, String color) {
    }

    /**
     * @param points projected coordinates, x and y interleaved
     */
    private record LineData(String id, double[] points, String text, String baseVoltage, String color, boolean disconnected) {
    }

    private interface GridItemConsumer {
        void accept(int item, int cell);
    }

    /**
     * Lines, tie lines and boundary lines are drawn alike: the CGMES geographical layout import puts a tie line's
     * positions on its two boundary line halves, which are then drawn as, and navigate to, that tie line. A
     * substation takes its highest voltage level nominal voltage, a line the highest of its two ends.
     *
     * @param color the color of a base voltage, or the given default color for a null one
     */
    static MapNetworkData build(Network network, DoubleFunction<String> baseVoltageName, BinaryOperator<String> color) {
        List<MarkerData> substations = new ArrayList<>();
        List<LineData> lines = new ArrayList<>();
        if (network != null) {
            network.getSubstationStream().forEach(substation -> {
                SubstationPosition position = substation.getExtension(SubstationPosition.class);
                if (position != null) {
                    String baseVoltage = baseVoltageName.apply(substation.getVoltageLevelStream()
                            .mapToDouble(VoltageLevel::getNominalV).max().orElse(Double.NaN));
                    substations.add(new MarkerData(substation.getId(),
                            projectX(position.getCoordinate().getLongitude()), projectY(position.getCoordinate().getLatitude()),
                            substation.getNameOrId(), baseVoltage, color.apply(baseVoltage, DEFAULT_SUBSTATION_COLOR)));
                }
            });
            network.getLineStream().forEach(line -> addLine(lines, line, line,
                    Math.max(line.getTerminal1().getVoltageLevel().getNominalV(), line.getTerminal2().getVoltageLevel().getNominalV()),
                    !line.getTerminal1().isConnected() || !line.getTerminal2().isConnected(), baseVoltageName, color));
            network.getTieLineStream().forEach(tieLine -> addLine(lines, tieLine, tieLine, nominalV(tieLine), isDisconnected(tieLine),
                    baseVoltageName, color));
            network.getBoundaryLineStream().forEach(boundaryLine -> {
                TieLine tieLine = boundaryLine.getTieLine().orElse(null);
                if (tieLine == null) {
                    addLine(lines, boundaryLine, boundaryLine, boundaryLine.getTerminal().getVoltageLevel().getNominalV(),
                            !boundaryLine.getTerminal().isConnected(), baseVoltageName, color);
                } else if (tieLine.getExtension(LinePosition.class) == null) {
                    addLine(lines, boundaryLine, tieLine, nominalV(tieLine), isDisconnected(tieLine), baseVoltageName, color);
                }
            });
        }
        return pack(substations, lines);
    }

    private static double nominalV(TieLine tieLine) {
        return Math.max(tieLine.getBoundaryLine1().getTerminal().getVoltageLevel().getNominalV(),
                tieLine.getBoundaryLine2().getTerminal().getVoltageLevel().getNominalV());
    }

    private static boolean isDisconnected(TieLine tieLine) {
        return !tieLine.getBoundaryLine1().getTerminal().isConnected() || !tieLine.getBoundaryLine2().getTerminal().isConnected();
    }

    /**
     * Adds {@code positioned}'s line position, if any, drawn as and navigating to {@code shown}: they differ for a
     * tie line half.
     */
    private static <T extends Identifiable<T>> void addLine(List<LineData> lines, T positioned, Identifiable<?> shown, double nominalV,
                                                            boolean disconnected, DoubleFunction<String> baseVoltageName,
                                                            BinaryOperator<String> color) {
        LinePosition<T> position = positioned.getExtension(LinePosition.class);
        if (position != null && !position.getCoordinates().isEmpty()) {
            List<Coordinate> coordinates = position.getCoordinates();
            double[] points = new double[coordinates.size() * 2];
            for (int i = 0; i < coordinates.size(); i++) {
                points[2 * i] = projectX(coordinates.get(i).getLongitude());
                points[2 * i + 1] = projectY(coordinates.get(i).getLatitude());
            }
            String baseVoltage = baseVoltageName.apply(nominalV);
            lines.add(new LineData(shown.getId(), points, shown.getNameOrId(), baseVoltage,
                    color.apply(baseVoltage, DEFAULT_LINE_COLOR), disconnected));
        }
    }

    private static MapNetworkData pack(List<MarkerData> substations, List<LineData> lines) {
        List<Color> colors = new ArrayList<>();
        Map<String, Integer> colorIndices = new HashMap<>();
        int substationCount = substations.size();
        String[] substationIds = new String[substationCount];
        String[] substationTexts = new String[substationCount];
        String[] substationBaseVoltages = new String[substationCount];
        double[] substationX = new double[substationCount];
        double[] substationY = new double[substationCount];
        int[] substationColor = new int[substationCount];
        for (int i = 0; i < substationCount; i++) {
            MarkerData substation = substations.get(i);
            substationIds[i] = substation.id();
            substationTexts[i] = substation.text();
            substationBaseVoltages[i] = substation.baseVoltage();
            substationX[i] = substation.x();
            substationY[i] = substation.y();
            substationColor[i] = colorIndex(substation.color(), colors, colorIndices);
        }

        int lineCount = lines.size();
        int pointCount = lines.stream().mapToInt(line -> line.points().length / 2).sum();
        String[] lineIds = new String[lineCount];
        String[] lineTexts = new String[lineCount];
        String[] lineBaseVoltages = new String[lineCount];
        int[] lineColor = new int[lineCount];
        boolean[] lineDisconnected = new boolean[lineCount];
        int[] lineStart = new int[lineCount + 1];
        double[] lineBounds = new double[lineCount * 4];
        double[] pointX = new double[pointCount];
        double[] pointY = new double[pointCount];
        int[] pointLine = new int[pointCount];
        int k = 0;
        for (int i = 0; i < lineCount; i++) {
            LineData line = lines.get(i);
            lineIds[i] = line.id();
            lineTexts[i] = line.text();
            lineBaseVoltages[i] = line.baseVoltage();
            lineColor[i] = colorIndex(line.color(), colors, colorIndices);
            lineDisconnected[i] = line.disconnected();
            lineStart[i] = k;
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (int p = 0; p < line.points().length; p += 2) {
                double x = line.points()[p];
                double y = line.points()[p + 1];
                pointX[k] = x;
                pointY[k] = y;
                pointLine[k] = i;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                k++;
            }
            lineBounds[i * 4] = minX;
            lineBounds[i * 4 + 1] = minY;
            lineBounds[i * 4 + 2] = maxX;
            lineBounds[i * 4 + 3] = maxY;
        }
        lineStart[lineCount] = k;
        Grid grid = buildGrid(substationX, substationY, lineStart, pointX, pointY);
        return new MapNetworkData(colors, substationIds, substationTexts, substationBaseVoltages, substationX, substationY,
                substationColor, lineIds, lineTexts, lineBaseVoltages, lineColor, lineDisconnected, lineStart, lineBounds,
                pointX, pointY, pointLine, grid, substationSpacing(grid, substationX, substationY));
    }

    private static int colorIndex(String color, List<Color> colors, Map<String, Integer> colorIndices) {
        return colorIndices.computeIfAbsent(color, c -> {
            colors.add(Color.decode(c));
            return colors.size() - 1;
        });
    }

    private static Grid buildGrid(double[] substationX, double[] substationY, int[] lineStart, double[] pointX, double[] pointY) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < substationX.length; i++) {
            minX = Math.min(minX, substationX[i]);
            minY = Math.min(minY, substationY[i]);
            maxX = Math.max(maxX, substationX[i]);
            maxY = Math.max(maxY, substationY[i]);
        }
        for (int k = 0; k < pointX.length; k++) {
            minX = Math.min(minX, pointX[k]);
            minY = Math.min(minY, pointY[k]);
            maxX = Math.max(maxX, pointX[k]);
            maxY = Math.max(maxY, pointY[k]);
        }
        if (minX == Double.POSITIVE_INFINITY) {
            return null;
        }
        Grid bounds = new Grid(minX, minY, maxX, maxY,
                Math.max((maxX - minX) / GRID_SIZE, 1e-9), Math.max((maxY - minY) / GRID_SIZE, 1e-9), null, null);
        // cells as one flat item array: a first pass counts each cell's items, a second one fills them in
        int[] cellStart = new int[GRID_SIZE * GRID_SIZE + 1];
        forEachGridItem(bounds, substationX, substationY, lineStart, pointX, pointY, (item, cell) -> cellStart[cell + 1]++);
        for (int cell = 0; cell < GRID_SIZE * GRID_SIZE; cell++) {
            cellStart[cell + 1] += cellStart[cell];
        }
        int[] items = new int[cellStart[GRID_SIZE * GRID_SIZE]];
        int[] cursor = Arrays.copyOf(cellStart, GRID_SIZE * GRID_SIZE);
        forEachGridItem(bounds, substationX, substationY, lineStart, pointX, pointY, (item, cell) -> items[cursor[cell]++] = item);
        return new Grid(minX, minY, maxX, maxY, bounds.cellWidth(), bounds.cellHeight(), cellStart, items);
    }

    private static void forEachGridItem(Grid grid, double[] substationX, double[] substationY, int[] lineStart,
                                        double[] pointX, double[] pointY, GridItemConsumer consumer) {
        for (int i = 0; i < substationX.length; i++) {
            forEachCell(grid, i, substationX[i], substationY[i], substationX[i], substationY[i], consumer);
        }
        for (int line = 0; line + 1 < lineStart.length; line++) {
            for (int k = lineStart[line]; k < lineStart[line + 1] - 1; k++) {
                forEachCell(grid, -(k + 1),
                        Math.min(pointX[k], pointX[k + 1]), Math.min(pointY[k], pointY[k + 1]),
                        Math.max(pointX[k], pointX[k + 1]), Math.max(pointY[k], pointY[k + 1]), consumer);
            }
        }
    }

    private static void forEachCell(Grid grid, int item, double x0, double y0, double x1, double y1, GridItemConsumer consumer) {
        for (int cy = grid.cellY(y0); cy <= grid.cellY(y1); cy++) {
            for (int cx = grid.cellX(x0); cx <= grid.cellX(x1); cx++) {
                consumer.accept(item, grid.cellIndex(cx, cy));
            }
        }
    }

    private static double substationSpacing(Grid grid, double[] substationX, double[] substationY) {
        if (grid == null || substationX.length < 2) {
            return Double.POSITIVE_INFINITY;
        }
        int step = Math.max(1, substationX.length / SPACING_SAMPLE_SIZE);
        List<Double> distances = new ArrayList<>();
        for (int i = 0; i < substationX.length; i += step) {
            double squaredDistance = nearestSquaredDistance(grid, substationX, substationY, i);
            if (squaredDistance < Double.POSITIVE_INFINITY) {
                distances.add(Math.sqrt(squaredDistance));
            }
        }
        if (distances.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        Collections.sort(distances);
        return distances.get(distances.size() / 2);
    }

    /**
     * Searched through the grid ring by ring around substation {@code i}: a substation in ring r + 1 or beyond is
     * at least r cells away, so the search stops once the best distance is below that. Substations sharing a
     * position are skipped, they don't make the view any denser.
     */
    private static double nearestSquaredDistance(Grid grid, double[] substationX, double[] substationY, int i) {
        double x = substationX[i];
        double y = substationY[i];
        int cx = grid.cellX(x);
        int cy = grid.cellY(y);
        double cellSize = Math.min(grid.cellWidth(), grid.cellHeight());
        double best = Double.POSITIVE_INFINITY;
        for (int r = 0; r < GRID_SIZE && best > (r - 1) * cellSize * (r - 1) * cellSize; r++) {
            for (int gy = Math.max(0, cy - r); gy <= Math.min(GRID_SIZE - 1, cy + r); gy++) {
                for (int gx = Math.max(0, cx - r); gx <= Math.min(GRID_SIZE - 1, cx + r); gx++) {
                    if (Math.max(Math.abs(gx - cx), Math.abs(gy - cy)) == r) {
                        best = Math.min(best, nearestSquaredDistanceInCell(grid, substationX, substationY, x, y, grid.cellIndex(gx, gy)));
                    }
                }
            }
        }
        return best;
    }

    private static double nearestSquaredDistanceInCell(Grid grid, double[] substationX, double[] substationY,
                                                       double x, double y, int cell) {
        double best = Double.POSITIVE_INFINITY;
        for (int j = grid.cellStart()[cell]; j < grid.cellStart()[cell + 1]; j++) {
            int item = grid.items()[j];
            if (item >= 0) {
                double dx = substationX[item] - x;
                double dy = substationY[item] - y;
                double distance = dx * dx + dy * dy;
                if (distance > 0 && distance < best) {
                    best = distance;
                }
            }
        }
        return best;
    }

    // Leaflet's EPSG:3857 (L.CRS.EPSG3857.latLngToPoint) at zoom 0
    static double projectX(double longitude) {
        return WORLD_SIZE * (longitude / 360 + 0.5);
    }

    static double projectY(double latitude) {
        double sin = Math.sin(Math.toRadians(Math.clamp(latitude, -MAX_LATITUDE, MAX_LATITUDE)));
        return WORLD_SIZE * (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI));
    }

    static double unprojectLongitude(double x) {
        return (x / WORLD_SIZE - 0.5) * 360;
    }

    static double unprojectLatitude(double y) {
        return Math.toDegrees(Math.asin(Math.tanh(2 * Math.PI * (0.5 - y / WORLD_SIZE))));
    }

    /**
     * Substation marker size in whole pixels at a zoom of the given {@code scale} (2^zoom), so zooming only ever
     * produces a handful of sizes.
     */
    int substationSize(double scale) {
        return (int) Math.clamp(Math.round(substationSpacing * scale * SUBSTATION_SPACING_FACTOR), SUBSTATION_MIN_SIZE, SUBSTATION_MAX_SIZE);
    }

    /**
     * South, west, north and east of everything drawn, null when there is nothing to draw.
     */
    double[] latLngBounds() {
        return grid == null ? null : new double[] {unprojectLatitude(grid.maxY()), unprojectLongitude(grid.minX()),
            unprojectLatitude(grid.minY()), unprojectLongitude(grid.maxX())};
    }

    /**
     * Substations win over lines, as they're drawn on top; among each kind the closest one wins. Elements of a
     * hidden base voltage aren't hit.
     */
    Hit hitTest(double latitude, double longitude, double zoom, Set<String> hiddenBaseVoltages) {
        if (grid == null) {
            return null;
        }
        double px = projectX(longitude);
        double py = projectY(latitude);
        double scale = Math.pow(2, zoom);
        double substationLimit = (substationSize(scale) / 2.0 + SUBSTATION_CLICK_TOLERANCE) / scale;
        double lineLimit = LINE_HIT_DISTANCE / scale;
        double tolerance = Math.max(substationLimit, lineLimit);
        int bestSubstation = -1;
        double bestSubstationDistance = substationLimit * substationLimit;
        int bestLine = -1;
        double bestLineDistance = lineLimit * lineLimit;
        for (int cy = grid.cellY(py - tolerance); cy <= grid.cellY(py + tolerance); cy++) {
            for (int cx = grid.cellX(px - tolerance); cx <= grid.cellX(px + tolerance); cx++) {
                int cell = grid.cellIndex(cx, cy);
                for (int j = grid.cellStart()[cell]; j < grid.cellStart()[cell + 1]; j++) {
                    int item = grid.items()[j];
                    if (item >= 0) {
                        double dx = substationX[item] - px;
                        double dy = substationY[item] - py;
                        double distance = dx * dx + dy * dy;
                        if (distance <= bestSubstationDistance && !hiddenBaseVoltages.contains(substationBaseVoltages[item])) {
                            bestSubstationDistance = distance;
                            bestSubstation = item;
                        }
                    } else {
                        int k = -item - 1;
                        double distance = squaredDistanceToSegment(px, py, pointX[k], pointY[k], pointX[k + 1], pointY[k + 1]);
                        if (distance <= bestLineDistance && !hiddenBaseVoltages.contains(lineBaseVoltages[pointLine[k]])) {
                            bestLineDistance = distance;
                            bestLine = pointLine[k];
                        }
                    }
                }
            }
        }
        if (bestSubstation >= 0) {
            return new Hit("s" + bestSubstation, true, substationIds[bestSubstation], substationTexts[bestSubstation],
                    unprojectLatitude(substationY[bestSubstation]), unprojectLongitude(substationX[bestSubstation]));
        }
        if (bestLine >= 0) {
            double[] anchor = lineMiddle(bestLine);
            return new Hit("l" + bestLine, false, lineIds[bestLine], lineTexts[bestLine],
                    unprojectLatitude(anchor[1]), unprojectLongitude(anchor[0]));
        }
        return null;
    }

    private static double squaredDistanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.clamp(((px - ax) * dx + (py - ay) * dy) / lengthSquared, 0, 1);
        double ex = px - (ax + t * dx);
        double ey = py - (ay + t * dy);
        return ex * ex + ey * ey;
    }

    // Where Leaflet anchored a polyline's tooltip: halfway along its on-screen length. Pixel positions at any zoom
    // are the zoom 0 ones scaled, so measuring at zoom 0 is exact.
    private double[] lineMiddle(int line) {
        int start = lineStart[line];
        int end = lineStart[line + 1];
        double length = 0;
        for (int k = start; k < end - 1; k++) {
            length += Math.hypot(pointX[k + 1] - pointX[k], pointY[k + 1] - pointY[k]);
        }
        double remaining = length / 2;
        for (int k = start; k < end - 1; k++) {
            double segment = Math.hypot(pointX[k + 1] - pointX[k], pointY[k + 1] - pointY[k]);
            if (segment > 0 && remaining <= segment) {
                double t = remaining / segment;
                return new double[] {pointX[k] + t * (pointX[k + 1] - pointX[k]), pointY[k] + t * (pointY[k + 1] - pointY[k])};
            }
            remaining -= segment;
        }
        return new double[] {pointX[start], pointY[start]};
    }
}
