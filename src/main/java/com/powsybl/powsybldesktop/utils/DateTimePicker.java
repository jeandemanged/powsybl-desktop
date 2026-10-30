/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.utils;

import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * A {@link ZonedDateTime} editor: a date picker, hour and minute spinners and a time zone choice (whole hour UTC
 * offsets, and the value's own zone if another one, e.g. Europe/Paris). The value's seconds are kept across edits.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class DateTimePicker extends HBox {

    private static final int MIN_OFFSET_HOURS = -12;
    private static final int MAX_OFFSET_HOURS = 14;

    private final DatePicker datePicker = new DatePicker();
    private final Spinner<Integer> hourSpinner = timeSpinner(23);
    private final Spinner<Integer> minuteSpinner = timeSpinner(59);
    private final ComboBox<ZoneId> zoneComboBox = new ComboBox<>();
    private ZonedDateTime value;
    // set while the controls show a new value, which isn't an edit
    private boolean showing;
    private Consumer<ZonedDateTime> onEdit = _ -> { };

    // for FXML
    public DateTimePicker() {
        this(ZonedDateTime.now().withSecond(0).withNano(0));
    }

    public DateTimePicker(ZonedDateTime initial) {
        super(4);
        setAlignment(Pos.CENTER_LEFT);
        datePicker.setPrefWidth(130);
        zoneComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ZoneId zone) {
                if (zone == null) {
                    return "";
                }
                return zone instanceof ZoneOffset offset ? "UTC" + (offset.equals(ZoneOffset.UTC) ? "" : offset.getId()) : zone.getId();
            }

            @Override
            public ZoneId fromString(String string) {
                throw new UnsupportedOperationException();
            }
        });
        zoneComboBox.getItems().setAll(IntStream.rangeClosed(MIN_OFFSET_HOURS, MAX_OFFSET_HOURS).mapToObj(ZoneOffset::ofHours).toList());
        getChildren().addAll(datePicker, hourSpinner, new Label(":"), minuteSpinner, zoneComboBox);
        // squeezed, e.g. in a form's value column, the spinners' digits are hidden
        getChildren().forEach(child -> ((Region) child).setMinWidth(Region.USE_PREF_SIZE));
        setValue(initial);
        datePicker.valueProperty().addListener((_, _, _) -> edited());
        hourSpinner.valueProperty().addListener((_, _, _) -> edited());
        minuteSpinner.valueProperty().addListener((_, _, _) -> edited());
        zoneComboBox.valueProperty().addListener((_, _, _) -> edited());
    }

    public ZonedDateTime getValue() {
        return value;
    }

    /**
     * Shows {@code value}, which isn't reported as an edit.
     */
    public void setValue(ZonedDateTime value) {
        this.value = Objects.requireNonNull(value);
        showing = true;
        try {
            datePicker.setValue(value.toLocalDate());
            hourSpinner.getValueFactory().setValue(value.getHour());
            minuteSpinner.getValueFactory().setValue(value.getMinute());
            if (!zoneComboBox.getItems().contains(value.getZone())) {
                zoneComboBox.getItems().addFirst(value.getZone());
            }
            // replacing the items instead left the same value unselected, shown blank
            zoneComboBox.getSelectionModel().select(value.getZone());
        } finally {
            showing = false;
        }
    }

    /**
     * Called with the new value whenever the user changes it.
     */
    public void setOnEdit(Consumer<ZonedDateTime> onEdit) {
        this.onEdit = Objects.requireNonNull(onEdit);
    }

    private void edited() {
        LocalDate date = datePicker.getValue();
        // the date field cleared: the previous date stays
        if (showing || date == null) {
            return;
        }
        LocalTime time = LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue(), value.getSecond(), value.getNano());
        value = ZonedDateTime.of(date, time, zoneComboBox.getValue());
        onEdit.accept(value);
    }

    private static Spinner<Integer> timeSpinner(int max) {
        Spinner<Integer> spinner = new Spinner<>();
        SpinnerValueFactory.IntegerSpinnerValueFactory factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, max);
        factory.setWrapAround(true);
        // two digits, and text that isn't a number in range leaves the value as it was instead of throwing
        factory.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer number) {
                return number == null ? "" : String.format(Locale.ROOT, "%02d", number);
            }

            @Override
            public Integer fromString(String string) {
                try {
                    int number = Integer.parseInt(string.trim());
                    return number >= 0 && number <= max ? number : factory.getValue();
                } catch (NumberFormatException _) {
                    return factory.getValue();
                }
            }
        });
        spinner.setValueFactory(factory);
        spinner.setEditable(true);
        spinner.setPrefWidth(65);
        // typed text is otherwise only committed on Enter
        spinner.focusedProperty().addListener((_, _, focused) -> {
            if (!focused) {
                spinner.getEditor().setText(factory.getConverter().toString(factory.getConverter().fromString(spinner.getEditor().getText())));
                spinner.commitValue();
            }
        });
        return spinner;
    }
}
