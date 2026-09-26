/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.commons.parameters.Parameter;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * Builds editable controls for a list of importer/exporter {@link Parameter}s, each control writing its value
 * straight into a {@link Properties} (mutated in place, no Apply/OK button).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class ParameterFormBuilder {

    private ParameterFormBuilder() {
    }

    static GridPane build(List<Parameter> parameters, Properties properties) {
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

        for (Parameter parameter : parameters) {
            Control control = buildControl(parameter, properties);
            Label label = new Label(parameter.getDescription());
            label.setWrapText(true);
            Tooltip tooltip = new Tooltip(parameter.getDescription()
                    + Messages.get("parameter.tooltip.suffix", parameter.getName(), parameter.getDefaultValue()));
            label.setTooltip(tooltip);
            control.setTooltip(tooltip);
            grid.addRow(grid.getRowCount(), label, control);
        }
        return grid;
    }

    private static Control buildControl(Parameter parameter, Properties properties) {
        String value = properties.getProperty(parameter.getName(), formatValue(parameter.getDefaultValue()));
        return switch (parameter.getType()) {
            case BOOLEAN -> {
                CheckBox checkBox = new CheckBox();
                checkBox.setSelected(Boolean.parseBoolean(value));
                checkBox.selectedProperty().addListener((observable, oldValue, newValue) ->
                        properties.setProperty(parameter.getName(), String.valueOf(newValue)));
                yield checkBox;
            }
            case STRING -> parameter.getPossibleValues() != null ? buildChoiceControl(parameter, value, properties) : buildTextControl(parameter, value, properties);
            case INTEGER, DOUBLE, STRING_LIST -> buildTextControl(parameter, value, properties);
        };
    }

    private static ChoiceBox<String> buildChoiceControl(Parameter parameter, String value, Properties properties) {
        ChoiceBox<String> choiceBox = new ChoiceBox<>();
        parameter.getPossibleValues().stream().map(Object::toString).forEach(choiceBox.getItems()::add);
        choiceBox.setValue(value.isEmpty() ? null : value);
        choiceBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                properties.setProperty(parameter.getName(), newValue);
            }
        });
        return choiceBox;
    }

    private static TextField buildTextControl(Parameter parameter, String value, Properties properties) {
        TextField textField = new TextField(value);
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.isBlank()) {
                properties.remove(parameter.getName());
            } else {
                properties.setProperty(parameter.getName(), newValue);
            }
        });
        return textField;
    }

    private static String formatValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(Object::toString).reduce((a, b) -> a + "," + b).orElse("");
        }
        return Objects.toString(value, "");
    }
}
