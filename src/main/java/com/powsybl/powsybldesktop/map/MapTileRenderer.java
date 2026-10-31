/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Country;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Draws the Map view's tiles: Leaflet tile {@code (x, y)} at zoom {@code z} covers pixels {@code 256 x} to
 * {@code 256 (x + 1)} (and likewise in y) of the world drawn {@code 256 * 2^z} pixels wide, i.e. the zoom 0 pixel
 * positions of {@link MapNetworkData} times 2^z. Each tile draws everything crossing it in full and lets Java2D
 * clip it, so lines, dashes and markers join seamlessly across tiles. Stateless and thread-safe: tiles are drawn
 * concurrently on background threads.
 * <p>
 * The offline basemap is drawn here too, below the network: the country outlines bundled as
 * {@code countries.geojson} (Natural Earth 1:50m admin-0 countries, public domain, see docs/ai/ui-conventions.md
 * for how it was generated). It also tells the country of a place, see {@link #countryAt}. Its sea is the map container's background color, set by map.js.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class MapTileRenderer {

    static final int TILE_SIZE = 256;

    private static final Color LAND_COLOR = new Color(0xF2EFE9);
    private static final Color BORDER_COLOR = new Color(0x9E9E9E);
    private static final Stroke BORDER_STROKE = new BasicStroke(1);
    private static final Stroke LINE_STROKE = new BasicStroke((float) MapNetworkData.LINE_WEIGHT, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    /** For disconnected lines: dash and gap lengths, round caps eating the line weight out of each gap. */
    private static final Stroke DASHED_LINE_STROKE = new BasicStroke((float) MapNetworkData.LINE_WEIGHT, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND, 10, new float[] {6, 6}, 0);
    /** Lines without route of their own, drawn straight between their substations: thinner and translucent. */
    private static final Stroke IMPLIED_LINE_STROKE = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke DASHED_IMPLIED_LINE_STROKE = new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10, new float[] {4, 4}, 0);
    private static final int IMPLIED_LINE_ALPHA = 128;

    /**
     * Every polygon ring of every country, projected like {@link MapNetworkData}: ring {@code i}'s points are
     * {@code ringStart[i]} (inclusive) to {@code ringStart[i + 1]} (exclusive), and it belongs to feature
     * {@code ringFeature[i]}, whose country is {@code featureCountry[ringFeature[i]]} (null for the few unrecognized
     * territories, e.g. Somaliland).
     */
    private record Countries(int[] ringStart, double[] ringBounds, double[] pointX, double[] pointY,
                             int[] ringFeature, Country[] featureCountry) {
    }

    // loaded on first use, by the first tile drawing thread needing it rather than the FX thread
    private static final class CountriesHolder {
        private static final Countries COUNTRIES = loadCountries();
    }

    private MapTileRenderer() {
    }

    /**
     * @param data the network to draw, null for none
     * @param showCountries whether to draw the offline basemap
     * @param ratio device pixels per CSS pixel: the tile is drawn {@code 256 * ratio} pixels wide
     * @return the tile as PNG, or null when it would be fully transparent
     */
    static byte[] render(MapNetworkData data, boolean showCountries, Set<String> hiddenBaseVoltages,
                         int zoom, int tileX, int tileY, double ratio) throws IOException {
        int pixels = (int) Math.ceil(TILE_SIZE * ratio);
        double scale = Math.pow(2, zoom);
        double originX = tileX * (double) TILE_SIZE;
        double originY = tileY * (double) TILE_SIZE;
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        boolean drawn = false;
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(ratio, ratio);
            g.translate(-originX, -originY);
            if (showCountries) {
                drawn = drawCountries(g, CountriesHolder.COUNTRIES, scale, originX, originY);
            }
            if (data != null && data.grid() != null) {
                drawn |= drawNetwork(g, image, data, hiddenBaseVoltages, scale, originX, originY, ratio);
            }
        } finally {
            g.dispose();
        }
        if (!drawn) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * The country at a place according to the offline basemap, whichever basemap is shown: null at sea or in an
     * unrecognized territory. Its borders are coarse (about 1 km), so near one the answer is only a guess. The first
     * call loads the basemap if no tile has yet.
     */
    static Country countryAt(double latitude, double longitude) {
        Countries countries = CountriesHolder.COUNTRIES;
        double x = MapNetworkData.projectX(longitude);
        double y = MapNetworkData.projectY(latitude);
        // even-odd per feature, as drawn: a point in a hole (Lesotho in South Africa) is in two rings of its feature
        boolean[] inside = new boolean[countries.featureCountry().length];
        for (int ring = 0; ring < countries.ringStart().length - 1; ring++) {
            int b = ring * 4;
            if (x < countries.ringBounds()[b] || x > countries.ringBounds()[b + 2]
                    || y < countries.ringBounds()[b + 1] || y > countries.ringBounds()[b + 3]) {
                continue;
            }
            if (ringContains(countries.pointX(), countries.pointY(), countries.ringStart()[ring], countries.ringStart()[ring + 1], x, y)) {
                inside[countries.ringFeature()[ring]] ^= true;
            }
        }
        for (int feature = 0; feature < inside.length; feature++) {
            if (inside[feature]) {
                return countries.featureCountry()[feature];
            }
        }
        return null;
    }

    // ray casting, the ring's closing edge from its last point back to its first included
    private static boolean ringContains(double[] pointX, double[] pointY, int start, int end, double x, double y) {
        boolean inside = false;
        for (int i = start, j = end - 1; i < end; j = i++) {
            if ((pointY[i] > y) != (pointY[j] > y)
                    && x < (pointX[j] - pointX[i]) * (y - pointY[i]) / (pointY[j] - pointY[i]) + pointX[i]) {
                inside = !inside;
            }
        }
        return inside;
    }

    // Countries don't overlap, so even-odd filling leaves exactly the holes (e.g. Lesotho in South Africa) unfilled.
    private static boolean drawCountries(Graphics2D g, Countries countries, double scale, double originX, double originY) {
        double margin = 1 / scale;
        double minX = originX / scale - margin;
        double minY = originY / scale - margin;
        double maxX = (originX + TILE_SIZE) / scale + margin;
        double maxY = (originY + TILE_SIZE) / scale + margin;
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        boolean empty = true;
        for (int ring = 0; ring < countries.ringStart().length - 1; ring++) {
            int b = ring * 4;
            if (countries.ringBounds()[b + 2] < minX || countries.ringBounds()[b] > maxX
                    || countries.ringBounds()[b + 3] < minY || countries.ringBounds()[b + 1] > maxY) {
                continue;
            }
            appendPath(path, countries.pointX(), countries.pointY(), countries.ringStart()[ring], countries.ringStart()[ring + 1], scale);
            path.closePath();
            empty = false;
        }
        if (empty) {
            return false;
        }
        g.setColor(LAND_COLOR);
        g.fill(path);
        g.setColor(BORDER_COLOR);
        g.setStroke(BORDER_STROKE);
        g.draw(path);
        return true;
    }

    // Points from start (inclusive) to end (exclusive), at the zoom of the given scale. Sub-pixel steps are
    // invisible, and skipping them keeps zoomed-out paths short.
    private static void appendPath(Path2D.Double path, double[] pointX, double[] pointY, int start, int end, double scale) {
        double lastX = pointX[start] * scale;
        double lastY = pointY[start] * scale;
        path.moveTo(lastX, lastY);
        for (int k = start + 1; k < end; k++) {
            double x = pointX[k] * scale;
            double y = pointY[k] * scale;
            if (k < end - 1 && Math.abs(x - lastX) < 1 && Math.abs(y - lastY) < 1) {
                continue;
            }
            path.lineTo(x, y);
            lastX = x;
            lastY = y;
        }
    }

    /**
     * All lines crossing the tile, then all substations in it, on top of them.
     */
    private static boolean drawNetwork(Graphics2D g, BufferedImage image, MapNetworkData data, Set<String> hiddenBaseVoltages,
                                       double scale, double originX, double originY, double ratio) {
        int substationSize = data.substationSize(scale);
        double margin = (Math.max(MapNetworkData.LINE_WEIGHT, substationSize / 2.0) + 1) / scale;
        double minX = originX / scale - margin;
        double minY = originY / scale - margin;
        double maxX = (originX + TILE_SIZE) / scale + margin;
        double maxY = (originY + TILE_SIZE) / scale + margin;
        MapNetworkData.Grid grid = data.grid();
        if (!grid.intersects(minX, minY, maxX, maxY)) {
            return false;
        }
        // a line segment is listed in every cell it crosses, hence the bit sets
        BitSet lines = new BitSet(data.lineIds().length);
        BitSet substations = new BitSet(data.substationIds().length);
        for (int cy = grid.cellY(minY); cy <= grid.cellY(maxY); cy++) {
            for (int cx = grid.cellX(minX); cx <= grid.cellX(maxX); cx++) {
                int cell = grid.cellIndex(cx, cy);
                for (int j = grid.cellStart()[cell]; j < grid.cellStart()[cell + 1]; j++) {
                    int item = grid.items()[j];
                    if (item >= 0) {
                        substations.set(item);
                    } else {
                        lines.set(data.pointLine()[-item - 1]);
                    }
                }
            }
        }
        boolean drawn = drawLines(g, data, lines, hiddenBaseVoltages, scale, minX, minY, maxX, maxY);
        return drawSubstations(image, data, substations, hiddenBaseVoltages, substationSize, scale, originX, originY, ratio,
                minX, minY, maxX, maxY) || drawn;
    }

    private static boolean drawLines(Graphics2D g, MapNetworkData data, BitSet lines, Set<String> hiddenBaseVoltages,
                                     double scale, double minX, double minY, double maxX, double maxY) {
        boolean drawn = false;
        for (int line = lines.nextSetBit(0); line >= 0; line = lines.nextSetBit(line + 1)) {
            int b = line * 4;
            double[] bounds = data.lineBounds();
            if (bounds[b + 2] < minX || bounds[b] > maxX || bounds[b + 3] < minY || bounds[b + 1] > maxY
                    || MapNetworkData.isHidden(hiddenBaseVoltages, data.lineBaseVoltages()[line])) {
                continue;
            }
            Path2D.Double path = new Path2D.Double();
            appendPath(path, data.pointX(), data.pointY(), data.lineStart()[line], data.lineStart()[line + 1], scale);
            Color color = data.colors().get(data.lineColor()[line]);
            boolean implied = data.lineImplied()[line];
            g.setColor(implied ? new Color(color.getRed(), color.getGreen(), color.getBlue(), IMPLIED_LINE_ALPHA) : color);
            g.setStroke(data.lineDisconnected()[line]
                    ? (implied ? DASHED_IMPLIED_LINE_STROKE : DASHED_LINE_STROKE)
                    : (implied ? IMPLIED_LINE_STROKE : LINE_STROKE));
            g.draw(path);
            drawn = true;
        }
        return drawn;
    }

    // Squares snapped to whole device pixels, drawn without antialiasing so they stay crisp.
    private static boolean drawSubstations(BufferedImage image, MapNetworkData data, BitSet substations, Set<String> hiddenBaseVoltages,
                                           int substationSize, double scale, double originX, double originY, double ratio,
                                           double minX, double minY, double maxX, double maxY) {
        int size = (int) Math.ceil(substationSize * ratio);
        boolean drawn = false;
        Graphics2D g = image.createGraphics();
        try {
            for (int i = substations.nextSetBit(0); i >= 0; i = substations.nextSetBit(i + 1)) {
                double x = data.substationX()[i];
                double y = data.substationY()[i];
                if (x < minX || x > maxX || y < minY || y > maxY || MapNetworkData.isHidden(hiddenBaseVoltages, data.substationBaseVoltages()[i])) {
                    continue;
                }
                g.setColor(data.colors().get(data.substationColor()[i]));
                g.fillRect((int) Math.round((x * scale - originX) * ratio - size / 2.0),
                        (int) Math.round((y * scale - originY) * ratio - size / 2.0), size, size);
                drawn = true;
            }
        } finally {
            g.dispose();
        }
        return drawn;
    }

    private static Countries loadCountries() {
        JsonNode root;
        try (InputStream stream = MapTileRenderer.class.getResourceAsStream("countries.geojson")) {
            root = new ObjectMapper().readTree(Objects.requireNonNull(stream));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<JsonNode> rings = new ArrayList<>();
        List<Integer> ringFeatures = new ArrayList<>();
        List<Country> featureCountries = new ArrayList<>();
        for (JsonNode feature : root.get("features")) {
            JsonNode code = feature.get("properties").get("country");
            featureCountries.add(code == null ? null : Country.valueOf(code.asText()));
            JsonNode geometry = feature.get("geometry");
            JsonNode coordinates = geometry.get("coordinates");
            int before = rings.size();
            if ("Polygon".equals(geometry.get("type").asText())) {
                coordinates.forEach(rings::add);
            } else {
                coordinates.forEach(polygon -> polygon.forEach(rings::add));
            }
            for (int i = before; i < rings.size(); i++) {
                ringFeatures.add(featureCountries.size() - 1);
            }
        }
        int pointCount = rings.stream().mapToInt(JsonNode::size).sum();
        int[] ringStart = new int[rings.size() + 1];
        double[] ringBounds = new double[rings.size() * 4];
        double[] pointX = new double[pointCount];
        double[] pointY = new double[pointCount];
        int k = 0;
        for (int i = 0; i < rings.size(); i++) {
            ringStart[i] = k;
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (JsonNode point : rings.get(i)) {
                // GeoJSON is [lng, lat]
                pointX[k] = MapNetworkData.projectX(point.get(0).asDouble());
                pointY[k] = MapNetworkData.projectY(point.get(1).asDouble());
                minX = Math.min(minX, pointX[k]);
                minY = Math.min(minY, pointY[k]);
                maxX = Math.max(maxX, pointX[k]);
                maxY = Math.max(maxY, pointY[k]);
                k++;
            }
            ringBounds[i * 4] = minX;
            ringBounds[i * 4 + 1] = minY;
            ringBounds[i * 4 + 2] = maxX;
            ringBounds[i * 4 + 3] = maxY;
        }
        ringStart[rings.size()] = k;
        return new Countries(ringStart, ringBounds, pointX, pointY, ringFeatures.stream().mapToInt(Integer::intValue).toArray(),
                featureCountries.toArray(new Country[0]));
    }
}
