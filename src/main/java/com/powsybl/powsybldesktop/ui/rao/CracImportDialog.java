/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.commons.PowsyblException;
import com.powsybl.entsoe.utils.CapacityCalculationRegion;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.crac.api.parameters.JsonCracCreationParameters;
import com.powsybl.powsybldesktop.rao.CracImportParameters;
import com.powsybl.powsybldesktop.ui.controls.FileChooserPreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Asks for the CRAC creation parameters an import of a CRAC file of a given format needs (see
 * {@link CracImportParameters}): the side of the lines its CNECs monitor by default, and the time stamp (in UTC), capacity
 * calculation region and ICS costs of the formats that use them. An OpenRAO CRAC creation parameters JSON file can be
 * loaded for the others. OK is disabled while a required value is missing. A {@code Dialog} subclass so that its test
 * can reach its controls.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class CracImportDialog extends Dialog<CracCreationParameters> {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final String format;
    private CracCreationParameters parameters;
    final ChoiceBox<CracCreationParameters.MonitoredLineSide> sideChoiceBox =
            new ChoiceBox<>(FXCollections.observableArrayList(CracCreationParameters.MonitoredLineSide.values()));
    final DatePicker datePicker = new DatePicker();
    final TextField timeField = new TextField();
    final ChoiceBox<CapacityCalculationRegion> regionChoiceBox = new ChoiceBox<>(FXCollections.observableArrayList(CapacityCalculationRegion.values()));
    final TextField icsCostUpField = new TextField();
    final TextField icsCostDownField = new TextField();
    final Label parametersFileLabel = new Label(Messages.get("crac.import.parametersFile.none"));
    final Label problemLabel = new Label();

    CracImportDialog(Window owner, String fileName, String format, CracCreationParameters initialParameters) {
        this.format = format;
        this.parameters = CracImportParameters.copy(initialParameters);
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(Messages.get("crac.import.title", fileName));
        setResizable(true);

        sideChoiceBox.setConverter(converter(side -> Messages.get("crac.import.side." + side.name())));
        regionChoiceBox.setConverter(converter(region -> region.name().replace('_', ' ') + " (" + region.getEIC() + ")"));
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
        addRow(grid, "crac.import.side", sideChoiceBox);
        if (CracImportParameters.needsTimestamp(format)) {
            addRow(grid, "crac.import.timestamp", new HBox(8, datePicker, timeField, new Label("UTC")));
        }
        if (CracImportParameters.needsCapacityCalculationRegion(format)) {
            addRow(grid, "crac.import.region", regionChoiceBox);
        }
        if (CracImportParameters.hasIcsCosts(format)) {
            addRow(grid, "crac.import.icsCostUp", icsCostUpField);
            addRow(grid, "crac.import.icsCostDown", icsCostDownField);
        }
        Button parametersFileButton = new Button(Messages.get("crac.import.parametersFile"));
        parametersFileButton.setMinWidth(Region.USE_PREF_SIZE);
        parametersFileButton.setOnAction(_ -> loadParametersFile());
        addRow(grid, "crac.import.otherParameters", new HBox(8, parametersFileButton, parametersFileLabel));

        VBox content = new VBox(10, grid, problemLabel);
        content.setPrefWidth(640);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        refreshFields();
        datePicker.valueProperty().addListener((_, _, _) -> refreshProblems());
        timeField.textProperty().addListener((_, _, _) -> refreshProblems());
        regionChoiceBox.valueProperty().addListener((_, _, _) -> refreshProblems());
        icsCostUpField.textProperty().addListener((_, _, _) -> refreshProblems());
        icsCostDownField.textProperty().addListener((_, _, _) -> refreshProblems());
        refreshProblems();
        setResultConverter(button -> button == ButtonType.OK ? result() : null);
    }

    private static void addRow(GridPane grid, String labelKey, Node control) {
        Label label = new Label(Messages.get(labelKey));
        label.setMinWidth(Region.USE_PREF_SIZE);
        grid.addRow(grid.getRowCount(), label, control);
    }

    private static <T> StringConverter<T> converter(Function<T, String> toString) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : toString.apply(value);
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private void refreshFields() {
        sideChoiceBox.setValue(monitoredLineSide(parameters));
        if (CracImportParameters.needsTimestamp(format)) {
            OffsetDateTime timestamp = CracImportParameters.timestamp(parameters, format);
            OffsetDateTime utc = timestamp == null ? null : timestamp.withOffsetSameInstant(ZoneOffset.UTC);
            datePicker.setValue(utc == null ? null : utc.toLocalDate());
            timeField.setText(utc == null ? "" : utc.format(TIME));
        }
        if (CracImportParameters.needsCapacityCalculationRegion(format)) {
            regionChoiceBox.setValue(CracImportParameters.capacityCalculationRegion(parameters));
        }
        if (CracImportParameters.hasIcsCosts(format)) {
            icsCostUpField.setText(Double.toString(CracImportParameters.flowBasedConstraint(parameters).getIcsCostUp()));
            icsCostDownField.setText(Double.toString(CracImportParameters.flowBasedConstraint(parameters).getIcsCostDown()));
        }
    }

    // the enum value of the default monitored sides, which the parameters only tell as a set of sides
    private static CracCreationParameters.MonitoredLineSide monitoredLineSide(CracCreationParameters parameters) {
        for (CracCreationParameters.MonitoredLineSide side : CracCreationParameters.MonitoredLineSide.values()) {
            CracCreationParameters probe = new CracCreationParameters();
            probe.setDefaultMonitoredLineSide(side);
            if (probe.getDefaultMonitoredSides().equals(parameters.getDefaultMonitoredSides())) {
                return side;
            }
        }
        return CracCreationParameters.MonitoredLineSide.MONITOR_LINES_ON_BOTH_SIDES;
    }

    private void loadParametersFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Messages.get("networks.file.supportedFiles", "json"), "*.json"));
        FileChooserPreferences.applyLastDirectory(fileChooser);
        File file = fileChooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (file != null) {
            FileChooserPreferences.saveLastDirectory(file);
            loadParametersFile(file.toPath());
        }
    }

    void loadParametersFile(Path path) {
        try {
            parameters = JsonCracCreationParameters.read(path);
            parametersFileLabel.setText(path.getFileName().toString());
            refreshFields();
            refreshProblems();
        } catch (PowsyblException | OpenRaoException | UncheckedIOException e) {
            problemLabel.setText(Messages.get("crac.import.parametersFile.failed", path.getFileName(), e.getMessage()));
            problemLabel.setVisible(true);
        }
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

    private static Double number(TextField field) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException _) {
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
        if (CracImportParameters.hasIcsCosts(format) && (number(icsCostUpField) == null || number(icsCostDownField) == null)) {
            problems.add(Messages.get("crac.import.invalidIcsCosts"));
        }
        problemLabel.setText(String.join("\n", problems));
        problemLabel.setVisible(!problems.isEmpty());
        getDialogPane().lookupButton(ButtonType.OK).setDisable(!problems.isEmpty());
    }

    // OK is disabled while a value is missing or invalid
    private CracCreationParameters result() {
        parameters.setDefaultMonitoredLineSide(sideChoiceBox.getValue());
        if (CracImportParameters.needsTimestamp(format)) {
            CracImportParameters.setTimestamp(parameters, format, timestamp());
        }
        if (CracImportParameters.needsCapacityCalculationRegion(format)) {
            CracImportParameters.setCapacityCalculationRegion(parameters, regionChoiceBox.getValue());
        }
        if (CracImportParameters.hasIcsCosts(format)) {
            CracImportParameters.flowBasedConstraint(parameters).setIcsCostUp(number(icsCostUpField));
            CracImportParameters.flowBasedConstraint(parameters).setIcsCostDown(number(icsCostDownField));
        }
        return parameters;
    }

    static String formatName(String format) {
        return Messages.get("crac.format." + format.toLowerCase(Locale.ROOT));
    }
}
