/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.edit;

import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.iidm.network.extensions.ConnectablePosition;
import com.powsybl.powsybldesktop.core.NetworkChanges;
import com.powsybl.powsybldesktop.network.edit.Connection;
import com.powsybl.powsybldesktop.network.edit.Kinds;
import com.powsybl.powsybldesktop.network.edit.LineModifications;
import com.powsybl.powsybldesktop.network.edit.NetworkEdits;
import com.powsybl.powsybldesktop.ui.form.CreationForm;
import com.powsybl.powsybldesktop.ui.form.DetailForm;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.stage.Window;

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
                LineModifications.otherThanLineEnds(network, line), null, ConnectablePosition.Direction.TOP);
        return CreateDialog.show(owner, Messages.get("edit.line.tapTitle", Kinds.describe(line)), form, () -> {
            LineModifications.TapSpec spec = new LineModifications.TapSpec(percent.get(), teePointId.get(), substation.get(), line1Id.get(),
                    line2Id.get(), newLineId.get(), r.get(), x.get());
            Connection at = tapped.connection();
            return NetworkChanges.apply(sceneModel.getMainModel(), network, n -> LineModifications.tapLine(n, line, spec, at));
        });
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
                LineModifications.otherThanLineEnds(network, line), null, ConnectablePosition.Direction.TOP);
        return CreateDialog.show(owner, Messages.get("edit.line.connectTitle", Kinds.describe(line)), form, () -> {
            double position = percent.get();
            String id1 = line1Id.get();
            String id2 = line2Id.get();
            Connection at = connection.connection();
            return NetworkChanges.apply(sceneModel.getMainModel(), network, n -> LineModifications.connectVoltageLevelOnLine(n, line, position, id1, id2, at));
        });
    }

    /**
     * Reverts a tap: merges the two lines on each side of a tee point into one, removing the tapping line and the tee
     * point voltage level.
     */
    public static Optional<Boolean> revertTapLine(SceneModel sceneModel, Window owner, Network network, Line line) {
        List<Line> lines = lines(network);
        List<Line> atTeePoint = LineModifications.teePointLines(line);
        CreationForm form = new CreationForm();
        CreationForm.Field<Line> merged1 = form.choice("edit.line.merged1", lines, CreationForm.identifiableFormat(), at(atTeePoint, 0, line), true);
        CreationForm.Field<Line> merged2 = form.choice("edit.line.merged2", lines, CreationForm.identifiableFormat(), at(atTeePoint, 1, null), true);
        CreationForm.Field<Line> removed = form.choice("edit.line.removedLine", lines, CreationForm.identifiableFormat(), at(atTeePoint, 2, null), true);
        CreationForm.Field<String> mergedId = form.id(network, NetworkEdits.uniqueId(network, "L"));
        CreationForm.Field<String> mergedName = form.text("desktop.common.column.name", null, false);
        return CreateDialog.show(owner, Messages.get("edit.line.revertTapTitle"), form, () -> {
            Line line1 = merged1.get();
            Line line2 = merged2.get();
            Line tapping = removed.get();
            String id = mergedId.get();
            String name = mergedName.get();
            return NetworkChanges.apply(sceneModel.getMainModel(), network, n -> LineModifications.revertTapLine(n, line1, line2, tapping, id, name));
        });
    }

    /**
     * Reverts a connection on a line: merges two lines into one, removing their common voltage level if left empty.
     */
    public static Optional<Boolean> revertConnectVoltageLevelOnLine(SceneModel sceneModel, Window owner, Network network, Line line) {
        List<Line> lines = lines(network);
        CreationForm form = new CreationForm();
        CreationForm.Field<Line> line1 = form.choice("edit.line.merged1", lines, CreationForm.identifiableFormat(), line, true);
        CreationForm.Field<Line> line2 = form.choice("edit.line.merged2", lines, CreationForm.identifiableFormat(), LineModifications.sharingAVoltageLevel(lines, line), true);
        CreationForm.Field<String> mergedId = form.id(network, NetworkEdits.uniqueId(network, "L"));
        CreationForm.Field<String> mergedName = form.text("desktop.common.column.name", null, false);
        return CreateDialog.show(owner, Messages.get("edit.line.revertConnectTitle"), form, () -> {
            Line merged1 = line1.get();
            Line merged2 = line2.get();
            String id = mergedId.get();
            String name = mergedName.get();
            return NetworkChanges.apply(sceneModel.getMainModel(), network, n -> LineModifications.revertConnectVoltageLevelOnLine(n, merged1, merged2, id, name));
        });
    }

    /**
     * Replaces a tee point by a busbar section or bus of an existing voltage level: the two lines on each side of the
     * tee point reconnected to it, the tapping line and the tee point removed.
     */
    public static Optional<Boolean> replaceTeePoint(SceneModel sceneModel, Window owner, Network network, Line line) {
        List<Line> lines = lines(network);
        List<Line> atTeePoint = LineModifications.teePointLines(line);
        CreationForm form = new CreationForm();
        CreationForm.Field<Line> teePointLine1 = form.choice("edit.line.merged1", lines, CreationForm.identifiableFormat(), at(atTeePoint, 0, line), true);
        CreationForm.Field<Line> teePointLine2 = form.choice("edit.line.merged2", lines, CreationForm.identifiableFormat(), at(atTeePoint, 1, null), true);
        CreationForm.Field<Line> removed = form.choice("edit.line.removedLine", lines, CreationForm.identifiableFormat(), at(atTeePoint, 2, null), true);
        CreationForm.Field<String> newLine1Id = form.text("edit.line.line1Id", NetworkEdits.uniqueId(network, "L"), true);
        CreationForm.Field<String> newLine2Id = form.text("edit.line.line2Id", NetworkEdits.uniqueId(network, "L") + "_2", true);
        ConnectionSection connection = new ConnectionSection(form, Messages.get("detail.section.connection"), voltageLevels(network),
                voltageLevels(network).stream().findFirst().orElse(null), null, ConnectablePosition.Direction.TOP);
        return CreateDialog.show(owner, Messages.get("edit.line.replaceTeePointTitle"), form, () -> {
            Line line1 = teePointLine1.get();
            Line line2 = teePointLine2.get();
            Line tapping = removed.get();
            String busOrBusbarSectionId = connection.busOrBusbarSectionId();
            String id1 = newLine1Id.get();
            String id2 = newLine2Id.get();
            return NetworkChanges.apply(sceneModel.getMainModel(), network,
                    n -> LineModifications.replaceTeePoint(n, line1, line2, tapping, busOrBusbarSectionId, id1, id2));
        });
    }

    private static Line at(List<Line> lines, int index, Line otherwise) {
        return index < lines.size() ? lines.get(index) : otherwise;
    }
}
