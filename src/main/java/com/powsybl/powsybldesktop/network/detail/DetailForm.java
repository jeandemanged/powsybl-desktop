/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.detail;

import com.powsybl.commons.PowsyblException;
import com.powsybl.powsybldesktop.network.tables.TableColumnSupport;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.controlsfx.control.SearchableComboBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * An equipment detail's form: labeled rows grouped in titled sections, each editor writing straight to the IIDM object
 * when committed (Enter or focus loss, Escape reverts), with the tables' edit feedback (error popup and red flash, green
 * flash) and disabled while not {@link #editableProperty editable} (the network busy lock). {@link #refresh} re-reads
 * every row, e.g. after a network update, except a field the user is typing in. Shared by every equipment detail view.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class DetailForm implements DetailPart {

    public static final String MW = "MW";
    public static final String MVAR = "Mvar";
    public static final String MVA = "MVA";
    public static final String KV = "kV";
    public static final String A = "A";
    public static final String DEGREE = "°";
    public static final String OHM = "Ω";
    public static final String PERCENT = "%";
    public static final String SIEMENS = "S";

    private static final String MISSING = "-";

    private final GridPane root = new GridPane();
    private final List<Runnable> refreshers = new ArrayList<>();
    private final BooleanProperty editable = new SimpleBooleanProperty(true);
    private final Supplier<GuiParameters> guiParameters;
    private final Runnable onEdit;
    private Supplier<?> structure = () -> null;
    private Object shownStructure;
    private int row;

    /**
     * @param onEdit run after every applied edit, e.g. {@code mainModel.setUpdate(...)}
     */
    public DetailForm(Supplier<GuiParameters> guiParameters, Runnable onEdit) {
        this.guiParameters = Objects.requireNonNull(guiParameters);
        this.onEdit = Objects.requireNonNull(onEdit);
        root.getStyleClass().add("detail-form");
        // one grid for all sections, so that their columns line up
        root.setHgap(8);
        root.setVgap(4);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(170);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setPrefWidth(240);
        ColumnConstraints unitColumn = new ColumnConstraints();
        unitColumn.setMinWidth(30);
        root.getColumnConstraints().setAll(labelColumn, valueColumn, unitColumn);
    }

    public Node getNode() {
        return root;
    }

    public Supplier<GuiParameters> getGuiParameters() {
        return guiParameters;
    }

    @Override
    public BooleanProperty editableProperty() {
        return editable;
    }

    @Override
    public void refresh() {
        refreshers.forEach(Runnable::run);
    }

    /**
     * Declares what the rows were built from, e.g. a list of measurements, for {@link #isStale} to tell when its owner
     * must rebuild the form.
     */
    public DetailForm structure(Supplier<?> structure) {
        this.structure = Objects.requireNonNull(structure);
        shownStructure = structure.get();
        return this;
    }

    public boolean isStale() {
        return !Objects.equals(structure.get(), shownStructure);
    }

    /**
     * Starts a new section: a title, or a separator if {@code titleKey} is null.
     */
    public DetailForm section(String titleKey, Object... args) {
        Node header;
        if (titleKey != null) {
            Label title = new Label(args.length == 0 ? Messages.get(titleKey) : Messages.get(titleKey, args));
            title.getStyleClass().add("section-title");
            header = title;
        } else {
            header = new Separator();
        }
        if (row > 0) {
            GridPane.setMargin(header, new Insets(8, 0, 0, 0));
        }
        root.add(header, 0, row++, 3, 1);
        return this;
    }

    public DetailForm text(String labelKey, Supplier<String> getter) {
        Label value = new Label();
        return node(labelKey, value, null, () -> value.setText(textOrMissing(getter.get())));
    }

    public DetailForm value(String labelKey, String unit, GuiParameters.Quantity quantity, DoubleSupplier getter) {
        Label value = new Label();
        return node(labelKey, value, unit, () -> value.setText(format(quantity, getter.getAsDouble())));
    }

    /**
     * A double editor: blank or "-" sets NaN, which IIDM refuses where a value is mandatory.
     */
    public DetailForm doubleField(String labelKey, String unit, GuiParameters.Quantity quantity, DoubleSupplier getter, DoubleConsumer setter) {
        return textEditor(labelKey, unit, () -> format(quantity, getter.getAsDouble()), text -> setter.accept(parseDouble(text)));
    }

    /**
     * An integer editor: blank or "-" sets null, i.e. no value.
     */
    public DetailForm intField(String labelKey, Supplier<Integer> getter, Consumer<Integer> setter) {
        return textEditor(labelKey, null, () -> getter.get() == null ? MISSING : getter.get().toString(), text -> setter.accept(parseInt(text)));
    }

    /**
     * A text editor: blank or "-" sets null.
     */
    public DetailForm textField(String labelKey, Supplier<String> getter, Consumer<String> setter) {
        return textEditor(labelKey, null, () -> textOrMissing(getter.get()), text -> setter.accept(isMissing(text) ? null : text.trim()));
    }

    public DetailForm booleanField(String labelKey, BooleanSupplier getter, Consumer<Boolean> setter) {
        CheckBox checkBox = new CheckBox();
        checkBox.disableProperty().bind(editable.not());
        checkBox.setOnAction(event -> {
            apply(checkBox, () -> setter.accept(checkBox.isSelected()));
            checkBox.setSelected(getter.getAsBoolean());
        });
        return node(labelKey, checkBox, null, () -> checkBox.setSelected(getter.getAsBoolean()));
    }

    public <T> DetailForm choiceField(String labelKey, List<T> choices, StringConverter<T> converter, Supplier<T> getter, Consumer<T> setter) {
        ChoiceBox<T> choiceBox = new ChoiceBox<>();
        choiceBox.getItems().setAll(choices);
        choiceBox.setConverter(converter);
        return choiceField(labelKey, choiceBox, choiceBox.valueProperty(), getter, setter);
    }

    /**
     * Like {@link #choiceField}, with a search field filtering long lists of choices (e.g. countries).
     */
    public <T> DetailForm searchableChoiceField(String labelKey, List<T> choices, StringConverter<T> converter, Supplier<T> getter, Consumer<T> setter) {
        SearchableComboBox<T> comboBox = new SearchableComboBox<>(FXCollections.observableArrayList(choices));
        comboBox.setConverter(converter);
        return choiceField(labelKey, comboBox, comboBox.valueProperty(), getter, setter);
    }

    private <T> DetailForm choiceField(String labelKey, Control control, ObjectProperty<T> value, Supplier<T> getter, Consumer<T> setter) {
        control.disableProperty().bind(editable.not());
        boolean[] refreshing = {false};
        Runnable refresher = () -> {
            refreshing[0] = true;
            value.set(getter.get());
            refreshing[0] = false;
        };
        value.addListener((observable, oldValue, newValue) -> {
            if (!refreshing[0] && newValue != null && !newValue.equals(getter.get())) {
                apply(control, () -> setter.accept(newValue));
                // reverting a rejected value from within this change would leave the skin's popup checking it
                Platform.runLater(refresher);
            }
        });
        return node(labelKey, control, null, refresher);
    }

    /**
     * A custom row, e.g. a link with edit buttons; {@code refresher} re-reads it, may be null.
     */
    public DetailForm node(String labelKey, Node node, String unit, Runnable refresher) {
        Label label = new Label(Messages.get(labelKey));
        GridPane.setHalignment(label, HPos.LEFT);
        root.add(label, 0, row);
        root.add(node, 1, row);
        if (node instanceof Control control) {
            control.setMaxWidth(Double.MAX_VALUE);
        }
        if (unit != null) {
            root.add(new Label(unit), 2, row);
        }
        row++;
        if (refresher != null) {
            refreshers.add(refresher);
            refresher.run();
        }
        return this;
    }

    /**
     * Runs {@code edit} with the edit feedback on {@code node}, then this form's onEdit if applied.
     */
    public boolean apply(Node node, Runnable edit) {
        return apply(node, edit, onEdit);
    }

    /**
     * Runs {@code edit} with the edit feedback on {@code node}, then {@code onEdit} if applied.
     */
    public static boolean apply(Node node, Runnable edit, Runnable onEdit) {
        try {
            edit.run();
        } catch (PowsyblException e) {
            TableColumnSupport.notifyEditError(node, e);
            return false;
        } catch (IllegalArgumentException e) {
            // some IIDM extensions validate with IllegalArgumentException (e.g. GeneratorEntsoeCategory's code)
            TableColumnSupport.notifyEditError(node, new PowsyblException(e.getMessage(), e));
            return false;
        }
        TableColumnSupport.flashEditSuccess(node);
        onEdit.run();
        return true;
    }

    private DetailForm textEditor(String labelKey, String unit, Supplier<String> display, Consumer<String> commit) {
        TextField field = new TextField();
        field.setPrefColumnCount(12);
        field.disableProperty().bind(editable.not());
        String[] shown = {null};
        Runnable show = () -> {
            shown[0] = display.get();
            field.setText(shown[0]);
        };
        Runnable commitIfChanged = () -> {
            if (!field.getText().equals(shown[0])) {
                apply(field, () -> commit.accept(field.getText()));
                show.run();
            }
        };
        field.setOnAction(event -> commitIfChanged.run());
        field.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (!focused) {
                commitIfChanged.run();
            }
        });
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                show.run();
            }
        });
        return node(labelKey, field, unit, () -> {
            // leaves alone what the user is typing
            if (!field.isFocused() || field.getText().equals(shown[0])) {
                show.run();
            }
        });
    }

    private String format(GuiParameters.Quantity quantity, double value) {
        return Double.isNaN(value) ? MISSING : guiParameters.get().format(quantity, value);
    }

    private static String textOrMissing(String text) {
        return text == null || text.isBlank() ? MISSING : text;
    }

    private static boolean isMissing(String text) {
        return text.isBlank() || MISSING.equals(text.trim());
    }

    /**
     * {@code value}, or an edit error if null, for the integer fields of mandatory values.
     */
    public static int mandatory(Integer value) {
        if (value == null) {
            throw new PowsyblException(Messages.get("desktop.common.editError.mandatory"));
        }
        return value;
    }

    static double parseDouble(String text) {
        if (isMissing(text)) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new PowsyblException(Messages.get("desktop.common.editError.invalidNumber", text.trim()));
        }
    }

    private static Integer parseInt(String text) {
        if (isMissing(text)) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            throw new PowsyblException(Messages.get("desktop.common.editError.invalidNumber", text.trim()));
        }
    }
}
