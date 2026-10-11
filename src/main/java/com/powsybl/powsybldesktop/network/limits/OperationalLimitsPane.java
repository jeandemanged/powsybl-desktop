/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.limits;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.BoundaryLine;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.DetectionKind;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.LimitType;
import com.powsybl.iidm.network.LoadingLimits;
import com.powsybl.iidm.network.OperationalLimitsGroup;
import com.powsybl.iidm.network.Terminal;
import com.powsybl.iidm.network.ThreeSides;
import com.powsybl.iidm.network.ThreeWindingsTransformer;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.NetworkViolations;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Read-only detail of an equipment's flow limits: for each side, every selected operational limits group, and in each
 * group its current, active power and apparent power limits, with the side's present value. Groups and limits crossed
 * by the given violations are highlighted. Supports lines, tie lines, two- and three-winding transformers and boundary
 * lines; meant to grow into the limits view and editor.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class OperationalLimitsPane extends VBox {

    private static final List<LimitType> FLOW_LIMIT_TYPES = List.of(LimitType.CURRENT, LimitType.ACTIVE_POWER, LimitType.APPARENT_POWER);
    private static final double CELL_SIZE = 24;

    private final GuiParameters guiParameters;

    private record Side(ThreeSides side, Terminal terminal, List<OperationalLimitsGroup> selectedGroups) {
    }

    /**
     * A permanent (null acceptable duration) or temporary limit.
     */
    record LimitRow(String name, double value, Integer acceptableDuration, boolean fictitious) {
    }

    public OperationalLimitsPane(GuiParameters guiParameters) {
        super(8);
        this.guiParameters = Objects.requireNonNull(guiParameters);
    }

    public void setEquipment(Identifiable<?> equipment, List<FlowViolation> violations) {
        getChildren().clear();
        List<Side> sides = sides(equipment);
        if (sides.isEmpty()) {
            getChildren().add(new Label(Messages.get("limits.noLimits")));
            return;
        }
        for (Side side : sides) {
            Label sideTitle = new Label(Messages.get("limits.side", side.side().getNum()));
            sideTitle.getStyleClass().add("limits-side-title");
            getChildren().add(sideTitle);
            if (side.selectedGroups().isEmpty()) {
                getChildren().add(new Label(Messages.get("limits.noSelectedGroup")));
            }
            for (OperationalLimitsGroup group : side.selectedGroups()) {
                List<LimitViolation> groupViolations = violations.stream()
                        .map(FlowViolation::violation)
                        .filter(v -> v.getSide() == side.side() && group.getId().equals(v.getOperationalLimitsGroupId()))
                        .toList();
                getChildren().add(groupNode(side, group, groupViolations));
            }
        }
    }

    private static List<Side> sides(Identifiable<?> equipment) {
        return switch (equipment) {
            case Branch<?> branch -> Arrays.stream(TwoSides.values())
                    .map(s -> new Side(s.toThreeSides(), branch.getTerminal(s), branch.getAllSelectedOperationalLimitsGroups(s)))
                    .toList();
            case ThreeWindingsTransformer transformer -> Arrays.stream(ThreeSides.values())
                    .map(s -> new Side(s, transformer.getTerminal(s), transformer.getLeg(s).getAllSelectedOperationalLimitsGroups()))
                    .toList();
            case BoundaryLine boundaryLine -> List.of(new Side(ThreeSides.ONE, boundaryLine.getTerminal(), boundaryLine.getAllSelectedOperationalLimitsGroups()));
            default -> List.of();
        };
    }

    private VBox groupNode(Side side, OperationalLimitsGroup group, List<LimitViolation> groupViolations) {
        Label title = new Label(Messages.get("limits.group", group.getId()));
        title.getStyleClass().add("limits-group-title");
        VBox box = new VBox(6, title);
        box.getStyleClass().add("limits-group");
        if (!groupViolations.isEmpty()) {
            box.getStyleClass().add("limits-group-violated");
        }
        boolean empty = true;
        for (LimitType type : FLOW_LIMIT_TYPES) {
            Optional<? extends LoadingLimits> limits = group.getLoadingLimits(type);
            if (limits.isPresent()) {
                empty = false;
                List<LimitViolation> typeViolations = groupViolations.stream()
                        .filter(v -> v.getLimitType().name().equals(type.name()))
                        .toList();
                box.getChildren().addAll(limitsHeader(side, type), limitsTable(limits.get(), quantity(type), typeViolations));
            }
        }
        if (empty) {
            box.getChildren().add(new Label(Messages.get("limits.noLimits")));
        }
        return box;
    }

    private Label limitsHeader(Side side, LimitType type) {
        double value = NetworkViolations.flowValue(side.terminal(), type);
        return new Label(Messages.get("limits.header", Messages.get("desktop.common.limitViolationType." + type.name()),
                Double.isNaN(value) ? "-" : guiParameters.format(quantity(type), value)));
    }

    private static GuiParameters.Quantity quantity(LimitType type) {
        return type == LimitType.CURRENT ? GuiParameters.Quantity.CURRENT : GuiParameters.Quantity.POWER;
    }

    private TableView<LimitRow> limitsTable(LoadingLimits limits, GuiParameters.Quantity quantity, List<LimitViolation> violations) {
        List<LimitRow> rows = new ArrayList<>();
        // a LOW detection kind limit set has no permanent limit: getPermanentLimit() throws
        if (limits.getDetectionKind() == DetectionKind.HIGH) {
            rows.add(new LimitRow(limits.getPermanentLimitName(), limits.getPermanentLimit(), null, false));
        }
        limits.getTemporaryLimits().forEach(tl -> rows.add(new LimitRow(tl.getName(), tl.getValue(), tl.getAcceptableDuration(), tl.isFictitious())));

        TableView<LimitRow> table = new TableView<>();
        table.setEditable(false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(column("limits.column.name", LimitRow::name));
        table.getColumns().add(column("limits.column.value", row -> limitText(row.value(), quantity)));
        // as a string: MessageFormat would group the digits, e.g. "2,400 s"
        table.getColumns().add(column("limits.column.acceptableDuration", row -> row.acceptableDuration() == null
                ? Messages.get("limits.permanent") : Messages.get("limits.duration", String.valueOf(row.acceptableDuration()))));
        table.getColumns().add(column("limits.column.fictitious", row -> row.fictitious() ? Messages.get("limits.fictitious") : ""));
        table.getItems().setAll(rows);
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(LimitRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("limit-crossed");
                if (!empty && row != null && isCrossed(row, violations)) {
                    getStyleClass().add("limit-crossed");
                }
            }
        });
        table.setFixedCellSize(CELL_SIZE);
        table.setPrefHeight((rows.size() + 1.5) * CELL_SIZE);
        table.setMinHeight(table.getPrefHeight());
        return table;
    }

    // Double.MAX_VALUE is the conventional value of a last, never acceptable temporary limit
    private String limitText(double value, GuiParameters.Quantity quantity) {
        if (Double.isNaN(value)) {
            return "-";
        }
        return value == Double.MAX_VALUE ? "∞" : guiParameters.format(quantity, value);
    }

    // a violation's limit is the crossed one's original value, the permanent or a temporary limit
    static boolean isCrossed(LimitRow row, List<LimitViolation> violations) {
        return violations.stream().anyMatch(v -> v.getLimit() == row.value() && Objects.equals(v.getLimitName(), row.name()));
    }

    private static TableColumn<LimitRow, String> column(String key, Function<LimitRow, String> getter) {
        TableColumn<LimitRow, String> column = new TableColumn<>(Messages.get(key));
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        return column;
    }
}
