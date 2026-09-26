/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.contingency;

import com.powsybl.contingency.Contingency;
import com.powsybl.contingency.ContingencyBuilder;
import com.powsybl.contingency.ContingencyElement;
import com.powsybl.contingency.ContingencyElementType;
import com.powsybl.contingency.list.ContingencyList;
import com.powsybl.contingency.list.DefaultContingencyList;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.ChoiceBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Inline editor for a {@link DefaultContingencyList}: a master table of its explicit {@link Contingency} entries,
 * and (for the selected one) a nested table of its elements. Since both are immutable, every add/remove/edit
 * rebuilds the affected object(s) and pushes a whole new {@link DefaultContingencyList} via {@code onReplace}.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class DefaultContingencyListFormController {

    @FXML
    private TextField nameField;

    @FXML
    private TableView<Contingency> contingenciesTableView;

    @FXML
    private TableColumn<Contingency, String> idColumn;

    @FXML
    private TableColumn<Contingency, String> elementsColumn;

    @FXML
    private Button removeContingencyButton;

    @FXML
    private TableView<ContingencyElementRow> elementsTableView;

    @FXML
    private TableColumn<ContingencyElementRow, ContingencyElementType> elementTypeColumn;

    @FXML
    private TableColumn<ContingencyElementRow, String> elementIdColumn;

    @FXML
    private Button removeElementButton;

    private final ObservableList<Contingency> contingencies = FXCollections.observableArrayList();
    private final ObservableList<ContingencyElementRow> elementRows = FXCollections.observableArrayList();

    private Consumer<ContingencyList> onReplace;
    // Suppresses the master table's selection listener (bindElements) from resetting elementRows when the
    // selection-index change it's reacting to is this form's own in-place rebuild of the selected row, not a
    // genuine row change - same rationale as ContingenciesController.applyingReplace.
    private boolean applyingElementChange;

    @FXML
    private void initialize() {
        contingenciesTableView.setItems(contingencies);
        idColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getId()));
        idColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        idColumn.setOnEditCommit(event -> renameContingency(event.getRowValue(), event.getNewValue()));
        elementsColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(elementsSummary(cellData.getValue())));
        removeContingencyButton.disableProperty().bind(contingenciesTableView.getSelectionModel().selectedItemProperty().isNull());
        contingenciesTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> bindElements(newValue));

        elementsTableView.setItems(elementRows);
        elementTypeColumn.setCellValueFactory(cellData -> cellData.getValue().typeProperty());
        elementTypeColumn.setCellFactory(ChoiceBoxTableCell.forTableColumn(ContingencyElementType.values()));
        elementTypeColumn.setOnEditCommit(event -> {
            event.getRowValue().typeProperty().set(event.getNewValue());
            commitElements();
        });
        elementIdColumn.setCellValueFactory(cellData -> cellData.getValue().idProperty());
        elementIdColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        elementIdColumn.setOnEditCommit(event -> {
            event.getRowValue().idProperty().set(event.getNewValue());
            commitElements();
        });
        removeElementButton.disableProperty().bind(elementsTableView.getSelectionModel().selectedItemProperty().isNull());

        nameField.setOnAction(event -> commitContingencies());
        nameField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (Boolean.FALSE.equals(isFocused)) {
                commitContingencies();
            }
        });
    }

    public void setContingencyList(DefaultContingencyList list, Consumer<ContingencyList> onReplace) {
        this.onReplace = onReplace;
        nameField.setText(list.getName());
        contingencies.setAll(list.getContingencies());
    }

    private void bindElements(Contingency contingency) {
        if (applyingElementChange) {
            return;
        }
        elementRows.setAll(contingency == null ? List.of() : toRows(contingency.getElements()));
    }

    private static List<ContingencyElementRow> toRows(List<ContingencyElement> elements) {
        return elements.stream().map(e -> new ContingencyElementRow(e.getType(), e.getId())).collect(Collectors.toList());
    }

    private void renameContingency(Contingency row, String newId) {
        int index = contingencies.indexOf(row);
        if (index < 0 || newId == null || newId.isBlank()) {
            return;
        }
        Contingency rebuilt = new Contingency(newId, row.getElements());
        applyingElementChange = true;
        try {
            contingencies.set(index, rebuilt);
            if (contingenciesTableView.getSelectionModel().getSelectedItem() == row) {
                contingenciesTableView.getSelectionModel().select(rebuilt);
            }
        } finally {
            applyingElementChange = false;
        }
        commitContingencies();
    }

    private void commitElements() {
        Contingency selected = contingenciesTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        int index = contingencies.indexOf(selected);
        if (index < 0) {
            return;
        }
        Contingency rebuilt = buildFromRows(selected.getId(), elementRows);
        applyingElementChange = true;
        try {
            contingencies.set(index, rebuilt);
            contingenciesTableView.getSelectionModel().select(rebuilt);
        } finally {
            applyingElementChange = false;
        }
        commitContingencies();
    }

    private void commitContingencies() {
        onReplace.accept(new DefaultContingencyList(nameField.getText(), List.copyOf(contingencies)));
    }

    @FXML
    private void onAddContingency() {
        Contingency created = new Contingency(uniqueId(), List.of());
        contingencies.add(created);
        contingenciesTableView.getSelectionModel().select(created);
        commitContingencies();
    }

    @FXML
    private void onRemoveContingency() {
        Contingency selected = contingenciesTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        contingencies.remove(selected);
        commitContingencies();
    }

    @FXML
    private void onAddElement() {
        if (contingenciesTableView.getSelectionModel().getSelectedItem() == null) {
            return;
        }
        elementRows.add(new ContingencyElementRow(ContingencyElementType.LINE, ""));
    }

    @FXML
    private void onRemoveElement() {
        ContingencyElementRow selected = elementsTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        elementRows.remove(selected);
        commitElements();
    }

    private String uniqueId() {
        Set<String> existingIds = contingencies.stream().map(Contingency::getId).collect(Collectors.toSet());
        int n = contingencies.size() + 1;
        String candidate;
        do {
            candidate = "contingency-" + n++;
        } while (existingIds.contains(candidate));
        return candidate;
    }

    private static String elementsSummary(Contingency contingency) {
        return contingency.getElements().stream()
                .map(element -> element.getType() + ":" + element.getId())
                .collect(Collectors.joining(", "));
    }

    private static Contingency buildFromRows(String id, List<ContingencyElementRow> rows) {
        ContingencyBuilder builder = Contingency.builder(id);
        for (ContingencyElementRow row : rows) {
            addToBuilder(builder, row.getType(), row.getId());
        }
        return builder.build();
    }

    private static void addToBuilder(ContingencyBuilder builder, ContingencyElementType type, String elementId) {
        switch (type) {
            case GENERATOR -> builder.addGenerator(elementId);
            case STATIC_VAR_COMPENSATOR -> builder.addStaticVarCompensator(elementId);
            case SHUNT_COMPENSATOR -> builder.addShuntCompensator(elementId);
            case BRANCH -> builder.addBranch(elementId);
            case HVDC_LINE -> builder.addHvdcLine(elementId);
            case BUSBAR_SECTION -> builder.addBusbarSection(elementId);
            case DANGLING_LINE, BOUNDARY_LINE -> builder.addBoundaryLine(elementId);
            case LINE -> builder.addLine(elementId);
            case TWO_WINDINGS_TRANSFORMER -> builder.addTwoWindingsTransformer(elementId);
            case THREE_WINDINGS_TRANSFORMER -> builder.addThreeWindingsTransformer(elementId);
            case LOAD -> builder.addLoad(elementId);
            case SWITCH -> builder.addSwitch(elementId);
            case BATTERY -> builder.addBattery(elementId);
            case BUS -> builder.addBus(elementId);
            case TIE_LINE -> builder.addTieLine(elementId);
            case VOLTAGE_SOURCE_CONVERTER -> builder.addVoltageSourceConverter(elementId);
            case DC_LINE -> builder.addDcLine(elementId);
            case DC_GROUND -> builder.addDcGround(elementId);
            case DC_NODE -> builder.addDcNode(elementId);
        }
    }
}
