/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.detail;

import com.powsybl.iidm.network.PhaseTapChanger;
import com.powsybl.iidm.network.PhaseTapChangerStep;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A phase tap changer's steps as voltage phasors, in per unit of the source voltage: Us along the horizontal axis,
 * each step's Ul = rho.e^(j.alpha) a dot labelled with its tap position, the dots joined in position order, and the Ul
 * arrow and alpha arc of the current position, drawn bigger and colored. Same scale on both axes, so that angles are
 * true; tall enough for every position's label, up to a limit beyond which only some are labelled.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class PhaseTapChangerDiagram extends Region {

    /**
     * One step, alpha in degrees.
     */
    public record Step(int position, double alpha, double rho) {

        private double x() {
            return rho * Math.cos(Math.toRadians(alpha));
        }

        private double y() {
            return rho * Math.sin(Math.toRadians(alpha));
        }
    }

    private static final double PADDING = 12;
    // room for the labels right of the dots
    private static final double LABEL_ROOM = 34;
    private static final double LABEL_HEIGHT = 12;
    private static final double DOT_RADIUS = 2.5;
    private static final double CURRENT_RADIUS = 5;
    private static final double ARROW_LENGTH = 10;
    private static final double ARROW_WIDTH = 4;
    private static final double MIN_SIZE = 300;
    private static final double MAX_HEIGHT = 900;
    // below this angle, the alpha label goes outside the angle, which is too narrow for it
    private static final double NARROW_ANGLE = 16;

    private List<Step> steps = List.of();
    private Integer current;

    public PhaseTapChangerDiagram() {
        getStyleClass().add("phase-tap-changer-diagram");
        setMinSize(200, 160);
    }

    /**
     * The steps of {@code changer} and its tap position, if any.
     */
    public void show(PhaseTapChanger changer) {
        steps = IntStream.rangeClosed(changer.getLowTapPosition(), changer.getHighTapPosition())
                .mapToObj(position -> {
                    PhaseTapChangerStep step = changer.getStep(position);
                    return new Step(position, step.getAlpha(), step.getRho());
                })
                .filter(step -> Double.isFinite(step.alpha()) && Double.isFinite(step.rho()))
                .toList();
        current = changer.findTapPosition().isPresent() ? changer.getTapPosition() : null;
        requestLayout();
    }

    @Override
    protected double computePrefWidth(double height) {
        return Math.max(MIN_SIZE, labelledScale() * (maxX() - minX()) + 2 * PADDING + LABEL_ROOM);
    }

    @Override
    protected double computePrefHeight(double width) {
        return Math.clamp(labelledScale() * (maxY() - minY()) + 2 * PADDING, MIN_SIZE, MAX_HEIGHT);
    }

    // the scale spacing consecutive dots by a label's height on average
    private double labelledScale() {
        double length = 0;
        for (int i = 1; i < steps.size(); i++) {
            length += Math.hypot(steps.get(i).x() - steps.get(i - 1).x(), steps.get(i).y() - steps.get(i - 1).y());
        }
        return length > 0 ? LABEL_HEIGHT * 1.2 * (steps.size() - 1) / length : 0;
    }

    private double minX() {
        return Math.min(0, steps.stream().mapToDouble(Step::x).min().orElse(0));
    }

    private double maxX() {
        return Math.max(1, steps.stream().mapToDouble(Step::x).max().orElse(1));
    }

    private double minY() {
        return Math.min(0, steps.stream().mapToDouble(Step::y).min().orElse(0));
    }

    private double maxY() {
        return Math.max(0, steps.stream().mapToDouble(Step::y).max().orElse(0));
    }

    @Override
    protected void layoutChildren() {
        getChildren().setAll(draw(getWidth(), getHeight()));
    }

    private List<Node> draw(double width, double height) {
        double minY = minY();
        double maxY = maxY();
        double maxX = maxX();
        double minX = minX();
        double scaleX = (width - 2 * PADDING - LABEL_ROOM) / (maxX - minX);
        double scale = maxY > minY ? Math.min(scaleX, (height - 2 * PADDING) / (maxY - minY)) : scaleX;
        if (!(scale > 0)) {
            return List.of();
        }
        // centered in the room left
        double originX = PADDING - minX * scale + (width - 2 * PADDING - LABEL_ROOM - (maxX - minX) * scale) / 2;
        double originY = PADDING + maxY * scale + (height - 2 * PADDING - (maxY - minY) * scale) / 2;
        Projection projection = new Projection(originX, originY, scale);

        List<Node> nodes = new ArrayList<>();
        Line axis = new Line(originX, originY, projection.x(maxX * 1.08), originY);
        axis.getStyleClass().add("phase-diagram-axis");
        nodes.add(axis);
        nodes.addAll(arrow(projection, 1, 0, 0));

        Polyline locus = new Polyline();
        steps.forEach(step -> locus.getPoints().addAll(projection.x(step.x()), projection.y(step.y())));
        locus.getStyleClass().add("phase-diagram-locus");
        nodes.add(locus);

        Step currentStep = steps.stream().filter(step -> current != null && step.position() == current).findFirst().orElse(null);
        if (currentStep != null) {
            nodes.addAll(arrow(projection, currentStep.x(), currentStep.y(), CURRENT_RADIUS));
            double radius = Math.min(60, currentStep.rho() * scale * 0.2);
            Arc arc = new Arc(originX, originY, radius, radius, 0, currentStep.alpha());
            arc.setType(ArcType.OPEN);
            arc.getStyleClass().add("phase-diagram-angle");
            Text alpha = new Text("α");
            double labelAngle = Math.abs(currentStep.alpha()) >= NARROW_ANGLE ? currentStep.alpha() / 2
                    : currentStep.alpha() + Math.copySign(NARROW_ANGLE / 2, currentStep.alpha());
            alpha.setX(originX + (radius + 4) * Math.cos(Math.toRadians(labelAngle)) - 3);
            alpha.setY(originY - (radius + 4) * Math.sin(Math.toRadians(labelAngle)) + 4);
            alpha.getStyleClass().add("phase-diagram-label");
            nodes.addAll(List.of(arc, alpha));
        }

        // every label when they fit, else every few ones, the ends and the current one
        double spacing = steps.size() < 2 ? Double.MAX_VALUE
                : Math.hypot(projection.x(steps.getLast().x()) - projection.x(steps.getFirst().x()),
                projection.y(steps.getLast().y()) - projection.y(steps.getFirst().y())) / (steps.size() - 1);
        int labelEvery = (int) Math.ceil(LABEL_HEIGHT / spacing);
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            boolean isCurrent = step == currentStep;
            Circle dot = new Circle(projection.x(step.x()), projection.y(step.y()), isCurrent ? CURRENT_RADIUS : DOT_RADIUS);
            dot.getStyleClass().add(isCurrent ? "phase-diagram-current" : "phase-diagram-step");
            nodes.add(dot);
            if (isCurrent || i % labelEvery == 0 || i == steps.size() - 1) {
                Text label = new Text(String.valueOf(step.position()));
                label.setX(dot.getCenterX() + dot.getRadius() + 4);
                label.setY(dot.getCenterY() + 4);
                label.getStyleClass().addAll("phase-diagram-label", isCurrent ? "phase-diagram-current-label" : "phase-diagram-step-label");
                nodes.add(label);
            }
        }
        return nodes;
    }

    // from the origin to (x, y) in per unit, short of it by gap pixels
    private static List<Node> arrow(Projection projection, double x, double y, double gap) {
        double length = Math.hypot(projection.x(x) - projection.originX(), projection.y(y) - projection.originY());
        if (length < ARROW_LENGTH + gap) {
            return List.of();
        }
        double unitX = (projection.x(x) - projection.originX()) / length;
        double unitY = (projection.y(y) - projection.originY()) / length;
        double endX = projection.x(x) - unitX * gap;
        double endY = projection.y(y) - unitY * gap;
        double baseX = endX - unitX * ARROW_LENGTH;
        double baseY = endY - unitY * ARROW_LENGTH;
        Line shaft = new Line(projection.originX(), projection.originY(), baseX, baseY);
        shaft.getStyleClass().add("phase-diagram-vector");
        Polygon head = new Polygon(endX, endY,
                baseX - unitY * ARROW_WIDTH, baseY + unitX * ARROW_WIDTH,
                baseX + unitY * ARROW_WIDTH, baseY - unitX * ARROW_WIDTH);
        head.getStyleClass().add("phase-diagram-arrow-head");
        return List.of(shaft, head);
    }

    private record Projection(double originX, double originY, double scale) {

        private double x(double x) {
            return originX + x * scale;
        }

        private double y(double y) {
            return originY - y * scale;
        }
    }
}
