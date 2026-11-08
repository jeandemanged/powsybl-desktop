/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.iidm.modification.topology.ConnectVoltageLevelOnLineBuilder;
import com.powsybl.iidm.modification.topology.CreateLineOnLineBuilder;
import com.powsybl.iidm.modification.topology.ReplaceTeePointByVoltageLevelOnLineBuilder;
import com.powsybl.iidm.modification.topology.RevertConnectVoltageLevelOnLineBuilder;
import com.powsybl.iidm.modification.topology.RevertCreateLineOnLineBuilder;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.LineAdder;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The powsybl-iidm-modification topology modifications of lines: tapping a line or connecting a voltage level on it,
 * reverting them, replacing a tee point; and the lines and voltage levels proposed for them.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class LineModifications {

    private LineModifications() {
    }

    /**
     * A tap of a line at {@code positionPercent} of its length: a tee point voltage level {@code teePointId} (in a
     * fictitious substation {@code <teePointId>_S} if {@code fictitiousSubstation}) cutting it in lines
     * {@code line1Id} and {@code line2Id}, and a new line {@code newLineId} from the tee point.
     */
    public record TapSpec(double positionPercent, String teePointId, boolean fictitiousSubstation, String line1Id, String line2Id,
                          String newLineId, double r, double x) {
    }

    /**
     * Taps {@code line}, the new line going to {@code tapped}.
     */
    public static void tapLine(Network network, Line line, TapSpec spec, Connection tapped) {
        LineAdder newLine = network.newLine().setId(spec.newLineId()).setR(spec.r()).setX(spec.x()).setG1(0).setB1(0).setG2(0).setB2(0);
        CreateLineOnLineBuilder builder = new CreateLineOnLineBuilder()
                .withLine(line)
                .withLineAdder(newLine)
                .withPositionPercent(spec.positionPercent())
                .withBusbarSectionOrBusId(tapped.busOrBusbarSectionId())
                .withFictitiousVoltageLevelId(spec.teePointId())
                .withCreateFictitiousSubstation(spec.fictitiousSubstation())
                .withFictitiousSubstationId(spec.teePointId() + "_S")
                .withLine1Id(spec.line1Id())
                .withLine2Id(spec.line2Id());
        FeederOrders.reserving(orders -> {
            if (tapped.isNodeBreaker()) {
                builder.withPositionForNewLine(tapped.reserve(orders, 1).order());
            }
            orders.apply(network, builder.build());
        });
    }

    /**
     * Cuts {@code line} at {@code positionPercent} of its length in lines {@code line1Id} and {@code line2Id}, both
     * connected at {@code connection}.
     */
    public static void connectVoltageLevelOnLine(Network network, Line line, double positionPercent, String line1Id, String line2Id,
                                                 Connection connection) {
        ConnectVoltageLevelOnLineBuilder builder = new ConnectVoltageLevelOnLineBuilder()
                .withLine(line)
                .withPositionPercent(positionPercent)
                .withBusbarSectionOrBusId(connection.busOrBusbarSectionId())
                .withLine1Id(line1Id)
                .withLine2Id(line2Id);
        FeederOrders.reserving(orders -> {
            if (connection.isNodeBreaker()) {
                int order = connection.reserve(orders, 2).order();
                builder.withPositionForNewLine1(order).withPositionForNewLine2(order + 1);
            }
            orders.apply(network, builder.build());
        });
    }

    /**
     * Merges {@code merged1} and {@code merged2} into line {@code mergedId}, removing the tapping line {@code removed}
     * and their tee point.
     *
     * @param mergedName none if null
     */
    public static void revertTapLine(Network network, Line merged1, Line merged2, Line removed, String mergedId, String mergedName) {
        RevertCreateLineOnLineBuilder builder = new RevertCreateLineOnLineBuilder()
                .withLineToBeMerged1Id(merged1.getId())
                .withLineToBeMerged2Id(merged2.getId())
                .withLineToBeDeletedId(removed.getId())
                .withMergedLineId(mergedId);
        if (mergedName != null) {
            builder.withMergedLineName(mergedName);
        }
        NetworkEdits.apply(network, builder.build());
    }

    /**
     * Merges {@code line1} and {@code line2} into line {@code mergedId}, removing their common voltage level if left empty.
     *
     * @param mergedName none if null
     */
    public static void revertConnectVoltageLevelOnLine(Network network, Line line1, Line line2, String mergedId, String mergedName) {
        RevertConnectVoltageLevelOnLineBuilder builder = new RevertConnectVoltageLevelOnLineBuilder()
                .withLine1Id(line1.getId())
                .withLine2Id(line2.getId())
                .withLineId(mergedId);
        if (mergedName != null) {
            builder.withLineName(mergedName);
        }
        NetworkEdits.apply(network, builder.build());
    }

    /**
     * Reconnects the two lines on each side of a tee point to {@code busOrBusbarSectionId}, as lines {@code newLine1Id}
     * and {@code newLine2Id}, removing the tapping line {@code removed} and the tee point.
     */
    public static void replaceTeePoint(Network network, Line teePointLine1, Line teePointLine2, Line removed, String busOrBusbarSectionId,
                                       String newLine1Id, String newLine2Id) {
        NetworkEdits.apply(network, new ReplaceTeePointByVoltageLevelOnLineBuilder()
                .withTeePointLine1(teePointLine1.getId())
                .withTeePointLine2(teePointLine2.getId())
                .withTeePointLineToRemove(removed.getId())
                .withBbsOrBusId(busOrBusbarSectionId)
                .withNewLine1Id(newLine1Id)
                .withNewLine2Id(newLine2Id)
                .build());
    }

    /**
     * A voltage level not at an end of {@code line}, to connect to it, or else its first end's.
     */
    public static VoltageLevel otherThanLineEnds(Network network, Line line) {
        return network.getVoltageLevelStream()
                .filter(voltageLevel -> voltageLevel != line.getTerminal1().getVoltageLevel() && voltageLevel != line.getTerminal2().getVoltageLevel())
                .findFirst().orElse(line.getTerminal1().getVoltageLevel());
    }

    /**
     * The lines of the tee point at an end of {@code line}, if it has exactly three: {@code line} itself first.
     */
    public static List<Line> teePointLines(Line line) {
        if (line == null) {
            return List.of();
        }
        for (VoltageLevel voltageLevel : List.of(line.getTerminal1().getVoltageLevel(), line.getTerminal2().getVoltageLevel())) {
            List<Line> lines = voltageLevel.getConnectableStream(Line.class).toList();
            if (lines.size() == 3) {
                List<Line> ordered = new ArrayList<>(lines);
                ordered.remove(line);
                ordered.addFirst(line);
                return ordered;
            }
        }
        return List.of();
    }

    /**
     * The first of {@code lines} other than {@code line} sharing a voltage level with it, null if none.
     */
    public static Line sharingAVoltageLevel(List<Line> lines, Line line) {
        if (line == null) {
            return null;
        }
        return lines.stream()
                .filter(other -> other != line && other.getTerminals().stream().anyMatch(terminal -> line.getTerminals().stream()
                        .anyMatch(own -> own.getVoltageLevel() == terminal.getVoltageLevel())))
                .findFirst().orElse(null);
    }
}
