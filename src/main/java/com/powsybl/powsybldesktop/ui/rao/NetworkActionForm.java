/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.powsybldesktop.rao.CracElements;
import com.powsybl.powsybldesktop.rao.NetworkActionEdits;
import com.powsybl.powsybldesktop.rao.NetworkActionEdits.ElementaryActionSpec;
import com.powsybl.powsybldesktop.rao.NetworkActionEdits.NetworkActionSpec;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits;
import com.powsybl.powsybldesktop.ui.controls.RowsTableEditor;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The form of a network action of the CRAC view: its id, name, operator, speed, activation cost and elementary actions.
 * OpenRAO's network actions are immutable: each change replaces the action by a new one with the same usage rules
 * ({@link NetworkActionEdits#change}), applied at once (Enter or focus loss for the text fields, each edit for the
 * actions) and told {@code onEdited}; a refused change is told below the fields and the fields reverted (the actions
 * kept as typed, to be fixed). Its usage rules are replaced likewise ({@link UsageRulesPane}, {@link UsageRuleEdits#set}).
 * An action with an elementary action OpenRAO's adders don't make is read-only.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class NetworkActionForm {

    private final Crac crac;
    private final Network network;
    private final Consumer<NetworkAction> onEdited;
    private NetworkAction action;
    private boolean refreshing;
    private final VBox root;
    final TextField idField = new TextField();
    final TextField nameField = new TextField();
    final TextField operatorField = new TextField();
    final TextField speedField = new TextField();
    final TextField activationCostField = new TextField();
    final RowsTableEditor actionsEditor = new RowsTableEditor(List.of(Messages.get("crac.networkAction.column.kind"),
            Messages.get("crac.networkAction.column.element"), Messages.get("crac.networkAction.column.value")));
    final Button addActionsButton = new Button(Messages.get("crac.networkAction.addActions"));
    final UsageRulesPane usageRulesPane;
    final Label problemLabel = new Label();
    final Label errorLabel = new Label();

    /**
     * @param usageRules what the usage rules pane takes from the view
     * @param picker    the ids of the network elements picked to be acted on, given those already acted on
     */
    NetworkActionForm(Crac crac, Network network, NetworkAction action, Consumer<NetworkAction> onEdited, UsageRulesPane.Context usageRules,
                      Function<Set<String>, List<String>> picker) {
        this.crac = crac;
        this.network = network;
        this.action = action;
        this.onEdited = onEdited;

        for (TextField field : List.of(idField, nameField, operatorField, speedField, activationCostField)) {
            field.setOnAction(_ -> apply());
            field.focusedProperty().addListener((_, wasFocused, focused) -> {
                if (Boolean.TRUE.equals(wasFocused) && !focused) {
                    apply();
                }
            });
        }
        actionsEditor.setOnEdit(rows -> {
            List<ElementaryActionSpec> actions = NetworkActionEdits.parseActions(rows);
            if (!refreshing) {
                change(spec(actions));
            }
        });
        addActionsButton.setOnAction(_ -> addActions(picker));
        Label help = new Label(Messages.get("crac.networkAction.actionsHelp"));
        for (Label label : List.of(help, problemLabel, errorLabel)) {
            FormFields.wrap(label);
        }
        problemLabel.getStyleClass().add("alert-label");
        errorLabel.getStyleClass().add("alert-label");
        usageRulesPane = new UsageRulesPane(usageRules, rules -> edit(() -> (NetworkAction) UsageRuleEdits.set(crac, network, action, rules)));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.addRow(0, FormFields.boldLabel("crac.details.id"), idField);
        grid.addRow(1, FormFields.boldLabel("crac.details.name"), nameField);
        grid.addRow(2, FormFields.boldLabel("crac.details.operator"), operatorField);
        grid.addRow(3, FormFields.boldLabel("crac.details.speed"), speedField);
        grid.addRow(4, FormFields.boldLabel("crac.details.activationCost"), activationCostField);
        root = new VBox(8, grid, errorLabel, FormFields.boldLabel("crac.networkAction.actions"), actionsEditor, addActionsButton, help, problemLabel,
                FormFields.boldLabel("crac.details.usageRules"), usageRulesPane.root());
        refresh();
    }

    Node root() {
        return root;
    }

    // the one edited: a new one after a replacement
    NetworkAction action() {
        return action;
    }

    private void refresh() {
        refreshing = true;
        try {
            Optional<NetworkActionSpec> spec = NetworkActionSpec.of(action);
            idField.setText(action.getId());
            nameField.setText(action.getName().equals(action.getId()) ? "" : action.getName());
            operatorField.setText(action.getOperator() == null ? "" : action.getOperator());
            speedField.setText(FormFields.text(action.getSpeed().orElse(null)));
            activationCostField.setText(FormFields.text(action.getActivationCost().orElse(null)));
            actionsEditor.setRows(spec.map(s -> NetworkActionEdits.actionRows(s.actions()))
                    .orElseGet(() -> action.getElementaryActions().stream().map(elementary -> List.of(elementary.getType(),
                            NetworkAction.getNetworkElementId(elementary), "")).toList()));
            boolean editable = spec.isPresent();
            for (TextField field : List.of(idField, nameField, operatorField, speedField, activationCostField)) {
                field.setEditable(editable);
            }
            actionsEditor.setDisable(!editable);
            addActionsButton.setDisable(!editable);
            usageRulesPane.setRules(UsageRuleEdits.of(action), editable);
            List<String> problems = new ArrayList<>();
            if (!editable) {
                problems.add(Messages.get("networkActionEdits.unsupported", action.getId()));
            }
            List<String> missing = CracElements.missing(network, action);
            if (!missing.isEmpty()) {
                problems.add(Messages.get("crac.missingElements", String.join(", ", missing)));
            }
            problemLabel.setText(String.join("\n", problems));
            problemLabel.setVisible(!problems.isEmpty());
        } finally {
            refreshing = false;
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null ? "" : message);
        errorLabel.setVisible(message != null);
    }

    // the fields' network action, with these elementary actions
    private NetworkActionSpec spec(List<ElementaryActionSpec> actions) {
        return new NetworkActionSpec(idField.getText(), nameField.getText(), operatorField.getText(), FormFields.integer(speedField, "crac.details.speed"),
                FormFields.number(activationCostField, "crac.details.activationCost"), actions);
    }

    private List<ElementaryActionSpec> currentActions() {
        return NetworkActionSpec.of(action).map(NetworkActionSpec::actions).orElse(List.of());
    }

    // a field's change: the elementary actions are the action's, those of the table being applied by their own edits
    private void apply() {
        if (refreshing || NetworkActionSpec.of(action).isEmpty()) {
            return;
        }
        try {
            change(spec(currentActions()));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            refresh();
        }
    }

    // the default actions on the elements picked, added to the action's
    private void addActions(Function<Set<String>, List<String>> picker) {
        List<ElementaryActionSpec> actions = new ArrayList<>(currentActions());
        Set<String> present = actions.stream().flatMap(elementary -> elementary.elementIds().stream()).collect(Collectors.toCollection(HashSet::new));
        for (String id : picker.apply(Set.copyOf(present))) {
            Identifiable<?> identifiable = network.getIdentifiable(id);
            if (identifiable != null && present.add(id)) {
                NetworkActionEdits.defaultAction(identifiable).ifPresent(actions::add);
            }
        }
        if (actions.size() == currentActions().size()) {
            return;
        }
        try {
            change(spec(actions));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            refresh();
        }
    }

    // throws when refused, the action as it was, a new object (put back): told onEdited
    private void change(NetworkActionSpec spec) {
        edit(() -> NetworkActionEdits.change(crac, network, action, spec));
    }

    // throws when refused, the action as it was, a new object (put back): told onEdited
    private void edit(Supplier<NetworkAction> edit) {
        String id = action.getId();
        try {
            NetworkAction changed = edit.get();
            showError(null);
            boolean replaced = changed != action;
            action = changed;
            refresh();
            if (replaced) {
                onEdited.accept(action);
            }
        } catch (IllegalArgumentException e) {
            NetworkAction putBack = crac.getNetworkAction(id);
            if (putBack == null) {
                // not put back (see CracEdits.replace): the CRAC changed all the same
                onEdited.accept(action);
            } else if (putBack != action) {
                action = putBack;
                onEdited.accept(action);
            }
            throw e;
        }
    }
}
