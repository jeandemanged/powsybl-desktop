/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.powsybldesktop.rao.UsageRuleEdits.UsageRuleSpec;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The usage rules of a remedial action form: listed, one asked and added, the selected ones removed. Each change hands
 * all the rules to the form, which replaces its action: a refused change is told below the list, the rules as they
 * were. Read-only for an action that can't be replaced.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class UsageRulesPane {

    /**
     * What the pane takes from the view: the text of a rule, and a rule asked to be added.
     */
    record Context(Function<UsageRuleSpec, String> text, Supplier<Optional<UsageRuleSpec>> ask) {
    }

    private final Context context;
    private final Consumer<List<UsageRuleSpec>> onChange;
    private final VBox root;
    final ListView<UsageRuleSpec> listView = new ListView<>();
    final Button addButton = new Button(null, new FontIcon("mdi2p-plus"));
    final Button removeButton = new Button(null, new FontIcon("mdi2m-minus"));
    final Label errorLabel = new Label();

    /**
     * @param onChange replaces the action with these rules, throwing an {@link IllegalArgumentException} when refused
     */
    UsageRulesPane(Context context, Consumer<List<UsageRuleSpec>> onChange) {
        this.context = context;
        this.onChange = onChange;
        listView.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(UsageRuleSpec rule, boolean empty) {
                super.updateItem(rule, empty);
                setText(empty || rule == null ? null : context.text().apply(rule));
            }
        });
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setPlaceholder(new Label(Messages.get("crac.networkAction.noUsageRule")));
        listView.setFixedCellSize(26);
        listView.setPrefHeight(26 * 4 + 2);
        addButton.setTooltip(new Tooltip(Messages.get("crac.usageRules.add")));
        addButton.setOnAction(_ -> add());
        removeButton.setTooltip(new Tooltip(Messages.get("crac.usageRules.remove")));
        removeButton.setOnAction(_ -> removeSelected());
        FormFields.wrap(errorLabel);
        errorLabel.getStyleClass().add("alert-label");
        errorLabel.setVisible(false);
        root = new VBox(4, listView, new HBox(4, addButton, removeButton), errorLabel);
    }

    VBox root() {
        return root;
    }

    /**
     * Shows {@code rules}, editable or not.
     */
    void setRules(List<UsageRuleSpec> rules, boolean editable) {
        listView.setItems(FXCollections.observableArrayList(rules));
        addButton.setDisable(!editable);
        removeButton.disableProperty().unbind();
        if (editable) {
            removeButton.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        } else {
            removeButton.setDisable(true);
        }
    }

    private void add() {
        context.ask().get().ifPresent(rule -> {
            if (listView.getItems().contains(rule)) {
                showError(Messages.get("usageRuleEdits.duplicate"));
                return;
            }
            List<UsageRuleSpec> rules = new ArrayList<>(listView.getItems());
            rules.add(rule);
            apply(rules);
        });
    }

    private void removeSelected() {
        List<UsageRuleSpec> rules = new ArrayList<>(listView.getItems());
        rules.removeAll(List.copyOf(listView.getSelectionModel().getSelectedItems()));
        apply(rules);
    }

    private void apply(List<UsageRuleSpec> rules) {
        try {
            onChange.accept(rules);
            showError(null);
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null ? "" : message);
        errorLabel.setVisible(message != null);
    }
}
