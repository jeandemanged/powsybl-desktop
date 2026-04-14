/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.ShuntCompensator;
import com.powsybl.iidm.network.ShuntCompensatorLinearModel;
import com.powsybl.iidm.network.ShuntCompensatorModelType;
import com.powsybl.iidm.network.ShuntCompensatorNonLinearModel;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Modal popup listing every section (from 1 to the shunt compensator's maximum section count) of a shunt
 * compensator, with its accumulated B/G and the resulting P/Q at nominal voltage, opened from the info button
 * next to the model type in the Shunt Compensators table. For a linear model, B/G are the same per section
 * (bPerSection/gPerSection scaled by the section count), so only section 1 is editable and edits go straight to
 * bPerSection/gPerSection; for a non-linear model, every section's B/G is independently stored and editable.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class ShuntCompensatorSectionsDialog {

    private static final StringConverter<Double> VALUE_FORMAT = new StringConverter<>() {
        @Override
        public String toString(Double value) {
            return String.format(Locale.ROOT, "%.7f", value);
        }

        @Override
        public Double fromString(String text) {
            return Double.valueOf(text.trim());
        }
    };

    // Duplicated from TableColumnSupport's private constant rather than exposing it, since it's just the CSS
    // class name, not shared behavior.
    private static final String EDITABLE_CELL_STYLE_CLASS = "editable-cell";

    private ShuntCompensatorSectionsDialog() {
    }

    static void show(Window owner, ShuntCompensator shuntCompensator) {
        boolean linear = shuntCompensator.getModelType() == ShuntCompensatorModelType.LINEAR;
        double nominalV2 = Math.pow(shuntCompensator.getTerminal().getVoltageLevel().getNominalV(), 2);
        Predicate<Integer> editablePredicate = linear ? section -> section == 1 : section -> true;

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.setTitle(Messages.get("shuntCompensators.sections.dialogTitle"));

        VBox header = new VBox(2);
        header.getChildren().add(new Label(Messages.get("shuntCompensators.sections.header.name", shuntCompensator.getNameOrId())));
        header.getChildren().add(new Label(Messages.get("shuntCompensators.sections.header.type", ShuntCompensatorsController.typeLabel(shuntCompensator))));
        header.getChildren().add(new Label(Messages.get("shuntCompensators.sections.header.modelType",
                ShuntCompensatorsController.modelTypeLabel(shuntCompensator.getModelType()))));

        TableView<Integer> sectionsTableView = new TableView<>();
        sectionsTableView.setEditable(true);
        List<Integer> sections = new ArrayList<>();
        for (int section = 1; section <= shuntCompensator.getMaximumSectionCount(); section++) {
            sections.add(section);
        }
        sectionsTableView.getItems().setAll(sections);

        sectionsTableView.getColumns().add(readOnlyColumn("shuntCompensators.sections.column.section", String::valueOf));
        sectionsTableView.getColumns().add(editableColumn("shuntCompensators.sections.column.b",
                shuntCompensator::getB, (section, value) -> setB(shuntCompensator, section, value), editablePredicate, sectionsTableView));
        sectionsTableView.getColumns().add(editableColumn("shuntCompensators.sections.column.g",
                shuntCompensator::getG, (section, value) -> setG(shuntCompensator, section, value), editablePredicate, sectionsTableView));
        sectionsTableView.getColumns().add(readOnlyColumn("shuntCompensators.sections.column.q",
                section -> formatValue(-shuntCompensator.getB(section) * nominalV2)));
        sectionsTableView.getColumns().add(readOnlyColumn("shuntCompensators.sections.column.p",
                section -> formatValue(shuntCompensator.getG(section) * nominalV2)));
        sectionsTableView.setPrefSize(500, 300);
        sectionsTableView.setMaxWidth(Double.MAX_VALUE);
        sectionsTableView.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(sectionsTableView, Priority.ALWAYS);

        VBox content = new VBox(10, header, sectionsTableView);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    private static void setB(ShuntCompensator shuntCompensator, int section, double value) {
        if (shuntCompensator.getModelType() == ShuntCompensatorModelType.LINEAR) {
            shuntCompensator.getModel(ShuntCompensatorLinearModel.class).setBPerSection(value);
        } else {
            shuntCompensator.getModel(ShuntCompensatorNonLinearModel.class).getAllSections().get(section - 1).setB(value);
        }
    }

    private static void setG(ShuntCompensator shuntCompensator, int section, double value) {
        if (shuntCompensator.getModelType() == ShuntCompensatorModelType.LINEAR) {
            shuntCompensator.getModel(ShuntCompensatorLinearModel.class).setGPerSection(value);
        } else {
            shuntCompensator.getModel(ShuntCompensatorNonLinearModel.class).getAllSections().get(section - 1).setG(value);
        }
    }

    private static TableColumn<Integer, String> readOnlyColumn(String titleKey, Function<Integer, String> valueGetter) {
        TableColumn<Integer, String> column = new TableColumn<>(Messages.get(titleKey));
        column.setSortable(false);
        column.setEditable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cellData.getValue())));
        return column;
    }

    private static TableColumn<Integer, Double> editableColumn(String titleKey, Function<Integer, Double> valueGetter,
                                                                 BiConsumer<Integer, Double> setter, Predicate<Integer> editablePredicate,
                                                                 TableView<Integer> tableView) {
        TableColumn<Integer, Double> column = new TableColumn<>(Messages.get(titleKey));
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(valueGetter.apply(cellData.getValue())));
        // Keyed by section rather than held on the TableCell instance - see EditableSectionCell.commitEdit's
        // comment for why.
        Map<Integer, String> flashStyles = new HashMap<>();
        column.setCellFactory(col -> new EditableSectionCell(editablePredicate, flashStyles));
        column.setOnEditCommit(event -> {
            setter.accept(event.getRowValue(), event.getNewValue());
            tableView.refresh();
        });
        return column;
    }

    // startEdit is a no-op for a section the editablePredicate rejects (a linear model's sections above 1, whose
    // B/G are derived from bPerSection/gPerSection rather than independently stored).
    private static final class EditableSectionCell extends TextFieldTableCell<Integer, Double> {
        private final Predicate<Integer> editablePredicate;
        private final Map<Integer, String> flashStyles;

        EditableSectionCell(Predicate<Integer> editablePredicate, Map<Integer, String> flashStyles) {
            super(VALUE_FORMAT);
            this.editablePredicate = editablePredicate;
            this.flashStyles = flashStyles;
        }

        @Override
        public void startEdit() {
            Integer section = getTableRow() == null ? null : getTableRow().getItem();
            if (section == null || !editablePredicate.test(section)) {
                return;
            }
            super.startEdit();
        }

        // The column's onEditCommit handler (fired synchronously by super.commitEdit) refreshes the whole table
        // so sibling rows/columns pick up the new value - for a linear shunt, editing section 1 changes every
        // other section's B/G too. That refresh can recycle this exact TableCell to a different row before the
        // flash fades, so the success flash is keyed by section (re-derived in updateItem) rather than applied
        // directly to "this", the same fix as TableColumnSupport's connected-column flash.
        @Override
        public void commitEdit(Double newValue) {
            Integer section = getTableRow() == null ? null : getTableRow().getItem();
            try {
                super.commitEdit(newValue);
                if (section != null) {
                    TableColumnSupport.flashKeyed(this, section, flashStyles, TableColumnSupport.EDIT_SUCCESS_STYLE_CLASS);
                }
            } catch (PowsyblException e) {
                TableColumnSupport.notifyEditError(this, e);
                updateItem(getItem(), false);
            }
        }

        // applyKeyedFlash runs unconditionally, even when empty (section == null): a cell recycled to an empty
        // filler row otherwise keeps whatever flash class a previous row left on it forever, since nothing else
        // ever revisits that cell to clear it.
        @Override
        public void updateItem(Double value, boolean empty) {
            super.updateItem(value, empty);
            Integer section = getTableRow() == null ? null : getTableRow().getItem();
            getStyleClass().remove(EDITABLE_CELL_STYLE_CLASS);
            if (!empty && section != null && editablePredicate.test(section)) {
                getStyleClass().add(EDITABLE_CELL_STYLE_CLASS);
            }
            TableColumnSupport.applyKeyedFlash(this, empty ? null : section, flashStyles);
        }
    }

    private static String formatValue(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
