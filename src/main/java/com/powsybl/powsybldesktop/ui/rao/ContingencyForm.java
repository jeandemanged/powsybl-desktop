/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.powsybldesktop.rao.CracEdits;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The form of a contingency of the CRAC view: its id and name, changed only when nothing refers to it (OpenRAO can't
 * rename a contingency, so it is replaced by a new one), and its elements, changed in place even when referred to.
 * Each change is applied at once (Enter or focus loss for the text fields) and told {@code onEdited}, with the
 * contingency, a new one after a rename; a refused change is told below the fields and reverted.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class ContingencyForm {

    private final Crac crac;
    private final Network network;
    private final Consumer<Contingency> onEdited;
    private final Function<Set<String>, List<String>> picker;
    private Contingency contingency;
    private final VBox root;
    final TextField idField = new TextField();
    final TextField nameField = new TextField();
    final ListView<ContingencyElement> elementsListView = new ListView<>();
    final Button addElementsButton = new Button(Messages.get("crac.contingency.addElements"));
    final Button removeElementsButton = new Button(Messages.get("desktop.common.action.remove"));
    final Label referencesLabel = new Label();
    final Label problemLabel = new Label();
    final Label errorLabel = new Label();

    /**
     * @param picker the ids of the network objects picked to be added, given those already in the contingency
     */
    ContingencyForm(Crac crac, Network network, Contingency contingency, Consumer<Contingency> onEdited, Function<Set<String>, List<String>> picker) {
        this.crac = crac;
        this.network = network;
        this.contingency = contingency;
        this.onEdited = onEdited;
        this.picker = picker;

        commitOnEnterOrFocusLost(idField, this::rename);
        commitOnEnterOrFocusLost(nameField, this::rename);
        elementsListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        elementsListView.setPrefHeight(200);
        elementsListView.setPlaceholder(new Label(Messages.get("cracEdits.contingency.empty")));
        elementsListView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(ContingencyElement element, boolean empty) {
                super.updateItem(element, empty);
                setText(empty || element == null ? null : elementText(element));
            }
        });
        addElementsButton.setOnAction(_ -> addElements());
        removeElementsButton.disableProperty().bind(elementsListView.getSelectionModel().selectedItemProperty().isNull());
        removeElementsButton.setOnAction(_ -> {
            List<ContingencyElement> elements = new ArrayList<>(this.contingency.getElements());
            elements.removeAll(elementsListView.getSelectionModel().getSelectedItems());
            setElements(elements);
        });
        for (Label label : List.of(referencesLabel, problemLabel, errorLabel)) {
            label.setWrapText(true);
            label.setMinHeight(Region.USE_PREF_SIZE);
            label.managedProperty().bind(label.visibleProperty());
        }
        problemLabel.getStyleClass().add("alert-label");
        errorLabel.getStyleClass().add("alert-label");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.addRow(0, boldLabel("crac.details.id"), idField);
        grid.addRow(1, boldLabel("crac.details.name"), nameField);
        root = new VBox(8, grid, errorLabel, boldLabel("crac.details.elements"), elementsListView,
                new HBox(4, addElementsButton, removeElementsButton), problemLabel, referencesLabel);
        refresh();
    }

    Node root() {
        return root;
    }

    // the one edited: a new one after a rename
    Contingency contingency() {
        return contingency;
    }

    private static Label boldLabel(String key) {
        Label label = new Label(Messages.get(key));
        label.getStyleClass().add("crac-details-label");
        return label;
    }

    private static void commitOnEnterOrFocusLost(TextField field, Runnable commit) {
        field.setOnAction(_ -> commit.run());
        field.focusedProperty().addListener((_, wasFocused, focused) -> {
            if (Boolean.TRUE.equals(wasFocused) && !focused) {
                commit.run();
            }
        });
    }

    private String elementText(ContingencyElement element) {
        Identifiable<?> identifiable = network.getIdentifiable(element.getId());
        String name = identifiable == null ? Messages.get("crac.details.missingElement", element.getId())
                : identifiable.getNameOrId().equals(element.getId()) ? element.getId() : identifiable.getNameOrId() + " (" + element.getId() + ")";
        return name + " — " + element.getType();
    }

    private void refresh() {
        idField.setText(contingency.getId());
        // empty when it is the id, as OpenRAO names a contingency without a name
        nameField.setText(contingency.getName().filter(name -> !name.equals(contingency.getId())).orElse(""));
        elementsListView.setItems(FXCollections.observableArrayList(contingency.getElements()));
        List<String> references = CracEdits.references(crac, contingency.getId());
        referencesLabel.setText(references.isEmpty() ? Messages.get("crac.contingency.notReferenced")
                : Messages.get("crac.contingency.referencedBy", String.join(", ", references)));
        referencesLabel.setVisible(true);
        // a referenced contingency can't be renamed
        idField.setEditable(references.isEmpty());
        nameField.setEditable(references.isEmpty());
        CracEdits.contingencyProblem(network, contingency).ifPresentOrElse(problem -> {
            problemLabel.setText(problem);
            problemLabel.setVisible(true);
        }, () -> problemLabel.setVisible(false));
    }

    private void showError(String message) {
        errorLabel.setText(message == null ? "" : message);
        errorLabel.setVisible(message != null);
    }

    private void rename() {
        try {
            Contingency renamed = CracEdits.rename(crac, contingency, idField.getText(), nameField.getText());
            showError(null);
            if (renamed != contingency) {
                contingency = renamed;
                onEdited.accept(contingency);
            }
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            Contingency putBack = crac.getContingency(contingency.getId());
            if (putBack == null) {
                // not put back (see CracEdits.replace): the CRAC changed all the same
                onEdited.accept(contingency);
            } else if (putBack != contingency) {
                // put back as a new object, the one to edit from now on
                contingency = putBack;
                onEdited.accept(contingency);
            }
        }
        refresh();
    }

    private void addElements() {
        Set<String> present = contingency.getElements().stream().map(ContingencyElement::getId).collect(Collectors.toSet());
        List<String> picked = picker.apply(present);
        if (picked.isEmpty()) {
            return;
        }
        List<ContingencyElement> elements = new ArrayList<>(contingency.getElements());
        try {
            picked.forEach(id -> elements.add(CracEdits.element(network.getIdentifiable(id))));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        setElements(elements);
    }

    private void setElements(List<ContingencyElement> elements) {
        CracEdits.setElements(contingency, elements);
        showError(null);
        refresh();
        onEdited.accept(contingency);
    }
}
