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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Builds editable controls for a list of {@link Parameter} and reads them back into a {@link Properties},
 * shared between {@link ExportNetworkDialog} and {@link ImportNetworkDialog}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class ParameterFormBuilder {

    private static final double LABEL_COLUMN_PERCENT_WIDTH = 65;

    private ParameterFormBuilder() {
    }

    /**
     * Fixes the name/value columns at a constant width ratio so it's preserved when the dialog is resized.
     */
    static void configureColumns(GridPane grid) {
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setPercentWidth(LABEL_COLUMN_PERCENT_WIDTH);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setPercentWidth(100 - LABEL_COLUMN_PERCENT_WIDTH);
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        grid.setMaxWidth(Double.MAX_VALUE);
    }

    static Map<Parameter, Control> addRows(GridPane grid, int firstRow, List<Parameter> parameters) {
        Map<Parameter, Control> controls = new LinkedHashMap<>();
        int row = firstRow;
        for (Parameter parameter : parameters) {
            Control control = buildControl(parameter);
            controls.put(parameter, control);
            control.setMaxWidth(Double.MAX_VALUE);
            Label label = new Label(parameter.getDescription());
            label.setWrapText(true);
            Tooltip tooltip = new Tooltip(parameter.getDescription()
                    + Messages.get("parameter.tooltip.suffix", parameter.getName(), parameter.getDefaultValue()));
            label.setTooltip(tooltip);
            control.setTooltip(tooltip);
            grid.addRow(row++, label, control);
        }
        return controls;
    }

    private static Control buildControl(Parameter parameter) {
        return switch (parameter.getType()) {
            case BOOLEAN -> {
                CheckBox checkBox = new CheckBox();
                checkBox.setSelected(Boolean.TRUE.equals(parameter.getDefaultValue()));
                yield checkBox;
            }
            case STRING -> parameter.getPossibleValues() != null ? buildChoiceControl(parameter) : buildTextControl(parameter);
            case INTEGER, DOUBLE, STRING_LIST -> buildTextControl(parameter);
        };
    }

    private static ChoiceBox<String> buildChoiceControl(Parameter parameter) {
        ChoiceBox<String> choiceBox = new ChoiceBox<>();
        parameter.getPossibleValues().stream().map(Object::toString).forEach(choiceBox.getItems()::add);
        choiceBox.setValue(Objects.toString(parameter.getDefaultValue(), null));
        return choiceBox;
    }

    private static TextField buildTextControl(Parameter parameter) {
        TextField textField = new TextField();
        textField.setText(formatValue(parameter.getDefaultValue()));
        return textField;
    }

    private static String formatValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(Object::toString).reduce((a, b) -> a + "," + b).orElse("");
        }
        return Objects.toString(value, "");
    }

    static Properties toProperties(Map<Parameter, Control> controls) {
        Properties properties = new Properties();
        controls.forEach((parameter, control) -> {
            String value = switch (control) {
                case CheckBox checkBox -> String.valueOf(checkBox.isSelected());
                case ChoiceBox<?> choiceBox -> Objects.toString(choiceBox.getValue(), null);
                case TextField textField -> textField.getText().isBlank() ? null : textField.getText();
                default -> null;
            };
            if (value != null) {
                properties.setProperty(parameter.getName(), value);
            }
        });
        return properties;
    }
}
