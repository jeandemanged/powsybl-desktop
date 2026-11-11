/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.sensitivity;

import com.powsybl.powsybldesktop.sensitivity.SensitivityElements;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks elements among candidates: a searchable checklist, without the ones already picked.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class ElementPickerDialog extends Dialog<List<String>> {

    private final Map<SensitivityElements.Candidate, BooleanProperty> checked = new LinkedHashMap<>();
    final TextField searchField = new TextField();
    final ListView<SensitivityElements.Candidate> listView = new ListView<>();
    final Button selectShownButton = new Button(Messages.get("sensitivityFactors.picker.selectShown"));

    ElementPickerDialog(Window owner, String title, List<SensitivityElements.Candidate> candidates, Set<String> excludedIds) {
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(title);
        setResizable(true);
        candidates.stream().filter(candidate -> !excludedIds.contains(candidate.id()))
                .forEach(candidate -> checked.put(candidate, new SimpleBooleanProperty()));
        FilteredList<SensitivityElements.Candidate> shown = new FilteredList<>(FXCollections.observableArrayList(checked.keySet()));
        searchField.setPromptText(Messages.get("desktop.common.search.prompt"));
        searchField.textProperty().addListener((_, _, text) -> {
            String filter = text.toLowerCase(Locale.ROOT);
            shown.setPredicate(candidate -> filter.isBlank() || candidate.id().toLowerCase(Locale.ROOT).contains(filter)
                    || candidate.name().toLowerCase(Locale.ROOT).contains(filter) || candidate.kind().toLowerCase(Locale.ROOT).contains(filter));
        });
        listView.setItems(shown);
        listView.setPlaceholder(new Label(Messages.get("sensitivityFactors.picker.none")));
        listView.setCellFactory(CheckBoxListCell.forListView(checked::get, new StringConverter<>() {
            @Override
            public String toString(SensitivityElements.Candidate candidate) {
                return candidate == null ? "" : label(candidate);
            }

            @Override
            public SensitivityElements.Candidate fromString(String string) {
                throw new UnsupportedOperationException();
            }
        }));
        selectShownButton.setOnAction(_ -> shown.forEach(candidate -> checked.get(candidate).set(true)));
        Button unselectShown = new Button(Messages.get("sensitivityFactors.picker.unselectShown"));
        unselectShown.setOnAction(_ -> shown.forEach(candidate -> checked.get(candidate).set(false)));
        HBox.setHgrow(searchField, Priority.ALWAYS);
        VBox content = new VBox(8, new HBox(8, searchField, selectShownButton, unselectShown), listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
        content.setPrefSize(560, 480);
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        setResultConverter(button -> button == ButtonType.OK
                ? checked.entrySet().stream().filter(e -> e.getValue().get()).map(e -> e.getKey().id()).toList()
                : List.of());
    }

    static String label(SensitivityElements.Candidate candidate) {
        String name = candidate.name().equals(candidate.id()) ? candidate.id() : candidate.name() + " (" + candidate.id() + ")";
        return name + " — " + candidate.kind();
    }
}
