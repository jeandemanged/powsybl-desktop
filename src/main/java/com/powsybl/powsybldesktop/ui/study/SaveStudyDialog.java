/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.study;

import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.study.Study;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Modal popup choosing what a study saves: the kinds of data, and which root networks.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class SaveStudyDialog {

    record Choice(List<Network> networks, Set<Study.Part> parts) {
    }

    private SaveStudyDialog() {
    }

    static Optional<Choice> show(Window owner, List<Network> networks) {
        Map<Study.Part, CheckBox> partBoxes = new LinkedHashMap<>();
        for (Study.Part part : Study.Part.values()) {
            partBoxes.put(part, checkBox(Messages.get(partKey(part))));
        }
        Map<Network, CheckBox> networkBoxes = new LinkedHashMap<>();
        networks.forEach(network -> networkBoxes.put(network, checkBox(network.getNameOrId())));

        VBox networkList = new VBox(6);
        networkList.getChildren().addAll(networkBoxes.values());
        ScrollPane networkScroll = new ScrollPane(networkList);
        networkScroll.setFitToWidth(true);
        networkScroll.setPrefViewportHeight(Math.min(240, 26.0 * Math.max(1, networks.size())));

        VBox content = new VBox(8);
        content.setPadding(new Insets(4));
        content.getChildren().add(header("study.save.dialog.data", partBoxes.values()));
        content.getChildren().addAll(partBoxes.values());
        content.getChildren().add(header("study.save.dialog.networks", networkBoxes.values()));
        content.getChildren().add(networks.isEmpty() ? new Label(Messages.get("study.save.dialog.noNetwork")) : networkScroll);
        content.setPrefWidth(420);

        ButtonType saveButton = new ButtonType(Messages.get("study.save.dialog.save"), ButtonBar.ButtonData.OK_DONE);
        Dialog<Choice> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.setTitle(Messages.get("study.save.title"));
        dialog.setHeaderText(Messages.get("study.save.dialog.header"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        List<CheckBox> allBoxes = Stream.concat(partBoxes.values().stream(), networkBoxes.values().stream()).toList();
        dialog.getDialogPane().lookupButton(saveButton).disableProperty().bind(Bindings.createBooleanBinding(
                () -> allBoxes.stream().noneMatch(CheckBox::isSelected),
                allBoxes.stream().map(CheckBox::selectedProperty).toArray(Observable[]::new)));

        dialog.setResultConverter(buttonType -> buttonType == saveButton
                ? new Choice(
                        networkBoxes.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList(),
                        partBoxes.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey)
                                .collect(() -> EnumSet.noneOf(Study.Part.class), Set::add, Set::addAll))
                : null);
        return dialog.showAndWait();
    }

    private static CheckBox checkBox(String text) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setSelected(true);
        return checkBox;
    }

    private static HBox header(String key, Collection<CheckBox> boxes) {
        Label title = new Label(Messages.get(key));
        title.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button all = new Button(Messages.get("study.save.dialog.all"));
        all.setOnAction(_ -> boxes.forEach(box -> box.setSelected(true)));
        Button none = new Button(Messages.get("study.save.dialog.none"));
        none.setOnAction(_ -> boxes.forEach(box -> box.setSelected(false)));
        all.setDisable(boxes.isEmpty());
        none.setDisable(boxes.isEmpty());
        HBox header = new HBox(6, title, spacer, all, none);
        header.setPadding(new Insets(6, 0, 0, 0));
        return header;
    }

    private static String partKey(Study.Part part) {
        return switch (part) {
            case PARAMETERS -> "desktop.common.parameters";
            case CONTINGENCIES -> "main.toolbar.contingencies";
            case LOAD_FLOW_RESULTS -> "main.toolbar.loadflowresult";
            case SECURITY_ANALYSIS_RESULTS -> "main.toolbar.securityAnalysisResults";
            case REPORTS -> "main.toolbar.reports";
            case LOGS -> "main.toolbar.logs";
        };
    }
}
