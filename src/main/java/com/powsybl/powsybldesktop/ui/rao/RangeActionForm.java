/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.rao;

import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.rangeaction.RangeAction;
import com.powsybl.powsybldesktop.rao.CracElements;
import com.powsybl.powsybldesktop.rao.RangeActionEdits;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.ConnectedAreaSpec;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.KeyedElement;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.RangeActionSpec;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.RangeSpec;
import com.powsybl.powsybldesktop.rao.RangeActionEdits.Type;
import com.powsybl.powsybldesktop.rao.UsageRuleEdits;
import com.powsybl.powsybldesktop.ui.controls.RowsTableEditor;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The form of a range action of the CRAC view: its id, name, operator, speed, costs, group, ranges and, by type, its PST
 * or HVDC line, its injections and their keys, its counter-trade areas. OpenRAO's range actions are immutable: each
 * change replaces the action by a new one with the same usage rules ({@link RangeActionEdits#change}), applied at once
 * (Enter or focus loss for the text fields, each edit for the tables) and told {@code onEdited}; a refused change is told
 * and the fields reverted (the tables kept as typed, to be fixed). Its usage rules are replaced likewise
 * ({@link UsageRulesPane}, {@link UsageRuleEdits#set}). A counter-trade, which OpenRAO 7.5.1 can't remove, is read-only.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class RangeActionForm {

    /**
     * The ids of the network elements picked for a range action of a type, given those it is already on.
     */
    interface Picker {
        List<String> pick(Type type, Set<String> present);
    }

    private final Crac crac;
    private final Network network;
    private final Consumer<RangeAction<?>> onEdited;
    private final Type type;
    private RangeAction<?> action;
    private boolean refreshing;
    private final VBox root;
    final TextField idField = new TextField();
    final TextField nameField = new TextField();
    final TextField operatorField = new TextField();
    final TextField speedField = new TextField();
    final TextField activationCostField = new TextField();
    final TextField groupIdField = new TextField();
    final TextField upVariationCostField = new TextField();
    final TextField downVariationCostField = new TextField();
    final TextField initialSetpointField = new TextField();
    final TextField elementField = new TextField();
    final TextField areaField = new TextField();
    final Button pickElementButton = new Button(Messages.get("crac.rangeAction.pickElement"));
    final Label initialTapLabel = new Label();
    final RowsTableEditor rangesEditor = new RowsTableEditor(List.of(Messages.get("crac.rangeAction.column.rangeType"),
            Messages.get("crac.rangeAction.column.min"), Messages.get("crac.rangeAction.column.max")));
    final RowsTableEditor keysEditor = new RowsTableEditor(List.of(Messages.get("crac.rangeAction.column.injection"),
            Messages.get("crac.rangeAction.column.key")));
    final Button addInjectionsButton = new Button(Messages.get("crac.rangeAction.addInjections"));
    final RowsTableEditor connectedAreasEditor = new RowsTableEditor(List.of(Messages.get("crac.rangeAction.column.area"),
            Messages.get("crac.rangeAction.column.rangeType"), Messages.get("crac.rangeAction.column.min"), Messages.get("crac.rangeAction.column.max")));
    final UsageRulesPane usageRulesPane;
    final Label problemLabel = new Label();
    final Label errorLabel = new Label();

    RangeActionForm(Crac crac, Network network, RangeAction<?> action, Consumer<RangeAction<?>> onEdited, UsageRulesPane.Context usageRules, Picker picker) {
        this.crac = crac;
        this.network = network;
        this.action = action;
        this.onEdited = onEdited;
        usageRulesPane = new UsageRulesPane(usageRules, rules -> edit(() -> (RangeAction<?>) UsageRuleEdits.set(crac, network, this.action, rules)));
        this.type = current().type();

        for (TextField field : textFields()) {
            field.setOnAction(_ -> apply());
            field.focusedProperty().addListener((_, wasFocused, focused) -> {
                if (Boolean.TRUE.equals(wasFocused) && !focused) {
                    apply();
                }
            });
        }
        rangesEditor.setOnEdit(rows -> {
            List<RangeSpec> ranges = RangeActionEdits.parseRanges(rows);
            if (!refreshing) {
                change(spec(current().elements(), current().connectedAreas(), ranges));
            }
        });
        keysEditor.setOnEdit(rows -> {
            List<KeyedElement> keys = RangeActionEdits.parseKeys(rows);
            if (!refreshing) {
                change(spec(keys, current().connectedAreas(), current().ranges()));
            }
        });
        connectedAreasEditor.setOnEdit(rows -> {
            List<ConnectedAreaSpec> areas = RangeActionEdits.parseConnectedAreas(rows);
            if (!refreshing) {
                change(spec(current().elements(), areas, current().ranges()));
            }
        });
        pickElementButton.setOnAction(_ -> picker.pick(type, Set.of(elementField.getText().trim())).stream().findFirst().ifPresent(id -> {
            elementField.setText(id);
            apply();
        }));
        addInjectionsButton.setOnAction(_ -> addInjections(picker));
        Label rangesHelp = new Label(Messages.get(type == Type.PST ? "crac.rangeAction.tapRangesHelp" : "crac.rangeAction.rangesHelp"));
        Label areasHelp = new Label(Messages.get("crac.rangeAction.connectedAreasHelp"));
        for (Label label : List.of(rangesHelp, areasHelp, initialTapLabel, problemLabel, errorLabel)) {
            FormFields.wrap(label);
        }
        problemLabel.getStyleClass().add("alert-label");
        errorLabel.getStyleClass().add("alert-label");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.addRow(0, FormFields.boldLabel("crac.details.id"), idField);
        grid.addRow(1, FormFields.boldLabel("crac.details.name"), nameField);
        grid.addRow(2, FormFields.boldLabel("crac.details.actionType"), new Label(type.label()));
        switch (type) {
            case PST -> {
                grid.addRow(3, FormFields.boldLabel("crac.rangeAction.pst"), elementRow());
                grid.addRow(4, FormFields.boldLabel("crac.rangeAction.initialTap"), initialTapLabel);
            }
            case HVDC -> {
                grid.addRow(3, FormFields.boldLabel("crac.rangeAction.hvdcLine"), elementRow());
                grid.addRow(4, FormFields.boldLabel("crac.rangeAction.initialSetpoint"), initialSetpointField);
            }
            case INJECTION -> grid.addRow(3, FormFields.boldLabel("crac.rangeAction.initialSetpoint"), initialSetpointField);
            case COUNTER_TRADE -> {
                grid.addRow(3, FormFields.boldLabel("crac.rangeAction.area"), areaField);
                grid.addRow(4, FormFields.boldLabel("crac.rangeAction.initialSetpoint"), initialSetpointField);
            }
        }
        int row = grid.getRowCount();
        grid.addRow(row, FormFields.boldLabel("crac.details.operator"), operatorField);
        grid.addRow(row + 1, FormFields.boldLabel("crac.details.speed"), speedField);
        grid.addRow(row + 2, FormFields.boldLabel("crac.details.activationCost"), activationCostField);
        grid.addRow(row + 3, FormFields.boldLabel("crac.rangeAction.upVariationCost"), upVariationCostField);
        grid.addRow(row + 4, FormFields.boldLabel("crac.rangeAction.downVariationCost"), downVariationCostField);
        grid.addRow(row + 5, FormFields.boldLabel("crac.rangeAction.groupId"), groupIdField);

        root = new VBox(8, grid, errorLabel);
        if (type == Type.INJECTION) {
            root.getChildren().addAll(FormFields.boldLabel("crac.rangeAction.injections"), keysEditor, addInjectionsButton);
        }
        if (type == Type.COUNTER_TRADE) {
            root.getChildren().addAll(FormFields.boldLabel("crac.rangeAction.connectedAreas"), connectedAreasEditor, areasHelp);
        }
        root.getChildren().addAll(FormFields.boldLabel(type == Type.PST ? "crac.rangeAction.tapRanges" : "crac.rangeAction.ranges"), rangesEditor, rangesHelp,
                problemLabel, FormFields.boldLabel("crac.details.usageRules"), usageRulesPane.root());
        refresh();
    }

    private Node elementRow() {
        HBox.setHgrow(elementField, Priority.ALWAYS);
        return new HBox(4, elementField, pickElementButton);
    }

    private List<TextField> textFields() {
        return List.of(idField, nameField, operatorField, speedField, activationCostField, groupIdField, upVariationCostField, downVariationCostField,
                initialSetpointField, elementField, areaField);
    }

    Node root() {
        return root;
    }

    // the one edited: a new one after a replacement
    RangeAction<?> action() {
        return action;
    }

    // the form is only made for an action of a supported type
    private RangeActionSpec current() {
        return RangeActionSpec.of(action).orElseThrow();
    }

    private void refresh() {
        refreshing = true;
        try {
            RangeActionSpec spec = current();
            idField.setText(spec.id());
            nameField.setText(spec.name().equals(spec.id()) ? "" : spec.name());
            operatorField.setText(FormFields.text(spec.operator()));
            speedField.setText(FormFields.text(spec.speed()));
            activationCostField.setText(FormFields.text(spec.activationCost()));
            groupIdField.setText(FormFields.text(spec.groupId()));
            upVariationCostField.setText(FormFields.text(spec.upVariationCost()));
            downVariationCostField.setText(FormFields.text(spec.downVariationCost()));
            initialSetpointField.setText(FormFields.text(spec.initialSetpoint()));
            elementField.setText(spec.elements().size() == 1 && type != Type.INJECTION ? spec.elements().getFirst().id() : "");
            areaField.setText(FormFields.text(spec.areaId()));
            initialTapLabel.setText(spec.pstTaps() == null ? "" : Integer.toString(spec.pstTaps().initialTap()));
            rangesEditor.setRows(RangeActionEdits.rangeRows(spec.ranges()));
            keysEditor.setRows(RangeActionEdits.keyRows(spec.elements()));
            connectedAreasEditor.setRows(RangeActionEdits.connectedAreaRows(spec.connectedAreas()));
            // OpenRAO 7.5.1 can't remove a counter-trade, so can't replace it
            boolean editable = type != Type.COUNTER_TRADE;
            for (TextField field : textFields()) {
                field.setEditable(editable);
            }
            for (Node node : List.<Node>of(rangesEditor, keysEditor, connectedAreasEditor, pickElementButton, addInjectionsButton)) {
                node.setDisable(!editable);
            }
            usageRulesPane.setRules(UsageRuleEdits.of(action), editable);
            List<String> problems = new ArrayList<>();
            if (!editable) {
                problems.add(Messages.get("rangeActionEdits.counterTradeFixed"));
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

    // the fields' range action, with these injections, connected areas and ranges; a PST keeps its taps but on another
    // transformer, whose taps are the network's
    private RangeActionSpec spec(List<KeyedElement> keys, List<ConnectedAreaSpec> areas, List<RangeSpec> ranges) {
        RangeActionSpec current = current();
        List<KeyedElement> elements = switch (type) {
            case PST, HVDC -> List.of(new KeyedElement(elementField.getText(), 1));
            case INJECTION -> keys;
            case COUNTER_TRADE -> List.of();
        };
        boolean sameElement = type == Type.PST && elements.getFirst().id().equals(current.elements().getFirst().id());
        return new RangeActionSpec(type, idField.getText(), nameField.getText(), operatorField.getText(), FormFields.integer(speedField, "crac.details.speed"),
                FormFields.number(activationCostField, "crac.details.activationCost"), groupIdField.getText(),
                FormFields.number(upVariationCostField, "crac.rangeAction.upVariationCost"),
                FormFields.number(downVariationCostField, "crac.rangeAction.downVariationCost"), elements,
                type == Type.PST ? null : FormFields.number(initialSetpointField, "crac.rangeAction.initialSetpoint"), sameElement ? current.pstTaps() : null,
                type == Type.COUNTER_TRADE ? areaField.getText() : null, type == Type.COUNTER_TRADE ? areas : List.of(), ranges);
    }

    // a field's change: the tables are the action's, theirs being applied by their own edits
    private void apply() {
        if (refreshing || type == Type.COUNTER_TRADE) {
            return;
        }
        try {
            RangeActionSpec current = current();
            change(spec(current.elements(), current.connectedAreas(), current.ranges()));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            refresh();
        }
    }

    // the generators and loads picked, of key 1, added to the action's injections
    private void addInjections(Picker picker) {
        List<KeyedElement> keys = new ArrayList<>(current().elements());
        Set<String> present = keys.stream().map(KeyedElement::id).collect(Collectors.toCollection(HashSet::new));
        for (String id : picker.pick(Type.INJECTION, Set.copyOf(present))) {
            Identifiable<?> injection = network.getIdentifiable(id);
            if ((injection instanceof Generator || injection instanceof Load) && present.add(id)) {
                keys.add(new KeyedElement(id, 1));
            }
        }
        if (keys.size() == current().elements().size()) {
            return;
        }
        try {
            change(spec(keys, List.of(), current().ranges()));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            refresh();
        }
    }

    // throws when refused, the action as it was, a new object (put back): told onEdited
    private void change(RangeActionSpec spec) {
        edit(() -> RangeActionEdits.change(crac, network, action, spec));
    }

    // throws when refused, the action as it was, a new object (put back): told onEdited
    private void edit(Supplier<RangeAction<?>> edit) {
        String id = action.getId();
        try {
            RangeAction<?> changed = edit.get();
            showError(null);
            boolean replaced = changed != action;
            action = changed;
            refresh();
            if (replaced) {
                onEdited.accept(action);
            }
        } catch (IllegalArgumentException e) {
            RangeAction<?> putBack = crac.getRangeAction(id);
            if (putBack != null && putBack != action) {
                action = putBack;
                onEdited.accept(action);
            }
            throw e;
        }
    }
}
