/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

import com.powsybl.powsybldesktop.utils.AbstractDisposableController;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Category-list + detail-grid form shape, factored out of {@link com.powsybl.powsybldesktop.parameters.LoadFlowParametersController}'s
 * pattern and generalized over the parameters type {@code T}, since {@link SldParametersController} and
 * {@link NadParametersController} need it identically. Field edits write straight into {@code T} (mutated
 * in place, no Apply/OK button - same as {@code LoadFlowParametersController}) and, unlike load flow
 * parameters, also invoke {@link #setOnChange(Runnable) onChange} so the caller can refresh the diagram
 * currently on screen.
 *
 * @param <T> the PowSyBl parameters object being edited ({@code SldParameters} or {@code NadParameters})
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public abstract class AbstractDiagramParametersController<T> extends AbstractDisposableController {

    @FXML
    protected SplitPane splitPane;

    protected ObjectProperty<T> parametersProperty;
    protected final Map<String, GridPane> categoryGrids = new LinkedHashMap<>();
    protected final List<Runnable> refreshers = new ArrayList<>();
    protected boolean refreshing;
    private Runnable onChange;

    public void setParametersProperty(ObjectProperty<T> parametersProperty) {
        this.parametersProperty = Objects.requireNonNull(parametersProperty);
        refresh();
        listenerManager.listen(parametersProperty, (observable, oldValue, newValue) -> refresh());
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    protected void refresh() {
        refreshing = true;
        try {
            refreshers.forEach(Runnable::run);
        } finally {
            refreshing = false;
        }
    }

    protected void notifyChange() {
        if (onChange != null) {
            onChange.run();
        }
    }

    // called once by the subclass's initialize(), after its category titles are known
    protected void buildCategoryList(Map<String, String> categoryTitles) {
        categoryTitles.keySet().forEach(category -> categoryGrids.put(category, createCategoryGrid()));

        buildFields();

        ListView<String> categoryListView = new ListView<>();
        Map<String, ScrollPane> categoryPanes = new LinkedHashMap<>();
        categoryTitles.forEach((category, title) -> {
            GridPane grid = categoryGrids.get(category);
            if (grid.getChildren().isEmpty()) {
                return;
            }
            ScrollPane scrollPane = new ScrollPane(grid);
            scrollPane.setFitToWidth(true);
            categoryPanes.put(title, scrollPane);
            categoryListView.getItems().add(title);
        });

        StackPane detailPane = new StackPane();
        categoryListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                detailPane.getChildren().setAll(categoryPanes.get(newValue));
            }
        });
        categoryListView.setPrefWidth(220);
        categoryListView.getSelectionModel().selectFirst();

        splitPane.getItems().addAll(categoryListView, detailPane);
        splitPane.setDividerPositions(0.22);
    }

    protected abstract void buildFields();

    private GridPane createCategoryGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setHalignment(HPos.RIGHT);
        labelColumn.setPrefWidth(320);
        labelColumn.setMaxWidth(320);
        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setHalignment(HPos.LEFT);
        controlColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, controlColumn);
        return grid;
    }

    protected void addRow(String category, String labelText, String tooltipText, Node control) {
        GridPane grid = categoryGrids.get(category);
        Label label = new Label(labelText);
        label.setWrapText(true);
        if (tooltipText != null) {
            Tooltip tooltip = new Tooltip(tooltipText);
            label.setTooltip(tooltip);
            Tooltip.install(control, tooltip);
        }
        grid.addRow(grid.getRowCount(), label, control);
    }

    protected static void bindCommit(TextField textField, Runnable commit) {
        textField.setOnAction(event -> commit.run());
        textField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (Boolean.TRUE.equals(wasFocused) && Boolean.FALSE.equals(isFocused)) {
                commit.run();
            }
        });
    }

    protected void addBooleanField(String category, String label, String tooltip,
                                    Predicate<T> getter, BiConsumer<T, Boolean> setter) {
        CheckBox checkBox = new CheckBox();
        refreshers.add(() -> checkBox.setSelected(getter.test(parametersProperty.getValue())));
        checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing) {
                setter.accept(parametersProperty.getValue(), newValue);
                notifyChange();
            }
        });
        addRow(category, label, tooltip, checkBox);
    }

    protected <E extends Enum<E>> void addEnumField(String category, String label, String tooltip, Class<E> enumClass,
                                                     Function<T, E> getter, BiConsumer<T, E> setter) {
        ChoiceBox<E> choiceBox = new ChoiceBox<>(FXCollections.observableArrayList(enumClass.getEnumConstants()));
        refreshers.add(() -> choiceBox.setValue(getter.apply(parametersProperty.getValue())));
        choiceBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!refreshing && newValue != null) {
                setter.accept(parametersProperty.getValue(), newValue);
                notifyChange();
            }
        });
        addRow(category, label, tooltip, choiceBox);
    }

    protected void addDoubleField(String category, String label, String tooltip,
                                   Function<T, Double> getter, BiConsumer<T, Double> setter) {
        TextField textField = new TextField();
        refreshers.add(() -> textField.setText(Double.toString(getter.apply(parametersProperty.getValue()))));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            try {
                setter.accept(parametersProperty.getValue(), Double.parseDouble(textField.getText()));
                notifyChange();
            } catch (NumberFormatException e) {
                refresh();
            }
        };
        bindCommit(textField, commit);
        addRow(category, label, tooltip, textField);
    }

    protected void addIntField(String category, String label, String tooltip,
                               Function<T, Integer> getter, BiConsumer<T, Integer> setter) {
        TextField textField = new TextField();
        refreshers.add(() -> textField.setText(Integer.toString(getter.apply(parametersProperty.getValue()))));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            try {
                setter.accept(parametersProperty.getValue(), Integer.parseInt(textField.getText()));
                notifyChange();
            } catch (NumberFormatException e) {
                refresh();
            }
        };
        bindCommit(textField, commit);
        addRow(category, label, tooltip, textField);
    }

    protected void addStringField(String category, String label, String tooltip,
                                   Function<T, String> getter, BiConsumer<T, String> setter) {
        TextField textField = new TextField();
        refreshers.add(() -> textField.setText(Objects.toString(getter.apply(parametersProperty.getValue()), "")));
        Runnable commit = () -> {
            if (refreshing) {
                return;
            }
            setter.accept(parametersProperty.getValue(), textField.getText());
            notifyChange();
        };
        bindCommit(textField, commit);
        addRow(category, label, tooltip, textField);
    }
}
