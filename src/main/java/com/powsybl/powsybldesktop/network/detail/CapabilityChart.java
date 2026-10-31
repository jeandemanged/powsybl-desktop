/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.iidm.network.MinMaxReactiveLimits;
import com.powsybl.iidm.network.ReactiveCapabilityCurve;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * P/Q diagram of an equipment's reactive limits, P on the vertical axis and Q on the horizontal one: the capability
 * area's outline (curve points, or min/max Q over the [min P, max P] range) with the target and solved operating points.
 * Both in generator convention.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CapabilityChart extends LineChart<Number, Number> {

    /**
     * One point of a capability outline: at active power p, reactive power between minQ and maxQ.
     */
    public record Point(double p, double minQ, double maxQ) {
    }

    private final XYChart.Series<Number, Number> limitsSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> targetSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> solvedSeries = new XYChart.Series<>();

    public CapabilityChart() {
        super(new NumberAxis(), new NumberAxis());
        getXAxis().setLabel(Messages.get("detail.chart.q"));
        getYAxis().setLabel(Messages.get("detail.chart.p"));
        // the outline goes back along the min Q side: points must stay in insertion order
        setAxisSortingPolicy(SortingPolicy.NONE);
        setAnimated(false);
        limitsSeries.setName(Messages.get("detail.tab.reactiveLimits"));
        targetSeries.setName(Messages.get("detail.chart.target"));
        solvedSeries.setName(Messages.get("detail.chart.solved"));
        getData().setAll(List.of(limitsSeries, targetSeries, solvedSeries));
        getStyleClass().add("capability-chart");
    }

    /**
     * The outline points of {@code limits}: the curve's own, or a min/max Q rectangle over [minP, maxP] when both are
     * finite. None for other kinds (reactive capability shape).
     */
    public static List<Point> points(ReactiveLimits limits, double minP, double maxP) {
        if (limits instanceof ReactiveCapabilityCurve curve) {
            return curve.getPoints().stream().map(point -> new Point(point.getP(), point.getMinQ(), point.getMaxQ())).toList();
        }
        if (limits instanceof MinMaxReactiveLimits minMax) {
            return rectangle(minMax.getMinQ(), minMax.getMaxQ(), minP, maxP);
        }
        return List.of();
    }

    public static List<Point> rectangle(double minQ, double maxQ, double minP, double maxP) {
        return Double.isFinite(minP) && Double.isFinite(maxP)
                ? List.of(new Point(minP, minQ, maxQ), new Point(maxP, minQ, maxQ))
                : List.of();
    }

    public void setLimits(List<Point> points) {
        List<Point> sorted = points.stream()
                .filter(point -> Double.isFinite(point.p()) && Double.isFinite(point.minQ()) && Double.isFinite(point.maxQ()))
                .sorted(Comparator.comparingDouble(Point::p))
                .toList();
        List<XYChart.Data<Number, Number>> outline = new ArrayList<>();
        sorted.forEach(point -> outline.add(new XYChart.Data<>(point.maxQ(), point.p())));
        sorted.reversed().forEach(point -> outline.add(new XYChart.Data<>(point.minQ(), point.p())));
        if (!sorted.isEmpty()) {
            outline.add(new XYChart.Data<>(sorted.getFirst().maxQ(), sorted.getFirst().p()));
        }
        limitsSeries.getData().setAll(outline);
    }

    /**
     * NaN values hide their point, e.g. no solved point before a load flow.
     */
    public void setOperatingPoints(double targetP, double targetQ, double solvedP, double solvedQ) {
        setPoint(targetSeries, targetP, targetQ);
        setPoint(solvedSeries, solvedP, solvedQ);
    }

    private static void setPoint(XYChart.Series<Number, Number> series, double p, double q) {
        if (Double.isFinite(p) && Double.isFinite(q)) {
            series.getData().setAll(List.of(new XYChart.Data<>(q, p)));
        } else {
            series.getData().clear();
        }
    }
}
