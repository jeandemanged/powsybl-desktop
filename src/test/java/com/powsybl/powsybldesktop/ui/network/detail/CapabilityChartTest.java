/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.detail;

import com.powsybl.powsybldesktop.ui.testutil.AbstractHeadlessApplicationTest;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dragging a {@link CapabilityChart}'s curve points and P lines, and its operating point symbols.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
class CapabilityChartTest extends AbstractHeadlessApplicationTest {

    private CapabilityChart chart;
    private final List<List<CapabilityChart.Point>> curves = new ArrayList<>();
    private final List<Double> maxPs = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        chart = new CapabilityChart();
        chart.setCurve(List.of(new CapabilityChart.Point(0, -50, 50), new CapabilityChart.Point(100, -30, 30)));
        chart.setActivePowerLimits(0, 100);
        chart.setOperatingPoints(50, 10, 50, 10);
        chart.setOnCurveEdited(curves::add);
        chart.setOnMaxPEdited(maxPs::add);
        stage.setScene(new Scene(chart, 600, 500));
        stage.show();
    }

    @Test
    void showsTheSolvedPointAsACrosshairOverTheTargetBubble() {
        assertNotNull(chart.lookup(".solved-crosshair"));
        assertEquals(2, chart.lookupAll(".chart-line-symbol").stream()
                .filter(node -> !node.getStyleClass().contains("chart-legend-item-symbol"))
                .filter(node -> node.getStyleClass().contains("series1") || node.getStyleClass().contains("series2")).count());
    }

    @Test
    void draggingAPointEditsTheCurve() {
        // the max Q vertex of the point at P = 100
        Node vertex = chart.lookupAll(".capability-curve-point").stream().toList().get(1);
        drag(vertex, MouseButton.PRIMARY).moveBy(40, 0).drop();

        assertEquals(1, curves.size());
        List<CapabilityChart.Point> curve = curves.getFirst();
        assertEquals(2, curve.size());
        assertEquals(new CapabilityChart.Point(0, -50, 50), curve.getFirst());
        assertTrue(curve.getLast().maxQ() > 30, curve.toString());
        assertEquals(-30, curve.getLast().minQ());
    }

    @Test
    void draggingTheMaxPLineEditsMaxP() {
        Line maxPHandle = (Line) chart.lookupAll("Line").stream()
                .filter(node -> node instanceof Line line && line.getStrokeWidth() > 5 && !line.isMouseTransparent())
                .findFirst().orElseThrow();
        drag(maxPHandle, MouseButton.PRIMARY).moveBy(0, -40).drop();

        assertEquals(1, maxPs.size());
        assertTrue(maxPs.getFirst() > 100, maxPs.toString());
    }

    @Test
    void nothingIsDraggedWhenNotEditable() {
        interact(() -> chart.editableProperty().set(false));
        Node vertex = chart.lookupAll(".capability-curve-point").stream().toList().get(1);
        drag(vertex, MouseButton.PRIMARY).moveBy(40, 0).drop();

        assertTrue(curves.isEmpty());
    }
}
