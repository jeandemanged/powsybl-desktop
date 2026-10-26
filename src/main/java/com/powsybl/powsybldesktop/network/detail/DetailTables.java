/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.powsybldesktop.network.tables.CellFields;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Columns of the detail views' row tables (tap changer steps, shunt sections, limits, properties, reactive capability
 * curve points), editing in {@link CellFields} fields. Rows are keyed by index: some are records recreated by every
 * refresh, which would not match the edited row anymore.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class DetailTables {

    public static final StringConverter<Integer> INTEGER_FORMAT = new StringConverter<>() {
        @Override
        public String toString(Integer value) {
            return value == null ? "-" : value.toString();
        }

        @Override
        public Integer fromString(String text) {
            return Integer.valueOf(text.trim());
        }
    };

    public static final StringConverter<String> TEXT_FORMAT = new StringConverter<>() {
        @Override
        public String toString(String value) {
            return value == null ? "" : value;
        }

        @Override
        public String fromString(String text) {
            return text.trim();
        }
    };

    private DetailTables() {
    }

    public static <R, T> TableColumn<R, T> readOnlyColumn(String titleKey, StringConverter<T> format, Function<R, T> getter) {
        TableColumn<R, T> column = new TableColumn<>(Messages.get(titleKey));
        column.setSortable(false);
        column.setEditable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(T value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : format.toString(value));
            }
        });
        return column;
    }

    /**
     * @param onEdit run after an applied edit, e.g. the view's network update
     */
    public static <R, T> TableColumn<R, T> editableColumn(String titleKey, StringConverter<T> format, Function<R, T> getter,
                                                          BiConsumer<R, T> setter, Runnable onEdit) {
        return editableColumn(titleKey, format, getter, setter, onEdit, row -> true);
    }

    /**
     * @param rowEditable whether a row's value can be edited, e.g. not a permanent limit's acceptable duration
     */
    public static <R, T> TableColumn<R, T> editableColumn(String titleKey, StringConverter<T> format, Function<R, T> getter,
                                                          BiConsumer<R, T> setter, Runnable onEdit, Predicate<R> rowEditable) {
        TableColumn<R, T> column = new TableColumn<>(Messages.get(titleKey));
        column.setSortable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        CellFields.textColumn(column, format, rowEditable, CellFields.RowKey.INDEX);
        column.setOnEditCommit(event -> {
            setter.accept(event.getRowValue(), event.getNewValue());
            onEdit.run();
        });
        return column;
    }

    public static <R> TableColumn<R, Boolean> checkBoxColumn(String titleKey, Function<R, Boolean> getter, BiConsumer<R, Boolean> setter,
                                                             Runnable onEdit, BooleanProperty editable) {
        TableColumn<R, Boolean> column = new TableColumn<>(Messages.get(titleKey));
        column.setSortable(false);
        column.setEditable(false);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(getter.apply(cellData.getValue())));
        column.setCellFactory(col -> new CheckBoxCell<>(getter, setter, onEdit, editable));
        return column;
    }

    /**
     * Sizes {@code table} to its rows, for tables stacked in a scrolling pane.
     */
    public static void fitHeight(TableView<?> table, int rows) {
        double cellSize = 26;
        table.setFixedCellSize(cellSize);
        table.setPrefHeight((Math.max(rows, 1) + 1.5) * cellSize);
        table.setMinHeight(table.getPrefHeight());
        table.setMaxHeight(table.getPrefHeight());
    }

    private static final class CheckBoxCell<R> extends TableCell<R, Boolean> {
        private final CheckBox checkBox = new CheckBox();

        private CheckBoxCell(Function<R, Boolean> getter, BiConsumer<R, Boolean> setter, Runnable onEdit, BooleanProperty editable) {
            checkBox.disableProperty().bind(editable.not());
            checkBox.setOnAction(event -> {
                R row = getTableRow().getItem();
                DetailForm.apply(checkBox, () -> setter.accept(row, checkBox.isSelected()), onEdit);
                checkBox.setSelected(getter.apply(row));
            });
            getStyleClass().add("centered-column");
        }

        @Override
        protected void updateItem(Boolean value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || value == null) {
                setGraphic(null);
            } else {
                checkBox.setSelected(value);
                setGraphic(checkBox);
            }
        }
    }

}
