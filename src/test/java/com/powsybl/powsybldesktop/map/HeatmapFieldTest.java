/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class HeatmapFieldTest {

    private static final double SPACING = 0.01;

    @Test
    void noValueGivesNoField() {
        assertNull(HeatmapField.build(new double[] {1, 2}, new double[] {1, 2}, new double[] {Double.NaN, Double.NaN}, SPACING));
        assertNull(HeatmapField.build(new double[0], new double[0], new double[0], Double.POSITIVE_INFINITY));
    }

    @Test
    void valueNearAnIsolatedSubstationIsItsValue() {
        // far enough apart for neither to reach the other
        HeatmapField field = HeatmapField.build(new double[] {100, 101}, new double[] {100, 100}, new double[] {-12, 33}, SPACING);

        assertNotNull(field);
        assertEquals(-12, field.value(100, 100), 1e-4);
        assertEquals(33, field.value(101.002, 100.001), 1e-4);
        assertEquals(1, field.alpha(100, 100), 1e-6);
    }

    @Test
    void valueBetweenSubstationsIsInterpolated() {
        HeatmapField field = HeatmapField.build(new double[] {100, 100.02}, new double[] {100, 100}, new double[] {0, 10}, SPACING);

        assertNotNull(field);
        assertEquals(5, field.value(100.01, 100), 0.5);
        double quarter = field.value(100.005, 100);
        assertTrue(quarter > 0 && quarter < 5, "value " + quarter);
    }

    @Test
    void fieldFadesOutAwayFromSubstations() {
        HeatmapField field = HeatmapField.build(new double[] {100}, new double[] {100}, new double[] {20}, SPACING);

        assertNotNull(field);
        assertEquals(1, field.alpha(100.02, 100), 1e-6);
        double fading = field.alpha(100.045, 100);
        assertTrue(fading > 0 && fading < 1, "alpha " + fading);
        assertEquals(0, field.alpha(100.07, 100), 1e-6);
        assertTrue(Double.isNaN(field.value(100.07, 100)));
        assertEquals(0, field.argb(100.07, 100, HeatmapField.diverging(0, 10)));
    }

    @Test
    void substationsWithoutValueAreSkipped() {
        HeatmapField field = HeatmapField.build(new double[] {100, 100.01}, new double[] {100, 100}, new double[] {7, Double.NaN}, SPACING);

        assertNotNull(field);
        assertEquals(7, field.value(100.01, 100), 1e-4);
        assertEquals(7, field.meanValue(), 1e-9);
    }

    @Test
    void rangeIsSymmetricAroundTheCenter() {
        HeatmapField field = HeatmapField.build(new double[] {100, 101}, new double[] {100, 100}, new double[] {-12, 33}, SPACING);

        assertNotNull(field);
        assertEquals(40, field.range(0, 10));
        assertEquals(10.5, field.meanValue());
        assertEquals(30, field.range(field.meanValue(), 10));

        HeatmapField violations = HeatmapField.build(new double[] {100, 101}, new double[] {100, 100}, new double[] {-0.08, 0.03}, SPACING);
        assertNotNull(violations);
        assertEquals(0.1, violations.range(0, 0.05), 1e-9);
    }

    @Test
    void divergingCenterIsWhite() {
        HeatmapField field = HeatmapField.build(new double[] {100}, new double[] {100}, new double[] {20}, SPACING);

        assertNotNull(field);
        assertEquals(0xF7F7F7, field.argb(100, 100, HeatmapField.diverging(20, 10)) & 0xFFFFFF);
        assertEquals(0x2166AC, field.argb(100, 100, HeatmapField.diverging(40, 10)) & 0xFFFFFF);
        assertEquals(0xB2182B, field.argb(100, 100, HeatmapField.diverging(0, 10)) & 0xFFFFFF);
    }

    @Test
    void signedIntensityIsTransparentAtZeroAndMoreOpaqueFartherFromIt() {
        HeatmapField.ColorScale scale = HeatmapField.signedIntensity(0.1);

        assertEquals(0, scale.argb(0, 1) >>> 24);
        int weakOver = scale.argb(0.02, 1);
        int strongOver = scale.argb(0.08, 1);
        assertEquals(0xB2182B, weakOver & 0xFFFFFF);
        assertTrue((weakOver >>> 24) < (strongOver >>> 24));
        assertEquals(0x2166AC, scale.argb(-0.05, 1) & 0xFFFFFF);
        assertEquals(scale.argb(0.1, 1) >>> 24, scale.argb(0.5, 1) >>> 24);
    }
}
