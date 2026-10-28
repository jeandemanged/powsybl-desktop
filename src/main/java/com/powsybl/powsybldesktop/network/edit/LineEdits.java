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
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.network.detail.DetailForm;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Dialogs cutting a line: tapping it with a tee point to an existing voltage level (a new line from a fictitious voltage
 * level on it), or connecting a voltage level on it (the line cut in two lines to it); their reverts, and replacing a
 * tee point by a voltage level.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class LineEdits {

    private static final double MIDDLE_PERCENT = 50;

    private LineEdits() {
    }

    private static List<Line> lines(Network network) {
        return network.getLineStream().sorted(Comparator.comparing(Line::getNameOrId)).toList();
    }

    private static List<VoltageLevel> voltageLevels(Network network) {
        return network.getVoltageLevelStream().toList();
    }

    /**
     * Taps {@code line} at a fictitious tee point voltage level, cutting it in two lines, with a new line from the tee
     * point to a busbar section or bus of an existing voltage level.
     */
    public static Optional<Boolean> tapLine(SceneModel sceneModel, Window owner, Line line) {
        Network network = line.getParentNetwork();
        CreationForm form = new CreationForm();
        CreationForm.Field<Double> percent = form.number("edit.line.positionPercent", DetailForm.PERCENT, MIDDLE_PERCENT, true);
        CreationForm.Field<String> teePointId = form.id(network, line.getId() + "_VL", "edit.line.teePointId");
        CreationForm.Field<Boolean> substation = form.check("edit.line.fictitiousSubstation", true);
        CreationForm.Field<String> line1Id = form.text("edit.line.line1Id", line.getId() + "_1", true);
        CreationForm.Field<String> line2Id = form.text("edit.line.line2Id", line.getId() + "_2", true);
        form.section("edit.line.newLine");
        CreationForm.Field<String> newLineId = form.text("desktop.common.column.id", NetworkEdits.uniqueId(network, line.getId() + "_TAP"), true);
        CreationForm.Field<Double> r = form.number("desktop.common.column.r", DetailForm.OHM, 0, true);
        CreationForm.Field<Double> x = form.number("desktop.common.column.x", DetailForm.OHM, 1, true);
        ConnectionSection tapped = new ConnectionSection(form, Messages.get("edit.line.tappedVoltageLevel"), voltageLevels(network),
                otherThanLineEnds(network, line), null, ConnectablePosition.Direction.TOP);
        return CreateDialog.show(owner, Messages.get("edit.line.tapTitle", Kinds.describe(line)), form, () -> {
            LineAdder newLine = network.newLine().setId(newLineId.get()).setR(r.get()).setX(x.get()).setG1(0).setB1(0).setG2(0).setB2(0);
            CreateLineOnLineBuilder builder = new CreateLineOnLineBuilder()
                    .withLine(line)
                    .withLineAdder(newLine)
                    .withPositionPercent(percent.get())
                    .withBusbarSectionOrBusId(tapped.busOrBusbarSectionId())
                    .withFictitiousVoltageLevelId(teePointId.get())
                    .withCreateFictitiousSubstation(substation.get())
                    .withFictitiousSubstationId(teePointId.get() + "_S")
                    .withLine1Id(line1Id.get())
                    .withLine2Id(line2Id.get());
            return NetworkEdits.apply(sceneModel.getMainModel(), network, n -> FeederOrders.reserving(orders -> {
                if (tapped.isNodeBreaker()) {
                    builder.withPositionForNewLine(tapped.reserve(orders, 1).order());
                }
                NetworkEdits.apply(n, builder.build());
            }));
        });
    }

    // a voltage level not at an end of the line, to connect to it
    private static VoltageLevel otherThanLineEnds(Network network, Line line) {
        return network.getVoltageLevelStream()
                .filter(voltageLevel -> voltageLevel != line.getTerminal1().getVoltageLevel() && voltageLevel != line.getTerminal2().getVoltageLevel())
                .findFirst().orElse(line.getTerminal1().getVoltageLevel());
    }

    /**
     * Cuts {@code line} in two lines connected to a busbar section or bus of an existing voltage level.
     */
    public static Optional<Boolean> connectVoltageLevelOnLine(SceneModel sceneModel, Window owner, Line line) {
        Network network = line.getParentNetwork();
        CreationForm form = new CreationForm();
        CreationForm.Field<Double> percent = form.number("edit.line.positionPercent", DetailForm.PERCENT, MIDDLE_PERCENT, true);
        CreationForm.Field<String> line1Id = form.text("edit.line.line1Id", line.getId() + "_1", true);
        CreationForm.Field<String> line2Id = form.text("edit.line.line2Id", line.getId() + "_2", true);
        ConnectionSection connection = new ConnectionSection(form, Messages.get("detail.section.connection"), voltageLevels(network),
                otherThanLineEnds(network, line), null, ConnectablePosition.Direction.TOP);
        return CreateDialog.show(owner, Messages.get("edit.line.connectTitle", Kinds.describe(line)), form, () -> {
            ConnectVoltageLevelOnLineBuilder builder = new ConnectVoltageLevelOnLineBuilder()
                    .withLine(line)
                    .withPositionPercent(percent.get())
                    .withBusbarSectionOrBusId(connection.busOrBusbarSectionId())
                    .withLine1Id(line1Id.get())
                    .withLine2Id(line2Id.get());
            return NetworkEdits.apply(sceneModel.getMainModel(), network, n -> FeederOrders.reserving(orders -> {
                if (connection.isNodeBreaker()) {
                    int order = connection.reserve(orders, 2).order();
                    builder.withPositionForNewLine1(order).withPositionForNewLine2(order + 1);
                }
                NetworkEdits.apply(n, builder.build());
            }));
        });
    }

    /**
     * Reverts a tap: merges the two lines on each side of a tee point into one, removing the tapping line and the tee
     * point voltage level.
     */
    public static Optional<Boolean> revertTapLine(SceneModel sceneModel, Window owner, Network network, Line line) {
        List<Line> lines = lines(network);
        List<Line> atTeePoint = teePointLines(line);
        CreationForm form = new CreationForm();
        CreationForm.Field<Line> merged1 = form.choice("edit.line.merged1", lines, CreationForm.identifiableFormat(), at(atTeePoint, 0, line), true);
        CreationForm.Field<Line> merged2 = form.choice("edit.line.merged2", lines, CreationForm.identifiableFormat(), at(atTeePoint, 1, null), true);
        CreationForm.Field<Line> removed = form.choice("edit.line.removedLine", lines, CreationForm.identifiableFormat(), at(atTeePoint, 2, null), true);
        CreationForm.Field<String> mergedId = form.id(network, NetworkEdits.uniqueId(network, "L"));
        CreationForm.Field<String> mergedName = form.text("desktop.common.column.name", null, false);
        return CreateDialog.show(owner, Messages.get("edit.line.revertTapTitle"), form, () -> {
            RevertCreateLineOnLineBuilder builder = new RevertCreateLineOnLineBuilder()
                    .withLineToBeMerged1Id(merged1.get().getId())
                    .withLineToBeMerged2Id(merged2.get().getId())
                    .withLineToBeDeletedId(removed.get().getId())
                    .withMergedLineId(mergedId.get());
            if (mergedName.get() != null) {
                builder.withMergedLineName(mergedName.get());
            }
            return NetworkEdits.apply(sceneModel.getMainModel(), network, n -> NetworkEdits.apply(n, builder.build()));
        });
    }

    /**
     * Reverts a connection on a line: merges two lines into one, removing their common voltage level if left empty.
     */
    public static Optional<Boolean> revertConnectVoltageLevelOnLine(SceneModel sceneModel, Window owner, Network network, Line line) {
        List<Line> lines = lines(network);
        CreationForm form = new CreationForm();
        CreationForm.Field<Line> line1 = form.choice("edit.line.merged1", lines, CreationForm.identifiableFormat(), line, true);
        CreationForm.Field<Line> line2 = form.choice("edit.line.merged2", lines, CreationForm.identifiableFormat(), sharingAVoltageLevel(lines, line), true);
        CreationForm.Field<String> mergedId = form.id(network, NetworkEdits.uniqueId(network, "L"));
        CreationForm.Field<String> mergedName = form.text("desktop.common.column.name", null, false);
        return CreateDialog.show(owner, Messages.get("edit.line.revertConnectTitle"), form, () -> {
            RevertConnectVoltageLevelOnLineBuilder builder = new RevertConnectVoltageLevelOnLineBuilder()
                    .withLine1Id(line1.get().getId())
                    .withLine2Id(line2.get().getId())
                    .withLineId(mergedId.get());
            if (mergedName.get() != null) {
                builder.withLineName(mergedName.get());
            }
            return NetworkEdits.apply(sceneModel.getMainModel(), network, n -> NetworkEdits.apply(n, builder.build()));
        });
    }

    /**
     * Replaces a tee point by a busbar section or bus of an existing voltage level: the two lines on each side of the
     * tee point reconnected to it, the tapping line and the tee point removed.
     */
    public static Optional<Boolean> replaceTeePoint(SceneModel sceneModel, Window owner, Network network, Line line) {
        List<Line> lines = lines(network);
        List<Line> atTeePoint = teePointLines(line);
        CreationForm form = new CreationForm();
        CreationForm.Field<Line> teePointLine1 = form.choice("edit.line.merged1", lines, CreationForm.identifiableFormat(), at(atTeePoint, 0, line), true);
        CreationForm.Field<Line> teePointLine2 = form.choice("edit.line.merged2", lines, CreationForm.identifiableFormat(), at(atTeePoint, 1, null), true);
        CreationForm.Field<Line> removed = form.choice("edit.line.removedLine", lines, CreationForm.identifiableFormat(), at(atTeePoint, 2, null), true);
        CreationForm.Field<String> newLine1Id = form.text("edit.line.line1Id", NetworkEdits.uniqueId(network, "L"), true);
        CreationForm.Field<String> newLine2Id = form.text("edit.line.line2Id", NetworkEdits.uniqueId(network, "L") + "_2", true);
        ConnectionSection connection = new ConnectionSection(form, Messages.get("detail.section.connection"), voltageLevels(network),
                voltageLevels(network).stream().findFirst().orElse(null), null, ConnectablePosition.Direction.TOP);
        return CreateDialog.show(owner, Messages.get("edit.line.replaceTeePointTitle"), form, () -> NetworkEdits.apply(sceneModel.getMainModel(),
                network, n -> NetworkEdits.apply(n, new ReplaceTeePointByVoltageLevelOnLineBuilder()
                        .withTeePointLine1(teePointLine1.get().getId())
                        .withTeePointLine2(teePointLine2.get().getId())
                        .withTeePointLineToRemove(removed.get().getId())
                        .withBbsOrBusId(connection.busOrBusbarSectionId())
                        .withNewLine1Id(newLine1Id.get())
                        .withNewLine2Id(newLine2Id.get())
                        .build())));
    }

    // the lines of the tee point at an end of line, if it has exactly three: the line itself first
    private static List<Line> teePointLines(Line line) {
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

    private static Line at(List<Line> lines, int index, Line otherwise) {
        return index < lines.size() ? lines.get(index) : otherwise;
    }

    private static Line sharingAVoltageLevel(List<Line> lines, Line line) {
        if (line == null) {
            return null;
        }
        return lines.stream()
                .filter(other -> other != line && other.getTerminals().stream().anyMatch(terminal -> line.getTerminals().stream()
                        .anyMatch(own -> own.getVoltageLevel() == terminal.getVoltageLevel())))
                .findFirst().orElse(null);
    }
}
