/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.contingency.violations.LimitViolation;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VoltageLevel;
import com.powsybl.powsybldesktop.SceneModel;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.Loading;
import com.powsybl.powsybldesktop.violations.VoltageViolation;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Adds the columns of the limit violation tables of the Violations and Security Analysis Results views, all read-only,
 * each identified by its id.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class ViolationColumns<S> {

    private final TableView<S> table;
    private final Supplier<SceneModel> sceneModel;
    private final Supplier<GuiParameters> guiParameters;

    ViolationColumns(TableView<S> table, Supplier<SceneModel> sceneModel, Supplier<GuiParameters> guiParameters) {
        this.table = table;
        this.sceneModel = sceneModel;
        this.guiParameters = guiParameters;
    }

    <T> TableColumn<S, T> add(String id, String headerKey, double prefWidth, String styleClass) {
        TableColumn<S, T> column = new TableColumn<>(Messages.get(headerKey));
        column.setId(id);
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        if (styleClass != null) {
            column.getStyleClass().add(styleClass);
        }
        table.getColumns().add(column);
        return column;
    }

    ViolationColumns<S> text(String id, String headerKey, Function<S, String> getter) {
        TableColumnSupport.configureNullableColumn(add(id, headerKey, 150, null), getter);
        return this;
    }

    ViolationColumns<S> count(String id, String headerKey, Function<S, Integer> getter) {
        TableColumnSupport.configureNullableIntColumn(add(id, headerKey, 0, "right-aligned-column"), getter);
        return this;
    }

    /**
     * The violation's subject as a link to its container, sorted by name. Nothing for a null violation.
     */
    ViolationColumns<S> subject(String headerKey, Function<S, LimitViolation> violationGetter) {
        TableColumn<S, S> column = add("subject", headerKey, 150, null);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(S row, boolean empty) {
                super.updateItem(row, empty);
                LimitViolation violation = empty || row == null ? null : violationGetter.apply(row);
                setGraphic(violation == null ? null : SecurityAnalysisResultsController.subjectNode(sceneModel.get(), violation));
            }
        });
        column.setComparator(Comparator.comparing(row -> {
            LimitViolation violation = violationGetter.apply(row);
            return violation == null ? "" : SecurityAnalysisResultsController.subjectName(sceneModel.get().getNetwork(), violation);
        }));
        return this;
    }

    ViolationColumns<S> equipmentType(Function<S, Identifiable<?>> equipmentGetter) {
        TableColumnSupport.configureNullableColumn(this.<String>add("type", "desktop.common.column.type", 180, null),
                row -> equipmentTypeText(equipmentGetter.apply(row).getType()));
        return this;
    }

    private static String equipmentTypeText(IdentifiableType type) {
        return switch (type) {
            case LINE -> Messages.get("desktop.common.equipment.line");
            case TIE_LINE -> Messages.get("desktop.common.equipment.tieLine");
            case TWO_WINDINGS_TRANSFORMER, THREE_WINDINGS_TRANSFORMER -> Messages.get("desktop.common.equipment.transformer");
            case BOUNDARY_LINE -> Messages.get("desktop.common.equipment.boundaryLine");
            default -> type.name();
        };
    }

    /**
     * Side, limit type, limit name, acceptable duration, value, permanent limit and limit reduction (hidden) of the
     * row's flow violation.
     */
    ViolationColumns<S> flowViolation(Function<S, FlowViolation> violationGetter) {
        Function<S, LimitViolation> limitViolation = violationGetter.andThen(FlowViolation::violation);
        Function<S, StringConverter<Double>> format = row -> FlowViolationsDialog.format(violationGetter.apply(row), guiParameters.get());
        TableColumnSupport.configureNullableIntColumn(add("side", "desktop.common.column.side", 0, "centered-column"),
                row -> limitViolation.apply(row).getSide() == null ? null : limitViolation.apply(row).getSide().getNum());
        limitType(limitViolation);
        TableColumnSupport.configureNullableColumn(this.<String>add("limitName", "securityAnalysisResults.column.limitName", 150, null),
                row -> limitViolation.apply(row).getLimitName());
        // a permanent limit violation's Integer.MAX_VALUE shown as "-", sorted last
        TableColumnSupport.configureNullableIntColumn(add("acceptableDuration", "securityAnalysisResults.column.acceptableDuration", 0, "centered-column"),
                row -> limitViolation.apply(row).getAcceptableDuration() == Integer.MAX_VALUE ? null : limitViolation.apply(row).getAcceptableDuration());
        TableColumnSupport.configureNullableDoubleColumn(add("value", "securityAnalysisResults.column.value", 0, "right-aligned-column"),
                row -> limitViolation.apply(row).getValue(), format);
        TableColumnSupport.configureNullableDoubleColumn(add("permanentLimit", "violations.column.permanentLimit", 0, "right-aligned-column"),
                row -> violationGetter.apply(row).permanentLimit(), format);
        TableColumn<S, Double> limitReductionColumn = add("limitReduction", "securityAnalysisResults.column.limitReduction", 0, "right-aligned-column");
        TableColumnSupport.configureNullableDoubleColumn(limitReductionColumn, row -> limitViolation.apply(row).getLimitReduction(),
                format(GuiParameters.Quantity.LIMIT_REDUCTION));
        limitReductionColumn.setVisible(false);
        return this;
    }

    private void limitType(Function<S, LimitViolation> violationGetter) {
        TableColumnSupport.configureNullableColumn(this.<String>add("limitType", "securityAnalysisResults.column.limitType", 0, "centered-column"),
                row -> TableColumnSupport.limitViolationTypeText(violationGetter.apply(row).getLimitType()));
    }

    /**
     * The flow violation's loading, nothing for a null one, with an info button opening {@code onInfoClick}.
     */
    ViolationColumns<S> loading(String headerKey, Function<S, FlowViolation> violationGetter, BiConsumer<Window, S> onInfoClick) {
        return loading("loading", headerKey, row -> {
            FlowViolation violation = violationGetter.apply(row);
            return violation == null ? null : Loading.of(violation);
        }, onInfoClick);
    }

    /**
     * A loading, e.g. another state's, with an info button opening {@code onInfoClick} if not null.
     */
    ViolationColumns<S> loading(String id, String headerKey, Function<S, Loading> loadingGetter, BiConsumer<Window, S> onInfoClick) {
        TableColumnSupport.configureLoadingColumn(add(id, headerKey, 230, null), loadingGetter, guiParameters, onInfoClick);
        return this;
    }

    /**
     * A flow value, e.g. another state's, in the unit of the row's flow violation.
     */
    ViolationColumns<S> flowValue(String id, String headerKey, Function<S, Double> valueGetter, Function<S, FlowViolation> violationGetter) {
        TableColumnSupport.configureNullableDoubleColumn(add(id, headerKey, 0, "right-aligned-column"), valueGetter,
                row -> FlowViolationsDialog.format(violationGetter.apply(row), guiParameters.get()));
        return this;
    }

    ViolationColumns<S> voltageValue(String id, String headerKey, Function<S, Double> valueGetter) {
        TableColumnSupport.configureNullableDoubleColumn(add(id, headerKey, 0, "right-aligned-column"), valueGetter, format(GuiParameters.Quantity.VOLTAGE));
        return this;
    }

    /**
     * Bus, limit type, value, limit, nominal voltage, percentage of nominal voltage and voltage range of the row's
     * voltage violation.
     */
    ViolationColumns<S> voltageViolation(Function<S, VoltageViolation> violationGetter) {
        Function<S, LimitViolation> limitViolation = violationGetter.andThen(VoltageViolation::violation);
        TableColumnSupport.configureNullableColumn(this.<String>add("bus", "desktop.common.equipment.bus", 150, null), row -> violationGetter.apply(row).busId());
        limitType(limitViolation);
        TableColumnSupport.configureNullableDoubleColumn(add("value", "securityAnalysisResults.column.value", 0, "right-aligned-column"),
                row -> limitViolation.apply(row).getValue(), format(GuiParameters.Quantity.VOLTAGE));
        TableColumnSupport.configureNullableDoubleColumn(add("limit", "securityAnalysisResults.column.limit", 0, "right-aligned-column"),
                row -> limitViolation.apply(row).getLimit(), format(GuiParameters.Quantity.VOLTAGE));
        TableColumnSupport.configureNullableDoubleColumn(add("nominalV", "desktop.common.column.nominalVoltage", 0, "right-aligned-column"),
                row -> violationGetter.apply(row).nominalV(), format(GuiParameters.Quantity.VOLTAGE));
        TableColumnSupport.configureNullableDoubleColumn(add("percentOfNominal", "violations.column.percentOfNominal", 0, "right-aligned-column"),
                row -> violationGetter.apply(row).percentOfNominal(), format(GuiParameters.Quantity.PERCENTAGE));
        return voltageRange("voltageRange", "buses.column.voltageRange", row -> {
            VoltageViolation violation = violationGetter.apply(row);
            VoltageLevel voltageLevel = voltageLevel(violation);
            return voltageLevel == null ? null : VoltageRange.of(violation.violation().getValue(), voltageLevel, violation);
        });
    }

    /**
     * A voltage range, e.g. another state's.
     */
    ViolationColumns<S> voltageRange(String id, String headerKey, Function<S, VoltageRange> rangeGetter) {
        TableColumnSupport.configureVoltageRangeColumn(add(id, headerKey, 310, null), rangeGetter, guiParameters);
        return this;
    }

    VoltageLevel voltageLevel(VoltageViolation violation) {
        Network network = sceneModel.get().getNetwork();
        return network == null ? null : network.getNetwork().getVoltageLevel(violation.violation().getSubjectId());
    }

    /**
     * Moves the {@code id} column just left of the {@code beforeId} one.
     */
    ViolationColumns<S> moveBefore(String id, String beforeId) {
        TableColumn<S, ?> column = column(id);
        table.getColumns().remove(column);
        table.getColumns().add(table.getColumns().indexOf(column(beforeId)), column);
        return this;
    }

    private TableColumn<S, ?> column(String id) {
        return table.getColumns().stream().filter(c -> id.equals(c.getId())).findFirst().orElseThrow();
    }

    private StringConverter<Double> format(GuiParameters.Quantity quantity) {
        return TableColumnSupport.doubleFormat(guiParameters, quantity);
    }
}
