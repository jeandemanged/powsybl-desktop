/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An editable table of an identifiable's string pairs, one row per key: its properties (name, value) or its aliases
 * (alias, type). Rows are added from the fields below the table and removed by selection; a property's value is edited
 * in place. Shared by every equipment detail view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class KeyValueTablePane extends VBox implements DetailPart {

    private final TableView<String> tableView = new TableView<>();
    private final BooleanProperty editable = new SimpleBooleanProperty(true);
    private final Supplier<Collection<String>> keys;

    private KeyValueTablePane(String titleKey, String keyColumnKey, String valueColumnKey, Supplier<Collection<String>> keys,
                              Function<String, String> valueGetter, BiConsumer<String, String> valueSetter,
                              BiConsumer<String, String> adder, Consumer<String> remover, Runnable onEdit) {
        super(4);
        this.keys = Objects.requireNonNull(keys);
        Label title = new Label(Messages.get(titleKey));
        title.getStyleClass().add("section-title");

        TableColumn<String, String> keyColumn = new TableColumn<>(Messages.get(keyColumnKey));
        keyColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue()));
        keyColumn.setEditable(false);
        keyColumn.setPrefWidth(220);
        TableColumn<String, String> valueColumn = valueSetter == null
                ? DetailTables.readOnlyColumn(valueColumnKey, DetailTables.TEXT_FORMAT, valueGetter)
                : DetailTables.editableColumn(valueColumnKey, DetailTables.TEXT_FORMAT, valueGetter, valueSetter, onEdit);
        valueColumn.setPrefWidth(320);
        tableView.getColumns().setAll(keyColumn, valueColumn);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableView.setPlaceholder(new Label(Messages.get("detail.keyValue.empty")));
        tableView.editableProperty().bind(editable);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        TextField keyField = new TextField();
        keyField.setPromptText(Messages.get(keyColumnKey));
        TextField valueField = new TextField();
        valueField.setPromptText(Messages.get(valueColumnKey));
        HBox.setHgrow(keyField, Priority.ALWAYS);
        HBox.setHgrow(valueField, Priority.ALWAYS);
        Button addButton = new Button(Messages.get("desktop.common.action.add"));
        addButton.disableProperty().bind(editable.not().or(keyField.textProperty().isEmpty()));
        addButton.setOnAction(_ -> {
            String key = keyField.getText().trim();
            String value = valueField.getText().trim();
            if (DetailForm.apply(addButton, () -> {
                if (key.isEmpty()) {
                    throw new PowsyblException(Messages.get("detail.keyValue.blankKey"));
                }
                adder.accept(key, value);
            }, onEdit)) {
                keyField.clear();
                valueField.clear();
            }
            refresh();
        });
        Button removeButton = new Button(Messages.get("desktop.common.action.remove"));
        removeButton.disableProperty().bind(editable.not().or(tableView.getSelectionModel().selectedItemProperty().isNull()));
        removeButton.setOnAction(_ -> {
            String key = tableView.getSelectionModel().getSelectedItem();
            DetailForm.apply(removeButton, () -> remover.accept(key), onEdit);
            refresh();
        });
        HBox addBox = new HBox(4, keyField, valueField, addButton, removeButton);
        addBox.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(title, tableView, addBox);
        refresh();
    }

    public static KeyValueTablePane properties(Identifiable<?> identifiable, Runnable onEdit) {
        return new KeyValueTablePane("detail.properties", "desktop.common.column.name", "detail.properties.value",
                identifiable::getPropertyNames, identifiable::getProperty, identifiable::setProperty, identifiable::setProperty,
                identifiable::removeProperty, onEdit);
    }

    public static KeyValueTablePane aliases(Identifiable<?> identifiable, Runnable onEdit) {
        return new KeyValueTablePane("detail.aliases", "detail.aliases.alias", "desktop.common.column.type",
                identifiable::getAliases, alias -> identifiable.getAliasType(alias).orElse(""), null,
                (alias, type) -> {
                    if (type.isEmpty()) {
                        identifiable.addAlias(alias);
                    } else {
                        identifiable.addAlias(alias, type);
                    }
                },
                identifiable::removeAlias, onEdit);
    }

    @Override
    public BooleanProperty editableProperty() {
        return editable;
    }

    @Override
    public void refresh() {
        String selected = tableView.getSelectionModel().getSelectedItem();
        tableView.getItems().setAll(keys.get().stream().sorted().toList());
        if (selected != null && tableView.getItems().contains(selected)) {
            tableView.getSelectionModel().select(selected);
        }
    }
}
