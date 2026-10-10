/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.parameters;

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

    static GridPane build(List<Parameter> parameters, Properties properties, Runnable onChange) {
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
            Control control = buildControl(parameter, properties, onChange);
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

    private static Control buildControl(Parameter parameter, Properties properties, Runnable onChange) {
        String value = properties.getProperty(parameter.getName(), formatValue(parameter.getDefaultValue()));
        return switch (parameter.getType()) {
            case BOOLEAN -> {
                CheckBox checkBox = new CheckBox();
                checkBox.setSelected(Boolean.parseBoolean(value));
                checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    setValue(parameter, properties, String.valueOf(newValue));
                    onChange.run();
                });
                yield checkBox;
            }
            case STRING -> parameter.getPossibleValues() != null
                    ? buildChoiceControl(parameter, value, properties, onChange)
                    : buildTextControl(parameter, value, properties, onChange);
            case INTEGER, DOUBLE, STRING_LIST -> buildTextControl(parameter, value, properties, onChange);
        };
    }

    private static ChoiceBox<String> buildChoiceControl(Parameter parameter, String value, Properties properties, Runnable onChange) {
        ChoiceBox<String> choiceBox = new ChoiceBox<>();
        parameter.getPossibleValues().stream().map(Object::toString).forEach(choiceBox.getItems()::add);
        choiceBox.setValue(value.isEmpty() ? null : value);
        choiceBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                setValue(parameter, properties, newValue);
                onChange.run();
            }
        });
        return choiceBox;
    }

    private static TextField buildTextControl(Parameter parameter, String value, Properties properties, Runnable onChange) {
        TextField textField = new TextField(value);
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            setValue(parameter, properties, newValue);
            onChange.run();
        });
        return textField;
    }

    // only edited values are kept, so the importer/exporter falls back to its own default otherwise and a value
    // edited back to its default doesn't make the parameters differ from the saved ones
    private static void setValue(Parameter parameter, Properties properties, String value) {
        if (value.isBlank() || value.equals(formatValue(parameter.getDefaultValue()))) {
            properties.remove(parameter.getName());
        } else {
            properties.setProperty(parameter.getName(), value);
        }
    }

    private static String formatValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(Object::toString).reduce((a, b) -> a + "," + b).orElse("");
        }
        return Objects.toString(value, "");
    }
}
