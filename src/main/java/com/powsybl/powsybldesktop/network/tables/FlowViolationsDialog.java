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

import java.util.Comparator;
import java.util.List;

/**
 * Modal popup detailing an equipment's flow violations: all of them, most severe first (the one its loading shows
 * marked), from one or several network states (base case, pre- or post-contingency), then its limits
 * ({@link OperationalLimitsPane}) with the violated groups and crossed limits highlighted.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class FlowViolationsDialog {

    private FlowViolationsDialog() {
    }

    /**
     * A flow violation of the network state named {@code state}, null for the network's own state.
     */
    record Entry(String state, FlowViolation violation) {
    }

    /**
     * The network's own state's violations, along its sides' present values.
     */
    static void show(Window owner, Identifiable<?> equipment, List<FlowViolation> violations, GuiParameters guiParameters) {
        List<Entry> entries = violations.stream().map(v -> new Entry(null, v)).toList();
        show(owner, equipment, entries, violations.stream().min(FlowViolation.SEVERITY).orElse(null), true, guiParameters);
    }

    /**
     * Computed states' violations, e.g. a security analysis', with a state column. The network's present values
     * aren't shown, as they are its own state's.
     */
    static void showStates(Window owner, Identifiable<?> equipment, List<Entry> entries, FlowViolation worst, GuiParameters guiParameters) {
        show(owner, equipment, entries, worst, false, guiParameters);
    }

    private static void show(Window owner, Identifiable<?> equipment, List<Entry> entries, FlowViolation worst, boolean presentValues,
                             GuiParameters guiParameters) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.setTitle(Messages.get("violations.dialogTitle", equipment.getNameOrId()));

        OperationalLimitsPane limitsPane = new OperationalLimitsPane(guiParameters);
        limitsPane.setEquipment(equipment, entries.stream().map(Entry::violation).toList(), presentValues);
        limitsPane.setPadding(new Insets(4));
        ScrollPane limitsScrollPane = new ScrollPane(limitsPane);
        limitsScrollPane.setFitToWidth(true);
        limitsScrollPane.setPrefSize(700, 400);
        VBox.setVgrow(limitsScrollPane, Priority.ALWAYS);

        VBox content = new VBox(8, violationsTable(entries, worst, guiParameters), limitsScrollPane);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    static TableView<Entry> violationsTable(List<Entry> entries, FlowViolation worst, GuiParameters guiParameters) {
        TableView<Entry> table = new TableView<>();
        table.setEditable(false);
        table.setPlaceholder(new Label(Messages.get("violations.none")));
        // marks the violation the equipment's loading shows, also once sorted by another column
        TableColumn<Entry, FlowViolation> worstColumn = new TableColumn<>();
        worstColumn.setSortable(false);
        worstColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().violation()));
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
            protected void updateItem(Entry entry, boolean empty) {
                super.updateItem(entry, empty);
                getStyleClass().remove("worst-violation");
                if (!empty && entry != null && entry.violation() == worst) {
                    getStyleClass().add("worst-violation");
                }
            }
        });
        TableColumn<Entry, String> stateColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.contingency"));
        TableColumnSupport.configureNullableColumn(stateColumn, Entry::state);
        stateColumn.setPrefWidth(150);
        stateColumn.setVisible(entries.stream().anyMatch(entry -> entry.state() != null));
        TableColumn<Entry, Integer> sideColumn = new TableColumn<>(Messages.get("desktop.common.column.side"));
        TableColumnSupport.configureNullableIntColumn(sideColumn, e -> e.violation().violation().getSide() == null ? null : e.violation().violation().getSide().getNum());
        TableColumn<Entry, String> limitTypeColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.limitType"));
        TableColumnSupport.configureNullableColumn(limitTypeColumn, e -> TableColumnSupport.limitViolationTypeText(e.violation().violation().getLimitType()));
        TableColumn<Entry, String> groupColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.operationalLimitsGroupId"));
        TableColumnSupport.configureNullableColumn(groupColumn, e -> e.violation().violation().getOperationalLimitsGroupId());
        TableColumn<Entry, String> limitNameColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.limitName"));
        TableColumnSupport.configureNullableColumn(limitNameColumn, e -> e.violation().violation().getLimitName());
        TableColumn<Entry, String> durationColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.acceptableDuration"));
        TableColumnSupport.configureNullableColumn(durationColumn, e -> TableColumnSupport.acceptableDurationText(e.violation().violation().getAcceptableDuration()));
        TableColumn<Entry, Double> valueColumn = new TableColumn<>(Messages.get("securityAnalysisResults.column.value"));
        TableColumnSupport.configureNullableDoubleColumn(valueColumn, e -> e.violation().violation().getValue(), e -> format(e.violation(), guiParameters));
        TableColumn<Entry, Double> permanentLimitColumn = new TableColumn<>(Messages.get("violations.column.permanentLimit"));
        TableColumnSupport.configureNullableDoubleColumn(permanentLimitColumn, e -> e.violation().permanentLimit(), e -> format(e.violation(), guiParameters));
        TableColumn<Entry, Loading> loadingColumn = new TableColumn<>(Messages.get("desktop.common.column.loading"));
        TableColumnSupport.configureLoadingColumn(loadingColumn, e -> Loading.of(e.violation()), () -> guiParameters, null);
        loadingColumn.setPrefWidth(190);
        table.getColumns().addAll(List.of(worstColumn, stateColumn, sideColumn, limitTypeColumn, groupColumn, limitNameColumn, durationColumn,
                valueColumn, permanentLimitColumn, loadingColumn));
        table.getItems().setAll(entries.stream().sorted(Comparator.comparing(Entry::violation, FlowViolation.SEVERITY)).toList());
        table.setPrefHeight((Math.max(entries.size(), 1) + 1.5) * 28);
        table.setMinHeight(table.getPrefHeight());
        return table;
    }

    static StringConverter<Double> format(FlowViolation violation, GuiParameters guiParameters) {
        return TableColumnSupport.doubleFormat(() -> guiParameters, violation.violation().getLimitType() == LimitViolationType.CURRENT
                ? GuiParameters.Quantity.CURRENT : GuiParameters.Quantity.POWER);
    }
}
