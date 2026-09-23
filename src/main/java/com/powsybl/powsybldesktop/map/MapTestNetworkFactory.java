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

import java.util.List;

/**
 * Synthetic network for stress-testing the Map view: a {@value #SIZE} x {@value #SIZE} grid of substations
 * spread over mainland France, each linked to its right and bottom neighbours by a line.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class MapTestNetworkFactory {

    private static final int SIZE = 100;
    private static final double MIN_LATITUDE = 42.5;
    private static final double MAX_LATITUDE = 51.0;
    private static final double MIN_LONGITUDE = -4.5;
    private static final double MAX_LONGITUDE = 8.0;

    private MapTestNetworkFactory() {
    }

    public static Network create() {
        Network network = Network.create("mapTest", "code");
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                Substation substation = network.newSubstation()
                        .setId(substationId(row, col))
                        .add();
                substation.newExtension(SubstationPositionAdder.class)
                        .withCoordinate(coordinate(row, col))
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
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (col + 1 < SIZE) {
                    addLine(network, row, col, row, col + 1);
                }
                if (row + 1 < SIZE) {
                    addLine(network, row, col, row + 1, col);
                }
            }
        }
        return network;
    }

    private static void addLine(Network network, int row1, int col1, int row2, int col2) {
        Line line = network.newLine()
                .setId("L_" + row1 + "_" + col1 + "_" + row2 + "_" + col2)
                .setVoltageLevel1(voltageLevelId(row1, col1))
                .setBus1(busId(row1, col1))
                .setVoltageLevel2(voltageLevelId(row2, col2))
                .setBus2(busId(row2, col2))
                .setR(1)
                .setX(10)
                .add();
        line.newExtension(LinePositionAdder.class)
                .withCoordinates(List.of(coordinate(row1, col1), coordinate(row2, col2)))
                .add();
    }

    private static Coordinate coordinate(int row, int col) {
        double latitude = MAX_LATITUDE - row * (MAX_LATITUDE - MIN_LATITUDE) / (SIZE - 1);
        double longitude = MIN_LONGITUDE + col * (MAX_LONGITUDE - MIN_LONGITUDE) / (SIZE - 1);
        return new Coordinate(latitude, longitude);
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
