/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.edit;

import com.powsybl.commons.PowsyblException;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.controlsfx.control.SearchableComboBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The form of a creation dialog: the same labeled rows and sections as an equipment detail's
 * {@link com.powsybl.powsybldesktop.network.detail.DetailForm}, but whose editors only hold values, read when the
 * dialog applies them. A value that can't be read throws a {@link FieldException} naming its editor, for the dialog to
 * show the error there.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class CreationForm {

    private final GridPane root = new GridPane();
    private final List<ObservableValue<String>> requiredTexts = new ArrayList<>();
    private int row;

    public CreationForm() {
        root.getStyleClass().add("detail-form");
        root.setHgap(8);
        root.setVgap(4);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(170);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setPrefWidth(260);
        ColumnConstraints unitColumn = new ColumnConstraints();
        unitColumn.setMinWidth(30);
        root.getColumnConstraints().setAll(labelColumn, valueColumn, unitColumn);
    }

    public Node getNode() {
        return root;
    }

    /**
     * Whether every required text field is filled in: the dialog's OK button is disabled otherwise.
     */
    public BooleanBinding validProperty() {
        return Bindings.createBooleanBinding(() -> requiredTexts.stream().allMatch(text -> text.getValue() != null && !text.getValue().isBlank()),
                requiredTexts.toArray(ObservableValue[]::new));
    }

    /**
     * A value whose reading failed, to show on its editor.
     */
    public static final class FieldException extends PowsyblException {
        private final transient Node editor;

        FieldException(Node editor, String message) {
            super(message);
            this.editor = editor;
        }

        public Node getEditor() {
            return editor;
        }
    }

    /**
     * One row: its editor and the value read from it.
     */
    public static final class Field<T> {
        private final List<Node> nodes;
        private final Node editor;
        private final Supplier<T> reader;

        private Field(List<Node> nodes, Node editor, Supplier<T> reader) {
            this.nodes = nodes;
            this.editor = editor;
            this.reader = reader;
        }

        /**
         * The value, throwing a {@link FieldException} if it can't be read.
         */
        public T get() {
            return reader.get();
        }

        public Node getEditor() {
            return editor;
        }

        /**
         * Shows or hides the row, e.g. the rows of a topology kind not chosen.
         */
        public Field<T> visibleWhen(ObservableValue<Boolean> visible) {
            nodes.forEach(node -> {
                node.visibleProperty().bind(visible);
                node.managedProperty().bind(visible);
            });
            return this;
        }

        public boolean isVisible() {
            return editor.isVisible();
        }
    }

    public CreationForm section(String titleKey, Object... args) {
        return title(args.length == 0 ? Messages.get(titleKey) : Messages.get(titleKey, args));
    }

    /**
     * Starts a new section titled {@code text}.
     */
    public CreationForm title(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("section-title");
        if (row > 0) {
            GridPane.setMargin(title, new Insets(8, 0, 0, 0));
        }
        root.add(title, 0, row++, 3, 1);
        return this;
    }

    /**
     * A text editor; blank reads as null, an error if {@code required}.
     */
    public Field<String> text(String labelKey, String initial, boolean required) {
        TextField field = textField(initial);
        if (required) {
            requiredTexts.add(field.textProperty());
        }
        return row(labelKey, field, null, () -> {
            String text = field.getText() == null ? "" : field.getText().trim();
            if (text.isEmpty()) {
                if (required) {
                    throw new FieldException(field, Messages.get("desktop.common.editError.mandatory"));
                }
                return null;
            }
            return text;
        });
    }

    /**
     * The required id of an object to create in {@code network}, which must not be taken yet (by any network object).
     */
    public Field<String> id(Network network, String initial) {
        return id(network, initial, "desktop.common.column.id");
    }

    /**
     * Same as {@link #id(Network, String)}, labeled {@code labelKey}, e.g. for the id of another object than the
     * dialog's.
     */
    public Field<String> id(Network network, String initial, String labelKey) {
        Field<String> text = text(labelKey, initial, true);
        return new Field<>(text.nodes, text.editor, () -> {
            String id = text.get();
            if (network != null && network.getIdentifiable(id) != null) {
                throw new FieldException(text.editor, Messages.get("edit.create.idTaken", id));
            }
            return id;
        });
    }

    /**
     * A double editor; blank reads as NaN, an error if {@code required}.
     */
    public Field<Double> number(String labelKey, String unit, double initial, boolean required) {
        TextField field = textField(Double.isNaN(initial) ? "" : format(initial));
        if (required) {
            requiredTexts.add(field.textProperty());
        }
        return row(labelKey, field, unit, () -> {
            String text = field.getText() == null ? "" : field.getText().trim();
            if (text.isEmpty()) {
                if (required) {
                    throw new FieldException(field, Messages.get("desktop.common.editError.mandatory"));
                }
                return Double.NaN;
            }
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                throw new FieldException(field, Messages.get("desktop.common.editError.invalidNumber", text));
            }
        });
    }

    public Field<Integer> integer(String labelKey, int initial) {
        return integer(labelKey, initial, true);
    }

    /**
     * An integer editor; blank reads as null, an error if {@code required}.
     */
    public Field<Integer> integer(String labelKey, Integer initial, boolean required) {
        TextField field = textField(initial == null ? "" : String.valueOf(initial));
        if (required) {
            requiredTexts.add(field.textProperty());
        }
        return row(labelKey, field, null, () -> {
            String text = field.getText() == null ? "" : field.getText().trim();
            if (text.isEmpty() && !required) {
                return null;
            }
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException e) {
                throw new FieldException(field, Messages.get("desktop.common.editError.invalidNumber", text));
            }
        });
    }

    public Field<Boolean> check(String labelKey, boolean initial) {
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(initial);
        return row(labelKey, checkBox, null, checkBox::isSelected);
    }

    /**
     * A choice among {@code choices}; none chosen reads as null, an error if {@code required}.
     */
    public <T> Field<T> choice(String labelKey, List<T> choices, StringConverter<T> converter, T initial, boolean required) {
        return choice(labelKey, new ComboBox<>(), choices, converter, initial, required);
    }

    /**
     * Like {@link #choice}, with a search field filtering long lists of choices (e.g. countries).
     */
    public <T> Field<T> searchableChoice(String labelKey, List<T> choices, StringConverter<T> converter, T initial, boolean required) {
        return choice(labelKey, new SearchableComboBox<>(), choices, converter, initial, required);
    }

    private <T> Field<T> choice(String labelKey, ComboBox<T> comboBox, List<T> choices, StringConverter<T> converter, T initial, boolean required) {
        comboBox.getItems().setAll(choices);
        comboBox.setConverter(converter);
        comboBox.setValue(initial);
        comboBox.setVisibleRowCount(15);
        if (required) {
            requiredTexts.add(Bindings.createStringBinding(() -> comboBox.getValue() == null ? null : "x", comboBox.valueProperty()));
        }
        return row(labelKey, comboBox, null, () -> {
            if (required && comboBox.getValue() == null) {
                throw new FieldException(comboBox, Messages.get("desktop.common.editError.mandatory"));
            }
            return comboBox.getValue();
        });
    }

    /**
     * A custom row, whose value is read by {@code reader}.
     */
    public <T> Field<T> custom(String labelKey, Node editor, String unit, Supplier<T> reader) {
        return row(labelKey, editor, unit, reader);
    }

    /**
     * Shows a network object as its name or id, nothing for null.
     */
    public static <T extends Identifiable<?>> StringConverter<T> identifiableFormat() {
        return new StringConverter<>() {
            @Override
            public String toString(T identifiable) {
                return identifiable == null ? "" : identifiable.getNameOrId();
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static TextField textField(String initial) {
        TextField field = new TextField(initial);
        field.setPrefColumnCount(14);
        return field;
    }

    private <T> Field<T> row(String labelKey, Node editor, String unit, Supplier<T> reader) {
        List<Node> nodes = new ArrayList<>();
        Label label = new Label(Messages.get(labelKey));
        GridPane.setHalignment(label, HPos.LEFT);
        root.add(label, 0, row);
        nodes.add(label);
        root.add(editor, 1, row);
        nodes.add(editor);
        if (editor instanceof Control control) {
            control.setMaxWidth(Double.MAX_VALUE);
        }
        if (unit != null) {
            Label unitLabel = new Label(unit);
            root.add(unitLabel, 2, row);
            nodes.add(unitLabel);
        }
        row++;
        return new Field<>(nodes, editor, Objects.requireNonNull(reader));
    }

    // without a trailing ".0" for integral values, which most initial values are
    private static String format(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? String.valueOf((long) value) : String.valueOf(value);
    }
}
