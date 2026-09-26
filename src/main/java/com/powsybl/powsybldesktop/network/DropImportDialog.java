/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network;

import com.powsybl.iidm.network.Importer;
import com.powsybl.powsybldesktop.network.NetworksController.DroppedPath;
import com.powsybl.powsybldesktop.network.NetworksController.ImportChoice;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modal popup shown when dropped files or folders can't all be imported without asking: lists each of them with the
 * format it will be imported as, a format choice when several formats recognize it, or that it will be skipped.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class DropImportDialog {

    private DropImportDialog() {
    }

    /** @return the files or folders to import, in drop order, or empty if the user cancelled */
    static Optional<List<ImportChoice>> show(Window owner, List<DroppedPath> dropped) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.setPadding(new Insets(4));
        ColumnConstraints nameColumn = new ColumnConstraints();
        nameColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(new ColumnConstraints(), nameColumn, new ColumnConstraints());
        grid.addRow(0, new Label(), header("networks.drop.dialog.file"), header("networks.drop.dialog.format"));

        List<ComboBox<String>> formatChoices = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        for (DroppedPath droppedPath : dropped) {
            Label name = new Label(droppedPath.path().getFileName().toString());
            name.setTooltip(new Tooltip(droppedPath.path().toString()));
            Node action;
            ComboBox<String> formatChoice = null;
            Map<String, List<Importer>> accepting = droppedPath.accepting();
            if (accepting.isEmpty()) {
                Label unsupported = new Label(Messages.get("networks.drop.dialog.unsupported"));
                unsupported.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
                action = unsupported;
            } else if (accepting.size() == 1) {
                action = new Label(accepting.keySet().iterator().next());
            } else {
                formatChoice = new ComboBox<>();
                formatChoice.getItems().addAll(accepting.keySet());
                formatChoice.setPromptText(Messages.get("networks.drop.dialog.chooseFormat"));
                formatChoice.setMaxWidth(Double.MAX_VALUE);
                formatChoices.add(formatChoice);
                action = formatChoice;
            }
            String icon = Files.isDirectory(droppedPath.path()) ? "mdi2f-folder-outline" : "mdi2f-file-outline";
            grid.addRow(grid.getRowCount(), new FontIcon(icon), name, action);
            rows.add(new Row(droppedPath, formatChoice));
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(Math.min(400, 32.0 * (dropped.size() + 1)));
        scrollPane.setPrefViewportWidth(520);

        ButtonType importButton = new ButtonType(Messages.get("networks.drop.dialog.import"), ButtonBar.ButtonData.OK_DONE);
        Dialog<List<ImportChoice>> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.setTitle(Messages.get("networks.drop.dialog.title"));
        dialog.setHeaderText(Messages.get("networks.drop.dialog.header"));
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(importButton, ButtonType.CANCEL);

        dialog.getDialogPane().lookupButton(importButton).disableProperty().bind(Bindings.createBooleanBinding(
                () -> formatChoices.stream().anyMatch(choice -> choice.getValue() == null),
                formatChoices.stream().map(ComboBox::valueProperty).toArray(Observable[]::new)));

        dialog.setResultConverter(buttonType -> buttonType == importButton
                ? rows.stream().map(Row::toChoice).flatMap(Optional::stream).toList()
                : null);
        return dialog.showAndWait();
    }

    private static Label header(String key) {
        Label label = new Label(Messages.get(key));
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private record Row(DroppedPath droppedPath, ComboBox<String> formatChoice) {
        Optional<ImportChoice> toChoice() {
            Map<String, List<Importer>> accepting = droppedPath.accepting();
            if (accepting.isEmpty()) {
                return Optional.empty();
            }
            String format = formatChoice != null ? formatChoice.getValue() : accepting.keySet().iterator().next();
            return Optional.of(new ImportChoice(droppedPath.path(), format, accepting.get(format)));
        }
    }
}
