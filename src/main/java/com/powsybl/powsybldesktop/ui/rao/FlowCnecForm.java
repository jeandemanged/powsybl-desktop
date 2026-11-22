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
import com.powsybl.openrao.data.crac.api.cnec.FlowCnec;
import com.powsybl.powsybldesktop.rao.CnecEdits;
import com.powsybl.powsybldesktop.rao.CnecEdits.FlowCnecSpec;
import com.powsybl.powsybldesktop.rao.CnecEdits.StateRef;
import com.powsybl.powsybldesktop.rao.CnecEdits.ThresholdSpec;
import com.powsybl.powsybldesktop.rao.CracElements;
import com.powsybl.powsybldesktop.ui.controls.RowsTableEditor;
import com.powsybl.powsybldesktop.utils.Messages;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The form of a flow CNEC of the CRAC view. Its role (optimized, monitored) is changed in place; any other change
 * replaces the CNEC by a new one ({@link CnecEdits#change}), refused while a usage rule refers to it, those fields then
 * read-only. Each change is applied at once (Enter or focus loss for the text fields, each edit for the thresholds) and
 * told {@code onEdited}, with the CNEC as edited; a refused change is told below the fields and the fields reverted (the
 * thresholds kept as typed, to be fixed).
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
final class FlowCnecForm {

    private final Crac crac;
    private final Network network;
    private final Consumer<FlowCnec> onEdited;
    private final Supplier<Optional<String>> branchPicker;
    private FlowCnec cnec;
    // the branch picked, not yet applied when the change is refused
    private String branchId;
    private boolean refreshing;
    private final VBox root;
    final TextField idField = new TextField();
    final TextField nameField = new TextField();
    final Label branchLabel = new Label();
    final Button changeBranchButton = new Button(Messages.get("crac.cnec.changeBranch"));
    final ChoiceBox<StateRef> stateChoiceBox = new ChoiceBox<>();
    final CheckBox optimizedCheckBox = new CheckBox(Messages.get("crac.cnec.optimized"));
    final CheckBox monitoredCheckBox = new CheckBox(Messages.get("crac.cnec.monitored"));
    final TextField reliabilityMarginField = new TextField();
    final TextField operatorField = new TextField();
    final TextField borderField = new TextField();
    final TextField nominalVoltage1Field = new TextField();
    final TextField nominalVoltage2Field = new TextField();
    final TextField iMax1Field = new TextField();
    final TextField iMax2Field = new TextField();
    final RowsTableEditor thresholdsEditor = new RowsTableEditor(List.of(Messages.get("crac.cnec.column.side"), Messages.get("crac.cnec.column.unit"),
            Messages.get("crac.cnec.column.min"), Messages.get("crac.cnec.column.max")));
    final Label referencesLabel = new Label();
    final Label problemLabel = new Label();
    final Label errorLabel = new Label();

    /**
     * @param stateName  the text of a state
     * @param branchPicker the id of a branch picked to replace the CNEC's, if any
     */
    FlowCnecForm(Crac crac, Network network, FlowCnec cnec, Consumer<FlowCnec> onEdited, Function<StateRef, String> stateName,
                 Supplier<Optional<String>> branchPicker) {
        this.crac = crac;
        this.network = network;
        this.cnec = cnec;
        this.onEdited = onEdited;
        this.branchPicker = branchPicker;

        for (TextField field : List.of(idField, nameField, reliabilityMarginField, operatorField, borderField, nominalVoltage1Field, nominalVoltage2Field,
                iMax1Field, iMax2Field)) {
            field.setOnAction(_ -> apply());
            field.focusedProperty().addListener((_, wasFocused, focused) -> {
                if (Boolean.TRUE.equals(wasFocused) && !focused) {
                    apply();
                }
            });
        }
        stateChoiceBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(StateRef state) {
                return state == null ? "" : stateName.apply(state);
            }

            @Override
            public StateRef fromString(String string) {
                throw new UnsupportedOperationException();
            }
        });
        stateChoiceBox.valueProperty().addListener((_, _, _) -> apply());
        optimizedCheckBox.selectedProperty().addListener((_, _, _) -> apply());
        monitoredCheckBox.selectedProperty().addListener((_, _, _) -> apply());
        changeBranchButton.setOnAction(_ -> branchPicker.get().ifPresent(picked -> {
            branchId = picked;
            apply();
        }));
        thresholdsEditor.setOnEdit(rows -> {
            List<ThresholdSpec> thresholds = CnecEdits.parseThresholds(rows);
            if (!refreshing) {
                change(spec(thresholds));
            }
        });
        for (Label label : List.of(referencesLabel, problemLabel, errorLabel)) {
            label.setWrapText(true);
            label.setMinHeight(Region.USE_PREF_SIZE);
            label.managedProperty().bind(label.visibleProperty());
        }
        problemLabel.getStyleClass().add("alert-label");
        errorLabel.getStyleClass().add("alert-label");
        branchLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.addRow(0, boldLabel("crac.details.id"), idField);
        grid.addRow(1, boldLabel("crac.details.name"), nameField);
        grid.addRow(2, boldLabel("crac.cnec.branch"), new HBox(6, branchLabel, changeBranchButton));
        grid.addRow(3, boldLabel("crac.details.state"), stateChoiceBox);
        grid.addRow(4, boldLabel("crac.details.role"), new HBox(10, optimizedCheckBox, monitoredCheckBox));
        grid.addRow(5, boldLabel("crac.details.reliabilityMargin"), reliabilityMarginField);
        grid.addRow(6, boldLabel("crac.details.operator"), operatorField);
        grid.addRow(7, boldLabel("crac.cnec.border"), borderField);
        grid.addRow(8, boldLabel("crac.cnec.nominalVoltages"), new HBox(6, nominalVoltage1Field, nominalVoltage2Field));
        grid.addRow(9, boldLabel("crac.cnec.iMax"), new HBox(6, iMax1Field, iMax2Field));
        for (TextField field : List.of(nominalVoltage1Field, nominalVoltage2Field, iMax1Field, iMax2Field)) {
            field.setPrefColumnCount(7);
        }
        nominalVoltage1Field.setPromptText(Messages.get("crac.cnec.side1"));
        nominalVoltage2Field.setPromptText(Messages.get("crac.cnec.side2"));
        iMax1Field.setPromptText(Messages.get("crac.cnec.side1"));
        iMax2Field.setPromptText(Messages.get("crac.cnec.side2"));
        root = new VBox(8, grid, errorLabel, boldLabel("crac.details.thresholds"), thresholdsEditor, problemLabel, referencesLabel);
        refresh();
    }

    Node root() {
        return root;
    }

    // the one edited: a new one after a replacement
    FlowCnec cnec() {
        return cnec;
    }

    private static Label boldLabel(String key) {
        Label label = new Label(Messages.get(key));
        label.getStyleClass().add("crac-details-label");
        return label;
    }

    private static String text(Double value) {
        return value == null || value.isNaN() ? "" : value == Math.rint(value) ? Long.toString(value.longValue()) : value.toString();
    }

    private String elementName(String id) {
        Identifiable<?> identifiable = network.getIdentifiable(id);
        if (identifiable == null) {
            return Messages.get("crac.details.missingElement", id);
        }
        return identifiable.getNameOrId().equals(id) ? id : identifiable.getNameOrId() + " (" + id + ")";
    }

    private void refresh() {
        refreshing = true;
        try {
            FlowCnecSpec spec = FlowCnecSpec.of(cnec);
            branchId = spec.branchId();
            idField.setText(spec.id());
            nameField.setText(spec.name().equals(spec.id()) ? "" : spec.name());
            branchLabel.setText(elementName(spec.branchId()));
            stateChoiceBox.setItems(FXCollections.observableArrayList(CnecEdits.states(crac)));
            stateChoiceBox.setValue(spec.state());
            optimizedCheckBox.setSelected(spec.optimized());
            monitoredCheckBox.setSelected(spec.monitored());
            reliabilityMarginField.setText(text(spec.reliabilityMargin()));
            operatorField.setText(spec.operator() == null ? "" : spec.operator());
            borderField.setText(spec.border() == null ? "" : spec.border());
            nominalVoltage1Field.setText(text(spec.nominalVoltage1()));
            nominalVoltage2Field.setText(text(spec.nominalVoltage2()));
            iMax1Field.setText(text(spec.iMax1()));
            iMax2Field.setText(text(spec.iMax2()));
            thresholdsEditor.setRows(CnecEdits.thresholdRows(spec.thresholds()));
            List<String> references = CnecEdits.references(crac, cnec.getId());
            referencesLabel.setText(references.isEmpty() ? Messages.get("crac.cnec.notReferenced")
                    : Messages.get("crac.cnec.referencedBy", String.join(", ", references)));
            // a referenced CNEC can't be replaced: only its role can change
            boolean replaceable = references.isEmpty();
            for (TextField field : List.of(idField, nameField, reliabilityMarginField, operatorField, borderField, nominalVoltage1Field, nominalVoltage2Field,
                    iMax1Field, iMax2Field)) {
                field.setEditable(replaceable);
            }
            changeBranchButton.setDisable(!replaceable);
            stateChoiceBox.setDisable(!replaceable);
            thresholdsEditor.setDisable(!replaceable);
            List<String> missing = CracElements.missing(network, cnec);
            problemLabel.setText(missing.isEmpty() ? "" : Messages.get("crac.missingElements", String.join(", ", missing)));
            problemLabel.setVisible(!missing.isEmpty());
        } finally {
            refreshing = false;
        }
    }

    private void showError(String message) {
        errorLabel.setText(message == null ? "" : message);
        errorLabel.setVisible(message != null);
    }

    private static Double number(TextField field, String key) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException(text);
            }
            return value;
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException(Messages.get("crac.cnec.notANumber", Messages.get(key), text));
        }
    }

    // the fields' CNEC, with these thresholds
    private FlowCnecSpec spec(List<ThresholdSpec> thresholds) {
        Double margin = number(reliabilityMarginField, "crac.details.reliabilityMargin");
        return new FlowCnecSpec(idField.getText(), nameField.getText(), branchId, stateChoiceBox.getValue(), optimizedCheckBox.isSelected(),
                monitoredCheckBox.isSelected(), margin == null ? 0 : margin, operatorField.getText(), borderField.getText(),
                number(nominalVoltage1Field, "crac.cnec.nominalVoltages"), number(nominalVoltage2Field, "crac.cnec.nominalVoltages"),
                number(iMax1Field, "crac.cnec.iMax"), number(iMax2Field, "crac.cnec.iMax"), thresholds);
    }

    // a field's change: the thresholds are the CNEC's, those of the table being applied by their own edits
    private void apply() {
        if (refreshing) {
            return;
        }
        try {
            change(spec(FlowCnecSpec.of(cnec).thresholds()));
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            refresh();
        }
    }

    // throws when refused, the CNEC as it was, possibly a new object (OpenRAO put it back): told onEdited
    private void change(FlowCnecSpec spec) {
        String id = cnec.getId();
        try {
            FlowCnec changed = CnecEdits.change(crac, cnec, spec);
            showError(null);
            cnec = changed;
            refresh();
            onEdited.accept(cnec);
        } catch (IllegalArgumentException e) {
            FlowCnec putBack = crac.getFlowCnec(id);
            if (putBack != null && putBack != cnec) {
                cnec = putBack;
                onEdited.accept(cnec);
            }
            throw e;
        }
    }
}
