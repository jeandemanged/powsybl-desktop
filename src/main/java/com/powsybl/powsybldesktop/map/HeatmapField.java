/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

/**
 * A Map view heatmap: a value per substation (a voltage angle, a voltage violation) spread into a continuous field,
 * as a raster over {@link MapNetworkData}'s zoom-0 pixel coordinates. Built once per network refresh off the FX
 * thread, then sampled by the tile rendering threads, so tiles join seamlessly and don't depend on the zoom level.
 * <p>
 * Each raster cell is a modified Shepard interpolation of the substations within {@link #FADE_END} spacings, with
 * weights {@code ((R - d) / (R d))^2}: exact at a substation, continuous, and local - unlike plain inverse distance
 * weighting over the k nearest substations, which jumps wherever that neighbour set changes. The field fades out
 * between {@link #FADE_START} and {@link #FADE_END} spacings from the nearest substation, so it isn't extrapolated
 * over areas without any.
 * <p>
 * Values are stored premultiplied by their alpha, so that bilinear sampling next to a faded out cell doesn't pull
 * the value towards 0.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
record HeatmapField(double minX, double minY, double cellSize, int width, int height, float[] premultipliedValues,
                    float[] alphas, double minValue, double maxValue, double meanValue) {

    /** ColorBrewer's diverging RdBu, blue for the lowest values: white is the center of the scale. */
    static final String[] DIVERGING_COLOR_STOPS = {"#2166AC", "#67A9CF", "#F7F7F7", "#EF8A62", "#B2182B"};
    /** RdBu's ends. */
    static final String UNDER_COLOR = DIVERGING_COLOR_STOPS[0];
    static final String OVER_COLOR = DIVERGING_COLOR_STOPS[DIVERGING_COLOR_STOPS.length - 1];

    /** In substation spacings. */
    private static final double FADE_START = 3;
    private static final double FADE_END = 6;
    private static final double CELLS_PER_SPACING = 2;
    private static final int MAX_RASTER_SIZE = 1024;
    /** When there are fewer than two distinct substation positions: about 40 km at the equator. */
    private static final double FALLBACK_SPACING = MapNetworkData.WORLD_SIZE / 1024;
    static final double OPACITY = 0.65;
    /** Odd, for an entry exactly at the center of the scale. */
    private static final int[] DIVERGING_COLORS = colorTable(257);

    /**
     * Maps a sampled value, and the field's alpha there, to a non-premultiplied ARGB color. Called concurrently.
     */
    interface ColorScale {
        int argb(double value, double alpha);
    }

    /**
     * {@link #DIVERGING_COLOR_STOPS} from {@code center - range} to {@code center + range}, clamped beyond.
     */
    static ColorScale diverging(double center, double range) {
        return (value, alpha) -> {
            double t = Math.clamp((value - center) / (2 * range) + 0.5, 0, 1);
            return opacity(alpha) << 24 | DIVERGING_COLORS[(int) Math.round(t * (DIVERGING_COLORS.length - 1))];
        };
    }

    /**
     * Transparent at 0, then {@link #UNDER_COLOR} below it and {@link #OVER_COLOR} above it, more opaque the farther
     * from 0, fully at {@code range}.
     */
    static ColorScale signedIntensity(double range) {
        int under = Integer.parseInt(UNDER_COLOR.substring(1), 16);
        int over = Integer.parseInt(OVER_COLOR.substring(1), 16);
        return (value, alpha) -> opacity(alpha * Math.min(1, Math.abs(value) / range)) << 24 | (value < 0 ? under : over);
    }

    private static int opacity(double alpha) {
        return (int) Math.round(alpha * OPACITY * 255);
    }

    /**
     * @param values NaN for substations without one, skipped
     * @param spacing typical distance between substations, see {@link MapNetworkData#substationSpacing()}
     * @return null when no substation has a value
     */
    static HeatmapField build(double[] x, double[] y, double[] values, double spacing) {
        int count = 0;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minValue = Double.POSITIVE_INFINITY;
        double maxValue = Double.NEGATIVE_INFINITY;
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                count++;
                minX = Math.min(minX, x[i]);
                minY = Math.min(minY, y[i]);
                maxX = Math.max(maxX, x[i]);
                maxY = Math.max(maxY, y[i]);
                minValue = Math.min(minValue, values[i]);
                maxValue = Math.max(maxValue, values[i]);
                sum += values[i];
            }
        }
        if (count == 0) {
            return null;
        }
        double s = Double.isFinite(spacing) ? spacing : FALLBACK_SPACING;
        double radius = FADE_END * s;
        minX -= radius;
        minY -= radius;
        maxX += radius;
        maxY += radius;
        double cellSize = Math.max(s / CELLS_PER_SPACING, Math.max(maxX - minX, maxY - minY) / MAX_RASTER_SIZE);
        int width = (int) Math.ceil((maxX - minX) / cellSize);
        int height = (int) Math.ceil((maxY - minY) / cellSize);

        // substations with a value bucketed by the support radius: all those within it are in the 3 x 3 buckets around
        int bucketsX = (int) Math.ceil((maxX - minX) / radius);
        int bucketsY = (int) Math.ceil((maxY - minY) / radius);
        int[] bucketStart = new int[bucketsX * bucketsY + 1];
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                bucketStart[bucket(x[i], y[i], minX, minY, radius, bucketsX, bucketsY) + 1]++;
            }
        }
        for (int b = 0; b < bucketsX * bucketsY; b++) {
            bucketStart[b + 1] += bucketStart[b];
        }
        int[] items = new int[count];
        int[] cursor = bucketStart.clone();
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                items[cursor[bucket(x[i], y[i], minX, minY, radius, bucketsX, bucketsY)]++] = i;
            }
        }

        float[] raster = new float[width * height];
        float[] alphas = new float[width * height];
        double radiusSquared = radius * radius;
        for (int row = 0; row < height; row++) {
            double cy = minY + (row + 0.5) * cellSize;
            int by = Math.min(bucketsY - 1, (int) ((cy - minY) / radius));
            for (int col = 0; col < width; col++) {
                double cx = minX + (col + 0.5) * cellSize;
                int bx = Math.min(bucketsX - 1, (int) ((cx - minX) / radius));
                double weightSum = 0;
                double valueSum = 0;
                double nearestSquared = Double.POSITIVE_INFINITY;
                double exact = Double.NaN;
                for (int gy = Math.max(0, by - 1); gy <= Math.min(bucketsY - 1, by + 1); gy++) {
                    for (int gx = Math.max(0, bx - 1); gx <= Math.min(bucketsX - 1, bx + 1); gx++) {
                        int b = gy * bucketsX + gx;
                        for (int j = bucketStart[b]; j < bucketStart[b + 1]; j++) {
                            int i = items[j];
                            double dx = x[i] - cx;
                            double dy = y[i] - cy;
                            double squared = dx * dx + dy * dy;
                            if (squared >= radiusSquared) {
                                continue;
                            }
                            nearestSquared = Math.min(nearestSquared, squared);
                            if (squared == 0) {
                                exact = values[i];
                                continue;
                            }
                            double d = Math.sqrt(squared);
                            double w = (radius - d) / (radius * d);
                            w *= w;
                            weightSum += w;
                            valueSum += w * values[i];
                        }
                    }
                }
                if (nearestSquared == Double.POSITIVE_INFINITY) {
                    continue;
                }
                double value = Double.isNaN(exact) ? valueSum / weightSum : exact;
                double alpha = Math.clamp((radius - Math.sqrt(nearestSquared)) / ((FADE_END - FADE_START) * s), 0, 1);
                raster[row * width + col] = (float) (value * alpha);
                alphas[row * width + col] = (float) alpha;
            }
        }
        return new HeatmapField(minX, minY, cellSize, width, height, raster, alphas, minValue, maxValue, sum / count);
    }

    private static int bucket(double x, double y, double minX, double minY, double size, int bucketsX, int bucketsY) {
        int bx = Math.min(bucketsX - 1, (int) ((x - minX) / size));
        int by = Math.min(bucketsY - 1, (int) ((y - minY) / size));
        return by * bucketsX + bx;
    }

    /**
     * Half the span of a color scale symmetric around {@code center}: the largest deviation from it, rounded up to a
     * multiple of {@code step}, so the scale never clamps.
     */
    double range(double center, double step) {
        double deviation = Math.max(Math.abs(maxValue - center), Math.abs(minValue - center));
        return Math.max(step, Math.ceil(deviation / step) * step);
    }

    boolean intersects(double x0, double y0, double x1, double y1) {
        return x1 >= minX && x0 <= minX + width * cellSize && y1 >= minY && y0 <= minY + height * cellSize;
    }

    double alpha(double x, double y) {
        return sample(alphas, x, y);
    }

    /**
     * NaN where the field is faded out entirely.
     */
    double value(double x, double y) {
        double alpha = alpha(x, y);
        return alpha == 0 ? Double.NaN : sample(premultipliedValues, x, y) / alpha;
    }

    /**
     * The heatmap's non-premultiplied ARGB color at {@code (x, y)}, fully transparent where it's faded out.
     */
    int argb(double x, double y, ColorScale scale) {
        double alpha = alpha(x, y);
        return alpha == 0 ? 0 : scale.argb(sample(premultipliedValues, x, y) / alpha, alpha);
    }

    // bilinear, between cell centers; clamped to the edge cells, which are faded out anyway
    private double sample(float[] raster, double x, double y) {
        double gx = (x - minX) / cellSize - 0.5;
        double gy = (y - minY) / cellSize - 0.5;
        if (gx < -1 || gy < -1 || gx > width || gy > height) {
            return 0;
        }
        int x0 = (int) Math.floor(gx);
        int y0 = (int) Math.floor(gy);
        double fx = gx - x0;
        double fy = gy - y0;
        int ax = Math.clamp(x0, 0, width - 1);
        int bx = Math.clamp(x0 + 1, 0, width - 1);
        int ay = Math.clamp(y0, 0, height - 1);
        int by = Math.clamp(y0 + 1, 0, height - 1);
        double top = raster[ay * width + ax] * (1 - fx) + raster[ay * width + bx] * fx;
        double bottom = raster[by * width + ax] * (1 - fx) + raster[by * width + bx] * fx;
        return top * (1 - fy) + bottom * fy;
    }

    // DIVERGING_COLOR_STOPS evenly spaced, linearly interpolated in RGB
    private static int[] colorTable(int size) {
        int[] stops = new int[DIVERGING_COLOR_STOPS.length];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = Integer.parseInt(DIVERGING_COLOR_STOPS[i].substring(1), 16);
        }
        int[] table = new int[size];
        for (int i = 0; i < size; i++) {
            double position = (double) i / (size - 1) * (stops.length - 1);
            int stop = Math.min(stops.length - 2, (int) position);
            double f = position - stop;
            int rgb = 0;
            for (int shift = 16; shift >= 0; shift -= 8) {
                int a = stops[stop] >> shift & 0xFF;
                int b = stops[stop + 1] >> shift & 0xFF;
                rgb |= (int) Math.round(a + (b - a) * f) << shift;
            }
            table[i] = rgb;
        }
        return table;
    }
}
