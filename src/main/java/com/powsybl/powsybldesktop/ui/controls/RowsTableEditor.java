/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.controls;

import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A table of text rows to edit a list, map or nested structure as rows: cells edited in place (committed on Enter and
 * when the cell loses the focus, unlike {@code TextFieldTableCell}), rows added at the end and removed by selection.
 * Each change hands all the rows to the {@link #setOnEdit} consumer, which parses them: when it throws an
 * {@link IllegalArgumentException}, its message is shown below the table, the rows kept as typed so that they can be
 * fixed. {@link #setRows} replaces them, e.g. when the edited object is replaced.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class RowsTableEditor extends VBox {

    private final TableView<ObservableList<String>> table = new TableView<>();
    private final Label errorLabel = new Label();
    private final int columnCount;
    private Consumer<List<List<String>>> onEdit = _ -> { };

    public RowsTableEditor(List<String> columnTitles) {
        super(4);
        columnCount = columnTitles.size();
        for (int i = 0; i < columnCount; i++) {
            int column = i;
            TableColumn<ObservableList<String>, String> tableColumn = new TableColumn<>(columnTitles.get(i));
            tableColumn.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue().get(column)));
            tableColumn.setCellFactory(_ -> new EditingCell());
            tableColumn.setOnEditCommit(event -> {
                event.getRowValue().set(column, Objects.requireNonNullElse(event.getNewValue(), ""));
                commit();
            });
            tableColumn.setSortable(false);
            tableColumn.setPrefWidth(Math.max(90, 9 * columnTitles.get(i).length()));
            table.getColumns().add(tableColumn);
        }
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label(Messages.get("rowsTableEditor.empty")));
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(26);
        table.setPrefHeight(26 * 5 + 30);

        Button addButton = new Button(null, new FontIcon("mdi2p-plus"));
        addButton.setTooltip(new Tooltip(Messages.get("desktop.common.action.add")));
        addButton.setOnAction(_ -> {
            List<String> empty = new ArrayList<>();
            for (int i = 0; i < columnCount; i++) {
                empty.add("");
            }
            table.getItems().add(FXCollections.observableArrayList(empty));
            table.getSelectionModel().clearAndSelect(table.getItems().size() - 1);
            table.edit(table.getItems().size() - 1, table.getColumns().getFirst());
        });
        Button removeButton = new Button(null, new FontIcon("mdi2m-minus"));
        removeButton.setTooltip(new Tooltip(Messages.get("desktop.common.action.remove")));
        removeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        removeButton.setOnAction(_ -> {
            table.getItems().removeAll(List.copyOf(table.getSelectionModel().getSelectedItems()));
            commit();
        });
        errorLabel.getStyleClass().add("alert-label");
        errorLabel.setWrapText(true);
        errorLabel.setMinHeight(Region.USE_PREF_SIZE);
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        errorLabel.setVisible(false);
        getChildren().addAll(table, new HBox(4, addButton, removeButton), errorLabel);
    }

    public void setOnEdit(Consumer<List<List<String>>> onEdit) {
        this.onEdit = Objects.requireNonNull(onEdit);
    }

    /**
     * Replaces the rows, clearing any error.
     */
    public void setRows(List<List<String>> rows) {
        table.getItems().setAll(rows.stream().map(row -> {
            List<String> cells = new ArrayList<>(row);
            while (cells.size() < columnCount) {
                cells.add("");
            }
            return FXCollections.observableArrayList(cells);
        }).toList());
        showError(null);
    }

    public List<List<String>> getRows() {
        return table.getItems().stream().<List<String>>map(List::copyOf).toList();
    }

    public TableView<ObservableList<String>> getTable() {
        return table;
    }

    public String getError() {
        return errorLabel.isVisible() ? errorLabel.getText() : null;
    }

    private void commit() {
        try {
            onEdit.accept(getRows());
            showError(null);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null ? "" : message);
        errorLabel.setVisible(message != null);
    }

    // a text field while edited, committed on Enter and on focus loss, cancelled on Escape
    private static final class EditingCell extends TableCell<ObservableList<String>, String> {
        private TextField textField;

        @Override
        public void startEdit() {
            super.startEdit();
            if (!isEditing()) {
                return;
            }
            textField = new TextField(getItem());
            textField.setOnAction(_ -> commitEdit(textField.getText()));
            textField.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                }
            });
            textField.focusedProperty().addListener((_, _, focused) -> {
                if (!focused && isEditing()) {
                    commitEdit(textField.getText());
                }
            });
            setText(null);
            setGraphic(textField);
            textField.requestFocus();
            textField.selectAll();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else if (isEditing() && textField != null) {
                textField.setText(item);
                setText(null);
                setGraphic(textField);
            } else {
                setText(item);
                setGraphic(null);
            }
        }
    }
}
