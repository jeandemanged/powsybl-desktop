/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A multiple choice like ControlsFX's CheckComboBox, for long lists (e.g. countries): its popup has a search field
 * keeping the items whose text contains every word typed, like ControlsFX's SearchableComboBox. It shows its checked
 * items' {@code toString} (e.g. country codes), or its title when none is checked.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SearchableCheckComboBox<T> extends Button {

    private static final double POPUP_MIN_WIDTH = 220;
    private static final double LIST_HEIGHT = 300;

    private final Map<T, BooleanProperty> checks = new LinkedHashMap<>();
    private final ObservableList<T> checkedItems = FXCollections.observableArrayList();
    private final ObservableList<T> readOnlyCheckedItems = FXCollections.unmodifiableObservableList(checkedItems);
    private final StringProperty title = new SimpleStringProperty("");
    private final TextField searchField = new TextField();
    private final ListView<T> listView;
    private final VBox content;
    private final Popup popup = new Popup();
    private boolean updating;

    public SearchableCheckComboBox(List<T> items, StringConverter<T> converter) {
        for (T item : items) {
            BooleanProperty check = new SimpleBooleanProperty();
            check.addListener((_, _, _) -> {
                if (!updating) {
                    updateCheckedItems();
                }
            });
            checks.put(item, check);
        }
        FilteredList<T> filteredItems = new FilteredList<>(FXCollections.observableArrayList(items));
        searchField.setPromptText(Messages.get("desktop.common.search.prompt"));
        searchField.textProperty().addListener((_, _, text) -> filteredItems.setPredicate(matching(text, converter)));
        listView = new ListView<>(filteredItems);
        listView.setCellFactory(CheckBoxListCell.forListView(checks::get, converter));
        listView.setPrefHeight(LIST_HEIGHT);
        listView.setOnKeyPressed(event -> {
            T selected = listView.getSelectionModel().getSelectedItem();
            if (event.getCode() == KeyCode.SPACE && selected != null) {
                toggle(selected);
                event.consume();
            }
        });
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN && !filteredItems.isEmpty()) {
                listView.requestFocus();
                listView.getSelectionModel().selectFirst();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER && filteredItems.size() == 1) {
                toggle(filteredItems.getFirst());
                event.consume();
            }
        });

        content = new VBox(4, searchField, listView);
        content.getStyleClass().add("searchable-check-combo-box-popup");
        // a plain popup doesn't inherit its owner scene's stylesheets
        content.getStylesheets().add(Objects.requireNonNull(
                SearchableCheckComboBox.class.getResource("/com/powsybl/powsybldesktop/styles.css")).toExternalForm());
        popup.getContent().add(content);
        popup.setAutoHide(true);
        popup.setOnHidden(_ -> searchField.clear());

        textProperty().bind(Bindings.createStringBinding(() -> checkedItems.isEmpty() ? title.get()
                : checkedItems.stream().map(String::valueOf).collect(Collectors.joining(", ")), checkedItems, title));
        setGraphic(new FontIcon("mdi2c-chevron-down"));
        setContentDisplay(ContentDisplay.RIGHT);
        setAlignment(Pos.CENTER_LEFT);
        setOnAction(_ -> show());
    }

    public final StringProperty titleProperty() {
        return title;
    }

    public final void setTitle(String value) {
        title.set(value);
    }

    /**
     * The checked items, in the items' order.
     */
    public ObservableList<T> getCheckedItems() {
        return readOnlyCheckedItems;
    }

    /**
     * Checks exactly {@code items}, as a single change of the checked items.
     */
    public void setChecked(Collection<T> items) {
        updating = true;
        checks.forEach((item, check) -> check.set(items.contains(item)));
        updating = false;
        updateCheckedItems();
    }

    public void show() {
        content.setPrefWidth(Math.max(getWidth(), POPUP_MIN_WIDTH));
        Bounds bounds = localToScreen(getBoundsInLocal());
        popup.show(this, bounds.getMinX(), bounds.getMaxY());
        searchField.requestFocus();
    }

    private void toggle(T item) {
        BooleanProperty check = checks.get(item);
        check.set(!check.get());
    }

    private void updateCheckedItems() {
        List<T> checked = checks.entrySet().stream().filter(entry -> entry.getValue().get()).map(Map.Entry::getKey).toList();
        if (!checked.equals(checkedItems)) {
            checkedItems.setAll(checked);
        }
    }

    private static <T> Predicate<T> matching(String text, StringConverter<T> converter) {
        String[] words = text.trim().toLowerCase(Locale.ROOT).split("\\s+");
        return item -> {
            String itemText = converter.toString(item).toLowerCase(Locale.ROOT);
            return Arrays.stream(words).allMatch(itemText::contains);
        };
    }
}
