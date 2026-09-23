/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.map;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.Substation;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.Coordinate;
import com.powsybl.iidm.network.extensions.LinePositionAdder;
import com.powsybl.iidm.network.extensions.SubstationPositionAdder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Synthetic networks for stress-testing the Map view, made of a grid of substations linked to their neighbours.
 * Every line has {@value #LINE_BREAKS} breaks, zigzagging between its substations.
 * <ul>
 *     <li>{@link #create()}: 100 x 100 substations over mainland France, every neighbour linked.</li>
 *     <li>{@link #createEurope()}: 200 x 250 substations over Europe, all horizontal neighbours and a random subset of
 *     vertical ones linked, for {@value #EUROPE_LINE_COUNT} lines.</li>
 * </ul>
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class MapTestNetworkFactory {

    private static final Grid FRANCE = new Grid(100, 100, 42.5, 51.0, -4.5, 8.0);
    private static final Grid EUROPE = new Grid(200, 250, 36.0, 71.0, -10.0, 40.0);
    private static final int EUROPE_LINE_COUNT = 70_000;
    private static final int LINE_BREAKS = 10;
    /** Zigzag amplitude of the line breaks, as a fraction of the line length. */
    private static final double LINE_ZIGZAG = 0.1;

    private record Grid(int rows, int cols, double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
        Coordinate coordinate(int row, int col) {
            double latitude = maxLatitude - row * (maxLatitude - minLatitude) / (rows - 1);
            double longitude = minLongitude + col * (maxLongitude - minLongitude) / (cols - 1);
            return new Coordinate(latitude, longitude);
        }
    }

    private MapTestNetworkFactory() {
    }

    public static Network create() {
        Network network = createSubstations("mapTest10k", FRANCE);
        for (int row = 0; row < FRANCE.rows(); row++) {
            for (int col = 0; col < FRANCE.cols(); col++) {
                if (col + 1 < FRANCE.cols()) {
                    addLine(network, FRANCE, row, col, row, col + 1, LINE_BREAKS);
                }
                if (row + 1 < FRANCE.rows()) {
                    addLine(network, FRANCE, row, col, row + 1, col, LINE_BREAKS);
                }
            }
        }
        return network;
    }

    public static Network createEurope() {
        Network network = createSubstations("mapTest50k", EUROPE);
        List<int[]> verticalCandidates = new ArrayList<>();
        for (int row = 0; row < EUROPE.rows(); row++) {
            for (int col = 0; col < EUROPE.cols(); col++) {
                if (col + 1 < EUROPE.cols()) {
                    addLine(network, EUROPE, row, col, row, col + 1, LINE_BREAKS);
                }
                if (row + 1 < EUROPE.rows()) {
                    verticalCandidates.add(new int[] {row, col});
                }
            }
        }
        // fixed seed so the network is the same on every load
        Collections.shuffle(verticalCandidates, new Random(0));
        int verticalCount = EUROPE_LINE_COUNT - EUROPE.rows() * (EUROPE.cols() - 1);
        for (int[] cell : verticalCandidates.subList(0, verticalCount)) {
            addLine(network, EUROPE, cell[0], cell[1], cell[0] + 1, cell[1], LINE_BREAKS);
        }
        return network;
    }

    private static Network createSubstations(String id, Grid grid) {
        Network network = Network.create(id, "code");
        for (int row = 0; row < grid.rows(); row++) {
            for (int col = 0; col < grid.cols(); col++) {
                Substation substation = network.newSubstation()
                        .setId(substationId(row, col))
                        .add();
                substation.newExtension(SubstationPositionAdder.class)
                        .withCoordinate(grid.coordinate(row, col))
                        .add();
                VoltageLevel voltageLevel = substation.newVoltageLevel()
                        .setId(voltageLevelId(row, col))
                        .setNominalV(400)
                        .setTopologyKind(TopologyKind.BUS_BREAKER)
                        .add();
                voltageLevel.getBusBreakerView().newBus()
                        .setId(busId(row, col))
                        .add();
            }
        }
        return network;
    }

    private static void addLine(Network network, Grid grid, int row1, int col1, int row2, int col2, int breaks) {
        Line line = network.newLine()
                .setId("L_" + row1 + "_" + col1 + "_" + row2 + "_" + col2)
                .setVoltageLevel1(voltageLevelId(row1, col1))
                .setBus1(busId(row1, col1))
                .setVoltageLevel2(voltageLevelId(row2, col2))
                .setBus2(busId(row2, col2))
                .setR(1)
                .setX(10)
                .add();
        Coordinate from = grid.coordinate(row1, col1);
        Coordinate to = grid.coordinate(row2, col2);
        double deltaLatitude = to.getLatitude() - from.getLatitude();
        double deltaLongitude = to.getLongitude() - from.getLongitude();
        List<Coordinate> coordinates = new ArrayList<>(breaks + 2);
        coordinates.add(from);
        for (int i = 1; i <= breaks; i++) {
            double t = (double) i / (breaks + 1);
            double offset = i % 2 == 0 ? LINE_ZIGZAG : -LINE_ZIGZAG;
            coordinates.add(new Coordinate(from.getLatitude() + t * deltaLatitude - offset * deltaLongitude,
                    from.getLongitude() + t * deltaLongitude + offset * deltaLatitude));
        }
        coordinates.add(to);
        line.newExtension(LinePositionAdder.class)
                .withCoordinates(coordinates)
                .add();
    }

    private static String substationId(int row, int col) {
        return "S_" + row + "_" + col;
    }

    private static String voltageLevelId(int row, int col) {
        return "VL_" + row + "_" + col;
    }

    private static String busId(int row, int col) {
        return "B_" + row + "_" + col;
    }
}
