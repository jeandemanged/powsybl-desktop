/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TieLine;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.LinePosition;
import com.powsybl.iidm.network.extensions.LinePositionAdder;
import com.powsybl.iidm.network.extensions.SubstationPosition;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Geographical positions of substations ({@link SubstationPosition}) and routes of lines ({@link LinePosition}), both
 * immutable extensions, edited by replacing them. IIDM only takes a route on a line or a boundary line: a tie line's
 * route is that of its two boundary lines, each from its substation to the tie line's middle. A voltage level may have
 * no substation, its end then having no position.
 * <p>
 * The edits don't check the network busy lock: callers run them through {@link NetworkEdits#apply}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class PositionEdits {

    private PositionEdits() {
    }

    public static Optional<Coordinate> substationCoordinate(Substation substation) {
        SubstationPosition position = substation.getExtension(SubstationPosition.class);
        return position == null ? Optional.empty() : Optional.of(position.getCoordinate());
    }

    public static Optional<Coordinate> substationCoordinate(VoltageLevel voltageLevel) {
        return voltageLevel.getSubstation().flatMap(PositionEdits::substationCoordinate);
    }

    /**
     * The route of a line or boundary line, empty for none.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<Coordinate> route(Identifiable<?> lineOrBoundaryLine) {
        LinePosition<?> position = (LinePosition<?>) lineOrBoundaryLine.getExtension((Class) LinePosition.class);
        return position == null ? List.of() : position.getCoordinates();
    }

    /**
     * From one end's substation to the other's, empty if either has no position.
     */
    public static Optional<List<Coordinate>> straightRoute(Line line) {
        Optional<Coordinate> start = substationCoordinate(line.getTerminal1().getVoltageLevel());
        Optional<Coordinate> end = substationCoordinate(line.getTerminal2().getVoltageLevel());
        return start.isPresent() && end.isPresent() ? Optional.of(List.of(start.get(), end.get())) : Optional.empty();
    }

    /**
     * A tie line half's: from its substation to the middle of the tie line's straight route. Empty for an unpaired
     * boundary line, which has no other end.
     */
    public static Optional<List<Coordinate>> straightRoute(BoundaryLine boundaryLine) {
        TieLine tieLine = boundaryLine.getTieLine().orElse(null);
        if (tieLine == null) {
            return Optional.empty();
        }
        BoundaryLine other = tieLine.getBoundaryLine1() == boundaryLine ? tieLine.getBoundaryLine2() : tieLine.getBoundaryLine1();
        Optional<Coordinate> start = substationCoordinate(boundaryLine.getTerminal().getVoltageLevel());
        Optional<Coordinate> end = substationCoordinate(other.getTerminal().getVoltageLevel());
        if (start.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }
        Coordinate middle = new Coordinate((start.get().getLatitude() + end.get().getLatitude()) / 2,
                (start.get().getLongitude() + end.get().getLongitude()) / 2);
        return Optional.of(List.of(start.get(), middle));
    }

    /**
     * {@link #straightRoute(Line)} or {@link #straightRoute(BoundaryLine)}, empty for anything else.
     */
    public static Optional<List<Coordinate>> straightRoute(Identifiable<?> lineOrBoundaryLine) {
        return switch (lineOrBoundaryLine) {
            case Line line -> straightRoute(line);
            case BoundaryLine boundaryLine -> straightRoute(boundaryLine);
            default -> Optional.empty();
        };
    }

    /**
     * Moves {@code substation} to {@code coordinate}, along with the route ends of its lines and boundary lines that
     * were at its previous position.
     */
    public static void setSubstationPosition(Substation substation, Coordinate coordinate) {
        Coordinate previous = substationCoordinate(substation).orElse(null);
        substation.removeExtension(SubstationPosition.class);
        substation.newExtension(SubstationPositionAdder.class).withCoordinate(coordinate).add();
        if (previous == null || previous.equals(coordinate)) {
            return;
        }
        substation.getVoltageLevelStream()
                .flatMap(voltageLevel -> Stream.concat(voltageLevel.getConnectableStream(Line.class),
                        voltageLevel.getConnectableStream(BoundaryLine.class)))
                .distinct()
                .forEach(line -> {
                    List<Coordinate> route = new ArrayList<>(route(line));
                    int last = route.size() - 1;
                    boolean moved = false;
                    if (last >= 0 && route.getFirst().equals(previous)) {
                        route.set(0, coordinate);
                        moved = true;
                    }
                    if (last > 0 && route.get(last).equals(previous)) {
                        route.set(last, coordinate);
                        moved = true;
                    }
                    if (moved) {
                        setRoute(line, route);
                    }
                });
    }

    public static void removeSubstationPosition(Substation substation) {
        substation.removeExtension(SubstationPosition.class);
    }

    /**
     * Replaces the route of a line or boundary line, removed if {@code coordinates} is empty.
     */
    public static void setRoute(Identifiable<?> lineOrBoundaryLine, List<Coordinate> coordinates) {
        switch (lineOrBoundaryLine) {
            case Line line -> replaceRoute(line, coordinates);
            case BoundaryLine boundaryLine -> replaceRoute(boundaryLine, coordinates);
            default -> throw new IllegalArgumentException("No route on " + lineOrBoundaryLine.getId());
        }
    }

    @SuppressWarnings("unchecked")
    private static <I extends Identifiable<I>> void replaceRoute(I lineOrBoundaryLine, List<Coordinate> coordinates) {
        lineOrBoundaryLine.removeExtension(LinePosition.class);
        if (!coordinates.isEmpty()) {
            lineOrBoundaryLine.newExtension(LinePositionAdder.class).withCoordinates(List.copyOf(coordinates)).add();
        }
    }

    /**
     * How many lines and tie lines {@link #createStraightRoutes} gives a route to.
     */
    public static int unroutedCount(Network network) {
        return countLines(missingRoutes(network).keySet());
    }

    /**
     * Gives a straight route to every line, and tie line half, without route whose ends have positions.
     *
     * @return how many lines and tie lines got one
     */
    public static int createStraightRoutes(Network network) {
        Map<Identifiable<?>, List<Coordinate>> routes = missingRoutes(network);
        routes.forEach(PositionEdits::setRoute);
        return countLines(routes.keySet());
    }

    private static Map<Identifiable<?>, List<Coordinate>> missingRoutes(Network network) {
        Map<Identifiable<?>, List<Coordinate>> routes = new LinkedHashMap<>();
        network.getLineStream()
                .filter(line -> line.getExtension(LinePosition.class) == null)
                .forEach(line -> straightRoute(line).ifPresent(route -> routes.put(line, route)));
        network.getBoundaryLineStream()
                .filter(boundaryLine -> boundaryLine.getExtension(LinePosition.class) == null)
                .forEach(boundaryLine -> straightRoute(boundaryLine).ifPresent(route -> routes.put(boundaryLine, route)));
        return routes;
    }

    // a tie line's two halves count once
    private static int countLines(Set<Identifiable<?>> routed) {
        Set<Identifiable<?>> lines = new HashSet<>();
        routed.forEach(identifiable -> lines.add(identifiable instanceof BoundaryLine boundaryLine
                ? boundaryLine.getTieLine().<Identifiable<?>>map(tieLine -> tieLine).orElse(boundaryLine) : identifiable));
        return lines.size();
    }
}
