/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.contingency.violations.LimitViolationType;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.powsybldesktop.network.limits.OperationalLimitsPane;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.powsybldesktop.violations.FlowViolation;
import com.powsybl.powsybldesktop.violations.Loading;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * Modal popup detailing an equipment's flow violations: all of them, most severe first (the one its loading shows
 * marked), then its limits
 * ({@link OperationalLimitsPane}) with the violated groups and crossed limits highlighted.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class FlowViolationsDialog {

    private FlowViolationsDialog() {
    }

    static void show(Window owner, Identifiable<?> equipment, List<FlowViolation> violations, GuiParameters guiParameters) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.setTitle(Messages.get("violations.dialogTitle", equipment.getNameOrId()));

        OperationalLimitsPane limitsPane = new OperationalLimitsPane(guiParameters);
        limitsPane.setEquipment(equipment, violations);
        limitsPane.setPadding(new Insets(4));
        ScrollPane limitsScrollPane = new ScrollPane(limitsPane);
        limitsScrollPane.setFitToWidth(true);
        limitsScrollPane.setPrefSize(700, 400);
        VBox.setVgrow(limitsScrollPane, Priority.ALWAYS);

        VBox content = new VBox(8, violationsTable(violations, guiParameters), limitsScrollPane);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    static TableView<FlowViolation> violationsTable(List<FlowViolation> violations, GuiParameters guiParameters) {
        List<FlowViolation> sorted = violations.stream().sorted(FlowViolation.SEVERITY).toList();
        FlowViolation worst = sorted.isEmpty() ? null : sorted.getFirst();
        TableView<FlowViolation> table = new TableView<>();
        table.setEditable(false);
        table.setPlaceholder(new Label(Messages.get("violations.none")));
        // marks the violation the equipment's loading shows, also once sorted by another column
        TableColumn<FlowViolation, FlowViolation> worstColumn = new TableColumn<>();
        worstColumn.setSortable(false);
        worstColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        worstColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(FlowViolation violation, boolean empty) {
                super.updateItem(violation, empty);
                if (!empty && violation == worst) {
                    FontIcon icon = new FontIcon("mdi2s-star");
                    icon.getStyleClass().add("alert-icon");
                    setGraphic(icon);
                    setTooltip(new Tooltip(Messages.get("violations.worst")));
                } else {
                    setGraphic(null);
                    setTooltip(null);
                }
            }
        });
        worstColumn.setPrefWidth(30);
        table.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(FlowViolation violation, boolean empty) {
                super.updateItem(violation, empty);
                getStyleClass().remove("worst-violation");
                if (!empty && violation == worst) {
                    getStyleClass().add("worst-violation");
                }
            }
        });
        TableColumn<FlowViolation, Integer> sideColumn = new TableColumn<>(Messages.get("desktop.common.column.side"));
        TableColumnSupport.configureNullableIntColumn(sideColumn, v -> v.violation().getSide() == null ? null : v.violation().getSide().getNum());
        TableColumn<FlowViolation, String> limitTypeColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.limitType"));
        TableColumnSupport.configureNullableColumn(limitTypeColumn, v -> TableColumnSupport.limitViolationTypeText(v.violation().getLimitType()));
        TableColumn<FlowViolation, String> groupColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.operationalLimitsGroupId"));
        TableColumnSupport.configureNullableColumn(groupColumn, v -> v.violation().getOperationalLimitsGroupId());
        TableColumn<FlowViolation, String> limitNameColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.limitName"));
        TableColumnSupport.configureNullableColumn(limitNameColumn, v -> v.violation().getLimitName());
        TableColumn<FlowViolation, String> durationColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.acceptableDuration"));
        TableColumnSupport.configureNullableColumn(durationColumn, v -> TableColumnSupport.acceptableDurationText(v.violation().getAcceptableDuration()));
        TableColumn<FlowViolation, Double> valueColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.value"));
        TableColumnSupport.configureNullableDoubleColumn(valueColumn, v -> v.violation().getValue(), v -> format(v, guiParameters));
        TableColumn<FlowViolation, Double> permanentLimitColumn = new TableColumn<>(Messages.get("violations.column.permanentLimit"));
        TableColumnSupport.configureNullableDoubleColumn(permanentLimitColumn, FlowViolation::permanentLimit, v -> format(v, guiParameters));
        TableColumn<FlowViolation, Loading> loadingColumn = new TableColumn<>(Messages.get("desktop.common.column.loading"));
        TableColumnSupport.configureLoadingColumn(loadingColumn, Loading::of, () -> guiParameters, null);
        loadingColumn.setPrefWidth(190);
        table.getColumns().addAll(List.of(worstColumn, sideColumn, limitTypeColumn, groupColumn, limitNameColumn, durationColumn,
                valueColumn, permanentLimitColumn, loadingColumn));
        table.getItems().setAll(sorted);
        table.setPrefHeight((Math.max(violations.size(), 1) + 1.5) * 28);
        table.setMinHeight(table.getPrefHeight());
        return table;
    }

    static StringConverter<Double> format(FlowViolation violation, GuiParameters guiParameters) {
        return TableColumnSupport.doubleFormat(() -> guiParameters, violation.violation().getLimitType() == LimitViolationType.CURRENT
                ? GuiParameters.Quantity.CURRENT : GuiParameters.Quantity.POWER);
    }
}
