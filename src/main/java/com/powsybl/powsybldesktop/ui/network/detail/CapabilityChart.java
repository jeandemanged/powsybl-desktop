/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.detail;

import com.powsybl.iidm.network.MinMaxReactiveLimits;
import com.powsybl.iidm.network.ReactiveCapabilityCurve;
import com.powsybl.iidm.network.ReactiveLimits;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.stream.DoubleStream;

/**
 * P/Q diagram of an equipment's reactive limits, P on the vertical axis and Q on the horizontal one: the capability
 * area's outline (curve points, or min/max Q over the [min P, max P] range), the min and max P lines, the target
 * operating point (a bubble) and the solved one (a crosshair, so that both show when they overlap). Both in generator
 * convention. When editable, a curve's points are dragged, added and removed from the chart, and the P lines dragged:
 * each edit is reported on release.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CapabilityChart extends LineChart<Number, Number> {

    /**
     * One point of a capability outline: at active power p, reactive power between minQ and maxQ.
     */
    public record Point(double p, double minQ, double maxQ) {
    }

    private static final double PADDING = 0.05;
    private static final int TICKS = 6;

    private final XYChart.Series<Number, Number> limitsSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> targetSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> solvedSeries = new XYChart.Series<>();
    private final PLine minPLine = new PLine("desktop.common.column.minP");
    private final PLine maxPLine = new PLine("desktop.common.column.maxP");
    private final BooleanProperty editable = new SimpleBooleanProperty(true);
    private final ContextMenu menu = new ContextMenu();

    // sorted by P, edited in place while dragging
    private List<Point> points = new ArrayList<>();
    private List<Point> shownPoints = List.of();
    private boolean curve;
    private Consumer<List<Point>> onCurveEdited;
    // the drag in progress, if any: tracked by the chart, the mouse soon leaving the small node pressed; the axes stay
    // put meanwhile, so that what is dragged stays under the mouse
    private Consumer<MouseEvent> dragMove;
    private Runnable dragEnd;
    private double targetP = Double.NaN;
    private double targetQ = Double.NaN;
    private double solvedP = Double.NaN;
    private double solvedQ = Double.NaN;

    public CapabilityChart() {
        super(new NumberAxis(), new NumberAxis());
        xAxis().setAutoRanging(false);
        yAxis().setAutoRanging(false);
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
        // a P line along the outline must stay draggable; the points are separate nodes, still picked
        getData().forEach(series -> series.getNode().setMouseTransparent(true));
        // under the series, whose points must be picked first where a line crosses them
        getPlotChildren().addAll(0, List.of(minPLine.line, minPLine.label, minPLine.handle, maxPLine.line, maxPLine.label, maxPLine.handle));
        setOnContextMenuRequested(this::requestAddPointMenu);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (dragMove != null) {
                dragMove.accept(event);
                event.consume();
            }
        });
        addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (dragMove != null) {
                Runnable end = dragEnd;
                dragMove = null;
                dragEnd = null;
                event.consume();
                end.run();
            }
        });
        updateRanges();
    }

    /**
     * Whether the curve and P lines can be dragged, true by default.
     */
    public BooleanProperty editableProperty() {
        return editable;
    }

    /**
     * Called with the whole curve, sorted by P, after a point is dragged, added or removed; the curve can't be edited
     * without it.
     */
    public void setOnCurveEdited(Consumer<List<Point>> onCurveEdited) {
        this.onCurveEdited = onCurveEdited;
    }

    /**
     * Called with the new min P after its line is dragged; the line can't be dragged without it.
     */
    public void setOnMinPEdited(DoubleConsumer onEdited) {
        minPLine.setOnEdited(onEdited);
    }

    public void setOnMaxPEdited(DoubleConsumer onEdited) {
        maxPLine.setOnEdited(onEdited);
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

    /**
     * A read-only outline, e.g. min/max limits.
     */
    public void setLimits(List<Point> points) {
        show(points, false);
    }

    /**
     * A capability curve's points, editable from the chart.
     */
    public void setCurve(List<Point> points) {
        show(points, true);
    }

    /**
     * NaN hides a line.
     */
    public void setActivePowerLimits(double minP, double maxP) {
        if (dragMove == null) {
            minPLine.value = minP;
            maxPLine.value = maxP;
            updateRanges();
            requestChartLayout();
        }
    }

    /**
     * NaN values hide their point, e.g. no solved point before a load flow.
     */
    public void setOperatingPoints(double targetP, double targetQ, double solvedP, double solvedQ) {
        this.targetP = targetP;
        this.targetQ = targetQ;
        this.solvedP = solvedP;
        this.solvedQ = solvedQ;
        setPoint(targetSeries, targetP, targetQ, null);
        Region crosshair = new Region();
        crosshair.getStyleClass().add("solved-crosshair");
        setPoint(solvedSeries, solvedP, solvedQ, crosshair);
        updateRanges();
    }

    @Override
    protected void layoutPlotChildren() {
        super.layoutPlotChildren();
        minPLine.layout();
        maxPLine.layout();
    }

    private void show(List<Point> newPoints, boolean editableCurve) {
        if (dragMove != null) {
            return;
        }
        points = new ArrayList<>(sorted(newPoints.stream()
                .filter(point -> Double.isFinite(point.p()) && Double.isFinite(point.minQ()) && Double.isFinite(point.maxQ()))
                .toList()));
        shownPoints = List.copyOf(points);
        curve = editableCurve;
        List<XYChart.Data<Number, Number>> outline = new ArrayList<>();
        points.forEach(point -> outline.add(new XYChart.Data<>(point.maxQ(), point.p())));
        points.reversed().forEach(point -> outline.add(new XYChart.Data<>(point.minQ(), point.p())));
        if (!points.isEmpty()) {
            outline.add(new XYChart.Data<>(points.getFirst().maxQ(), points.getFirst().p()));
        }
        limitsSeries.getData().setAll(outline);
        int count = points.size();
        for (int i = 0; i < outline.size(); i++) {
            Node symbol = outline.get(i).getNode();
            if (symbol == null) {
                continue;
            }
            if (i == 2 * count) {
                // the closing point is the first one again
                symbol.setVisible(false);
            } else if (curve) {
                installVertex(symbol, i < count ? i : 2 * count - 1 - i, i < count);
            }
        }
        updateRanges();
    }

    private void installVertex(Node symbol, int index, boolean maxSide) {
        symbol.getStyleClass().add("capability-curve-point");
        symbol.cursorProperty().bind(Bindings.when(editable).then(Cursor.MOVE).otherwise(Cursor.DEFAULT));
        symbol.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && canEditCurve()) {
                startDrag(drag -> moveVertex(index, maxSide, p(drag), q(drag)), () -> {
                    if (!points.equals(shownPoints)) {
                        onCurveEdited.accept(sorted(points));
                    }
                });
                event.consume();
            }
        });
        symbol.setOnContextMenuRequested(event -> {
            event.consume();
            if (canEditCurve()) {
                MenuItem remove = new MenuItem(Messages.get("detail.reactiveLimits.removePoint"));
                // IIDM requires two points at least
                remove.setDisable(points.size() <= 2);
                remove.setOnAction(_ -> {
                    List<Point> edited = new ArrayList<>(points);
                    edited.remove(index);
                    onCurveEdited.accept(edited);
                });
                menu.getItems().setAll(remove);
                menu.show(symbol, event.getScreenX(), event.getScreenY());
            }
        });
    }

    private void moveVertex(int index, boolean maxSide, double p, double q) {
        Point point = points.get(index);
        points.set(index, new Point(p, maxSide ? point.minQ() : q, maxSide ? q : point.maxQ()));
        List<XYChart.Data<Number, Number>> outline = limitsSeries.getData();
        int count = points.size();
        XYChart.Data<Number, Number> max = outline.get(index);
        XYChart.Data<Number, Number> min = outline.get(2 * count - 1 - index);
        max.setYValue(p);
        min.setYValue(p);
        (maxSide ? max : min).setXValue(q);
        if (index == 0) {
            outline.get(2 * count).setYValue(p);
            outline.get(2 * count).setXValue(points.getFirst().maxQ());
        }
    }

    private void startDrag(Consumer<MouseEvent> move, Runnable end) {
        dragMove = move;
        dragEnd = end;
    }

    private void requestAddPointMenu(ContextMenuEvent event) {
        double x = xAxis().sceneToLocal(event.getSceneX(), event.getSceneY()).getX();
        double y = yAxis().sceneToLocal(event.getSceneX(), event.getSceneY()).getY();
        if (!canEditCurve() || x < 0 || x > xAxis().getWidth() || y < 0 || y > yAxis().getHeight()) {
            return;
        }
        double p = yAxis().getValueForDisplay(y).doubleValue();
        MenuItem add = new MenuItem(Messages.get("detail.reactiveLimits.addPoint"));
        add.setOnAction(_ -> {
            List<Point> edited = new ArrayList<>(points);
            edited.add(interpolated(p));
            onCurveEdited.accept(sorted(edited));
        });
        menu.getItems().setAll(add);
        menu.show(this, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    // the outline's Q range at p, the nearest point's beyond the curve
    private Point interpolated(double p) {
        if (points.isEmpty()) {
            return new Point(p, 0, 0);
        }
        if (p <= points.getFirst().p()) {
            return new Point(p, points.getFirst().minQ(), points.getFirst().maxQ());
        }
        for (int i = 1; i < points.size(); i++) {
            Point below = points.get(i - 1);
            Point above = points.get(i);
            if (p <= above.p()) {
                double ratio = (p - below.p()) / (above.p() - below.p());
                return new Point(p, below.minQ() + ratio * (above.minQ() - below.minQ()), below.maxQ() + ratio * (above.maxQ() - below.maxQ()));
            }
        }
        return new Point(p, points.getLast().minQ(), points.getLast().maxQ());
    }

    private boolean canEditCurve() {
        return curve && editable.get() && onCurveEdited != null;
    }

    private static List<Point> sorted(List<Point> points) {
        return points.stream().sorted(Comparator.comparingDouble(Point::p)).toList();
    }

    private double p(MouseEvent event) {
        return yAxis().getValueForDisplay(yAxis().sceneToLocal(event.getSceneX(), event.getSceneY()).getY()).doubleValue();
    }

    private double q(MouseEvent event) {
        return xAxis().getValueForDisplay(xAxis().sceneToLocal(event.getSceneX(), event.getSceneY()).getX()).doubleValue();
    }

    private NumberAxis xAxis() {
        return (NumberAxis) getXAxis();
    }

    private NumberAxis yAxis() {
        return (NumberAxis) getYAxis();
    }

    private void updateRanges() {
        if (dragMove != null) {
            return;
        }
        // zero in range, as the axes' default
        setRange(xAxis(), DoubleStream.concat(DoubleStream.of(0, targetQ, solvedQ),
                points.stream().flatMapToDouble(point -> DoubleStream.of(point.minQ(), point.maxQ()))));
        setRange(yAxis(), DoubleStream.concat(DoubleStream.of(0, targetP, solvedP, minPLine.value, maxPLine.value),
                points.stream().mapToDouble(Point::p)));
    }

    private static void setRange(NumberAxis axis, DoubleStream values) {
        double[] finite = values.filter(Double::isFinite).toArray();
        double min = DoubleStream.of(finite).min().orElse(0);
        double max = DoubleStream.of(finite).max().orElse(0);
        double span = max > min ? max - min : 1;
        double tick = niceTick(span * (1 + 2 * PADDING) / TICKS);
        axis.setLowerBound(Math.floor((min - span * PADDING) / tick) * tick);
        axis.setUpperBound(Math.ceil((max + span * PADDING) / tick) * tick);
        axis.setTickUnit(tick);
    }

    private static double niceTick(double raw) {
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double fraction = raw / magnitude;
        double nice;
        if (fraction <= 1) {
            nice = 1;
        } else if (fraction <= 2) {
            nice = 2;
        } else if (fraction <= 5) {
            nice = 5;
        } else {
            nice = 10;
        }
        return magnitude * nice;
    }

    private static void setPoint(XYChart.Series<Number, Number> series, double p, double q, Node symbol) {
        if (Double.isFinite(p) && Double.isFinite(q)) {
            XYChart.Data<Number, Number> data = new XYChart.Data<>(q, p);
            data.setNode(symbol);
            series.getData().setAll(List.of(data));
        } else {
            series.getData().clear();
        }
    }

    /**
     * A horizontal line across the plot at a P value, dragged through a wider transparent handle.
     */
    private final class PLine {

        private final Line line = new Line();
        private final Line handle = new Line();
        private final Text label;
        private double value = Double.NaN;
        private DoubleConsumer onEdited;

        private PLine(String labelKey) {
            label = new Text(Messages.get(labelKey));
            line.getStyleClass().add("capability-p-limit");
            label.getStyleClass().add("capability-p-limit-label");
            line.setMouseTransparent(true);
            label.setMouseTransparent(true);
            // a transparent stroke is still picked, unlike a null one
            handle.setStroke(Color.TRANSPARENT);
            handle.setStrokeWidth(9);
            handle.setCursor(Cursor.V_RESIZE);
            handle.setMouseTransparent(true);
            handle.setOnMousePressed(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    double pressedValue = value;
                    startDrag(drag -> {
                        value = p(drag);
                        layout();
                    }, () -> {
                        if (value != pressedValue) {
                            onEdited.accept(value);
                        }
                    });
                    event.consume();
                }
            });
        }

        private void setOnEdited(DoubleConsumer onEdited) {
            this.onEdited = onEdited;
            handle.mouseTransparentProperty().unbind();
            if (onEdited != null) {
                handle.mouseTransparentProperty().bind(editable.not());
            } else {
                handle.setMouseTransparent(true);
            }
        }

        private void layout() {
            boolean visible = Double.isFinite(value) && value >= yAxis().getLowerBound() && value <= yAxis().getUpperBound();
            line.setVisible(visible);
            label.setVisible(visible);
            handle.setVisible(visible);
            if (visible) {
                double y = yAxis().getDisplayPosition(value);
                double width = xAxis().getWidth();
                for (Line shape : List.of(line, handle)) {
                    shape.setStartX(0);
                    shape.setEndX(width);
                    shape.setStartY(y);
                    shape.setEndY(y);
                }
                label.setX(width - label.getLayoutBounds().getWidth() - 4);
                label.setY(y - 4);
            }
        }
    }
}
