/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.network.tables;

import com.powsybl.iidm.network.Substation;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.ArrayList;

/**
 * Modal popup listing a substation's geographical tags in a table, with a field to add new ones. PowSyBl's
 * {@link Substation#addGeographicalTag(String)} has no matching remove method and {@link Substation#getGeographicalTags()}
 * returns an unmodifiable set, so tags can only be added here, not removed.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class GeographicalTagsDialog {

    private GeographicalTagsDialog() {
    }

    static void show(Window owner, Substation substation) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.setTitle(Messages.get("substations.geographicalTags.dialogTitle", substation.getNameOrId()));

        TableView<String> tagsTableView = new TableView<>();
        tagsTableView.setEditable(false);
        TableColumn<String, String> tagColumn = new TableColumn<>(Messages.get("substations.geographicalTags.tagColumn"));
        tagColumn.setSortable(false);
        tagColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue()));
        tagsTableView.getColumns().add(tagColumn);
        tagsTableView.setPrefHeight(250);
        refreshTags(tagsTableView, substation);

        TextField newTagField = new TextField();
        newTagField.setPromptText(Messages.get("substations.geographicalTags.newTagPrompt"));
        HBox.setHgrow(newTagField, Priority.ALWAYS);
        Button addButton = new Button(Messages.get("substations.geographicalTags.addButton"));
        Runnable addTag = () -> {
            String tag = newTagField.getText().trim();
            if (!tag.isEmpty()) {
                substation.addGeographicalTag(tag);
                refreshTags(tagsTableView, substation);
                newTagField.clear();
            }
        };
        addButton.setOnAction(event -> addTag.run());
        newTagField.setOnAction(event -> addTag.run());

        VBox content = new VBox(8, tagsTableView, new HBox(5, newTagField, addButton));
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    private static void refreshTags(TableView<String> tagsTableView, Substation substation) {
        tagsTableView.getItems().setAll(new ArrayList<>(substation.getGeographicalTags()));
    }
}
