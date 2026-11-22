/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.entsoe.utils.CapacityCalculationRegion;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.powsybldesktop.rao.CracImportParameters;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Asks the values of a CRAC file's import that are the file's own: the time stamp, in UTC, of the formats holding
 * several (CIM, NC, flow-based constraint document), and NC's capacity calculation region. The other CRAC creation
 * parameters are those of the Parameters view's CRAC Creation tab, given as {@code parameters}, whose time stamp and
 * region pre-fill the fields; the result is a copy of them with the values asked. OK is disabled while a required value
 * is missing. A {@code Dialog} subclass so that its test can reach its controls.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class CracImportDialog extends Dialog<CracCreationParameters> {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final String format;
    private final CracCreationParameters parameters;
    final DatePicker datePicker = new DatePicker();
    final TextField timeField = new TextField();
    final ChoiceBox<CapacityCalculationRegion> regionChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(CapacityCalculationRegion.values()));
    final Label problemLabel = new Label();

    CracImportDialog(Window owner, String fileName, String format, CracCreationParameters parameters) {
        this.format = format;
        this.parameters = CracImportParameters.copy(parameters);
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(Messages.get("crac.import.title", fileName));
        setResizable(true);

        regionChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CapacityCalculationRegion region) {
                return region == null ? "" : region.name().replace('_', ' ') + " (" + region.getEIC() + ")";
            }

            @Override
            public CapacityCalculationRegion fromString(String string) {
                throw new UnsupportedOperationException();
            }
        });
        timeField.setPromptText("HH:mm");
        timeField.setPrefColumnCount(5);
        problemLabel.getStyleClass().add("alert-label");
        problemLabel.setWrapText(true);
        problemLabel.managedProperty().bind(problemLabel.visibleProperty());
        // all its lines, wrapped
        problemLabel.setMinHeight(Region.USE_PREF_SIZE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        addRow(grid, "crac.import.format", new Label(formatName(format)));
        if (CracImportParameters.needsTimestamp(format)) {
            addRow(grid, "crac.import.timestamp", new HBox(8, datePicker, timeField, new Label("UTC")));
            OffsetDateTime timestamp = CracImportParameters.timestamp(this.parameters, format);
            OffsetDateTime utc = timestamp == null ? null : timestamp.withOffsetSameInstant(ZoneOffset.UTC);
            datePicker.setValue(utc == null ? null : utc.toLocalDate());
            timeField.setText(utc == null ? "" : utc.format(TIME));
        }
        if (CracImportParameters.needsCapacityCalculationRegion(format)) {
            addRow(grid, "crac.import.region", regionChoiceBox);
            regionChoiceBox.setValue(CracImportParameters.capacityCalculationRegion(this.parameters));
        }
        Label otherParameters = new Label(Messages.get("crac.import.otherParameters.where"));
        otherParameters.setWrapText(true);
        otherParameters.setMinHeight(Region.USE_PREF_SIZE);

        VBox content = new VBox(10, grid, otherParameters, problemLabel);
        content.setPrefWidth(560);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        datePicker.valueProperty().addListener((_, _, _) -> refreshProblems());
        timeField.textProperty().addListener((_, _, _) -> refreshProblems());
        regionChoiceBox.valueProperty().addListener((_, _, _) -> refreshProblems());
        refreshProblems();
        setResultConverter(button -> button == ButtonType.OK ? result() : null);
    }

    private static void addRow(GridPane grid, String labelKey, Node control) {
        Label label = new Label(Messages.get(labelKey));
        label.setMinWidth(Region.USE_PREF_SIZE);
        grid.addRow(grid.getRowCount(), label, control);
    }

    private OffsetDateTime timestamp() {
        if (datePicker.getValue() == null) {
            return null;
        }
        try {
            return datePicker.getValue().atTime(LocalTime.parse(timeField.getText().trim(), TIME)).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException _) {
            return null;
        }
    }

    private void refreshProblems() {
        List<String> problems = new ArrayList<>();
        if (CracImportParameters.needsTimestamp(format) && timestamp() == null) {
            problems.add(Messages.get("crac.import.missingTimestamp"));
        }
        if (CracImportParameters.needsCapacityCalculationRegion(format) && regionChoiceBox.getValue() == null) {
            problems.add(Messages.get("crac.import.missingRegion"));
        }
        problemLabel.setText(String.join("\n", problems));
        problemLabel.setVisible(!problems.isEmpty());
        getDialogPane().lookupButton(ButtonType.OK).setDisable(!problems.isEmpty());
    }

    // OK is disabled while a value is missing or invalid
    private CracCreationParameters result() {
        if (CracImportParameters.needsTimestamp(format)) {
            CracImportParameters.setTimestamp(parameters, format, timestamp());
        }
        if (CracImportParameters.needsCapacityCalculationRegion(format)) {
            CracImportParameters.setCapacityCalculationRegion(parameters, regionChoiceBox.getValue());
        }
        return parameters;
    }

    static String formatName(String format) {
        return Messages.get("crac.format." + format.toLowerCase(Locale.ROOT));
    }
}
