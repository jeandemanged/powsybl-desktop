/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.commons.parameters.Parameter;
import com.powsybl.iidm.network.Exporter;
import com.powsybl.iidm.network.Importer;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.utils.FileChooserPreferences;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Modal dialog collecting an output file and the {@link Exporter#getParameters()} values for a network export.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public final class ExportNetworkDialog {

    private static final double MAX_DIALOG_WIDTH = 800;

    public record Request(Path outputPath, Properties parameters) {
    }

    private ExportNetworkDialog() {
    }

    public static Optional<Request> show(Window owner, Network network, String format, Exporter exporter) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.setTitle(Messages.get("networks.export.dialogTitle", format));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        ParameterFormBuilder.configureColumns(grid);

        TextField pathField = new TextField();
        pathField.setPrefWidth(350);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        FileChooser.ExtensionFilter allFiles = new FileChooser.ExtensionFilter(Messages.get("networks.file.allFiles"), "*.*");
        // Exporter doesn't expose file extensions; the matching importer for the same format does.
        Importer matchingImporter = Importer.find(format);
        List<String> baseExtensions = matchingImporter == null ? List.of() : matchingImporter.getSupportedExtensions();
        List<String> extensions = FileChooserPreferences.extensionPatterns(baseExtensions);
        Button browseButton = new Button(Messages.get("networks.file.browse"));
        browseButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            if (!extensions.isEmpty()) {
                fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                        Messages.get("networks.file.supportedFiles", String.join(", ", baseExtensions)), extensions));
            }
            fileChooser.getExtensionFilters().add(allFiles);
            FileChooserPreferences.applyLastDirectory(fileChooser);
            fileChooser.setInitialFileName(network.getNameOrId());
            File selectedFile = fileChooser.showSaveDialog(owner);
            if (selectedFile != null) {
                FileChooserPreferences.saveLastDirectory(selectedFile);
                pathField.setText(selectedFile.getAbsolutePath());
            }
        });
        HBox outputBox = new HBox(5, pathField, browseButton);
        outputBox.setMaxWidth(Double.MAX_VALUE);
        grid.addRow(0, new Label(Messages.get("networks.export.outputFile")), outputBox);

        Map<Parameter, Control> controls = ParameterFormBuilder.addRows(grid, 1, exporter.getParameters());

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(400);
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().setMaxWidth(MAX_DIALOG_WIDTH);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(pathField.textProperty().isEmpty());

        return dialog.showAndWait()
                .filter(ButtonType.OK::equals)
                .map(result -> new Request(Path.of(pathField.getText()), ParameterFormBuilder.toProperties(controls)));
    }
}
