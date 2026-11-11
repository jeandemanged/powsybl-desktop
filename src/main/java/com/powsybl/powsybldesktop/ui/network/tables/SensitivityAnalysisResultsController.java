/**
 * Copyright (c) 2026, Artelys (https://www.artelys.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.powsybldesktop.ui.network.tables;

import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.Network;
import com.powsybl.powsybldesktop.core.MainModel;
import com.powsybl.powsybldesktop.core.NetworkStudy;
import com.powsybl.powsybldesktop.parameters.GuiParameters;
import com.powsybl.powsybldesktop.sensitivity.SensitivityFactorMatrix;
import com.powsybl.powsybldesktop.sensitivity.SensitivityResultRows;
import com.powsybl.powsybldesktop.sensitivity.SensitivityTexts;
import com.powsybl.powsybldesktop.ui.AbstractDisposableController;
import com.powsybl.powsybldesktop.ui.controls.TableAutoFitLimiter;
import com.powsybl.powsybldesktop.ui.form.TableColumnSupport;
import com.powsybl.powsybldesktop.ui.window.SceneModel;
import com.powsybl.powsybldesktop.ui.window.SceneView;
import com.powsybl.powsybldesktop.utils.Messages;
import com.powsybl.sensitivity.SensitivityAnalysisResult;
import com.powsybl.sensitivity.SensitivityFactor;
import com.powsybl.sensitivity.SensitivityFunctionType;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The network's sensitivity analysis result, one state at a time, optionally one matrix at a time: each factor's
 * function and variable, function reference value and sensitivity. A value below the parameters' thresholds, which
 * Open Load Flow doesn't report, is told apart from one it couldn't compute (NaN). A failed contingency has no values.
 * The result is the root network's, whichever subnetwork is selected.
 *
 * @author Damien Jeandemange {@literal <damien.jeandemange at artelys.com>}
 */
public class SensitivityAnalysisResultsController extends AbstractDisposableController implements SceneView {

    // the matrix choice showing all of them
    private static final int ALL_MATRICES = -1;

    @FXML
    Label alertLabel;
    @FXML
    ChoiceBox<SensitivityResultRows.State> stateChoiceBox;
    @FXML
    ChoiceBox<Integer> matrixChoiceBox;
    @FXML
    Label modeLabel;
    @FXML
    TableView<SensitivityResultRows.Row> tableView;

    private final ObservableList<SensitivityResultRows.Row> rows = FXCollections.observableArrayList();
    private final Label noResultPlaceholder = new Label(Messages.get("sensitivityAnalysisResults.none"));
    private final Label failedPlaceholder = new Label(Messages.get("sensitivityAnalysisResults.stateFailed"));
    private final ListChangeListener<SensitivityFactorMatrix> matricesListener = _ -> updateAlert();
    private ObservableList<SensitivityFactorMatrix> watchedMatrices;
    private List<SensitivityFactorMatrix> resultMatrices = List.of();
    private boolean updating;

    private SceneModel sceneModel;
    private MainModel mainModel;

    @FXML
    private void initialize() {
        alertLabel.managedProperty().bind(alertLabel.visibleProperty());
        stateChoiceBox.setConverter(converter(SensitivityAnalysisResultsController::stateName));
        matrixChoiceBox.setConverter(converter(index -> index == ALL_MATRICES
                ? Messages.get("sensitivityAnalysisResults.allMatrices") : resultMatrices.get(index).name()));
        stateChoiceBox.valueProperty().addListener((_, _, _) -> updateRows());
        matrixChoiceBox.valueProperty().addListener((_, _, _) -> updateRows());

        tableView.setItems(rows);
        TableAutoFitLimiter.install(tableView);
        addTextColumn("matrix", "sensitivityAnalysisResults.column.matrix", 110, row -> resultMatrices.get(row.matrixIndex()).name());
        addTextColumn("functionType", "sensitivityAnalysisResults.column.functionType", 250, row -> SensitivityTexts.function(row.factor().getFunctionType()));
        addTextColumn("function", "sensitivityAnalysisResults.column.function", 130, row -> equipmentName(row.factor().getFunctionId()));
        addTextColumn("variableType", "sensitivityAnalysisResults.column.variableType", 200, row -> SensitivityTexts.variable(row.factor().getVariableType()));
        addTextColumn("variable", "sensitivityAnalysisResults.column.variable", 130, row -> row.factor().isVariableSet()
                ? Messages.get("sensitivityAnalysisResults.variableSet", row.factor().getVariableId()) : equipmentName(row.factor().getVariableId()));
        // not reported either below the thresholds
        addValueColumn("functionReference", "sensitivityAnalysisResults.column.functionReference", false,
                row -> row.value() == null ? null : row.value().getFunctionReference(), row -> referenceQuantity(row.factor()));
        addValueColumn("sensitivity", "sensitivityAnalysisResults.column.sensitivity", true,
                row -> row.value() == null ? null : row.value().getValue(), _ -> GuiParameters.Quantity.SENSITIVITY);
    }

    private static <T> StringConverter<T> converter(Function<T, String> toString) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : toString.apply(value);
            }

            @Override
            public T fromString(String string) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static String stateName(SensitivityResultRows.State state) {
        String name = state.preContingency() ? Messages.get("securityAnalysisResults.preContingency") : state.state().contingencyId();
        return switch (state.status()) {
            case SUCCESS -> name;
            case FAILURE -> Messages.get("sensitivityAnalysisResults.state.failed", name);
            case NO_IMPACT -> Messages.get("sensitivityAnalysisResults.state.noImpact", name);
        };
    }

    private void addTextColumn(String id, String titleKey, double width, Function<SensitivityResultRows.Row, String> value) {
        TableColumn<SensitivityResultRows.Row, String> column = new TableColumn<>(Messages.get(titleKey));
        column.setId(id);
        column.setPrefWidth(width);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(value.apply(cellData.getValue())));
        tableView.getColumns().add(column);
    }

    // null below the thresholds, NaN when not computed: both sorted as 0, told apart if tellMissing, "-" otherwise
    private void addValueColumn(String id, String titleKey, boolean tellMissing, Function<SensitivityResultRows.Row, Double> value,
                                Function<SensitivityResultRows.Row, GuiParameters.Quantity> quantity) {
        TableColumn<SensitivityResultRows.Row, SensitivityResultRows.Row> column = new TableColumn<>(Messages.get(titleKey));
        column.setId(id);
        column.setPrefWidth(120);
        column.setCellValueFactory(cellData -> new ReadOnlyObjectWrapper<>(cellData.getValue()));
        column.setComparator(Comparator.comparingDouble(row -> {
            Double v = value.apply(row);
            return v == null || Double.isNaN(v) ? 0 : v;
        }));
        column.setCellFactory(_ -> new ValueCell(tellMissing, value, quantity));
        tableView.getColumns().add(column);
    }

    private final class ValueCell extends TableCell<SensitivityResultRows.Row, SensitivityResultRows.Row> {
        private final boolean tellMissing;
        private final Function<SensitivityResultRows.Row, Double> value;
        private final Function<SensitivityResultRows.Row, GuiParameters.Quantity> quantity;

        private ValueCell(boolean tellMissing, Function<SensitivityResultRows.Row, Double> value,
                          Function<SensitivityResultRows.Row, GuiParameters.Quantity> quantity) {
            this.tellMissing = tellMissing;
            this.value = value;
            this.quantity = quantity;
        }

        @Override
        protected void updateItem(SensitivityResultRows.Row row, boolean empty) {
            super.updateItem(row, empty);
            getStyleClass().remove("sensitivity-missing");
            setTooltip(null);
            if (empty || row == null) {
                setText(null);
                return;
            }
            Double v = value.apply(row);
            if (tellMissing && (v == null || Double.isNaN(v))) {
                String key = v == null ? "belowThreshold" : "notComputed";
                setText(Messages.get("sensitivityAnalysisResults." + key));
                setTooltip(new Tooltip(Messages.get("sensitivityAnalysisResults." + key + ".tooltip")));
                getStyleClass().add("sensitivity-missing");
            } else {
                setText(TableColumnSupport.doubleFormat(() -> mainModel.getParametersModel().getGuiParameters(), quantity.apply(row)).toString(v));
            }
        }
    }

    private static GuiParameters.Quantity referenceQuantity(SensitivityFactor factor) {
        SensitivityFunctionType type = factor.getFunctionType();
        return switch (type) {
            case BRANCH_CURRENT_1, BRANCH_CURRENT_2, BRANCH_CURRENT_3 -> GuiParameters.Quantity.CURRENT;
            case BUS_VOLTAGE -> GuiParameters.Quantity.VOLTAGE;
            default -> GuiParameters.Quantity.POWER;
        };
    }

    private String equipmentName(String id) {
        Network network = sceneModel.getNetwork();
        Identifiable<?> identifiable = network == null ? null : network.getNetwork().getIdentifiable(id);
        return identifiable == null ? id : identifiable.getNameOrId();
    }

    @Override
    public void setSceneModel(SceneModel sceneModel) {
        this.sceneModel = Objects.requireNonNull(sceneModel);
        this.mainModel = sceneModel.getMainModel();
        updateResults();
        listenerManager.listen(sceneModel.networkProperty(), (_, _, _) -> updateResults());
        listenerManager.listen(mainModel.updateProperty(), (_, _, _) -> updateResults());
        // for the decimal places, read by the cells on every render
        listenerManager.listen(mainModel.getParametersModel().guiParametersRevisionProperty(), (_, _, _) -> tableView.refresh());
    }

    private NetworkStudy study() {
        Network network = sceneModel.getNetwork();
        return network == null ? null : mainModel.getStudy(network);
    }

    // keeps the shown state and matrix when they are still there
    private void updateResults() {
        NetworkStudy study = study();
        watch(study == null ? null : study.getSensitivityFactorMatrices());
        SensitivityAnalysisResult result = study == null ? null : study.getSensitivityAnalysisResult();
        resultMatrices = result == null ? List.of() : study.getSensitivityAnalysisMatrices();
        SensitivityResultRows.State shownState = stateChoiceBox.getValue();
        Integer shownMatrix = matrixChoiceBox.getValue();
        updating = true;
        try {
            List<SensitivityResultRows.State> states = result == null ? List.of() : SensitivityResultRows.states(result);
            stateChoiceBox.getItems().setAll(states);
            stateChoiceBox.setValue(states.stream().filter(state -> shownState != null && state.state().equals(shownState.state()))
                    .findFirst().orElse(states.stream().findFirst().orElse(null)));
            List<Integer> matrices = new ArrayList<>();
            if (result != null) {
                matrices.add(ALL_MATRICES);
                for (int i = 0; i < resultMatrices.size(); i++) {
                    matrices.add(i);
                }
            }
            matrixChoiceBox.getItems().setAll(matrices);
            matrixChoiceBox.setValue(matrices.contains(shownMatrix) ? shownMatrix : matrices.stream().findFirst().orElse(null));
        } finally {
            updating = false;
        }
        stateChoiceBox.setDisable(result == null);
        matrixChoiceBox.setDisable(result == null);
        modeLabel.setText(result == null ? "" : Messages.get(study.isSensitivityAnalysisDc() ? "sensitivityAnalysisResults.mode.dc" : "sensitivityAnalysisResults.mode.ac"));
        updateAlert();
        updateRows();
    }

    private void watch(ObservableList<SensitivityFactorMatrix> matrices) {
        if (watchedMatrices != matrices) {
            if (watchedMatrices != null) {
                watchedMatrices.removeListener(matricesListener);
            }
            watchedMatrices = matrices;
            if (matrices != null) {
                matrices.addListener(matricesListener);
            }
        }
    }

    private void updateAlert() {
        NetworkStudy study = study();
        boolean outdated = study != null && study.getSensitivityAnalysisResult() != null
                && !study.getSensitivityAnalysisMatrices().equals(study.getSensitivityFactorMatrices());
        alertLabel.setText(outdated ? Messages.get("sensitivityAnalysisResults.outdated") : "");
        alertLabel.setVisible(outdated);
    }

    private void updateRows() {
        if (updating) {
            return;
        }
        NetworkStudy study = study();
        SensitivityAnalysisResult result = study == null ? null : study.getSensitivityAnalysisResult();
        SensitivityResultRows.State state = stateChoiceBox.getValue();
        Integer matrix = matrixChoiceBox.getValue();
        if (result == null || state == null || matrix == null) {
            tableView.setPlaceholder(noResultPlaceholder);
            rows.clear();
        } else if (state.status() == SensitivityAnalysisResult.Status.FAILURE) {
            tableView.setPlaceholder(failedPlaceholder);
            rows.clear();
        } else {
            rows.setAll(SensitivityResultRows.rows(result, resultMatrices, state.state()).stream()
                    .filter(row -> matrix == ALL_MATRICES || row.matrixIndex() == matrix)
                    .toList());
        }
    }

    @Override
    public void dispose() {
        watch(null);
        super.dispose();
    }
}
